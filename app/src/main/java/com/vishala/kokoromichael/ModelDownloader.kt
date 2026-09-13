package com.vishala.kokoromichael

import android.content.Context

import java.io.File
import java.io.FileOutputStream


object ModelDownloader {

    private const val MODEL_ASSET =
        "kokoro/kokoro-v1.0.int8.onnx"

    private const val VOICE_ASSET =
        "kokoro/am_michael.bin"


    data class ModelFiles(
        val modelFile: File,
        val voiceFile: File
    )


    fun prepare(
        context: Context
    ): ModelFiles {

        val directory =
            File(
                context.filesDir,
                "kokoro"
            )


        if (!directory.exists()) {

            directory.mkdirs()
        }


        val modelFile =
            File(
                directory,
                "kokoro-v1.0.int8.onnx"
            )


        val voiceFile =
            File(
                directory,
                "am_michael.bin"
            )


        copyIfNeeded(
            context,
            MODEL_ASSET,
            modelFile
        )


        copyIfNeeded(
            context,
            VOICE_ASSET,
            voiceFile
        )


        if (
            !modelFile.exists() ||
            modelFile.length() == 0L
        ) {

            throw IllegalStateException(
                "Kokoro model is missing"
            )
        }


        if (
            !voiceFile.exists() ||
            voiceFile.length() == 0L
        ) {

            throw IllegalStateException(
                "Michael voice is missing"
            )
        }


        return ModelFiles(
            modelFile,
            voiceFile
        )
    }


    private fun copyIfNeeded(
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


        val temporary =
            File(
                destination.parentFile,
                destination.name + ".part"
            )


        if (temporary.exists()) {
            temporary.delete()
        }


        context.assets
            .open(assetPath)
            .use { input ->

                FileOutputStream(
                    temporary
                ).use { output ->

                    val buffer =
                        ByteArray(
                            1024 * 1024
                        )

                    while (true) {

                        val count =
                            input.read(
                                buffer
                            )

                        if (count <= 0) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )
                    }

                    output.flush()
                }
            }


        if (destination.exists()) {
            destination.delete()
        }


        if (
            !temporary.renameTo(
                destination
            )
        ) {

            temporary.copyTo(
                destination,
                overwrite = true
            )

            temporary.delete()
        }
    }
}
