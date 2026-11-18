package com.example.data.repo

import com.example.data.model.ArchiveEntryInfo
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveRepository {

  suspend fun createZip(sources: List<File>, destZip: File): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      destZip.parentFile?.mkdirs()
      ZipOutputStream(FileOutputStream(destZip)).use { zos ->
        for (source in sources) {
          if (source.exists()) addToZip(zos, source, source.name)
        }
      }
      destZip
    }
  }

  private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
    if (file.isDirectory) {
      val dirEntry = if (entryName.endsWith("/")) entryName else "$entryName/"
      zos.putNextEntry(ZipEntry(dirEntry))
      zos.closeEntry()
      file.listFiles()?.forEach { child -> addToZip(zos, child, "$dirEntry${child.name}") }
    } else {
      zos.putNextEntry(ZipEntry(entryName))
      FileInputStream(file).use { input -> input.copyTo(zos) }
      zos.closeEntry()
    }
  }

  suspend fun listArchiveEntries(archiveFile: File): Result<List<ArchiveEntryInfo>> = withContext(Dispatchers.IO) {
    runCatching {
      val name = archiveFile.name.lowercase(Locale.ROOT)
      when {
        name.endsWith(".zip") -> listZip(archiveFile)
        name.endsWith(".tar.gz") || name.endsWith(".tgz") -> listTarGz(archiveFile)
        name.endsWith(".tar") -> listTar(archiveFile)
        name.endsWith(".gz") -> listOf(
          ArchiveEntryInfo(archiveFile.name.removeSuffix(".gz"), archiveFile.length(), false)
        )
        name.endsWith(".7z") -> list7z(archiveFile)
        name.endsWith(".rar") -> listRar(archiveFile)
        else -> throw UnsupportedOperationException("Formato de archivo comprimido no soportado")
      }
    }
  }

  suspend fun extractArchive(archiveFile: File, destDir: File): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      if (!destDir.exists()) destDir.mkdirs()
      val name = archiveFile.name.lowercase(Locale.ROOT)
      when {
        name.endsWith(".zip") -> extractZip(archiveFile, destDir)
        name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTarGz(archiveFile, destDir)
        name.endsWith(".tar") -> extractTar(archiveFile, destDir)
        name.endsWith(".gz") -> extractGz(archiveFile, destDir)
        name.endsWith(".7z") -> extract7z(archiveFile, destDir)
        name.endsWith(".rar") -> extractRar(archiveFile, destDir)
        else -> throw UnsupportedOperationException("Formato no compatible para extracción")
      }
    }
  }

  private fun sanitizeEntryName(rawName: String): String = rawName.replace('\\', '/').trimStart('/')

  private fun safeDestFile(destDir: File, destCanonical: String, rawName: String, label: String): File {
    val cleanName = sanitizeEntryName(rawName)
    if (cleanName.isEmpty()) throw SecurityException("Nombre de entrada $label vacío")
    val outFile = File(destDir, cleanName)
    if (!outFile.canonicalPath.startsWith(destCanonical + File.separator) && outFile.canonicalPath != destCanonical) {
      throw SecurityException("Entrada $label insegura: $rawName")
    }
    return outFile
  }

  private fun listZip(file: File): List<ArchiveEntryInfo> {
    val entries = mutableListOf<ArchiveEntryInfo>()
    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        entries.add(ArchiveEntryInfo(sanitizeEntryName(entry.name), entry.size.coerceAtLeast(0L), entry.isDirectory, entry.compressedSize))
        entry = zis.nextEntry
      }
    }
    return entries
  }

  private fun extractZip(file: File, destDir: File): Int {
    var count = 0
    val destCanonical = destDir.canonicalPath
    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        val outFile = safeDestFile(destDir, destCanonical, entry.name, "ZIP")
        if (entry.isDirectory) outFile.mkdirs() else {
          outFile.parentFile?.mkdirs()
          FileOutputStream(outFile).use { output -> zis.copyTo(output) }
          count++
        }
        entry = zis.nextEntry
      }
    }
    return count
  }

  private fun listTar(file: File): List<ArchiveEntryInfo> {
    val entries = mutableListOf<ArchiveEntryInfo>()
    TarArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { tis ->
      var entry = tis.nextEntry
      while (entry != null) {
        entries.add(ArchiveEntryInfo(sanitizeEntryName(entry.name), entry.size, entry.isDirectory))
        entry = tis.nextEntry
      }
    }
    return entries
  }

  private fun listTarGz(file: File): List<ArchiveEntryInfo> {
    val entries = mutableListOf<ArchiveEntryInfo>()
    TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(FileInputStream(file)))).use { tis ->
      var entry = tis.nextEntry
      while (entry != null) {
        entries.add(ArchiveEntryInfo(sanitizeEntryName(entry.name), entry.size, entry.isDirectory))
        entry = tis.nextEntry
      }
    }
    return entries
  }

  private fun extractTar(file: File, destDir: File): Int =
    TarArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { tis -> extractTarStream(tis, destDir) }

  private fun extractTarGz(file: File, destDir: File): Int =
    TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(FileInputStream(file)))).use { tis -> extractTarStream(tis, destDir) }

  private fun extractTarStream(tis: TarArchiveInputStream, destDir: File): Int {
    var count = 0
    val destCanonical = destDir.canonicalPath
    var entry = tis.nextEntry
    while (entry != null) {
      val outFile = safeDestFile(destDir, destCanonical, entry.name, "TAR")
      if (entry.isDirectory) outFile.mkdirs() else {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { output -> tis.copyTo(output) }
        count++
      }
      entry = tis.nextEntry
    }
    return count
  }

  private fun extractGz(file: File, destDir: File): Int {
    val outName = file.name.removeSuffix(".gz").ifBlank { "descomprimido" }
    val outFile = safeDestFile(destDir, destDir.canonicalPath, outName, "GZ")
    outFile.parentFile?.mkdirs()
    GzipCompressorInputStream(BufferedInputStream(FileInputStream(file))).use { gz ->
      FileOutputStream(outFile).use { out -> gz.copyTo(out) }
    }
    return 1
  }

  private fun list7z(file: File): List<ArchiveEntryInfo> {
    val entries = mutableListOf<ArchiveEntryInfo>()
    SevenZFile.builder().setFile(file).get().use { sevenZ ->
      var entry = sevenZ.nextEntry
      while (entry != null) {
        entries.add(ArchiveEntryInfo(sanitizeEntryName(entry.name), entry.size, entry.isDirectory))
        entry = sevenZ.nextEntry
      }
    }
    return entries
  }

  private fun extract7z(file: File, destDir: File): Int {
    var count = 0
    val destCanonical = destDir.canonicalPath
    SevenZFile.builder().setFile(file).get().use { sevenZ ->
      var entry = sevenZ.nextEntry
      val buffer = ByteArray(16 * 1024)
      while (entry != null) {
        val outFile = safeDestFile(destDir, destCanonical, entry.name, "7z")
        if (entry.isDirectory) outFile.mkdirs() else {
          outFile.parentFile?.mkdirs()
          FileOutputStream(outFile).use { out ->
            var read = sevenZ.read(buffer)
            while (read > 0) {
              out.write(buffer, 0, read)
              read = sevenZ.read(buffer)
            }
          }
          count++
        }
        entry = sevenZ.nextEntry
      }
    }
    return count
  }

  private fun listRar(file: File): List<ArchiveEntryInfo> {
    val entries = mutableListOf<ArchiveEntryInfo>()
    Archive(file).use { archive ->
      for (header in archive.fileHeaders) {
        val cleanName = sanitizeEntryName(header.fileName)
        if (cleanName.isNotEmpty()) {
          entries.add(ArchiveEntryInfo(cleanName, header.fullUnpackSize, header.isDirectory, header.fullPackSize))
        }
      }
    }
    return entries
  }

  private fun extractRar(file: File, destDir: File): Int {
    var count = 0
    val destCanonical = destDir.canonicalPath
    Archive(file).use { archive ->
      var header = archive.nextFileHeader()
      while (header != null) {
        val cleanName = sanitizeEntryName(header.fileName)
        if (cleanName.isNotEmpty()) {
          val outFile = safeDestFile(destDir, destCanonical, cleanName, "RAR")
          if (header.isDirectory) outFile.mkdirs() else {
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { out -> archive.extractFile(header, out) }
            count++
          }
        }
        header = archive.nextFileHeader()
      }
    }
    return count
  }
}
