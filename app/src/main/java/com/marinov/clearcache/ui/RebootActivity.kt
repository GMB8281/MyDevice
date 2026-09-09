package com.marinov.clearcache.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.TimePicker
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.PrefsConstants

class RebootActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var switchReboot: MaterialSwitch
    private lateinit var textSwitchState: TextView
    private lateinit var timePicker: TimePicker
    private val dayButtons = mutableListOf<ToggleButton>()
    private val dayKeys = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

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

        initDayButtons()
        loadPreferences()

        switchReboot.setOnCheckedChangeListener { _, isChecked ->
            updateUiState(isChecked)
            prefs.edit().putBoolean(PrefsConstants.KEY_AUTO_REBOOT_ENABLED, isChecked).apply()

            if (isChecked) {
                val anyChecked = dayButtons.any { it.isChecked }
                if (!anyChecked) {
                    dayButtons[1].isChecked = true
                    prefs.edit().putBoolean("${PrefsConstants.KEY_REBOOT_DAY_PREFIX}mon", true).apply()
                }
            }
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

    private fun initDayButtons() {
        val buttonIds = listOf(R.id.btn_sun, R.id.btn_mon, R.id.btn_tue, R.id.btn_wed, R.id.btn_thu, R.id.btn_fri, R.id.btn_sat)
        for (i in buttonIds.indices) {
            val btn = findViewById<ToggleButton>(buttonIds[i])
            dayButtons.add(btn)
            btn.setOnCheckedChangeListener { _, _ ->
                prefs.edit().putBoolean("${PrefsConstants.KEY_REBOOT_DAY_PREFIX}${dayKeys[i]}", btn.isChecked).apply()
                checkIfAllDaysDisabled()
                if (switchReboot.isChecked) AlarmScheduler.scheduleReboot(this)
            }
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_AUTO_REBOOT_ENABLED, true)
        switchReboot.isChecked = isEnabled
        updateUiState(isEnabled)

        val hour = prefs.getInt(PrefsConstants.KEY_REBOOT_HOUR, 3)
        val minute = prefs.getInt(PrefsConstants.KEY_REBOOT_MINUTE, 0)
        timePicker.hour = hour
        timePicker.minute = minute

        var anyDaySet = false
        for (key in dayKeys) {
            if (prefs.contains("${PrefsConstants.KEY_REBOOT_DAY_PREFIX}$key")) {
                anyDaySet = true
                break
            }
        }
        if (!anyDaySet) {
            prefs.edit().putBoolean("${PrefsConstants.KEY_REBOOT_DAY_PREFIX}mon", true).apply()
        }

        for (i in dayKeys.indices) {
            dayButtons[i].isChecked = prefs.getBoolean("${PrefsConstants.KEY_REBOOT_DAY_PREFIX}${dayKeys[i]}", i == 1)
        }
    }

    private fun checkIfAllDaysDisabled() {
        val anyChecked = dayButtons.any { it.isChecked }
        if (!anyChecked && switchReboot.isChecked) {
            switchReboot.isChecked = false
        }
    }

    private fun updateUiState(isEnabled: Boolean) {
        textSwitchState.text = if (isEnabled) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        val alphaValue = if (isEnabled) 1.0f else 0.4f
        timePicker.isEnabled = isEnabled
        timePicker.alpha = alphaValue
        findViewById<TextView>(R.id.text_days_label).alpha = alphaValue
        dayButtons.forEach {
            it.isEnabled = isEnabled
            it.alpha = alphaValue
        }
    }
}