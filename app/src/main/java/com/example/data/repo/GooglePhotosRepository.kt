package com.example.data.repo

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.example.data.model.GooglePhotoMediaItem
import com.example.data.model.PickerSessionInfo
import com.example.data.prefs.UserPreferencesRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface GooglePhotosPickerApiService {
  @POST("v1/sessions")
  suspend fun createSession(
    @Header("Authorization") bearerToken: String,
    @Body body: RequestBody,
  ): Response<ResponseBody>

  @GET("v1/sessions/{sessionId}")
  suspend fun getSession(
    @Header("Authorization") bearerToken: String,
    @Path("sessionId") sessionId: String,
  ): Response<ResponseBody>

  @DELETE("v1/sessions/{sessionId}")
  suspend fun deleteSession(
    @Header("Authorization") bearerToken: String,
    @Path("sessionId") sessionId: String,
  ): Response<ResponseBody>

  @GET("v1/mediaItems")
  suspend fun listPickedMediaItems(
    @Header("Authorization") bearerToken: String,
    @Query("sessionId") sessionId: String,
    @Query("pageSize") pageSize: Int = 50,
    @Query("pageToken") pageToken: String? = null,
  ): Response<ResponseBody>
}

interface GooglePhotosLibraryApiService {
  @POST("v1/uploads")
  suspend fun uploadRawBytes(
    @Header("Authorization") bearerToken: String,
    @Header("X-Goog-Upload-Content-Type") mimeType: String,
    @Header("X-Goog-Upload-Protocol") protocol: String = "raw",
    @Body bytesBody: RequestBody,
  ): Response<ResponseBody>

  @POST("v1/mediaItems:batchCreate")
  suspend fun batchCreateMediaItems(
    @Header("Authorization") bearerToken: String,
    @Body payload: RequestBody,
  ): Response<ResponseBody>
}

sealed class PhotosAuthStepResult {
  data class Authorized(val accessToken: String, val email: String?, val name: String?) : PhotosAuthStepResult()
  data class NeedsConsent(val pendingIntent: PendingIntent) : PhotosAuthStepResult()
  data class Error(val message: String) : PhotosAuthStepResult()
}

class GooglePhotosRepository(
  private val context: Context,
  private val prefs: UserPreferencesRepository,
) {

  companion object {
    const val SCOPE_PHOTOS_PICKER_READONLY = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    const val SCOPE_PHOTOS_LIBRARY_APPEND = "https://www.googleapis.com/auth/photoslibrary.appendonly"

    fun parsePickerSessionJson(jsonText: String): PickerSessionInfo {
      val obj = JSONObject(jsonText)
      val polling = obj.optJSONObject("pollingConfig")
      val rawInterval = polling?.optString("pollInterval", "2.5s").orEmpty()
      val seconds = rawInterval.removeSuffix("s").toDoubleOrNull() ?: 2.5
      return PickerSessionInfo(
        sessionId = obj.getString("id"),
        pickerUri = obj.optString("pickerUri", ""),
        mediaItemsSet = obj.optBoolean("mediaItemsSet", false),
        pollIntervalMs = (seconds * 1000.0).toLong().coerceIn(1200L, 8000L),
      )
    }

    fun parsePickedMediaItemsJson(jsonText: String): Pair<List<GooglePhotoMediaItem>, String?> {
      val root = JSONObject(jsonText)
      val arr = root.optJSONArray("mediaItems") ?: JSONArray()
      val items = ArrayList<GooglePhotoMediaItem>(arr.length())
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val mediaFile = obj.optJSONObject("mediaFile") ?: obj
        val meta = mediaFile.optJSONObject("mediaFileMetadata")
        val id = obj.optString("id").ifBlank { "photo_$i" }
        val baseUrl = mediaFile.optString("baseUrl", obj.optString("baseUrl", ""))
        val mime = mediaFile.optString("mimeType", obj.optString("mimeType", "image/jpeg"))
        val filename = mediaFile.optString("filename", obj.optString("filename", "IMG_$id.jpg"))
        val width = meta?.optInt("width", 0) ?: 0
        val height = meta?.optInt("height", 0) ?: 0
        val createTime = obj.optString("createTime", "")
        if (baseUrl.isNotBlank()) {
          items.add(GooglePhotoMediaItem(id, filename, mime, baseUrl, width, height, createTime))
        }
      }
      val nextToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
      return items to nextToken
    }
  }

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  private val pickerApi: GooglePhotosPickerApiService = Retrofit.Builder()
    .baseUrl("https://photospicker.googleapis.com/")
    .client(okHttpClient)
    .build()
    .create(GooglePhotosPickerApiService::class.java)

  private val libraryApi: GooglePhotosLibraryApiService = Retrofit.Builder()
    .baseUrl("https://photoslibrary.googleapis.com/")
    .client(okHttpClient)
    .build()
    .create(GooglePhotosLibraryApiService::class.java)

  private val photosScopes = listOf(
    Scope(SCOPE_PHOTOS_PICKER_READONLY),
    Scope(SCOPE_PHOTOS_LIBRARY_APPEND),
    Scope("https://www.googleapis.com/auth/userinfo.email"),
    Scope("https://www.googleapis.com/auth/userinfo.profile"),
  )

  suspend fun authorizeGooglePhotos(activity: Activity): PhotosAuthStepResult {
    signInWithCredentialManagerIfConfigured(activity)
    return runCatching {
      val authRequest = AuthorizationRequest.builder().setRequestedScopes(photosScopes).build()
      val result = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(activity)
          .authorize(authRequest)
          .addOnSuccessListener { cont.resume(it) }
          .addOnFailureListener { cont.resumeWithException(it) }
      }
      if (result.hasResolution() && result.pendingIntent != null) {
        PhotosAuthStepResult.NeedsConsent(result.pendingIntent!!)
      } else {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
          PhotosAuthStepResult.Error("No se recibió token de autorización de Google Photos.")
        } else {
          val (email, name) = fetchUserInfo(token)
          if (email != null) prefs.setDriveAccount(email, name)
          PhotosAuthStepResult.Authorized(token, email, name)
        }
      }
    }.getOrElse { err ->
      PhotosAuthStepResult.Error(err.localizedMessage ?: "Error autorizando Google Photos")
    }
  }

  suspend fun trySilentAuthorize(): PhotosAuthStepResult {
    return runCatching {
      val authRequest = AuthorizationRequest.builder().setRequestedScopes(photosScopes).build()
      val result = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context)
          .authorize(authRequest)
          .addOnSuccessListener { cont.resume(it) }
          .addOnFailureListener { cont.resumeWithException(it) }
      }
      if (result.hasResolution() && result.pendingIntent != null) {
        PhotosAuthStepResult.NeedsConsent(result.pendingIntent!!)
      } else {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
          PhotosAuthStepResult.Error("Token silencioso no disponible")
        } else {
          val (email, name) = fetchUserInfo(token)
          if (email != null) prefs.setDriveAccount(email, name)
          PhotosAuthStepResult.Authorized(token, email, name)
        }
      }
    }.getOrElse { err ->
      PhotosAuthStepResult.Error(err.localizedMessage ?: "No se pudo refrescar sesión de Google Photos")
    }
  }

  suspend fun handleAuthorizationIntentResult(data: Intent?): PhotosAuthStepResult {
    return runCatching {
      val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
      val token = authResult.accessToken
      if (token.isNullOrBlank()) {
        PhotosAuthStepResult.Error("Autorización de Google Photos cancelada.")
      } else {
        val (email, name) = fetchUserInfo(token)
        if (email != null) prefs.setDriveAccount(email, name)
        PhotosAuthStepResult.Authorized(token, email, name)
      }
    }.getOrElse { err ->
      PhotosAuthStepResult.Error(err.localizedMessage ?: "Error al confirmar permisos de Google Photos")
    }
  }

  private suspend fun signInWithCredentialManagerIfConfigured(activity: Activity) {
    val webClientId = context.getString(R.string.google_web_client_id).trim()
    if (webClientId.isEmpty()) return
    runCatching {
      val credentialManager = CredentialManager.create(activity)
      val option = GetSignInWithGoogleOption.Builder(webClientId).build()
      val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
      val response = credentialManager.getCredential(activity, request)
      val cred = response.credential
      if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
        prefs.setDriveAccount(googleCred.id, googleCred.displayName)
      }
    }
  }

  private suspend fun fetchUserInfo(accessToken: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
    runCatching {
      val req = Request.Builder()
        .url("https://www.googleapis.com/oauth2/v3/userinfo")
        .header("Authorization", "Bearer $accessToken")
        .build()
      okHttpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null to null
        val json = JSONObject(resp.body?.string().orEmpty())
        json.optString("email").takeIf { it.isNotBlank() } to json.optString("name").takeIf { it.isNotBlank() }
      }
    }.getOrDefault(null to null)
  }

  suspend fun createPickerSession(accessToken: String): Result<PickerSessionInfo> = withContext(Dispatchers.IO) {
    runCatching {
      val emptyJson = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
      val resp = pickerApi.createSession("Bearer $accessToken", emptyJson)
      val body = resp.body()?.string() ?: resp.errorBody()?.string().orEmpty()
      if (!resp.isSuccessful) throw IllegalStateException("Picker API (${resp.code()}): $body")
      parsePickerSessionJson(body)
    }
  }

  suspend fun getPickerSessionStatus(accessToken: String, sessionId: String): Result<PickerSessionInfo> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = pickerApi.getSession("Bearer $accessToken", sessionId)
        val body = resp.body()?.string() ?: resp.errorBody()?.string().orEmpty()
        if (!resp.isSuccessful) throw IllegalStateException("Picker Session (${resp.code()}): $body")
        parsePickerSessionJson(body)
      }
    }

  suspend fun fetchPickedMediaItems(accessToken: String, sessionId: String): Result<List<GooglePhotoMediaItem>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val all = mutableListOf<GooglePhotoMediaItem>()
        var pageToken: String? = null
        do {
          val resp = pickerApi.listPickedMediaItems("Bearer $accessToken", sessionId, 50, pageToken)
          val body = resp.body()?.string() ?: resp.errorBody()?.string().orEmpty()
          if (!resp.isSuccessful) throw IllegalStateException("Picker mediaItems (${resp.code()}): $body")
          val (batch, next) = parsePickedMediaItemsJson(body)
          all.addAll(batch)
          pageToken = next
        } while (pageToken != null && all.size < 200)
        all
      }
    }

  suspend fun downloadMediaItemHighRes(
    accessToken: String,
    item: GooglePhotoMediaItem,
    destDir: File = File(context.cacheDir, "google_photos_cache"),
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      if (!destDir.exists()) destDir.mkdirs()
      val safeName = item.filename.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "${item.id}.jpg" }
      val target = File(destDir, "${item.id.takeLast(8)}_$safeName")
      if (target.exists() && target.length() > 0L) return@runCatching target
      val downloadUrl = if (item.baseUrl.contains("=")) item.baseUrl else "${item.baseUrl}=d"
      val req = Request.Builder().url(downloadUrl).header("Authorization", "Bearer $accessToken").build()
      okHttpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw IllegalStateException("Descarga Google Photos (${resp.code})")
        val stream = resp.body?.byteStream() ?: throw IllegalStateException("Flujo vacío desde Google Photos")
        stream.use { input -> FileOutputStream(target).use { out -> input.copyTo(out) } }
      }
      target
    }
  }

  suspend fun uploadEditedCopyToGooglePhotos(
    accessToken: String,
    imageFile: File,
    mimeType: String = "image/jpeg",
    description: String = "Editado con NexoFiles",
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      require(imageFile.exists() && imageFile.length() > 0L) { "El archivo editado no existe" }
      val uploadResp = libraryApi.uploadRawBytes(
        bearerToken = "Bearer $accessToken",
        mimeType = mimeType,
        bytesBody = imageFile.asRequestBody("application/octet-stream".toMediaType()),
      )
      val uploadToken = (uploadResp.body()?.string() ?: uploadResp.errorBody()?.string().orEmpty()).trim()
      if (!uploadResp.isSuccessful || uploadToken.isBlank()) {
        throw IllegalStateException("Upload Google Photos (${uploadResp.code()}): $uploadToken")
      }
      val batchPayload = JSONObject().apply {
        put(
          "newMediaItems",
          JSONArray().put(
            JSONObject().apply {
              put("description", description)
              put(
                "simpleMediaItem",
                JSONObject().apply {
                  put("fileName", imageFile.name)
                  put("uploadToken", uploadToken)
                }
              )
            }
          )
        )
      }
      val createResp = libraryApi.batchCreateMediaItems(
        bearerToken = "Bearer $accessToken",
        payload = batchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()),
      )
      val createBody = createResp.body()?.string() ?: createResp.errorBody()?.string().orEmpty()
      if (!createResp.isSuccessful) throw IllegalStateException("batchCreate (${createResp.code()}): $createBody")
      imageFile.name
    }
  }

  fun launchPickerUriInCustomTab(context: Context, pickerUri: String, toolbarColorArgb: Int = 0xFF08060E.toInt()): Boolean {
    if (pickerUri.isBlank()) return false
    return runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pickerUri)).apply {
        val extras = Bundle().apply {
          putBinder("android.support.customtabs.extra.SESSION", null)
          putInt("android.support.customtabs.extra.TOOLBAR_COLOR", toolbarColorArgb)
        }
        putExtras(extras)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }
}
