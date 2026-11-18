# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep NexoFiles model and entity classes
-keep class com.example.data.db.** { *; }
-keep class com.example.data.model.** { *; }

# JSch (SFTP/SSH) uses reflection for cryptographic algorithms and KEX
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.ietf.jgss.**

# Junrar (RAR archives)
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**

# Apache Commons Compress & Net
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.commons.net.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.tukaani.xz.**

# BiometricPrompt & Google Play Services / Credential Manager
-keepclassmembers class androidx.biometric.** { *; }
-dontwarn com.google.errorprone.annotations.**

# OkHttp optional TLS platforms & SLF4J static binder
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
