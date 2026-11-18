package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.NetworkServerEntity
import com.example.data.db.NexoDatabase
import com.example.data.db.SenderHistoryEntity
import com.example.data.model.GestureBinding
import com.example.data.model.LayoutViewMode
import com.example.data.model.SearchEngineMode
import com.example.data.prefs.UserPreferencesRepository
import com.example.security.BiometricAuthRequest
import com.example.security.BiometricAvailability
import com.example.security.BiometricSecurityManager
import com.example.ui.components.generateQrMatrix
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NexoFeaturesTest {

  @Test
  fun `qr matrix generator builds non-empty square bit matrix`() {
    val matrix = generateQrMatrix("http://192.168.1.45:8090/share", size = 33)!!
    assertTrue(matrix.width > 0)
    assertEquals(matrix.width, matrix.height)
    var darkPixels = 0
    for (x in 0 until matrix.width) {
      for (y in 0 until matrix.height) {
        if (matrix[x, y]) darkPixels++
      }
    }
    assertTrue(darkPixels > 40)
  }

  @Test
  fun `preferences repository persists 5-block settings pin and json backup`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = UserPreferencesRepository(context)

    prefs.setLayoutViewMode(LayoutViewMode.GRID)
    prefs.setSearchEngineMode(SearchEngineMode.DEEP_SCAN)
    prefs.setSenderNickname("Moto-Pro")
    prefs.setSenderSavePath("/storage/emulated/0/NexoShareTest")
    prefs.setDrawerConfig(listOf("FILES", "HOME", "SENDER"), setOf("RECYCLE_BIN"))
    prefs.setMasterPin("4826")
    prefs.saveGestureBinding(GestureBinding(id = "g1", gestureShape = "V", label = "Abrir Sender", targetRouteOrPath = "sender"))

    val settings = prefs.allSettings.first()
    assertEquals(LayoutViewMode.GRID, settings.layoutViewMode)
    assertEquals(SearchEngineMode.DEEP_SCAN, settings.searchEngineMode)
    assertEquals("Moto-Pro", settings.senderNickname)
    assertEquals("/storage/emulated/0/NexoShareTest", settings.senderSavePath)
    assertEquals(listOf("FILES", "HOME", "SENDER"), settings.drawerOrderIds)
    assertTrue("RECYCLE_BIN" in settings.drawerHiddenIds)
    assertTrue(prefs.verifyPin("4826", settings.masterPinHash))
    assertFalse(prefs.verifyPin("0000", settings.masterPinHash))
    assertEquals(1, settings.gestureBindings.size)

    val json = prefs.exportSettingsJson(JSONArray(), JSONArray())
    assertTrue(json.contains("NexoFiles"))
    assertTrue(json.contains("Moto-Pro"))

    prefs.resetToDefaults()
    val reset = prefs.allSettings.first()
    assertEquals(LayoutViewMode.DETAILED, reset.layoutViewMode)

    prefs.importSettingsJson(json)
    val restored = prefs.allSettings.first()
    assertEquals(LayoutViewMode.GRID, restored.layoutViewMode)
    assertEquals("Moto-Pro", restored.senderNickname)
  }

  @Test
  fun `room database persists ftp webdav smb servers and sender history`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val dao = db.nexoDao()

    dao.insertNetworkServer(
      NetworkServerEntity(
        name = "NAS Oficina",
        protocol = "FTP",
        host = "192.168.1.59",
        port = 21,
        ftpMode = "PASSIVE",
        encoding = "UTF-8",
        anonymous = true,
      )
    )
    dao.insertNetworkServer(
      NetworkServerEntity(
        name = "Nextcloud",
        protocol = "WEBDAV",
        host = "cloud.ejemplo.org/remote.php/dav",
        port = 443,
        username = "usuario",
        password = "secret",
        useHttps = true,
      )
    )
    dao.insertNetworkServer(
      NetworkServerEntity(
        name = "PC Casa",
        protocol = "SMB",
        host = "192.168.1.20",
        port = 445,
        domain = "WORKGROUP",
        username = "admin",
      )
    )

    val servers = dao.getNetworkServersSync()
    assertEquals(3, servers.size)
    assertEquals("PASSIVE", servers.first { it.protocol == "FTP" }.ftpMode)
    assertTrue(servers.first { it.protocol == "WEBDAV" }.useHttps)
    assertEquals("WORKGROUP", servers.first { it.protocol == "SMB" }.domain)

    dao.insertSenderHistory(
      SenderHistoryEntity(
        direction = "SENT",
        fileName = "informe.pdf",
        filePath = "/storage/emulated/0/Download/informe.pdf",
        sizeBytes = 204_800L,
        peerName = "Pixel 9",
      )
    )
    val history = dao.observeSenderHistory().first()
    assertEquals(1, history.size)
    assertEquals("informe.pdf", history.first().fileName)

    db.close()
  }

  @Test
  fun `biometric security manager builds prompt info and protects sensitive actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val security = BiometricSecurityManager(context)

    val availability = security.checkAvailability()
    assertTrue(availability in BiometricAvailability.entries)

    val promptInfo = security.buildPromptInfo(
      BiometricAuthRequest(
        title = "Bóveda Privada",
        subtitle = "Verifica tu identidad",
        allowDeviceCredential = false,
      )
    )
    assertEquals("Bóveda Privada", promptInfo.title.toString())
    assertEquals("Verifica tu identidad", promptInfo.subtitle.toString())
    assertEquals("Cancelar", promptInfo.negativeButtonText.toString())

    // When biometrics disabled and Master PIN exists, delegates to Master PIN fallback
    var pinFallbackCalled = false
    var authorizedCalled = false
    security.authorizeSensitiveOperation(
      activity = null,
      biometricEnabled = false,
      request = BiometricAuthRequest("Test", "Sub"),
      hasMasterPin = true,
      onRequestMasterPinFallback = { pinFallbackCalled = true },
      onAuthorized = { authorizedCalled = true },
    )
    assertTrue(pinFallbackCalled)
    assertFalse(authorizedCalled)

    // When biometrics disabled and no Master PIN, authorizes directly
    var directAuthorized = false
    security.authorizeSensitiveOperation(
      activity = null,
      biometricEnabled = false,
      request = BiometricAuthRequest("Test", "Sub"),
      hasMasterPin = false,
      onAuthorized = { directAuthorized = true },
    )
    assertTrue(directAuthorized)
  }

  @Test
  fun `format utils detects image video pdf note and archive extensions accurately`() {
    assertTrue(com.example.util.FormatUtils.isImage("foto_vacaciones.JPG"))
    assertTrue(com.example.util.FormatUtils.isImage("captura.webp"))
    assertFalse(com.example.util.FormatUtils.isImage("documento.pdf"))

    assertTrue(com.example.util.FormatUtils.isVideo("clip.mp4"))
    assertTrue(com.example.util.FormatUtils.isVideo("pelicula.MKV"))
    assertFalse(com.example.util.FormatUtils.isVideo("nota.txt"))

    assertTrue(com.example.util.FormatUtils.isPdf("contrato.PDF"))
    assertFalse(com.example.util.FormatUtils.isPdf("contrato.docx"))

    assertTrue(com.example.util.FormatUtils.isTextEditable("notas.md"))
    assertTrue(com.example.util.FormatUtils.isTextEditable("config.json"))
    assertTrue(com.example.util.FormatUtils.isArchive("respaldo.rar"))
    assertTrue(com.example.util.FormatUtils.isArchive("paquete.zip"))
    assertEquals("application/pdf", com.example.util.FormatUtils.getMimeType("manual.pdf"))
  }

  @Test
  fun `archive repository compresses zip lists entries and extracts safely`() = runBlocking {
    val tempRoot = java.nio.file.Files.createTempDirectory("nexo_archive_test").toFile()
    try {
      val srcFolder = java.io.File(tempRoot, "origen").apply { mkdirs() }
      val subFolder = java.io.File(srcFolder, "sub").apply { mkdirs() }
      val noteFile = java.io.File(srcFolder, "nota.txt").apply { writeText("Hola NexoFiles") }
      val jsonFile = java.io.File(subFolder, "datos.json").apply { writeText("{\"ok\":true}") }

      val repo = com.example.data.repo.ArchiveRepository()
      val destZip = java.io.File(tempRoot, "paquete.zip")

      val createRes = repo.createZip(listOf(noteFile, subFolder), destZip)
      assertTrue(createRes.isSuccess)
      assertTrue(destZip.exists() && destZip.length() > 0L)

      val entriesRes = repo.listArchiveEntries(destZip)
      assertTrue(entriesRes.isSuccess)
      val entryNames = entriesRes.getOrThrow().map { it.name }
      assertTrue(entryNames.any { it == "nota.txt" })
      assertTrue(entryNames.any { it.endsWith("datos.json") })

      val extractDir = java.io.File(tempRoot, "extraido")
      val extractRes = repo.extractArchive(destZip, extractDir)
      assertTrue(extractRes.isSuccess)
      assertEquals(2, extractRes.getOrThrow())
      assertEquals("Hola NexoFiles", java.io.File(extractDir, "nota.txt").readText())
      assertEquals("{\"ok\":true}", java.io.File(extractDir, "sub/datos.json").readText())
      assertEquals(jsonFile.length(), java.io.File(extractDir, "sub/datos.json").length())

      // Verify Zip-Slip traversal protection
      val evilZip = java.io.File(tempRoot, "evil.zip")
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(evilZip)).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("../../escape.txt"))
        zos.write("bad".toByteArray())
        zos.closeEntry()
      }
      val evilRes = repo.extractArchive(evilZip, java.io.File(tempRoot, "safe_out"))
      assertTrue(evilRes.isFailure)
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `format utils detects audio files and inspects text vs binary hex preview`() {
    assertTrue(com.example.util.FormatUtils.isAudio("cancion.mp3"))
    assertTrue(com.example.util.FormatUtils.isAudio("audio.FLAC"))
    assertTrue(com.example.util.FormatUtils.isAudio("nota_voz.ogg"))
    assertTrue(com.example.util.FormatUtils.isAudio("sonido.wav"))
    assertTrue(com.example.util.FormatUtils.isAudio("pista.m4a"))
    assertTrue(com.example.util.FormatUtils.isAudio("stream.aac"))
    assertFalse(com.example.util.FormatUtils.isAudio("video.mp4"))
    assertFalse(com.example.util.FormatUtils.isAudio("readme.txt"))

    val tempRoot = java.nio.file.Files.createTempDirectory("nexo_inspect_test").toFile()
    try {
      val jsonFile = java.io.File(tempRoot, "config.json").apply {
        writeText("{\n  \"app\": \"NexoFiles\",\n  \"ok\": true\n}")
      }
      val textInspection = com.example.util.FormatUtils.readTextOrHexPreview(jsonFile)
      assertFalse(textInspection.isBinary)
      assertFalse(textInspection.isTruncated)
      assertTrue(textInspection.textPreview.contains("\"app\": \"NexoFiles\""))
      assertTrue(textInspection.hexPreview.contains("000000"))

      val binaryFile = java.io.File(tempRoot, "module.nro").apply {
        val header = "NRO0".toByteArray(Charsets.US_ASCII)
        val payload = byteArrayOf(0x00, 0x01, 0x02, 0x7F.toByte(), 0xFF.toByte(), 0x10, 0x00, 0x41, 0x42, 0x43)
        writeBytes(header + payload)
      }
      val binInspection = com.example.util.FormatUtils.readTextOrHexPreview(binaryFile)
      assertTrue(binInspection.isBinary)
      assertTrue(binInspection.hexPreview.contains("000000"))
      assertTrue(binInspection.hexPreview.contains("4E 52 4F 30"))
      assertTrue(binInspection.hexPreview.contains("|NRO0"))
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `preferences repository stores and clears default app mappings and language`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = UserPreferencesRepository(context)
    prefs.clearAllDefaultApps()

    val initialSettings = prefs.allSettings.first()
    assertEquals(com.example.data.model.AppLanguageOption.AUTO, initialSettings.appLanguage)
    assertTrue(prefs.defaultAppMappings.first().isEmpty())

    prefs.setDefaultAppForExtension(".PDF", "internal:pdf")
    prefs.setDefaultAppForExtension("mp3", "com.example.musicplayer")
    val mappings = prefs.defaultAppMappings.first()
    assertEquals("internal:pdf", mappings["pdf"])
    assertEquals("com.example.musicplayer", mappings["mp3"])

    val removedCount = prefs.clearAllDefaultApps()
    assertEquals(2, removedCount)
    assertTrue(prefs.defaultAppMappings.first().isEmpty())

    prefs.setAppLanguage(com.example.data.model.AppLanguageOption.EN)
    assertEquals(com.example.data.model.AppLanguageOption.EN, prefs.allSettings.first().appLanguage)

    prefs.setAppLanguage(com.example.data.model.AppLanguageOption.ES)
    assertEquals(com.example.data.model.AppLanguageOption.ES, prefs.allSettings.first().appLanguage)

    prefs.setAppLanguage(com.example.data.model.AppLanguageOption.AUTO)
    assertEquals(com.example.data.model.AppLanguageOption.AUTO, prefs.allSettings.first().appLanguage)
  }

  @Test
  fun `resolveAppStrings switches cleanly between spanish and english`() {
    val es = com.example.ui.i18n.resolveAppStrings(
      com.example.data.model.AppLanguageOption.ES,
      java.util.Locale.ENGLISH,
    )
    assertFalse(es.isEnglish)
    assertEquals("Inicio", es.navHome)
    assertEquals("Archivos", es.navFiles)

    val en = com.example.ui.i18n.resolveAppStrings(
      com.example.data.model.AppLanguageOption.EN,
      java.util.Locale.forLanguageTag("es-ES"),
    )
    assertTrue(en.isEnglish)
    assertEquals("Home", en.navHome)
    assertEquals("Files", en.navFiles)

    val autoEn = com.example.ui.i18n.resolveAppStrings(
      com.example.data.model.AppLanguageOption.AUTO,
      java.util.Locale.ENGLISH,
    )
    assertTrue(autoEn.isEnglish)

    val autoEs = com.example.ui.i18n.resolveAppStrings(
      com.example.data.model.AppLanguageOption.AUTO,
      java.util.Locale.forLanguageTag("es-ES"),
    )
    assertFalse(autoEs.isEnglish)
    assertEquals("Google Photos", es.drawerGooglePhotos)
    assertEquals("Google Photos", en.drawerGooglePhotos)
    assertEquals("Fotos Similares", es.similarPhotosTitle)
    assertEquals("Similar Photos", en.similarPhotosTitle)
  }

  @Test
  fun `duplicate finder 3-phase md5 funnel filters by size head-tail sha256 and full md5`() = runBlocking {
    val tempRoot = java.nio.file.Files.createTempDirectory("nexo_dup_funnel_test").toFile()
    try {
      val dcimCamera = java.io.File(tempRoot, "DCIM/Camera").apply { mkdirs() }
      val downloads = java.io.File(tempRoot, "Download").apply { mkdirs() }
      val whatsapp = java.io.File(tempRoot, "WhatsApp/Media").apply { mkdirs() }

      // 1) Unique byte size -> discarded at Phase 1
      java.io.File(downloads, "unique_size.bin").writeBytes(ByteArray(12_345) { (it % 251).toByte() })

      // 2) Same 24 KB size, different header -> discarded at Phase 2 (8 KB head/tail SHA-256)
      val base24k = ByteArray(24 * 1024) { ((it * 31) and 0xFF).toByte() }
      val diffHeader24k = base24k.clone().also { it[10] = (it[10].toInt() xor 0xFF).toByte() }
      java.io.File(downloads, "struct_a.bin").writeBytes(base24k)
      java.io.File(downloads, "struct_b.bin").writeBytes(diffHeader24k)

      // 3) Same 28 KB size AND identical first 8 KB + last 8 KB, but different center byte -> discarded at Phase 3 (full MD5)
      val centerBase = ByteArray(28 * 1024) { ((it * 17 + 5) and 0xFF).toByte() }
      val centerModified = centerBase.clone().also {
        val mid = 14 * 1024
        it[mid] = (it[mid].toInt() xor 0x5A).toByte()
      }
      java.io.File(downloads, "mid_only_a.dat").writeBytes(centerBase)
      java.io.File(downloads, "mid_only_b.dat").writeBytes(centerModified)

      // 4) True 100% MD5 identical files across DCIM/Camera (original) and WhatsApp/Download (copies)
      val photoPayload = ByteArray(20 * 1024) { ((it * 73 + 19) and 0xFF).toByte() }
      val cameraOriginal = java.io.File(dcimCamera, "IMG_20260501.jpg").apply {
        writeBytes(photoPayload)
        setLastModified(1_700_000_000_000L)
      }
      val downloadCopy = java.io.File(downloads, "IMG_20260501 (1).jpg").apply {
        writeBytes(photoPayload)
        setLastModified(1_700_000_500_000L)
      }
      val waCopy = java.io.File(whatsapp, "IMG-20260501-WA0004.jpg").apply {
        writeBytes(photoPayload)
        setLastModified(1_700_000_900_000L)
      }

      val engine = com.example.data.repo.DuplicateFinderEngine()
      val phasesObserved = mutableSetOf<Int>()
      val summary = engine.scanDuplicates(
        roots = listOf(tempRoot),
        onProgress = { prog -> phasesObserved.add(prog.phase) },
      )

      assertTrue(phasesObserved.contains(1))
      assertTrue(phasesObserved.contains(3))
      assertEquals(8, summary.totalScannedFiles)
      assertEquals(7, summary.phase1SizeCandidates)
      assertEquals(5, summary.phase2StructuralCandidates)
      assertEquals(3, summary.phase3ConfirmedDuplicates)
      assertEquals(1, summary.groups.size)

      val group = summary.groups.single()
      assertEquals(3, group.files.size)
      assertEquals(32, group.md5.length)
      // Index 0 ("Conservar") must be the DCIM/Camera original
      assertEquals(cameraOriginal.absolutePath, group.files.first().path)
      assertTrue(group.files.drop(1).map { it.path }.containsAll(listOf(downloadCopy.absolutePath, waCopy.absolutePath)))
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `similar photos engine computes dhash hamming similarity and marks highest quality best shot`() = runBlocking {
    val tempRoot = java.nio.file.Files.createTempDirectory("nexo_similar_photos_test").toFile()
    try {
      // High-res sharp gradient + edge detail image (320x240)
      val hiRes = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
      for (y in 0 until 240) {
        for (x in 0 until 320) {
          val r = (x * 255) / 320
          val g = (y * 255) / 240
          val edgeBoost = if ((x / 16 + y / 16) % 2 == 0) 24 else 0
          val b = ((r + g) / 2 + edgeBoost).coerceIn(0, 255)
          hiRes.setPixel(x, y, android.graphics.Color.rgb(r, g, b))
        }
      }
      val hiFile = java.io.File(tempRoot, "IMG_BURST_01.png")
      java.io.FileOutputStream(hiFile).use { hiRes.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }

      // Downscaled copy of the same scene (160x120) -> visually >88% similar
      val lowRes = android.graphics.Bitmap.createScaledBitmap(hiRes, 160, 120, true)
      val lowFile = java.io.File(tempRoot, "IMG_BURST_02_compressed.jpg")
      java.io.FileOutputStream(lowFile).use { lowRes.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }

      // Completely different inverse vertical stripes image -> <88% similarity, must NOT group
      val different = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
      for (y in 0 until 240) {
        for (x in 0 until 320) {
          val lum = if ((x % 40) < 20) 250 - (x * 200 / 320) else (x * 200 / 320)
          different.setPixel(x, y, android.graphics.Color.rgb(lum, 255 - lum, lum / 2))
        }
      }
      val diffFile = java.io.File(tempRoot, "OTHER_SCENE.png")
      java.io.FileOutputStream(diffFile).use { different.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }

      val hashHi = com.example.data.repo.SimilarPhotosEngine.computeDHash(hiRes)
      val hashLow = com.example.data.repo.SimilarPhotosEngine.computeDHash(lowRes)
      val dist = com.example.data.repo.SimilarPhotosEngine.hammingDistance(hashHi, hashLow)
      val sim = com.example.data.repo.SimilarPhotosEngine.similarityPercent(dist)
      assertTrue("Expected >= 88% perceptual similarity, got $sim%", sim >= 88)

      val engine = com.example.data.repo.SimilarPhotosEngine(ApplicationProvider.getApplicationContext())
      val groups = engine.scanSimilarPhotos(customDirectories = listOf(tempRoot))

      assertEquals(1, groups.size)
      val group = groups.single()
      assertEquals(2, group.items.size)
      assertTrue(group.maxSimilarityPercent >= 88)
      val best = group.items.first { it.isBestShot }
      assertEquals(hiFile.absolutePath, best.path)
      assertFalse(group.items.any { it.path == diffFile.absolutePath })

      hiRes.recycle()
      lowRes.recycle()
      different.recycle()
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `photo editor engine renders non destructive crop rotation filters and exports copies`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempOutDir = java.nio.file.Files.createTempDirectory("nexo_photo_editor_test").toFile()
    try {
      val src = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
      src.eraseColor(android.graphics.Color.rgb(180, 70, 40))
      val originalCenterPixel = src.getPixel(100, 50)

      // 1) 90° rotation swaps width & height without mutating source
      val rotated = com.example.ui.editor.PhotoEditorEngine.renderEditedBitmap(
        source = src,
        params = com.example.data.model.PhotoEditParams(rotationDegrees = 90),
      )
      assertEquals(100, rotated.width)
      assertEquals(200, rotated.height)
      assertEquals(originalCenterPixel, src.getPixel(100, 50))
      rotated.recycle()

      // 2) 1:1 square crop + Mono filter produces equal R/G/B channels and square dimensions
      val squareCrop = com.example.ui.editor.PhotoEditorEngine.computeAspectCropRect(
        com.example.data.model.CropAspectPreset.SQUARE_1_1,
        imageWidth = 200,
        imageHeight = 100,
      )
      val monoSquare = com.example.ui.editor.PhotoEditorEngine.renderEditedBitmap(
        source = src,
        params = com.example.data.model.PhotoEditParams(
          cropAspect = com.example.data.model.CropAspectPreset.SQUARE_1_1,
          cropRect = squareCrop,
          filterPreset = com.example.data.model.TacticalFilterPreset.MONO,
        ),
      )
      assertEquals(100, monoSquare.width)
      assertEquals(100, monoSquare.height)
      val px = monoSquare.getPixel(50, 50)
      assertEquals(android.graphics.Color.red(px), android.graphics.Color.green(px))
      assertEquals(android.graphics.Color.green(px), android.graphics.Color.blue(px))

      // 3) Export JPEG and PNG copies without overwriting original
      val jpegFile = com.example.ui.editor.PhotoEditorEngine.exportEditedBitmapToDevice(
        context = context,
        sourceName = "Vacaciones.jpg",
        renderedBitmap = monoSquare,
        format = com.example.data.model.PhotoExportFormat.JPEG,
        outputDir = tempOutDir,
      ).getOrThrow()
      assertTrue(jpegFile.exists() && jpegFile.length() > 0L)
      assertTrue(jpegFile.name.endsWith(".jpg"))

      val pngFile = com.example.ui.editor.PhotoEditorEngine.exportEditedBitmapToDevice(
        context = context,
        sourceName = "Vacaciones.jpg",
        renderedBitmap = monoSquare,
        format = com.example.data.model.PhotoExportFormat.PNG,
        outputDir = tempOutDir,
      ).getOrThrow()
      assertTrue(pngFile.exists() && pngFile.length() > 0L)
      assertTrue(pngFile.name.endsWith(".png"))

      monoSquare.recycle()
      src.recycle()
    } finally {
      tempOutDir.deleteRecursively()
    }
  }

  @Test
  fun `google photos repository parses picker session and picked media items json`() {
    val sessionJson = """
      {
        "id": "sess_abc123",
        "pickerUri": "https://photospicker.google.com/session/sess_abc123",
        "mediaItemsSet": true,
        "pollingConfig": { "pollInterval": "3.0s" }
      }
    """.trimIndent()
    val session = com.example.data.repo.GooglePhotosRepository.parsePickerSessionJson(sessionJson)
    assertEquals("sess_abc123", session.sessionId)
    assertTrue(session.mediaItemsSet)
    assertEquals(3000L, session.pollIntervalMs)

    val itemsJson = """
      {
        "mediaItems": [
          {
            "id": "item_001",
            "createTime": "2026-05-10T12:00:00Z",
            "mediaFile": {
              "baseUrl": "https://lh3.googleusercontent.com/p/AF1QipTest",
              "mimeType": "image/jpeg",
              "filename": "DSC_0042.JPG",
              "mediaFileMetadata": { "width": 4032, "height": 3024 }
            }
          }
        ],
        "nextPageToken": "tok_next"
      }
    """.trimIndent()
    val (items, nextToken) = com.example.data.repo.GooglePhotosRepository.parsePickedMediaItemsJson(itemsJson)
    assertEquals(1, items.size)
    assertEquals("item_001", items.first().id)
    assertEquals("DSC_0042.JPG", items.first().filename)
    assertEquals(4032, items.first().width)
    assertEquals(3024, items.first().height)
    assertEquals("tok_next", nextToken)
  }
}

