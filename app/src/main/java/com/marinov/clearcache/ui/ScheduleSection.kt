package com.marinov.clearcache.ui

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.ScheduleType

/**
 * Controla a seção de periodicidade (Diário / Semanal / Mensal), no estilo dos roteadores TP-Link:
 *  - Diário: só o horário (TimePicker fica na própria Activity)
 *  - Semanal: dias da semana
 *  - Mensal: dia do mês
 *
 * Usa o layout `layout_schedule_options`, incluído em RebootActivity e AutoCleanCacheActivity.
 */
class ScheduleSection(
    activity: AppCompatActivity,
    private val prefs: SharedPreferences,
    private val config: Config,
    private val onChanged: () -> Unit,
    private val onNoDaySelected: () -> Unit
) {
    data class Config(
        val typeKey: String,
        val defaultType: ScheduleType,
        val dayPrefix: String,
        val dayOfMonthKey: String
    )

    private val container: View = activity.findViewById(R.id.container_schedule)
    private val toggleType: MaterialButtonToggleGroup = activity.findViewById(R.id.toggle_schedule_type)
    private val containerWeekly: View = activity.findViewById(R.id.container_weekly)
    private val containerMonthly: View = activity.findViewById(R.id.container_monthly)
    private val pickerDayOfMonth: NumberPicker = activity.findViewById(R.id.picker_day_of_month)

    private val dayKeys = AlarmScheduler.dayKeys
    private val dayButtons: List<ToggleButton> = listOf(
        R.id.btn_sun, R.id.btn_mon, R.id.btn_tue, R.id.btn_wed,
        R.id.btn_thu, R.id.btn_fri, R.id.btn_sat
    ).map { activity.findViewById<ToggleButton>(it) }

    private val typeByButtonId = mapOf(
        R.id.btn_type_daily to ScheduleType.DAILY,
        R.id.btn_type_weekly to ScheduleType.WEEKLY,
        R.id.btn_type_monthly to ScheduleType.MONTHLY
    )

    // Evita que atualizações feitas por código disparem salvamento/reagendamento
    private var updating = false

    val type: ScheduleType
        get() = typeByButtonId[toggleType.checkedButtonId] ?: config.defaultType

    init {
        pickerDayOfMonth.minValue = 1
        pickerDayOfMonth.maxValue = 31
        pickerDayOfMonth.wrapSelectorWheel = true

        loadPreferences()
        attachListeners()
    }

    fun hasAnyDay(): Boolean = dayButtons.any { it.isChecked }

    /** No modo semanal, garante ao menos um dia marcado (padrão: segunda-feira). */
    fun ensureDayForWeekly() {
        if (type == ScheduleType.WEEKLY && !hasAnyDay()) selectDefaultDay()
    }

    fun setEnabledState(enabled: Boolean) {
        container.alpha = if (enabled) 1.0f else 0.4f
        setEnabledRecursive(container, enabled)
    }

    // ------------------------------------------------------------------

    private fun loadPreferences() {
        updating = true

        val savedType = ScheduleType.fromKey(prefs.getString(config.typeKey, null), config.defaultType)
        toggleType.check(buttonIdFor(savedType))

        pickerDayOfMonth.value = prefs.getInt(config.dayOfMonthKey, 1).coerceIn(1, 31)

        // Mesmo padrão usado pelo AlarmScheduler: sem preferência salva, só a segunda-feira vem marcada
        for (i in dayKeys.indices) {
            dayButtons[i].isChecked = prefs.getBoolean("${config.dayPrefix}${dayKeys[i]}", i == 1)
        }

        applyVisibility(savedType)
        updating = false
    }

    private fun attachListeners() {
        toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updating) return@addOnButtonCheckedListener
            val newType = typeByButtonId[checkedId] ?: return@addOnButtonCheckedListener

            prefs.edit().putString(config.typeKey, newType.key).apply()
            if (newType == ScheduleType.WEEKLY && !hasAnyDay()) selectDefaultDay()
            applyVisibility(newType)
            onChanged()
        }

        for (i in dayButtons.indices) {
            dayButtons[i].setOnCheckedChangeListener { _, isChecked ->
                if (updating) return@setOnCheckedChangeListener
                prefs.edit().putBoolean("${config.dayPrefix}${dayKeys[i]}", isChecked).apply()
                if (type == ScheduleType.WEEKLY && !hasAnyDay()) onNoDaySelected()
                onChanged()
            }
        }

        pickerDayOfMonth.setOnValueChangedListener { _, _, newValue ->
            prefs.edit().putInt(config.dayOfMonthKey, newValue).apply()
            onChanged()
        }
    }

    private fun selectDefaultDay() {
        updating = true
        dayButtons[1].isChecked = true
        prefs.edit().putBoolean("${config.dayPrefix}${dayKeys[1]}", true).apply()
        updating = false
    }

    private fun applyVisibility(type: ScheduleType) {
        containerWeekly.visibility = if (type == ScheduleType.WEEKLY) View.VISIBLE else View.GONE
        containerMonthly.visibility = if (type == ScheduleType.MONTHLY) View.VISIBLE else View.GONE
    }

    private fun buttonIdFor(type: ScheduleType): Int =
        typeByButtonId.entries.first { it.value == type }.key

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) setEnabledRecursive(view.getChildAt(i), enabled)
        }
    }
}
