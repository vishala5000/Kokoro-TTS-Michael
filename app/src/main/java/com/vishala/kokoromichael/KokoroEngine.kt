package com.vishala.kokoromichael

object KokoroEngine {

    private var initialized = false

    /*
     * Kokoro has a 512-token context and the official model
     * documentation recommends <= 510 phonemes per pass.
     *
     * We deliberately use a conservative character limit here.
     *
     * This prevents long text from reaching the model/library
     * as one huge request.
     */
    private const val MAX_CHUNK_CHARS = 300

    /*
     * Small chunks are joined with silence so that the end of
     * one generated sentence does not collide with the beginning
     * of the next generated sentence.
     *
     * 120 ms at 24 kHz = 2880 samples.
     *
     * PCM16 mono = 2 bytes/sample.
     */
    private const val SAMPLE_RATE = 24000
    private const val SILENCE_MS = 80

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

    /**
     * Synthesize complete text.
     *
     * Long text is split before reaching Kokoro.
     *
     * The chunks are generated sequentially and then
     * concatenated in exactly the same order as the input.
     */
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

        val cleanText = normalizeText(text)

        if (cleanText.isEmpty()) {
            throw IllegalArgumentException(
                "Text cannot be empty"
            )
        }

        val safeSpeed = speed.coerceIn(
            0.5f,
            2.0f
        )

        /*
         * Short text:
         *
         * Don't unnecessarily split it.
         */
        if (cleanText.length <= MAX_CHUNK_CHARS) {

            return synthesizeSingle(
                cleanText,
                safeSpeed
            )
        }

        /*
         * Long text:
         *
         * Split into safe chunks.
         */
        val chunks = splitTextSafely(
            cleanText
        )

        if (chunks.isEmpty()) {
            throw IllegalStateException(
                "Unable to split text for synthesis"
            )
        }

        /*
         * Generate every chunk.
         */
        val audioParts = ArrayList<ByteArray>(
            chunks.size
        )

        for (chunk in chunks) {

            if (chunk.isBlank()) {
                continue
            }

            val audio = synthesizeSingle(
                chunk,
                safeSpeed
            )

            if (audio.isNotEmpty()) {
                audioParts.add(audio)
            }
        }

        if (audioParts.isEmpty()) {
            throw IllegalStateException(
                "Kokoro generated no audio"
            )
        }

        /*
         * Join all generated audio.
         */
        return concatenateAudio(
            audioParts
        )
    }

    /**
     * Generate exactly one chunk.
     */
    private fun synthesizeSingle(
        text: String,
        speed: Float
    ): ByteArray {

        val audio = nativeSynthesize(
            text,
            speed
        )

        return audio
            ?: throw IllegalStateException(
                "Kokoro synthesis returned no audio for chunk: $text"
            )
    }

    /**
     * Normalize only whitespace.
     *
     * We intentionally DO NOT remove words,
     * punctuation, numbers, or symbols.
     */
    private fun normalizeText(
        text: String
    ): String {

        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .joinToString(" ") { line ->
                line.trim()
            }
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    /**
     * Split long text without cutting words.
     *
     * Priority:
     *
     * 1. paragraph
     * 2. sentence
     * 3. comma / semicolon / colon
     * 4. word boundary
     *
     * We never cut inside a word.
     */
    private fun splitTextSafely(
        text: String
    ): List<String> {

        val result = ArrayList<String>()

        var remaining = text.trim()

        while (remaining.isNotEmpty()) {

            /*
             * Small enough:
             */
            if (remaining.length <= MAX_CHUNK_CHARS) {

                result.add(
                    remaining.trim()
                )

                break
            }

            /*
             * Look only inside the safe window.
             */
            val window = remaining.substring(
                0,
                MAX_CHUNK_CHARS
            )

            /*
             * First preference:
             * sentence ending.
             */
            var splitAt = findLastSentenceBoundary(
                window
            )

            /*
             * Second preference:
             * comma / semicolon / colon.
             */
            if (splitAt <= 0) {

                splitAt = findLastPunctuationBoundary(
                    window
                )
            }

            /*
             * Third preference:
             * whitespace.
             */
            if (splitAt <= 0) {

                splitAt = window.lastIndexOf(
                    ' '
                )
            }

            /*
             * Absolute fallback.
             *
             * This should almost never happen because
             * normal English text contains spaces.
             */
            if (splitAt <= 0) {

                splitAt = MAX_CHUNK_CHARS
            }

            val chunk = remaining
                .substring(
                    0,
                    splitAt
                )
                .trim()

            if (chunk.isNotEmpty()) {
                result.add(chunk)
            }

            /*
             * Remove exactly the consumed portion.
             *
             * Nothing after the boundary is discarded.
             */
            remaining = remaining
                .substring(splitAt)
                .trimStart()
        }

        return result
    }

    /**
     * Find the last sentence-ending punctuation.
     */
    private fun findLastSentenceBoundary(
        text: String
    ): Int {

        val candidates = listOf(
            text.lastIndexOf(". "),
            text.lastIndexOf("! "),
            text.lastIndexOf("? "),
            text.lastIndexOf(".\n"),
            text.lastIndexOf("!\n"),
            text.lastIndexOf("?\n")
        )

        val index = candidates.maxOrNull()
            ?: -1

        if (index < 0) {
            return -1
        }

        /*
         * Include the punctuation mark itself.
         */
        return index + 1
    }

    /**
     * Find a natural punctuation boundary.
     */
    private fun findLastPunctuationBoundary(
        text: String
    ): Int {

        val candidates = listOf(
            text.lastIndexOf(", "),
            text.lastIndexOf("; "),
            text.lastIndexOf(": "),
            text.lastIndexOf(" — "),
            text.lastIndexOf(" - ")
        )

        val index = candidates.maxOrNull()
            ?: -1

        if (index < 0) {
            return -1
        }

        /*
         * Keep punctuation inside the chunk.
         */
        return index + 1
    }

    /**
     * Concatenate PCM16 little-endian mono audio.
     *
     * Every ByteArray contains:
     *
     * 24,000 Hz
     * mono
     * signed PCM16
     * little endian
     */
    private fun concatenateAudio(
        parts: List<ByteArray>
    ): ByteArray {

        if (parts.size == 1) {
            return parts[0]
        }

        val silenceBytes =
            (SAMPLE_RATE * SILENCE_MS / 1000) * 2

        val totalSize =
            parts.sumOf { it.size } +
                silenceBytes * (parts.size - 1)

        val output = ByteArray(
            totalSize
        )

        var position = 0

        for (index in parts.indices) {

            val part = parts[index]

            System.arraycopy(
                part,
                0,
                output,
                position,
                part.size
            )

            position += part.size

            /*
             * Add a small amount of silence between
             * separately synthesized chunks.
             */
            if (index < parts.lastIndex) {

                position += silenceBytes
            }
        }

        return output
    }

    @Synchronized
    fun release() {

        if (!initialized) {
            return
        }

        nativeRelease()

        initialized = false
    }
}
