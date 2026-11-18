package com.example.data.repo

import com.example.data.model.VaultPortalState
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

data class StagedVaultPortalBlob(
  val id: String,
  val originalName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val nxvFile: File,
)

/**
 * Ephemeral LAN HTTP portal for unlocked Vault files.
 * Generates a 128-bit CSPRNG random session token, serves encrypted `.nexo_vault` blobs,
 * and delivers a NERV HUD standalone web client capable of 100% local decryption inside
 * mobile Safari (iPhone) and desktop browsers (using WebCrypto OR pure-JS PBKDF2-SHA256 + AES-256-GCM fallback).
 */
class VaultWebPortalServer {

  private val scope = CoroutineScope(Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null
  private var expiryJob: Job? = null
  private var stagingDir: File? = null
  private var stagedMap: Map<String, StagedVaultPortalBlob> = emptyMap()
  private var activeToken: String = ""

  private val _state = MutableStateFlow(VaultPortalState())
  val state: StateFlow<VaultPortalState> = _state.asStateFlow()

  fun start(
    blobs: List<StagedVaultPortalBlob>,
    stageRootDir: File,
    ttlMinutes: Int = 15,
  ) {
    stop(deleteStagedFiles = true)
    stagingDir = stageRootDir
    val tokenBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val token = tokenBytes.joinToString("") { "%02x".format(it) }
    activeToken = token
    stagedMap = blobs.associateBy { it.id }

    acceptJob = scope.launch {
      runCatching {
        val sock = try {
          ServerSocket(8899)
        } catch (_: Exception) {
          ServerSocket(0)
        }
        serverSocket = sock
        val port = sock.localPort
        val ip = detectLocalIpv4() ?: "127.0.0.1"
        val url = "http://$ip:$port/v/$token"
        val expiresAt = System.currentTimeMillis() + ttlMinutes * 60_000L
        _state.value = VaultPortalState(
          isRunning = true,
          url = url,
          token = token,
          port = port,
          expiresAtMs = expiresAt,
          itemCount = blobs.size,
          lastEvent = "SESIÓN ACTIVA · ${blobs.size} ARCHIVO(S) CIFRADOS",
        )

        expiryJob = launch {
          while (isActive && System.currentTimeMillis() < expiresAt) {
            delay(5_000L)
          }
          if (isActive) {
            stop(deleteStagedFiles = true)
          }
        }

        while (isActive && !sock.isClosed) {
          val client = runCatching { sock.accept() }.getOrNull() ?: break
          launch { handleClient(client) }
        }
      }.onFailure { err ->
        _state.value = _state.value.copy(
          isRunning = false,
          lastEvent = "ERROR PORTAL: ${err.localizedMessage}",
        )
      }
    }
  }

  fun stop(deleteStagedFiles: Boolean = true) {
    expiryJob?.cancel()
    expiryJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
    acceptJob?.cancel()
    acceptJob = null
    activeToken = ""
    stagedMap = emptyMap()
    if (deleteStagedFiles) {
      runCatching { stagingDir?.deleteRecursively() }
    }
    stagingDir = null
    _state.value = VaultPortalState(
      isRunning = false,
      lastEvent = "PORTAL DESACTIVADO",
    )
  }

  private fun handleClient(socket: Socket) {
    socket.use { s ->
      val input = BufferedInputStream(s.getInputStream())
      val output = BufferedOutputStream(s.getOutputStream())
      val requestLine = readLine(input) ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val path = parts[1].substringBefore('?')
      while (true) {
        val line = readLine(input) ?: break
        if (line.isEmpty()) break
      }

      val token = activeToken
      val currentState = _state.value
      if (token.isBlank() || System.currentTimeMillis() > currentState.expiresAtMs) {
        writeResponse(output, 401, "text/plain; charset=utf-8", "SESSION EXPIRED".toByteArray())
        return
      }

      when {
        path == "/v/$token" || path == "/v/$token/" -> {
          val html = buildPortalHtml(token, currentState.expiresAtMs)
          writeResponse(output, 200, "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
          _state.value = _state.value.copy(lastEvent = "CONEXIÓN CLIENTE: ${s.inetAddress.hostAddress}")
        }
        path == "/api/$token/items" -> {
          val arr = JSONArray()
          stagedMap.values.forEach { b ->
            arr.put(
              JSONObject().apply {
                put("id", b.id)
                put("name", b.originalName)
                put("mime", b.mimeType)
                put("size", b.sizeBytes)
                put("sizeFmt", FormatUtils.formatBytes(b.sizeBytes))
                put("nxvSize", b.nxvFile.length())
              }
            )
          }
          val payload = JSONObject().apply {
            put("expiresAtMs", currentState.expiresAtMs)
            put("items", arr)
          }.toString().toByteArray(StandardCharsets.UTF_8)
          writeResponse(output, 200, "application/json; charset=utf-8", payload)
        }
        path.startsWith("/api/$token/blob/") -> {
          val id = path.removePrefix("/api/$token/blob/").trim()
          val entry = stagedMap[id]
          if (entry == null || !entry.nxvFile.exists()) {
            writeResponse(output, 404, "text/plain; charset=utf-8", "NOT FOUND".toByteArray())
          } else {
            val file = entry.nxvFile
            val header = "HTTP/1.1 200 OK\r\n" +
              "Content-Type: application/octet-stream\r\n" +
              "Content-Length: ${file.length()}\r\n" +
              "Cache-Control: no-store\r\n" +
              "Connection: close\r\n\r\n"
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            FileInputStream(file).use { fis ->
              val buf = ByteArray(32 * 1024)
              var n = fis.read(buf)
              while (n != -1) {
                output.write(buf, 0, n)
                n = fis.read(buf)
              }
            }
            output.flush()
            _state.value = _state.value.copy(lastEvent = "BLOB DESCARGADO: ${entry.originalName}")
          }
        }
        else -> {
          writeResponse(output, 401, "text/plain; charset=utf-8", "UNAUTHORIZED TOKEN".toByteArray())
        }
      }
    }
  }

  private fun writeResponse(output: BufferedOutputStream, code: Int, contentType: String, body: ByteArray) {
    val statusText = when (code) {
      200 -> "OK"
      401 -> "Unauthorized"
      404 -> "Not Found"
      else -> "Error"
    }
    val header = "HTTP/1.1 $code $statusText\r\n" +
      "Content-Type: $contentType\r\n" +
      "Content-Length: ${body.size}\r\n" +
      "Cache-Control: no-store\r\n" +
      "Connection: close\r\n\r\n"
    output.write(header.toByteArray(StandardCharsets.UTF_8))
    output.write(body)
    output.flush()
  }

  private fun readLine(input: InputStream): String? {
    val sb = StringBuilder()
    while (true) {
      val b = input.read()
      if (b == -1) return if (sb.isEmpty()) null else sb.toString()
      if (b == '\n'.code) {
        if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.deleteCharAt(sb.length - 1)
        return sb.toString()
      }
      sb.append(b.toChar())
    }
  }

  private fun detectLocalIpv4(): String? {
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

  private fun buildPortalHtml(token: String, expiresAtMs: Long): String {
    return """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
  <title>NERV VAULT · PORTAL EFÍMERO</title>
  <style>
    :root { --bg:#08060E; --card:#120E21; --modal:#161129; --green:#22C55E; --purple:#7C3AED; --red:#EF4444; --orange:#F97316; --txt:#F3EFFF; --muted:#AEA1CF; }
    * { box-sizing:border-box; }
    body { margin:0; padding:16px; background:var(--bg); color:var(--txt); font-family:ui-monospace,SFMono-Regular,Menlo,monospace; }
    .hud-panel { max-width:760px; margin:0 auto 14px; background:var(--card); border:1px solid var(--purple); clip-path:polygon(10px 0,100% 0,100% calc(100% - 10px),calc(100% - 10px) 100%,0 100%,0 10px); padding:16px; }
    .hud-header { display:flex; justify-content:space-between; align-items:center; gap:10px; border-bottom:1px solid #332559; padding-bottom:10px; margin-bottom:12px; }
    .badge { padding:4px 8px; font-size:11px; font-weight:700; border:1px solid var(--green); color:var(--green); background:rgba(34,197,94,0.12); }
    .badge-warn { border-color:var(--orange); color:var(--orange); background:rgba(249,115,22,0.12); }
    h1 { margin:0; font-size:15px; letter-spacing:1.2px; color:var(--green); text-transform:uppercase; }
    .sub { font-size:12px; color:var(--muted); margin:4px 0 0; }
    input { width:100%; padding:12px; background:var(--modal); color:var(--txt); border:1px solid var(--purple); font-family:inherit; font-size:14px; margin-bottom:10px; }
    .btn { background:var(--green); color:#08060E; border:none; padding:10px 14px; font-weight:800; font-family:inherit; text-transform:uppercase; cursor:pointer; font-size:12px; letter-spacing:0.8px; }
    .btn-sec { background:var(--modal); color:var(--txt); border:1px solid var(--purple); }
    .btn-err { background:var(--red); color:#fff; }
    .row { display:flex; justify-content:space-between; align-items:center; gap:10px; padding:12px 0; border-bottom:1px solid #332559; }
    .meta { font-size:11px; color:var(--muted); }
    #previewBox { display:none; background:var(--modal); border:1px solid var(--green); padding:14px; max-width:760px; margin:0 auto 14px; }
    #previewMedia img, #previewMedia video { max-width:100%; max-height:65vh; display:block; margin:10px auto; border:1px solid #332559; }
    #previewMedia audio { width:100%; margin:10px 0; }
    #previewMedia pre { white-space:pre-wrap; word-break:break-word; max-height:50vh; overflow:auto; font-size:12px; color:var(--green); background:#08060E; padding:10px; border:1px solid #332559; }
    #statusBar { font-size:12px; color:var(--orange); margin-top:8px; }
  </style>
</head>
<body>
  <div class="hud-panel">
    <div class="hud-header">
      <div>
        <h1>NERV // NEXOVAULT REMOTE</h1>
        <p class="sub">DESENCRIPTACIÓN LOCAL ZERO-KNOWLEDGE EN NAVEGADOR (AES-256-GCM)</p>
      </div>
      <span id="ttlBadge" class="badge badge-warn">TTL --:--</span>
    </div>
    <label for="passInput" style="display:block;font-size:11px;color:var(--muted);margin-bottom:6px;">CLAVE DE SESIÓN (.NEXO_VAULT)</label>
    <input id="passInput" type="password" placeholder="Introduce la contraseña de respaldo..." autocomplete="off"/>
    <div id="statusBar">LISTO · INTRODUCE LA CONTRASEÑA Y SELECCIONA UN ARCHIVO PARA DESCIFRAR EN TU DISPOSITIVO</div>
  </div>

  <div id="previewBox">
    <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
      <strong id="previewTitle" style="color:var(--green);font-size:13px;"></strong>
      <div style="display:flex;gap:6px;">
        <a id="downloadDecryptedBtn" class="btn" style="text-decoration:none;">GUARDAR</a>
        <button class="btn btn-err" onclick="closePreview()">CERRAR</button>
      </div>
    </div>
    <div id="previewMedia"></div>
  </div>

  <div class="hud-panel">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
      <strong style="font-size:13px;letter-spacing:1px;">ARCHIVOS CIFRADOS (.NEXO_VAULT)</strong>
      <span class="badge" id="engineBadge">CRYPTO READY</span>
    </div>
    <div id="listRoot">Cargando inventario cifrado...</div>
  </div>

<script>
const TOKEN = "$token";
const EXPIRES_AT = $expiresAtMs;
const statusBar = document.getElementById("statusBar");
const hasSubtle = !!(window.crypto && window.crypto.subtle);
document.getElementById("engineBadge").textContent = hasSubtle ? "WEBCRYPTO NATIVO" : "AES-GCM JS SAFARI";

function updateTtl() {
  const rem = Math.max(0, Math.floor((EXPIRES_AT - Date.now()) / 1000));
  const m = String(Math.floor(rem / 60)).padStart(2, "0");
  const s = String(rem % 60).padStart(2, "0");
  document.getElementById("ttlBadge").textContent = "TTL " + m + ":" + s;
}
setInterval(updateTtl, 1000);
updateTtl();

// ---- Pure JS SHA-256 / HMAC / PBKDF2 / AES-256-GCM fallback (used on HTTP LAN in iPhone Safari) ----
const K256 = new Uint32Array([
  0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
  0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
  0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
  0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
  0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
  0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
  0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
  0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
]);
function rotr(x, n) { return (x >>> n) | (x << (32 - n)); }
function sha256(msg) {
  const l = msg.length;
  const padLen = ((l + 9 + 63) >> 6) << 6;
  const buf = new Uint8Array(padLen);
  buf.set(msg);
  buf[l] = 0x80;
  const dv = new DataView(buf.buffer);
  dv.setUint32(padLen - 8, Math.floor((l * 8) / 0x100000000), false);
  dv.setUint32(padLen - 4, (l * 8) >>> 0, false);
  let h0=0x6a09e667,h1=0xbb67ae85,h2=0x3c6ef372,h3=0xa54ff53a,h4=0x510e527f,h5=0x9b05688c,h6=0x1f83d9ab,h7=0x5be0cd19;
  const w = new Uint32Array(64);
  for (let i = 0; i < padLen; i += 64) {
    for (let t = 0; t < 16; t++) w[t] = dv.getUint32(i + t * 4, false);
    for (let t = 16; t < 64; t++) {
      const s0 = rotr(w[t-15],7) ^ rotr(w[t-15],18) ^ (w[t-15]>>>3);
      const s1 = rotr(w[t-2],17) ^ rotr(w[t-2],19) ^ (w[t-2]>>>10);
      w[t] = (w[t-16] + s0 + w[t-7] + s1) >>> 0;
    }
    let a=h0,b=h1,c=h2,d=h3,e=h4,f=h5,g=h6,h=h7;
    for (let t = 0; t < 64; t++) {
      const S1 = rotr(e,6)^rotr(e,11)^rotr(e,25);
      const ch = (e&f)^((~e)&g);
      const t1 = (h + S1 + ch + K256[t] + w[t]) >>> 0;
      const S0 = rotr(a,2)^rotr(a,13)^rotr(a,22);
      const maj = (a&b)^(a&c)^(b&c);
      const t2 = (S0 + maj) >>> 0;
      h=g; g=f; f=e; e=(d+t1)>>>0; d=c; c=b; b=a; a=(t1+t2)>>>0;
    }
    h0=(h0+a)>>>0;h1=(h1+b)>>>0;h2=(h2+c)>>>0;h3=(h3+d)>>>0;
    h4=(h4+e)>>>0;h5=(h5+f)>>>0;h6=(h6+g)>>>0;h7=(h7+h)>>>0;
  }
  const out = new Uint8Array(32);
  const odv = new DataView(out.buffer);
  [h0,h1,h2,h3,h4,h5,h6,h7].forEach((v,i)=>odv.setUint32(i*4,v,false));
  return out;
}
function hmacSha256(key, data) {
  let k = key.length > 64 ? sha256(key) : key;
  const kp = new Uint8Array(64); kp.set(k);
  const ipad = new Uint8Array(64 + data.length);
  const opad = new Uint8Array(96);
  for (let i=0;i<64;i++) { ipad[i]=kp[i]^0x36; opad[i]=kp[i]^0x5c; }
  ipad.set(data, 64);
  opad.set(sha256(ipad), 64);
  return sha256(opad);
}
function pbkdf2Sha256(passBytes, salt, iters) {
  const input = new Uint8Array(salt.length + 4);
  input.set(salt);
  input[salt.length+3] = 1;
  let u = hmacSha256(passBytes, input);
  const dk = new Uint8Array(u);
  for (let i = 1; i < iters; i++) {
    u = hmacSha256(passBytes, u);
    for (let j = 0; j < 32; j++) dk[j] ^= u[j];
  }
  return dk;
}
const SBOX = new Uint8Array([
  0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
  0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
  0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
  0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
  0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
  0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
  0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
  0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
  0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
  0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
  0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
  0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
  0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
  0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
  0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
  0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
]);
const RCON = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36];
function aes256ExpandKey(key) {
  const rk = new Uint8Array(240);
  rk.set(key);
  let bytes = 32, rconIdx = 0;
  while (bytes < 240) {
    let t0=rk[bytes-4],t1=rk[bytes-3],t2=rk[bytes-2],t3=rk[bytes-1];
    if (bytes % 32 === 0) {
      const nt0=SBOX[t1]^RCON[rconIdx++], nt1=SBOX[t2], nt2=SBOX[t3], nt3=SBOX[t0];
      t0=nt0;t1=nt1;t2=nt2;t3=nt3;
    } else if (bytes % 32 === 16) {
      t0=SBOX[t0];t1=SBOX[t1];t2=SBOX[t2];t3=SBOX[t3];
    }
    rk[bytes]=rk[bytes-32]^t0; rk[bytes+1]=rk[bytes-31]^t1;
    rk[bytes+2]=rk[bytes-30]^t2; rk[bytes+3]=rk[bytes-29]^t3;
    bytes += 4;
  }
  return rk;
}
function xtime(a) { return ((a << 1) ^ ((a & 0x80) ? 0x1b : 0)) & 0xff; }
function aes256EncryptBlock(rk, block) {
  const s = new Uint8Array(block);
  for (let i=0;i<16;i++) s[i]^=rk[i];
  for (let r=1;r<=14;r++) {
    for (let i=0;i<16;i++) s[i]=SBOX[s[i]];
    let t=s[1];s[1]=s[5];s[5]=s[9];s[9]=s[13];s[13]=t;
    t=s[2];s[2]=s[10];s[10]=t; t=s[6];s[6]=s[14];s[14]=t;
    t=s[15];s[15]=s[11];s[11]=s[7];s[7]=s[3];s[3]=t;
    if (r < 14) {
      for (let c=0;c<4;c++) {
        const i=c*4,a=s[i],b=s[i+1],d=s[i+2],e=s[i+3],u=a^b^d^e;
        s[i]=a^u^xtime(a^b); s[i+1]=b^u^xtime(b^d); s[i+2]=d^u^xtime(d^e); s[i+3]=e^u^xtime(e^a);
      }
    }
    const off=r*16;
    for (let i=0;i<16;i++) s[i]^=rk[off+i];
  }
  return s;
}
function gmul(x, y) {
  const z = new Uint8Array(16);
  const v = new Uint8Array(y);
  for (let i=0;i<128;i++) {
    if ((x[i>>3] & (0x80 >>> (i & 7))) !== 0) {
      for (let b=0;b<16;b++) z[b]^=v[b];
    }
    const lsb = v[15] & 1;
    for (let b=15;b>0;b--) v[b] = (v[b] >>> 1) | ((v[b-1] & 1) << 7);
    v[0] = v[0] >>> 1;
    if (lsb) v[0] ^= 0xe1;
  }
  return z;
}
function aesGcmDecryptJs(key, iv, cipherWithTag) {
  if (cipherWithTag.length < 16) throw new Error("Payload corrupto");
  const rk = aes256ExpandKey(key);
  const cLen = cipherWithTag.length - 16;
  const cipher = cipherWithTag.subarray(0, cLen);
  const tag = cipherWithTag.subarray(cLen);
  const h = aes256EncryptBlock(rk, new Uint8Array(16));

  let y = new Uint8Array(16);
  for (let off=0; off<cLen; off+=16) {
    const blk = new Uint8Array(16);
    blk.set(cipher.subarray(off, Math.min(off+16, cLen)));
    for (let i=0;i<16;i++) y[i]^=blk[i];
    y = gmul(y, h);
  }
  const lenBlk = new Uint8Array(16);
  const ldv = new DataView(lenBlk.buffer);
  const bits = cLen * 8;
  ldv.setUint32(8, Math.floor(bits / 0x100000000), false);
  ldv.setUint32(12, bits >>> 0, false);
  for (let i=0;i<16;i++) y[i]^=lenBlk[i];
  y = gmul(y, h);

  const j0 = new Uint8Array(16);
  j0.set(iv); j0[15] = 1;
  const tagMask = aes256EncryptBlock(rk, j0);
  let diff = 0;
  for (let i=0;i<16;i++) diff |= ((y[i] ^ tagMask[i]) ^ tag[i]);
  if (diff !== 0) throw new Error("Contraseña incorrecta o archivo alterado");

  const plain = new Uint8Array(cLen);
  const ctr = new Uint8Array(j0);
  const cdv = new DataView(ctr.buffer);
  for (let off=0; off<cLen; off+=16) {
    cdv.setUint32(12, (cdv.getUint32(12, false) + 1) >>> 0, false);
    const ks = aes256EncryptBlock(rk, ctr);
    const end = Math.min(16, cLen - off);
    for (let i=0;i<end;i++) plain[off+i] = cipher[off+i] ^ ks[i];
  }
  return plain;
}

async function decryptNxv(arrayBuffer, password) {
  const bytes = new Uint8Array(arrayBuffer);
  const magic = String.fromCharCode(bytes[0],bytes[1],bytes[2],bytes[3]);
  if (magic !== "NXV1") throw new Error("Formato .nexo_vault inválido");
  const salt = bytes.slice(4, 20);
  const iters = new DataView(bytes.buffer, 20, 4).getUint32(0, false);
  const iv = bytes.slice(24, 36);
  const cipherWithTag = bytes.slice(36);
  const passBytes = new TextEncoder().encode(password);

  let plainBytes;
  if (hasSubtle) {
    const baseKey = await window.crypto.subtle.importKey("raw", passBytes, "PBKDF2", false, ["deriveKey"]);
    const aesKey = await window.crypto.subtle.deriveKey(
      { name: "PBKDF2", salt, iterations: iters, hash: "SHA-256" },
      baseKey,
      { name: "AES-GCM", length: 256 },
      false,
      ["decrypt"]
    );
    const decrypted = await window.crypto.subtle.decrypt({ name: "AES-GCM", iv }, aesKey, cipherWithTag);
    plainBytes = new Uint8Array(decrypted);
  } else {
    const rawKey = pbkdf2Sha256(passBytes, salt, iters);
    plainBytes = aesGcmDecryptJs(rawKey, iv, cipherWithTag);
  }

  const metaLen = new DataView(plainBytes.buffer, plainBytes.byteOffset, 4).getUint32(0, false);
  const metaJson = new TextDecoder().decode(plainBytes.slice(4, 4 + metaLen));
  const meta = JSON.parse(metaJson);
  const content = plainBytes.slice(4 + metaLen);
  return { meta, content };
}

let activeObjectUrl = null;
function closePreview() {
  document.getElementById("previewBox").style.display = "none";
  document.getElementById("previewMedia").innerHTML = "";
  if (activeObjectUrl) { URL.revokeObjectURL(activeObjectUrl); activeObjectUrl = null; }
}

async function openAndDecrypt(item) {
  const pass = document.getElementById("passInput").value;
  if (!pass) {
    statusBar.textContent = "ERROR: INTRODUCE PRIMERO LA CONTRASEÑA DE SESIÓN";
    document.getElementById("passInput").focus();
    return;
  }
  try {
    statusBar.textContent = "DESCARGANDO BLOB CIFRADO: " + item.name + "...";
    const resp = await fetch("/api/" + TOKEN + "/blob/" + encodeURIComponent(item.id));
    if (!resp.ok) throw new Error("Error HTTP " + resp.status);
    const buf = await resp.arrayBuffer();
    statusBar.textContent = "DERIVANDO CLAVE Y DESCIFRANDO LOCALMENTE...";
    await new Promise(r => setTimeout(r, 20));
    const { meta, content } = await decryptNxv(buf, pass);

    closePreview();
    const mime = meta.mime || item.mime || "application/octet-stream";
    const blob = new Blob([content], { type: mime });
    activeObjectUrl = URL.createObjectURL(blob);

    const dlBtn = document.getElementById("downloadDecryptedBtn");
    dlBtn.href = activeObjectUrl;
    dlBtn.download = meta.name || item.name;

    document.getElementById("previewTitle").textContent = (meta.name || item.name) + " (" + content.length + " B)";
    const media = document.getElementById("previewMedia");
    if (mime.startsWith("image/")) {
      const img = document.createElement("img"); img.src = activeObjectUrl; media.appendChild(img);
    } else if (mime.startsWith("video/")) {
      const v = document.createElement("video"); v.controls = true; v.playsInline = true; v.src = activeObjectUrl; media.appendChild(v);
    } else if (mime.startsWith("audio/")) {
      const a = document.createElement("audio"); a.controls = true; a.src = activeObjectUrl; media.appendChild(a);
    } else if (mime.includes("pdf")) {
      const emb = document.createElement("iframe"); emb.src = activeObjectUrl; emb.style.width="100%"; emb.style.height="60vh"; emb.style.border="1px solid #332559"; media.appendChild(emb);
    } else if (mime.startsWith("text/") || mime.includes("json") || mime.includes("xml")) {
      const pre = document.createElement("pre");
      pre.textContent = new TextDecoder().decode(content.slice(0, 65536));
      media.appendChild(pre);
    } else {
      const p = document.createElement("p");
      p.textContent = "ARCHIVO BINARIO DESCIFRADO EXITOSAMENTE. PULSA GUARDAR PARA DESCARGAR.";
      p.style.color = "var(--green)";
      media.appendChild(p);
    }
    document.getElementById("previewBox").style.display = "block";
    statusBar.textContent = "DESCIFRADO EXITOSO EN RAM DEL NAVEGADOR: " + (meta.name || item.name);
  } catch (e) {
    statusBar.textContent = "FALLO DE DESCIFRADO: " + e.message;
  }
}

async function loadInventory() {
  const root = document.getElementById("listRoot");
  try {
    const resp = await fetch("/api/" + TOKEN + "/items");
    if (!resp.ok) throw new Error("HTTP " + resp.status);
    const data = await resp.json();
    if (!data.items || !data.items.length) {
      root.innerHTML = "<p class='sub'>NO HAY ARCHIVOS PREPARADOS EN ESTA SESIÓN.</p>";
      return;
    }
    root.innerHTML = "";
    data.items.forEach(item => {
      const row = document.createElement("div");
      row.className = "row";
      const left = document.createElement("div");
      left.innerHTML = "<div><strong>" + item.name.replace(/</g,"&lt;") + "</strong></div><div class='meta'>" + item.sizeFmt + " · " + item.mime + "</div>";
      const btn = document.createElement("button");
      btn.className = "btn";
      btn.textContent = "ABRIR / DESCIFRAR";
      btn.onclick = () => openAndDecrypt(item);
      row.appendChild(left);
      row.appendChild(btn);
      root.appendChild(row);
    });
  } catch (e) {
    root.innerHTML = "<p style='color:var(--red)'>ERROR: " + e.message + "</p>";
  }
}
loadInventory();
</script>
</body>
</html>
    """.trimIndent()
  }
}
