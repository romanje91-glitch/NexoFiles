package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class BiometricAvailability(val label: String) {
  AVAILABLE("Biometría o credencial del sistema disponible"),
  NONE_ENROLLED("Sin biometría ni bloqueo configurado en el dispositivo"),
  NO_HARDWARE("Este dispositivo no cuenta con sensor biométrico"),
  HARDWARE_UNAVAILABLE("Sensor biométrico temporalmente no disponible"),
  UNSUPPORTED("Autenticación biométrica no soportada en esta versión"),
}

data class BiometricAuthRequest(
  val title: String,
  val subtitle: String,
  val negativeButtonText: String = "Cancelar",
  val allowDeviceCredential: Boolean = true,
  val confirmationRequired: Boolean = false,
)

sealed interface BiometricAuthResult {
  data object Success : BiometricAuthResult
  data object Cancelled : BiometricAuthResult
  data class Unavailable(val availability: BiometricAvailability, val reason: String) : BiometricAuthResult
  data class Error(val errorCode: Int, val message: String) : BiometricAuthResult
}

class BiometricSecurityManager(private val appContext: Context) {

  fun checkAvailability(allowDeviceCredential: Boolean = true): BiometricAvailability {
    val biometricManager = BiometricManager.from(appContext)
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val authenticators = if (allowDeviceCredential) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK
      }
      biometricManager.canAuthenticate(authenticators)
    } else {
      val bioStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
      if (bioStatus == BiometricManager.BIOMETRIC_SUCCESS) {
        BiometricManager.BIOMETRIC_SUCCESS
      } else if (allowDeviceCredential && isLegacyKeyguardSecure()) {
        BiometricManager.BIOMETRIC_SUCCESS
      } else {
        bioStatus
      }
    }

    return when (result) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
      else -> BiometricAvailability.UNSUPPORTED
    }
  }

  private fun isLegacyKeyguardSecure(): Boolean {
    val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    return km?.isDeviceSecure == true
  }

  fun isReady(allowDeviceCredential: Boolean = true): Boolean =
    checkAvailability(allowDeviceCredential) == BiometricAvailability.AVAILABLE

  fun buildPromptInfo(request: BiometricAuthRequest): BiometricPrompt.PromptInfo {
    val builder = BiometricPrompt.PromptInfo.Builder()
      .setTitle(request.title)
      .setSubtitle(request.subtitle)
      .setConfirmationRequired(request.confirmationRequired)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val authenticators = if (request.allowDeviceCredential) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
          BiometricManager.Authenticators.BIOMETRIC_STRONG
      }
      builder.setAllowedAuthenticators(authenticators)
      if (!request.allowDeviceCredential) {
        builder.setNegativeButtonText(request.negativeButtonText)
      }
    } else {
      if (request.allowDeviceCredential && isLegacyKeyguardSecure()) {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
      } else {
        builder.setNegativeButtonText(request.negativeButtonText)
      }
    }
    return builder.build()
  }

  fun authenticate(
    activity: FragmentActivity,
    request: BiometricAuthRequest,
    onResult: (BiometricAuthResult) -> Unit,
  ): BiometricPrompt? {
    val availability = checkAvailability(request.allowDeviceCredential)
    if (availability != BiometricAvailability.AVAILABLE) {
      onResult(BiometricAuthResult.Unavailable(availability, availability.label))
      return null
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          onResult(BiometricAuthResult.Success)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          if (
            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            errorCode == BiometricPrompt.ERROR_CANCELED
          ) {
            onResult(BiometricAuthResult.Cancelled)
          } else {
            onResult(BiometricAuthResult.Error(errorCode, errString.toString()))
          }
        }
      },
    )

    prompt.authenticate(buildPromptInfo(request))
    return prompt
  }

  suspend fun authenticate(
    activity: FragmentActivity,
    request: BiometricAuthRequest,
  ): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
    val prompt = authenticate(activity, request) { result ->
      if (continuation.isActive) {
        continuation.resume(result)
      }
    }
    continuation.invokeOnCancellation {
      prompt?.cancelAuthentication()
    }
  }

  fun authorizeSensitiveOperation(
    activity: FragmentActivity?,
    biometricEnabled: Boolean,
    request: BiometricAuthRequest,
    hasMasterPin: Boolean = false,
    onRequestMasterPinFallback: (() -> Unit)? = null,
    onAuthorized: () -> Unit,
    onDeniedOrError: (String) -> Unit = {},
  ) {
    if (!biometricEnabled) {
      if (hasMasterPin && onRequestMasterPinFallback != null) {
        onRequestMasterPinFallback()
      } else {
        onAuthorized()
      }
      return
    }

    if (activity == null) {
      onAuthorized()
      return
    }

    authenticate(activity, request) { result ->
      when (result) {
        is BiometricAuthResult.Success -> onAuthorized()
        is BiometricAuthResult.Cancelled -> onDeniedOrError("Autenticación cancelada")
        is BiometricAuthResult.Unavailable -> {
          if (hasMasterPin && onRequestMasterPinFallback != null) {
            onRequestMasterPinFallback()
          } else {
            onAuthorized()
          }
        }
        is BiometricAuthResult.Error -> onDeniedOrError(result.message)
      }
    }
  }
}
