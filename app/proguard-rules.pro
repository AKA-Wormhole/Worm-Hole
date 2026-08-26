-keepclassmembers class com.wormhole.browser.core.webview.WebViewFactory$BlobDownloadBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.wormhole.browser.core.webview.WebAuthnBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep,allowobfuscation @interface android.webkit.JavascriptInterface

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class *
-keep,includedescriptorclasses class com.wormhole.browser.**$$serializer { *; }
-keepclassmembers class com.wormhole.browser.** {
    *** Companion;
}
-keepclasseswithmembers class com.wormhole.browser.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep @androidx.room.Entity class com.wormhole.browser.** { *; }
-keep @androidx.room.Dao class com.wormhole.browser.**

-dontwarn okhttp3.**
-dontwarn okio.**

-dontwarn com.google.api.client.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.joda.time.**

-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**

# GeckoView bundles Mozilla's fork of ExoPlayer, whose NonNullApi annotation
# references JetBrains' old kotlin-annotations-jvm migration-status classes.
# That artifact isn't (and doesn't need to be) on the runtime classpath --
# it's a source-retention annotation -- but R8 still resolves the reference
# during shrinking and treats the missing class as fatal unless told not to.
-dontwarn kotlin.annotations.jvm.**

