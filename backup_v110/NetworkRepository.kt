package com.example.data.repo

import com.example.data.db.NexoDao
import com.example.data.db.NetworkServerEntity
import com.example.data.model.LanDevice
import com.example.data.model.RemoteFileEntry
import com.example.data.model.RemoteProtocol
import com.example.data.model.ServerEncoding
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class NetworkRepository(private val dao: NexoDao) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  fun observeSavedServers(): Flow<List<NetworkServerEntity>> = dao.observeNetworkServers()

  suspend fun saveServer(server: NetworkServerEntity) {
    dao.insertNetworkServer(server)
  }

  suspend fun deleteServer(id: Long) {
    dao.deleteNetworkServerById(id)
  }

  suspend fun scanLanDevices(): List<LanDevice> = withContext(Dispatchers.IO) {
    val localIp = getLocalIpv4() ?: return@withContext emptyList()
    val prefix = localIp.substringBeforeLast('.')
    val targetPorts = listOf(445, 139, 21, 22, 80, 443, 8080)

    coroutineScope {
      (1..254).map { hostByte ->
        async {
          val ip = "$prefix.$hostByte"
          val openPorts = mutableListOf<Int>()
          for (port in targetPorts) {
            if (isPortOpen(ip, port, timeoutMs = 150)) {
              openPorts.add(port)
            }
          }
          if (openPorts.isNotEmpty()) {
            val hostName = runCatching {
              val addr = InetAddress.getByName(ip)
              addr.canonicalHostName.takeIf { it != ip } ?: ip
            }.getOrDefault(ip)
            val services = openPorts.map {
              when (it) {
                445, 139 -> "SMB"
                21 -> "FTP"
                22 -> "SFTP/SSH"
                80, 443, 8080 -> "HTTP/WebDAV"
                else -> "TCP:$it"
              }
            }.distinct()
            LanDevice(
              ipAddress = ip,
              hostname = hostName,
              openPorts = openPorts,
              services = services,
            )
          } else {
            null
          }
        }
      }.awaitAll().filterNotNull()
    }
  }

  suspend fun listRemoteDirectory(server: NetworkServerEntity, remotePath: String): Result<List<RemoteFileEntry>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val protocol = runCatching { RemoteProtocol.valueOf(server.protocol) }.getOrDefault(RemoteProtocol.FTP)
        when (protocol) {
          RemoteProtocol.FTP, RemoteProtocol.FTPS -> listFtp(server, remotePath, ftps = protocol == RemoteProtocol.FTPS)
          RemoteProtocol.SFTP -> listSftp(server, remotePath)
          RemoteProtocol.WEBDAV -> listWebDav(server, remotePath)
          RemoteProtocol.SMB -> {
            val hostOnly = server.host.substringBefore('/')
            val shareHint = server.host.substringAfter('/', "")
            if (!isPortOpen(hostOnly, server.port, 2500)) {
              throw IllegalStateException("No se pudo conectar al puerto SMB ${server.port} en $hostOnly")
            }
            val domainInfo = if (server.domain.isNotBlank()) " (Dominio: ${server.domain})" else ""
            val shareText = if (shareHint.isNotBlank()) "/$shareHint" else ""
            throw IllegalStateException(
              "Host SMB activo en $hostOnly:${server.port}$shareText$domainInfo. " +
                "Usa FTP/WebDAV o activa Samba-HTTP en el equipo para transferir directamente."
            )
          }
        }
      }
    }

  suspend fun downloadRemoteFile(
    server: NetworkServerEntity,
    remoteFilePath: String,
    localDestFile: File,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val protocol = runCatching { RemoteProtocol.valueOf(server.protocol) }.getOrDefault(RemoteProtocol.FTP)
      localDestFile.parentFile?.mkdirs()
      when (protocol) {
        RemoteProtocol.FTP, RemoteProtocol.FTPS -> {
          val client = createConfiguredFtpClient(server, protocol == RemoteProtocol.FTPS)
          try {
            connectAndLoginFtp(client, server)
            client.setFileType(FTP.BINARY_FILE_TYPE)
            FileOutputStream(localDestFile).use { fos ->
              if (!client.retrieveFile(remoteFilePath, fos)) {
                throw IllegalStateException("No se pudo descargar $remoteFilePath")
              }
            }
          } finally {
            runCatching { client.logout(); client.disconnect() }
          }
        }
        RemoteProtocol.SFTP -> {
          withSftp(server) { sftp ->
            FileOutputStream(localDestFile).use { fos ->
              sftp.get(remoteFilePath, fos)
            }
          }
        }
        RemoteProtocol.WEBDAV -> {
          val url = buildWebDavUrl(server, remoteFilePath)
          val reqBuilder = Request.Builder().url(url).get()
          applyBasicAuth(reqBuilder, server)
          httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
              throw IllegalStateException("Error WebDAV HTTP ${resp.code}")
            }
            resp.body?.byteStream()?.use { input ->
              FileOutputStream(localDestFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cuerpo de respuesta WebDAV vacío")
          }
        }
        RemoteProtocol.SMB -> throw UnsupportedOperationException("Usa FTP, SFTP o WebDAV para descarga directa")
      }
      localDestFile
    }
  }

  suspend fun uploadFileToRemote(
    server: NetworkServerEntity,
    localFile: File,
    remoteDirPath: String,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val protocol = runCatching { RemoteProtocol.valueOf(server.protocol) }.getOrDefault(RemoteProtocol.FTP)
      val remoteTarget = "${remoteDirPath.trimEnd('/')}/${localFile.name}"
      when (protocol) {
        RemoteProtocol.FTP, RemoteProtocol.FTPS -> {
          val client = createConfiguredFtpClient(server, protocol == RemoteProtocol.FTPS)
          try {
            connectAndLoginFtp(client, server)
            client.setFileType(FTP.BINARY_FILE_TYPE)
            FileInputStream(localFile).use { fis ->
              if (!client.storeFile(remoteTarget, fis)) {
                throw IllegalStateException("No se pudo subir ${localFile.name}")
              }
            }
          } finally {
            runCatching { client.logout(); client.disconnect() }
          }
        }
        RemoteProtocol.SFTP -> {
          withSftp(server) { sftp ->
            FileInputStream(localFile).use { fis ->
              sftp.put(fis, remoteTarget)
            }
          }
        }
        RemoteProtocol.WEBDAV -> {
          val url = buildWebDavUrl(server, remoteTarget)
          val reqBuilder = Request.Builder()
            .url(url)
            .put(localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
          applyBasicAuth(reqBuilder, server)
          httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code !in listOf(200, 201, 204)) {
              throw IllegalStateException("Error subiendo por WebDAV (${resp.code})")
            }
          }
        }
        RemoteProtocol.SMB -> throw UnsupportedOperationException("Usa FTP, SFTP o WebDAV para subida directa")
      }
    }
  }

  private fun createConfiguredFtpClient(server: NetworkServerEntity, ftps: Boolean): FTPClient {
    val client = if (ftps) FTPSClient() else FTPClient()
    val encoding = runCatching { ServerEncoding.valueOf(server.encoding) }.getOrDefault(ServerEncoding.AUTO)
    client.controlEncoding = encoding.charsetName
    client.connectTimeout = 8000
    return client
  }

  private fun connectAndLoginFtp(client: FTPClient, server: NetworkServerEntity) {
    client.connect(server.host, server.port)
    val user = if (server.anonymous || server.username.isBlank()) "anonymous" else server.username
    val pass = if (server.anonymous) "nexofiles@local" else server.password
    if (!client.login(user, pass)) {
      throw IllegalStateException("Credenciales FTP rechazadas para $user")
    }
    if (server.ftpMode.equals("ACTIVE", ignoreCase = true)) {
      client.enterLocalActiveMode()
    } else {
      client.enterLocalPassiveMode()
    }
  }

  private fun listFtp(server: NetworkServerEntity, remotePath: String, ftps: Boolean): List<RemoteFileEntry> {
    val client = createConfiguredFtpClient(server, ftps)
    return try {
      connectAndLoginFtp(client, server)
      val files = client.listFiles(remotePath) ?: emptyArray()
      val base = remotePath.trimEnd('/')
      files.filter { it.name != "." && it.name != ".." }.map { f ->
        RemoteFileEntry(
          name = f.name,
          path = "$base/${f.name}",
          isDirectory = f.isDirectory,
          sizeBytes = f.size,
          modifiedAt = f.timestamp?.timeInMillis ?: 0L,
        )
      }.sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    } finally {
      runCatching { client.logout(); client.disconnect() }
    }
  }

  private fun listWebDav(server: NetworkServerEntity, remotePath: String): List<RemoteFileEntry> {
    val url = buildWebDavUrl(server, remotePath)
    val propfindXml = """
      <?xml version="1.0" encoding="utf-8" ?>
      <D:propfind xmlns:D="DAV:">
        <D:prop>
          <D:displayname/>
          <D:getcontentlength/>
          <D:resourcetype/>
        </D:prop>
      </D:propfind>
    """.trimIndent()
    val reqBuilder = Request.Builder()
      .url(url)
      .method("PROPFIND", propfindXml.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
      .header("Depth", "1")
    applyBasicAuth(reqBuilder, server)

    val xmlResponse = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
      val body = resp.body?.string().orEmpty()
      if (!resp.isSuccessful && resp.code != 207) {
        throw IllegalStateException("Error WebDAV (${resp.code}): ${resp.message}")
      }
      body
    }

    return parseWebDavMultiStatus(xmlResponse, remotePath)
  }

  private fun parseWebDavMultiStatus(xml: String, currentRemotePath: String): List<RemoteFileEntry> {
    if (xml.isBlank()) return emptyList()
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    val responses = doc.getElementsByTagNameNS("*", "response")
    val normalizedCurrent = currentRemotePath.trimEnd('/').ifEmpty { "/" }
    val entries = mutableListOf<RemoteFileEntry>()

    for (i in 0 until responses.length) {
      val el = responses.item(i) as? Element ?: continue
      val href = el.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim().orEmpty()
      val decodedHref = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
      val cleanPath = ("/" + decodedHref.substringAfter("://", decodedHref).let {
        if (decodedHref.contains("://")) it.substringAfter('/', "") else it
      }.trim('/')).let { if (it.isEmpty()) "/" else it }

      if (cleanPath == normalizedCurrent || "$cleanPath/" == "$normalizedCurrent/") continue
      val collectionNodes = el.getElementsByTagNameNS("*", "collection")
      val isDir = collectionNodes.length > 0 || decodedHref.endsWith("/")
      val displayName = el.getElementsByTagNameNS("*", "displayname").item(0)?.textContent?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: cleanPath.trimEnd('/').substringAfterLast('/')
      val contentLen = el.getElementsByTagNameNS("*", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull() ?: 0L

      if (displayName.isNotBlank()) {
        entries.add(
          RemoteFileEntry(
            name = displayName,
            path = cleanPath,
            isDirectory = isDir,
            sizeBytes = contentLen,
            modifiedAt = 0L,
          )
        )
      }
    }
    return entries.sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
  }

  private fun buildWebDavUrl(server: NetworkServerEntity, remotePath: String): String {
    val cleanHost = server.host.trim()
      .removePrefix("https://")
      .removePrefix("http://")
      .trimEnd('/')
    val scheme = if (server.useHttps || server.port == 443) "https" else "http"
    val cleanPath = "/" + remotePath.trimStart('/')
    return "$scheme://$cleanHost:${server.port}$cleanPath"
  }

  private fun applyBasicAuth(builder: Request.Builder, server: NetworkServerEntity) {
    if (!server.anonymous && server.username.isNotBlank()) {
      builder.header("Authorization", Credentials.basic(server.username, server.password))
    }
  }

  private fun listSftp(server: NetworkServerEntity, remotePath: String): List<RemoteFileEntry> {
    val entries = mutableListOf<RemoteFileEntry>()
    withSftp(server) { sftp ->
      val raw = sftp.ls(remotePath)
      val base = remotePath.trimEnd('/')
      for (obj in raw) {
        val entry = obj as? ChannelSftp.LsEntry ?: continue
        if (entry.filename == "." || entry.filename == "..") continue
        entries.add(
          RemoteFileEntry(
            name = entry.filename,
            path = "$base/${entry.filename}",
            isDirectory = entry.attrs.isDir,
            sizeBytes = entry.attrs.size,
            modifiedAt = entry.attrs.mTime * 1000L,
          )
        )
      }
    }
    return entries.sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
  }

  private fun <T> withSftp(server: NetworkServerEntity, block: (ChannelSftp) -> T): T {
    val jsch = JSch()
    var session: Session? = null
    var channel: ChannelSftp? = null
    return try {
      session = jsch.getSession(server.username, server.host, server.port)
      session.setPassword(server.password)
      session.setConfig("StrictHostKeyChecking", "no")
      session.connect(8000)
      channel = session.openChannel("sftp") as ChannelSftp
      channel.connect(8000)
      block(channel)
    } finally {
      runCatching { channel?.disconnect() }
      runCatching { session?.disconnect() }
    }
  }

  private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
    return runCatching {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        true
      }
    }.getOrDefault(false)
  }

  private fun getLocalIpv4(): String? {
    return runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        val addrs = intf.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
        }
      }
      null
    }.getOrNull()
  }
}
