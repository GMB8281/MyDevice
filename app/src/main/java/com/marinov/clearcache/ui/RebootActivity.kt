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

class RebootActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var switchReboot: MaterialSwitch
    private lateinit var textSwitchState: TextView
    private lateinit var timePicker: TimePicker
    private lateinit var scheduleSection: ScheduleSection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reboot)

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        switchReboot = findViewById(R.id.switch_reboot)
        textSwitchState = findViewById(R.id.text_switch_state)
        timePicker = findViewById(R.id.time_picker)
        timePicker.setIs24HourView(true)

        scheduleSection = ScheduleSection(
            activity = this,
            prefs = prefs,
            config = ScheduleSection.Config(
                typeKey = PrefsConstants.KEY_REBOOT_SCHEDULE_TYPE,
                defaultType = ScheduleType.WEEKLY,
                dayPrefix = PrefsConstants.KEY_REBOOT_DAY_PREFIX,
                dayOfMonthKey = PrefsConstants.KEY_REBOOT_DAY_OF_MONTH
            ),
            onChanged = { if (switchReboot.isChecked) AlarmScheduler.scheduleReboot(this) },
            onNoDaySelected = { switchReboot.isChecked = false }
        )

        loadPreferences()

        switchReboot.setOnCheckedChangeListener { _, isChecked ->
            updateUiState(isChecked)
            prefs.edit().putBoolean(PrefsConstants.KEY_AUTO_REBOOT_ENABLED, isChecked).apply()

            if (isChecked) scheduleSection.ensureDayForWeekly()
            AlarmScheduler.scheduleReboot(this)
        }

        timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            prefs.edit()
                .putInt(PrefsConstants.KEY_REBOOT_HOUR, hourOfDay)
                .putInt(PrefsConstants.KEY_REBOOT_MINUTE, minute)
                .apply()
            if (switchReboot.isChecked) AlarmScheduler.scheduleReboot(this)
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_AUTO_REBOOT_ENABLED, true)
        switchReboot.isChecked = isEnabled
        updateUiState(isEnabled)

        timePicker.hour = prefs.getInt(PrefsConstants.KEY_REBOOT_HOUR, 3)
        timePicker.minute = prefs.getInt(PrefsConstants.KEY_REBOOT_MINUTE, 0)
    }

    private fun updateUiState(isEnabled: Boolean) {
        textSwitchState.text = if (isEnabled) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        timePicker.isEnabled = isEnabled
        timePicker.alpha = if (isEnabled) 1.0f else 0.4f
        scheduleSection.setEnabledState(isEnabled)
    }
}
