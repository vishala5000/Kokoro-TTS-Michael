package com.vishala.kokoromichael

object KokoroEngine {

    private var initialized = false

    init {
        System.loadLibrary("kokoro_jni")
    }

    private external fun nativeInit(
        modelPath: String,
        voicePath: String
    ): Int

    private external fun nativeSynthesize(
        text: String,
        speed: Float
    ): ByteArray?

    private external fun nativeRelease()

    @Synchronized
    fun initialize(
        modelPath: String,
        voicePath: String
    ) {
        if (initialized) return

        val result = nativeInit(
            modelPath,
            voicePath
        )

        if (result != 0) {
            throw IllegalStateException(
                "Kokoro initialization failed: error code $result"
            )
        }

        initialized = true
    }

    @Synchronized
    fun synthesize(
        text: String,
        speed: Float
    ): ByteArray {
        if (!initialized) {
            throw IllegalStateException(
                "Kokoro engine is not initialized"
            )
        }

        val audio = nativeSynthesize(
            text,
            speed
        )

        return audio
            ?: throw IllegalStateException(
                "Kokoro synthesis returned no audio"
            )
    }

    @Synchronized
    fun release() {
        if (!initialized) return

        nativeRelease()
        initialized = false
    }
}
