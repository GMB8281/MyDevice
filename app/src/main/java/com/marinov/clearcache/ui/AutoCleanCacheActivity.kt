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
import com.marinov.clearcache.logic.ScheduleType

class AutoCleanCacheActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var switchClean: MaterialSwitch
    private lateinit var textSwitchState: TextView
    private lateinit var timePicker: TimePicker
    private lateinit var scheduleSection: ScheduleSection

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

        // Padrão Diário: mantém o comportamento de quem já usava a limpeza automática
        scheduleSection = ScheduleSection(
            activity = this,
            prefs = prefs,
            config = ScheduleSection.Config(
                typeKey = PrefsConstants.KEY_CLEAN_SCHEDULE_TYPE,
                defaultType = ScheduleType.DAILY,
                dayPrefix = PrefsConstants.KEY_CLEAN_DAY_PREFIX,
                dayOfMonthKey = PrefsConstants.KEY_CLEAN_DAY_OF_MONTH
            ),
            onChanged = { if (switchClean.isChecked) AlarmScheduler.scheduleClean(this) },
            onNoDaySelected = { switchClean.isChecked = false }
        )

        loadPreferences()

        switchClean.setOnCheckedChangeListener { _, isChecked ->
            updateUiState(isChecked)
            prefs.edit().putBoolean(PrefsConstants.KEY_AUTO_CLEAN_ENABLED, isChecked).apply()

            if (isChecked) scheduleSection.ensureDayForWeekly()
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

        timePicker.hour = prefs.getInt(PrefsConstants.KEY_CLEAN_HOUR, 3)
        timePicker.minute = prefs.getInt(PrefsConstants.KEY_CLEAN_MINUTE, 0)
    }

    private fun updateUiState(isEnabled: Boolean) {
        textSwitchState.text = if (isEnabled) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        timePicker.isEnabled = isEnabled
        timePicker.alpha = if (isEnabled) 1.0f else 0.4f
        scheduleSection.setEnabledState(isEnabled)
    }
}
