package com.vishala.kokoromichael

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

import android.app.Activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale


class MainActivity : Activity() {

    private lateinit var textInput: EditText

    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView

    private val activityJob =
        SupervisorJob()

    private val scope =
        CoroutineScope(
            Dispatchers.Main.immediate +
                    activityJob
        )

    private var generationJob: Job? = null

    @Volatile
    private var stopRequested = false


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )


        textInput =
            findViewById(
                R.id.textInput
            )

        speedSeekBar =
            findViewById(
                R.id.speedSeekBar
            )

        speedText =
            findViewById(
                R.id.speedText
            )

        startButton =
            findViewById(
                R.id.startButton
            )

        stopButton =
            findViewById(
                R.id.stopButton
            )

        progressBar =
            findViewById(
                R.id.progressBar
            )

        progressText =
            findViewById(
                R.id.progressText
            )

        statusText =
            findViewById(
                R.id.statusText
            )


        setupSpeedControl()

        setupButtons()

        restoreSettings()

        updateInitialState()
    }


    private fun setupSpeedControl() {

        speedSeekBar.max =
            Settings.SPEED_STEPS

        speedSeekBar.progress =
            Settings.speedToProgress(
                Settings.DEFAULT_SPEED
            )


        speedSeekBar.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    val speed =
                        Settings.progressToSpeed(
                            progress
                        )

                    speedText.text =
                        String.format(
                            Locale.US,
                            "%.2fx",
                            speed
                        )
                }


                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }


                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {

                    val speed =
                        Settings.progressToSpeed(
                            seekBar?.progress ?: 50
                        )

                    Settings.saveSpeed(
                        this@MainActivity,
                        speed
                    )
                }
            }
        )
    }


    private fun setupButtons() {

        startButton.setOnClickListener {
            startGeneration()
        }


        stopButton.setOnClickListener {
            stopGeneration()
        }
    }


    private fun restoreSettings() {

        val speed =
            Settings.loadSpeed(
                this
            )

        speedSeekBar.progress =
            Settings.speedToProgress(
                speed
            )

        speedText.text =
            String.format(
                Locale.US,
                "%.2fx",
                speed
            )
    }


    private fun updateInitialState() {

        startButton.isEnabled = true

        stopButton.isEnabled = false

        progressBar.progress = 0

        progressText.text = "0%"

        statusText.text =
            getString(
                R.string.status_ready
            )
    }


    private fun startGeneration() {

        if (
            generationJob?.isActive == true
        ) {
            return
        }


        val input =
            textInput.text
                .toString()
                .trim()


        if (input.isEmpty()) {

            Toast.makeText(
                this,
                R.string.enter_text_first,
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val speed =
            Settings.progressToSpeed(
                speedSeekBar.progress
            )


        Settings.saveSpeed(
            this,
            speed
        )


        stopRequested = false

        startButton.isEnabled = false

        stopButton.isEnabled = true

        progressBar.progress = 0

        progressText.text = "0%"

        statusText.text =
            getString(
                R.string.status_preparing
            )


        generationJob =
            scope.launch {

                try {

                    generate(
                        input,
                        speed
                    )

                } catch (
                    error: Throwable
                ) {

                    statusText.text =
                        "Error: ${
                            error.message
                                ?: "Unknown error"
                        }"

                } finally {

                    startButton.isEnabled =
                        true

                    stopButton.isEnabled =
                        false

                    generationJob = null
                }
            }
    }


    private fun stopGeneration() {

        if (
            generationJob?.isActive != true
        ) {
            return
        }

        stopRequested = true

        statusText.text =
            getString(
                R.string.status_stopping
            )
    }


    private suspend fun generate(
        text: String,
        speed: Float
    ) {

        val files =
            withContext(
                Dispatchers.IO
            ) {

                ModelDownloader.prepare(
                    this@MainActivity
                )
            }


        withContext(
            Dispatchers.Main
        ) {

            statusText.text =
                getString(
                    R.string.status_loading_model
                )

            progressBar.progress = 5

            progressText.text = "5%"
        }


        withContext(
            Dispatchers.IO
        ) {

            KokoroEngine.initialize(
                files.modelFile.absolutePath,
                files.voiceFile.absolutePath
            )
        }


        val chunks =
            splitForProgress(
                text
            )


        if (chunks.isEmpty()) {
            throw IllegalArgumentException(
                "No usable text"
            )
        }


        withContext(
            Dispatchers.Main
        ) {

            statusText.text =
                getString(
                    R.string.status_generating
                )

            progressBar.progress = 10

            progressText.text = "10%"
        }


        val wavWriter =
            WavWriter(
                this@MainActivity
            )


        var completed = 0


        try {

            for (chunk in chunks) {

                if (stopRequested) {
                    break
                }


                val pcm =
                    withContext(
                        Dispatchers.Default
                    ) {

                        KokoroEngine.synthesize(
                            chunk,
                            speed
                        )
                    }


                if (pcm.isNotEmpty()) {

                    wavWriter.writePcm(
                        pcm
                    )
                }


                completed++


                val percentage =
                    10 +
                            (
                                completed * 85
                                        /
                                        chunks.size
                            )


                withContext(
                    Dispatchers.Main
                ) {

                    progressBar.progress =
                        percentage

                    progressText.text =
                        "$percentage%"
                }
            }


            val outputUri =
                withContext(
                    Dispatchers.IO
                ) {

                    wavWriter.finish()
                }


            withContext(
                Dispatchers.Main
            ) {

                progressBar.progress = 100

                progressText.text = "100%"


                if (stopRequested) {

                    statusText.text =
                        getString(
                            R.string.status_stopped,
                            outputUri.toString()
                        )

                } else {

                    statusText.text =
                        getString(
                            R.string.status_complete,
                            outputUri.toString()
                        )
                }


                Toast.makeText(
                    this@MainActivity,
                    R.string.audio_saved,
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (error: Throwable) {

            wavWriter.abort()

            throw error
        }
    }


    private fun splitForProgress(
        text: String
    ): List<String> {

        val cleaned =
            text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim()


        if (cleaned.isEmpty()) {
            return emptyList()
        }


        val sentences =
            cleaned
                .split(
                    Regex(
                        "(?<=[.!?])\\s+|\\n+"
                    )
                )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotEmpty()
                }


        if (sentences.isEmpty()) {
            return listOf(cleaned)
        }


        val result =
            ArrayList<String>()


        val maxChars =
            700


        var current =
            StringBuilder()


        for (sentence in sentences) {

            if (
                sentence.length >
                maxChars
            ) {

                if (
                    current.isNotEmpty()
                ) {

                    result.add(
                        current
                            .toString()
                            .trim()
                    )

                    current =
                        StringBuilder()
                }


                val words =
                    sentence.split(
                        Regex("\\s+")
                    )


                var wordChunk =
                    StringBuilder()


                for (word in words) {

                    val candidate =
                        if (
                            wordChunk.isEmpty()
                        ) {
                            word
                        } else {
                            "${wordChunk} $word"
                        }


                    if (
                        candidate.length >
                        maxChars &&
                        wordChunk.isNotEmpty()
                    ) {

                        result.add(
                            wordChunk
                                .toString()
                                .trim()
                        )

                        wordChunk =
                            StringBuilder(
                                word
                            )

                    } else {

                        wordChunk =
                            StringBuilder(
                                candidate
                            )
                    }
                }


                if (
                    wordChunk.isNotEmpty()
                ) {

                    result.add(
                        wordChunk
                            .toString()
                            .trim()
                    )
                }


                continue
            }


            val candidate =
                if (
                    current.isEmpty()
                ) {
                    sentence
                } else {
                    "${current} $sentence"
                }


            if (
                candidate.length >
                maxChars &&
                current.isNotEmpty()
            ) {

                result.add(
                    current
                        .toString()
                        .trim()
                )

                current =
                    StringBuilder(
                        sentence
                    )

            } else {

                current =
                    StringBuilder(
                        candidate
                    )
            }
        }


        if (
            current.isNotEmpty()
        ) {

            result.add(
                current
                    .toString()
                    .trim()
            )
        }


        return result
            .filter {
                it.isNotEmpty()
            }
    }


    override fun onDestroy() {

        stopRequested = true

        KokoroEngine.release()

        scope.cancel()

        super.onDestroy()
    }
}
