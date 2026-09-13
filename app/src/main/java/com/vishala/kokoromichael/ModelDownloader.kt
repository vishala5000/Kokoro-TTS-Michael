package com.vishala.kokoromichael

import android.content.Context
import java.io.File

object ModelDownloader {

    data class ModelFiles(
        val modelFile: File,
        val voiceFile: File
    )

    private const val MODEL_ASSET = "kokoro/kokoro.onnx"
    private const val VOICE_ASSET = "kokoro/am_michael.bin"
    private const val LEXICON_ASSET = "kokoro/kokoro-g2p.tab"

    private const val MODEL_NAME = "kokoro.onnx"
    private const val VOICE_NAME = "am_michael.bin"
    private const val LEXICON_NAME = "kokoro-g2p.tab"

    @Synchronized
    fun prepare(context: Context): ModelFiles {

        val modelDir = File(
            context.filesDir,
            "kokoro"
        )

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFile = File(
            modelDir,
            MODEL_NAME
        )

        val voiceFile = File(
            modelDir,
            VOICE_NAME
        )

        val lexiconFile = File(
            modelDir,
            LEXICON_NAME
        )

        copyAssetIfNeeded(
            context,
            MODEL_ASSET,
            modelFile
        )

        copyAssetIfNeeded(
            context,
            VOICE_ASSET,
            voiceFile
        )

        copyAssetIfNeeded(
            context,
            LEXICON_ASSET,
            lexiconFile
        )

        if (!modelFile.exists() ||
            modelFile.length() == 0L
        ) {
            throw IllegalStateException(
                "Kokoro model file is missing"
            )
        }

        if (!voiceFile.exists() ||
            voiceFile.length() == 0L
        ) {
            throw IllegalStateException(
                "Michael voice file is missing"
            )
        }

        if (!lexiconFile.exists() ||
            lexiconFile.length() == 0L
        ) {
            throw IllegalStateException(
                "Kokoro G2P lexicon is missing"
            )
        }

        return ModelFiles(
            modelFile = modelFile,
            voiceFile = voiceFile
        )
    }

    private fun copyAssetIfNeeded(
        context: Context,
        assetPath: String,
        destination: File
    ) {
        if (
            destination.exists() &&
            destination.length() > 0L
        ) {
            return
        }

        destination.parentFile?.mkdirs()

        context.assets
            .open(assetPath)
            .use { input ->

                destination
                    .outputStream()
                    .use { output ->

                        input.copyTo(
                            output,
                            DEFAULT_BUFFER_SIZE
                        )
                    }
            }
    }
}
