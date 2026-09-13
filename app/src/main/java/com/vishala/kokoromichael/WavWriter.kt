package com.vishala.kokoromichael

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile


class WavWriter(
    private val context: Context
) {

    companion object {

        private const val SAMPLE_RATE =
            24000

        private const val CHANNELS =
            1

        private const val BITS_PER_SAMPLE =
            16
    }


    private val temporaryFile =
        File.createTempFile(
            "kokoro_",
            ".wav",
            context.cacheDir
        )


    private val randomAccessFile =
        RandomAccessFile(
            temporaryFile,
            "rw"
        )


    private var dataSize =
        0L


    init {

        writeHeader(
            randomAccessFile,
            0L
        )
    }


    fun writePcm(
        pcm: ByteArray
    ) {

        if (pcm.isEmpty()) {
            return
        }


        randomAccessFile.seek(
            randomAccessFile.length()
        )


        randomAccessFile.write(
            pcm
        )


        dataSize +=
            pcm.size.toLong()
    }


    fun finish(): Uri {

        randomAccessFile.seek(0)

        writeHeader(
            randomAccessFile,
            dataSize
        )

        randomAccessFile.close()


        val resolver =
            context.contentResolver


        val collection =
            MediaStore.Audio.Media
                .getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )


        val values =
            ContentValues().apply {

                put(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    "audio.wav"
                )

                put(
                    MediaStore.Audio.Media.MIME_TYPE,
                    "audio/wav"
                )

                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC
                )

                put(
                    MediaStore.Audio.Media.IS_PENDING,
                    1
                )
            }


        val uri =
            resolver.insert(
                collection,
                values
            )
                ?: throw IllegalStateException(
                    "Could not create Music/audio.wav"
                )


        try {

            resolver.openOutputStream(
                uri,
                "w"
            ).use { output ->

                if (output == null) {

                    throw IllegalStateException(
                        "Could not open output stream"
                    )
                }


                FileInputStream(
                    temporaryFile
                ).use { input ->

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


            val publishedValues =
                ContentValues().apply {

                    put(
                        MediaStore.Audio.Media.IS_PENDING,
                        0
                    )
                }


            resolver.update(
                uri,
                publishedValues,
                null,
                null
            )


            temporaryFile.delete()

            return uri

        } catch (error: Throwable) {

            resolver.delete(
                uri,
                null,
                null
            )

            temporaryFile.delete()

            throw error
        }
    }


    fun abort() {

        try {
            randomAccessFile.close()
        } catch (_: Throwable) {
        }


        temporaryFile.delete()
    }


    private fun writeHeader(
        file: RandomAccessFile,
        audioDataLength: Long
    ) {

        val byteRate =
            SAMPLE_RATE *
                    CHANNELS *
                    BITS_PER_SAMPLE /
                    8


        val blockAlign =
            CHANNELS *
                    BITS_PER_SAMPLE /
                    8


        val riffChunkSize =
            36L +
                    audioDataLength


        file.writeBytes("RIFF")

        writeIntLE(
            file,
            riffChunkSize
        )

        file.writeBytes("WAVE")


        file.writeBytes("fmt ")

        writeIntLE(
            file,
            16
        )

        writeShortLE(
            file,
            1
        )

        writeShortLE(
            file,
            CHANNELS
        )

        writeIntLE(
            file,
            SAMPLE_RATE
        )

        writeIntLE(
            file,
            byteRate
        )

        writeShortLE(
            file,
            blockAlign
        )

        writeShortLE(
            file,
            BITS_PER_SAMPLE
        )


        file.writeBytes("data")

        writeIntLE(
            file,
            audioDataLength
        )
    }


    private fun writeIntLE(
        file: RandomAccessFile,
        value: Long
    ) {

        file.write(
            (value and 0xFF).toInt()
        )

        file.write(
            ((value shr 8) and 0xFF).toInt()
        )

        file.write(
            ((value shr 16) and 0xFF).toInt()
        )

        file.write(
            ((value shr 24) and 0xFF).toInt()
        )
    }


    private fun writeShortLE(
        file: RandomAccessFile,
        value: Int
    ) {

        file.write(
            value and 0xFF
        )

        file.write(
            (value shr 8) and 0xFF
        )
    }
}
