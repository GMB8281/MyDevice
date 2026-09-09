package com.marinov.clearcache.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.PrefsConstants
import com.marinov.clearcache.service.BatteryProtectionService
import androidx.core.content.edit

class BatteryProtectionActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchProtection: MaterialSwitch
    private lateinit var textSwitchState: TextView
    private lateinit var spinnerWeak: Spinner
    private lateinit var spinnerCritical: Spinner
    private lateinit var spinnerReserve: Spinner

    private var weakLevel = 20
    private var criticalLevel = 10
    private var reserveLevel = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_protection)

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        switchProtection = findViewById(R.id.switch_protection)
        textSwitchState = findViewById(R.id.text_switch_state)
        spinnerWeak = findViewById(R.id.spinner_weak)
        spinnerCritical = findViewById(R.id.spinner_critical)
        spinnerReserve = findViewById(R.id.spinner_reserve)

        loadPreferences()
        setupSpinners()
        setupSwitch()
    }

    private fun loadPreferences() {
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_BATTERY_PROTECTION_ENABLED, false)
        switchProtection.isChecked = isEnabled
        updateUiState(isEnabled)

        weakLevel = prefs.getInt(PrefsConstants.KEY_WEAK_BATTERY, 20)
        criticalLevel = prefs.getInt(PrefsConstants.KEY_CRITICAL_BATTERY, 10)
        reserveLevel = prefs.getInt(PrefsConstants.KEY_ENERGY_RESERVE, 5)
    }

    private fun setupSwitch() {
        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            updateUiState(isChecked)
            prefs.edit {putBoolean(PrefsConstants.KEY_BATTERY_PROTECTION_ENABLED, isChecked) }

            val serviceIntent = Intent(this, BatteryProtectionService::class.java)
            if (isChecked) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
        }
    }

    private fun setupSpinners() {
        updateSpinnerAdapters()

        spinnerReserve.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedString = parent?.getItemAtPosition(position) as String
                val selected = selectedString.replace("%", "").toIntOrNull() ?: return

                if (selected != reserveLevel) {
                    reserveLevel = selected
                    prefs.edit { putInt(PrefsConstants.KEY_ENERGY_RESERVE, reserveLevel) }

                    if (criticalLevel <= reserveLevel) {
                        criticalLevel = reserveLevel + 1
                        prefs.edit { putInt(PrefsConstants.KEY_CRITICAL_BATTERY, criticalLevel) }
                    }
                    if (weakLevel <= criticalLevel) {
                        weakLevel = criticalLevel + 1
                        prefs.edit { putInt(PrefsConstants.KEY_WEAK_BATTERY, weakLevel) }
                    }
                    updateSpinnerAdapters()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerCritical.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedString = parent?.getItemAtPosition(position) as String
                val selected = selectedString.replace("%", "").toIntOrNull() ?: return

                if (selected != criticalLevel) {
                    criticalLevel = selected
                    prefs.edit { putInt(PrefsConstants.KEY_CRITICAL_BATTERY, criticalLevel) }

                    if (weakLevel <= criticalLevel) {
                        weakLevel = criticalLevel + 1
                        prefs.edit { putInt(PrefsConstants.KEY_WEAK_BATTERY, weakLevel) }
                    }
                    updateSpinnerAdapters()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerWeak.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedString = parent?.getItemAtPosition(position) as String
                val selected = selectedString.replace("%", "").toIntOrNull() ?: return

                if (selected != weakLevel) {
                    weakLevel = selected
                    prefs.edit { putInt(PrefsConstants.KEY_WEAK_BATTERY, weakLevel) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSpinnerAdapters() {
        val reserveList = (0..49).toList()
        val criticalList = ((reserveLevel + 1)..99).toList()
        val weakList = ((criticalLevel + 1)..100).toList()

        val reserveStrings = reserveList.map { getString(R.string.percent_format, it) }
        val criticalStrings = criticalList.map { getString(R.string.percent_format, it) }
        val weakStrings = weakList.map { getString(R.string.percent_format, it) }

        val reserveAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reserveStrings)
        reserveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReserve.adapter = reserveAdapter
        spinnerReserve.setSelection(reserveList.indexOf(reserveLevel))

        val criticalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, criticalStrings)
        criticalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCritical.adapter = criticalAdapter
        spinnerCritical.setSelection(criticalList.indexOf(criticalLevel))

        val weakAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, weakStrings)
        weakAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerWeak.adapter = weakAdapter
        spinnerWeak.setSelection(weakList.indexOf(weakLevel))
    }

    private fun updateUiState(isEnabled: Boolean) {
        textSwitchState.text = if (isEnabled) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        val alphaValue = if (isEnabled) 1.0f else 0.4f

        spinnerWeak.isEnabled = isEnabled
        spinnerWeak.alpha = alphaValue
        spinnerCritical.isEnabled = isEnabled
        spinnerCritical.alpha = alphaValue
        spinnerReserve.isEnabled = isEnabled
        spinnerReserve.alpha = alphaValue

        findViewById<TextView>(R.id.label_weak).alpha = alphaValue
        findViewById<TextView>(R.id.label_critical).alpha = alphaValue
        findViewById<TextView>(R.id.label_reserve).alpha = alphaValue
    }
}