package com.vishala.kokoromichael

import android.content.Context


object Settings {

    const val VOICE =
        "am_michael"

    const val LANGUAGE =
        "en-us"

    const val SAMPLE_RATE =
        24000

    const val MIN_SPEED =
        0.5f

    const val MAX_SPEED =
        2.0f

    const val DEFAULT_SPEED =
        1.0f

    const val SPEED_STEPS =
        150


    private const val PREFS =
        "kokoro_settings"

    private const val SPEED_KEY =
        "speed"


    fun saveSpeed(
        context: Context,
        speed: Float
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putFloat(
                SPEED_KEY,
                speed.coerceIn(
                    MIN_SPEED,
                    MAX_SPEED
                )
            )
            .apply()
    }


    fun loadSpeed(
        context: Context
    ): Float {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getFloat(
                SPEED_KEY,
                DEFAULT_SPEED
            )
            .coerceIn(
                MIN_SPEED,
                MAX_SPEED
            )
    }


    fun progressToSpeed(
        progress: Int
    ): Float {

        return (
            MIN_SPEED +
                    progress.coerceIn(
                        0,
                        SPEED_STEPS
                    ) / 100f
        ).coerceIn(
            MIN_SPEED,
            MAX_SPEED
        )
    }


    fun speedToProgress(
        speed: Float
    ): Int {

        return (
            (
                speed.coerceIn(
                    MIN_SPEED,
                    MAX_SPEED
                ) - MIN_SPEED
            ) * 100f
        ).toInt()
    }
}
