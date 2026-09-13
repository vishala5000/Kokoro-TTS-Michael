package com.vishala.kokoromichael


object KokoroEngine {

    private var initialized =
        false


    init {
        System.loadLibrary(
            "kokoro_jni"
        )
    }


    @Synchronized
    fun initialize(
        modelPath: String,
        voicePath: String
    ) {

        if (initialized) {
            return
        }


        val error =
            nativeInit(
                modelPath,
                voicePath
            )


        if (
            !error.isNullOrEmpty()
        ) {

            throw IllegalStateException(
                error
            )
        }


        initialized = true
    }


    fun synthesize(
        text: String,
        speed: Float
    ): ByteArray {

        if (!initialized) {

            throw IllegalStateException(
                "Kokoro engine is not initialized"
            )
        }


        if (text.isBlank()) {
            return ByteArray(0)
        }


        return nativeSynthesize(
            text,
            speed.coerceIn(
                0.5f,
                2.0f
            )
        )
    }


    @Synchronized
    fun release() {

        if (!initialized) {
            return
        }


        try {
            nativeRelease()
        } finally {
            initialized = false
        }
    }


    private external fun nativeInit(
        modelPath: String,
        voicePath: String
    ): String?


    private external fun nativeSynthesize(
        text: String,
        speed: Float
    ): ByteArray


    private external fun nativeRelease()
}
