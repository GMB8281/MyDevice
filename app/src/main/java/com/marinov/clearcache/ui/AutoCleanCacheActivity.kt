package com.marinov.clearcache.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.PrefsConstants

class AutoCleanCacheActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var switchClean: MaterialSwitch
    private lateinit var textSwitchState: TextView
    private lateinit var timePicker: TimePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_clean)

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        switchClean = findViewById(R.id.switch_clean)
        textSwitchState = findViewById(R.id.text_switch_state)
        timePicker = findViewById(R.id.time_picker)
        timePicker.setIs24HourView(true)

        loadPreferences()

        switchClean.setOnCheckedChangeListener { _, isChecked ->
            updateUiState(isChecked)
            prefs.edit().putBoolean(PrefsConstants.KEY_AUTO_CLEAN_ENABLED, isChecked).apply()
            AlarmScheduler.scheduleClean(this)
        }

        timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            prefs.edit()
                .putInt(PrefsConstants.KEY_CLEAN_HOUR, hourOfDay)
                .putInt(PrefsConstants.KEY_CLEAN_MINUTE, minute)
                .apply()
            if (switchClean.isChecked) AlarmScheduler.scheduleClean(this)
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_AUTO_CLEAN_ENABLED, true)
        switchClean.isChecked = isEnabled
        updateUiState(isEnabled)

        val hour = prefs.getInt(PrefsConstants.KEY_CLEAN_HOUR, 3)
        val minute = prefs.getInt(PrefsConstants.KEY_CLEAN_MINUTE, 0)
        timePicker.hour = hour
        timePicker.minute = minute
    }

    private fun updateUiState(isEnabled: Boolean) {
        textSwitchState.text = if (isEnabled) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        val alphaValue = if (isEnabled) 1.0f else 0.4f
        timePicker.isEnabled = isEnabled
        timePicker.alpha = alphaValue
    }
}