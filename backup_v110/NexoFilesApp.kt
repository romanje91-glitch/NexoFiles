package com.example

import android.app.Application
import com.example.data.db.NexoDatabase
import com.example.data.prefs.BottomBarPreferences
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repo.AppManagerRepository
import com.example.data.repo.ArchiveRepository
import com.example.data.repo.DownloaderRepository
import com.example.data.repo.DuplicateFinderEngine
import com.example.data.repo.GoogleDriveRepository
import com.example.data.repo.GooglePhotosRepository
import com.example.data.repo.LocalFileRepository
import com.example.data.repo.LocalHttpServer
import com.example.data.repo.NetworkRepository
import com.example.data.repo.SenderRepository
import com.example.data.repo.SimilarPhotosEngine
import com.example.data.repo.StorageAnalyzerRepository
import com.example.data.repo.VaultRepository
import com.example.data.repo.VaultWebPortalServer
import com.example.security.BiometricSecurityManager

class AppContainer(application: Application) {
  val database = NexoDatabase.getInstance(application)
  val dao = database.nexoDao()
  val preferences = UserPreferencesRepository(application)
  val bottomBarPrefs = BottomBarPreferences(application)
  val security = BiometricSecurityManager(application)
  val localFiles = LocalFileRepository(application, dao)
  val archives = ArchiveRepository()
  val duplicateFinder = DuplicateFinderEngine()
  val similarPhotos = SimilarPhotosEngine(application)
  val analyzer = StorageAnalyzerRepository(duplicateFinder, similarPhotos)
  val googleDrive = GoogleDriveRepository(application, dao, preferences)
  val googlePhotos = GooglePhotosRepository(application, preferences)
  val httpServer = LocalHttpServer()
  val network = NetworkRepository(dao)
  val sender = SenderRepository(application, dao)
  val appManager = AppManagerRepository(application)
  val downloader = DownloaderRepository(application, dao)
  val vault = VaultRepository(application)
  val vaultWebPortal = VaultWebPortalServer()
}

class NexoFilesApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
