# ============================================================
# Kokoro JNI
# ============================================================

-keep class com.vishala.kokoromichael.KokoroEngine {
    *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}


# ============================================================
# Main application classes
# ============================================================

-keep class com.vishala.kokoromichael.MainActivity {
    *;
}

-keep class com.vishala.kokoromichael.ModelDownloader {
    *;
}

-keep class com.vishala.kokoromichael.WavWriter {
    *;
}

-keep class com.vishala.kokoromichael.Settings {
    *;
}
