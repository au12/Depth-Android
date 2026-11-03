package com.depth.breathtrainer.engine

import android.content.Context
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings

/** Persists table settings across launches, same role as ~/Library/Preferences/com.local.depth.plist */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("depth_settings", Context.MODE_PRIVATE)

    fun load(): TrainerSettings {
        val defaults = TrainerSettings()
        return TrainerSettings(
            mode = runCatching { TrainerMode.valueOf(prefs.getString("mode", defaults.mode.name)!!) }.getOrDefault(defaults.mode),
            maxHoldSeconds = prefs.getInt("maxHoldSeconds", defaults.maxHoldSeconds),
            rounds = prefs.getInt("rounds", defaults.rounds),
            co2HoldPct = prefs.getInt("co2HoldPct", defaults.co2HoldPct),
            co2RestStart = prefs.getInt("co2RestStart", defaults.co2RestStart),
            co2RestEnd = prefs.getInt("co2RestEnd", defaults.co2RestEnd),
            o2HoldStartPct = prefs.getInt("o2HoldStartPct", defaults.o2HoldStartPct),
            o2HoldEndPct = prefs.getInt("o2HoldEndPct", defaults.o2HoldEndPct),
            o2Rest = prefs.getInt("o2Rest", defaults.o2Rest),
            sqIn = prefs.getInt("sqIn", defaults.sqIn),
            sqHold1 = prefs.getInt("sqHold1", defaults.sqHold1),
            sqOut = prefs.getInt("sqOut", defaults.sqOut),
            sqHold2 = prefs.getInt("sqHold2", defaults.sqHold2),
            sqCycles = prefs.getInt("sqCycles", defaults.sqCycles),
            customRounds = prefs.getString("customRounds", defaults.customRounds)!!,
            soundOn = prefs.getBoolean("soundOn", defaults.soundOn),
            vibrateOn = prefs.getBoolean("vibrateOn", defaults.vibrateOn),
            prepOn = prefs.getBoolean("prepOn", defaults.prepOn)
        )
    }

    fun save(s: TrainerSettings) {
        prefs.edit().apply {
            putString("mode", s.mode.name)
            putInt("maxHoldSeconds", s.maxHoldSeconds)
            putInt("rounds", s.rounds)
            putInt("co2HoldPct", s.co2HoldPct)
            putInt("co2RestStart", s.co2RestStart)
            putInt("co2RestEnd", s.co2RestEnd)
            putInt("o2HoldStartPct", s.o2HoldStartPct)
            putInt("o2HoldEndPct", s.o2HoldEndPct)
            putInt("o2Rest", s.o2Rest)
            putInt("sqIn", s.sqIn)
            putInt("sqHold1", s.sqHold1)
            putInt("sqOut", s.sqOut)
            putInt("sqHold2", s.sqHold2)
            putInt("sqCycles", s.sqCycles)
            putString("customRounds", s.customRounds)
            putBoolean("soundOn", s.soundOn)
            putBoolean("vibrateOn", s.vibrateOn)
            putBoolean("prepOn", s.prepOn)
        }.apply()
    }
}
