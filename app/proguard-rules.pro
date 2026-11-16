# sshj lädt Algorithmen teils per Reflection; BouncyCastle registriert sich als JCA-Provider.
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**
-dontwarn org.slf4j.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.bouncycastle.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class de.mchwrk.checkmyraspi.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.mchwrk.checkmyraspi.**$$serializer { *; }
-dontwarn javax.security.auth.login.LoginContext
