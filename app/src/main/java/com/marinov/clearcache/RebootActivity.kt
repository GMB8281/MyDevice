package com.marinov.clearcache

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

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

        prefs = getSharedPreferences(CleanCacheDialogActivity.PREFS_NAME, Context.MODE_PRIVATE)

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
            prefs.edit().putBoolean("auto_reboot_enabled", isChecked).apply()

            // Ao ATIVAR o switch: se nenhum dia estiver marcado, seleciona segunda por padrão
            if (isChecked) {
                val anyChecked = dayButtons.any { it.isChecked }
                if (!anyChecked) {
                    dayButtons[1].isChecked = true
                    prefs.edit().putBoolean("reboot_day_mon", true).apply()
                }
            }

            AlarmScheduler.scheduleReboot(this)
        }

        timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            prefs.edit()
                .putInt("reboot_hour", hourOfDay)
                .putInt("reboot_minute", minute)
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
                prefs.edit().putBoolean("reboot_day_${dayKeys[i]}", btn.isChecked).apply()
                // Ao desmarcar um dia: se ficou sem nenhum e o switch está ON, desativa o switch
                checkIfAllDaysDisabled()
                if (switchReboot.isChecked) AlarmScheduler.scheduleReboot(this)
            }
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.getBoolean("auto_reboot_enabled", true)
        switchReboot.isChecked = isEnabled
        updateUiState(isEnabled)

        val hour = prefs.getInt("reboot_hour", 3)
        val minute = prefs.getInt("reboot_minute", 0)
        timePicker.hour = hour
        timePicker.minute = minute

        // Se nunca foi configurado, define segunda como padrão
        var anyDaySet = false
        for (key in dayKeys) {
            if (prefs.contains("reboot_day_$key")) {
                anyDaySet = true
                break
            }
        }

        if (!anyDaySet) {
            prefs.edit().putBoolean("reboot_day_mon", true).apply()
        }

        for (i in dayKeys.indices) {
            dayButtons[i].isChecked = prefs.getBoolean("reboot_day_${dayKeys[i]}", i == 1)
        }
    }

    /**
     * Se o switch está ATIVADO e o usuário desmarcou todos os dias,
     * desativa o switch automaticamente (em vez de forçar a segunda).
     * A seleção automática da segunda só ocorre ao LIGAR o switch sem dia algum.
     */
    private fun checkIfAllDaysDisabled() {
        val anyChecked = dayButtons.any { it.isChecked }
        if (!anyChecked && switchReboot.isChecked) {
            // Desativa o switch — o listener dele cuidará de salvar e reagendar
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