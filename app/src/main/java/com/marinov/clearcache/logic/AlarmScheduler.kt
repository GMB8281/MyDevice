package com.marinov.clearcache.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.marinov.clearcache.service.AutoCleanCacheService
import com.marinov.clearcache.service.AutoRebootService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmScheduler {
    private const val TAG = "ClearCache"

    const val ACTION_EXECUTE_ALARM = "ACTION_EXECUTE_ALARM"

    private const val REBOOT_REQUEST_CODE = 0
    private const val CLEAN_REQUEST_CODE = 100

    // Versões anteriores usavam um alarme por dia da semana (códigos 0..6)
    private val legacyRebootRequestCodes = 0..6

    val dayKeys = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
    private val dayCalendarValues = listOf(
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    )

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    fun scheduleAllAggressively(context: Context) {
        Log.d(TAG, "Iniciando agendamento agressivo de serviços...")
        scheduleReboot(context)
        scheduleClean(context)
    }

    /** Chamado pela UI/boot: (re)inicia ou para o serviço e arma o próximo alarme. */
    fun scheduleReboot(context: Context) {
        val prefs = prefs(context)
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_AUTO_REBOOT_ENABLED, true)

        if (isEnabled) {
            ContextCompat.startForegroundService(context, Intent(context, AutoRebootService::class.java))
        } else {
            context.stopService(Intent(context, AutoRebootService::class.java))
        }
        armReboot(context, prefs, isEnabled)
    }

    /** Chamado pelo próprio serviço quando o alarme dispara (alarmes exatos são de disparo único). */
    fun scheduleNextReboot(context: Context) {
        val prefs = prefs(context)
        armReboot(context, prefs, prefs.getBoolean(PrefsConstants.KEY_AUTO_REBOOT_ENABLED, true))
    }

    /** Chamado pela UI/boot: (re)inicia ou para o serviço e arma o próximo alarme. */
    fun scheduleClean(context: Context) {
        val prefs = prefs(context)
        val isEnabled = prefs.getBoolean(PrefsConstants.KEY_AUTO_CLEAN_ENABLED, true)

        if (isEnabled) {
            ContextCompat.startForegroundService(context, Intent(context, AutoCleanCacheService::class.java))
        } else {
            context.stopService(Intent(context, AutoCleanCacheService::class.java))
        }
        armClean(context, prefs, isEnabled)
    }

    /** Chamado pelo próprio serviço quando o alarme dispara (alarmes exatos são de disparo único). */
    fun scheduleNextClean(context: Context) {
        val prefs = prefs(context)
        armClean(context, prefs, prefs.getBoolean(PrefsConstants.KEY_AUTO_CLEAN_ENABLED, true))
    }

    // ------------------------------------------------------------------
    // Armar alarmes
    // ------------------------------------------------------------------

    private fun armReboot(context: Context, prefs: SharedPreferences, isEnabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (code in legacyRebootRequestCodes) {
            alarmManager.cancel(rebootPendingIntent(context, code))
        }

        if (!isEnabled) {
            Log.d(TAG, "Auto Reboot desativado. Alarmes cancelados.")
            return
        }

        val triggerAt = computeNextTrigger(
            prefs = prefs,
            typeKey = PrefsConstants.KEY_REBOOT_SCHEDULE_TYPE,
            defaultType = ScheduleType.WEEKLY,
            dayPrefix = PrefsConstants.KEY_REBOOT_DAY_PREFIX,
            dayOfMonthKey = PrefsConstants.KEY_REBOOT_DAY_OF_MONTH,
            hourKey = PrefsConstants.KEY_REBOOT_HOUR,
            minuteKey = PrefsConstants.KEY_REBOOT_MINUTE
        )
        if (triggerAt == null) {
            Log.d(TAG, "Auto Reboot sem dias selecionados. Nada agendado.")
            return
        }

        setAlarm(alarmManager, triggerAt, rebootPendingIntent(context, REBOOT_REQUEST_CODE))
        Log.d(TAG, "Próximo Auto Reboot: ${format(triggerAt)}")
    }

    private fun armClean(context: Context, prefs: SharedPreferences, isEnabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = cleanPendingIntent(context, CLEAN_REQUEST_CODE)
        alarmManager.cancel(pendingIntent)

        if (!isEnabled) {
            Log.d(TAG, "Auto Clean desativado. Alarmes cancelados.")
            return
        }

        val triggerAt = computeNextTrigger(
            prefs = prefs,
            typeKey = PrefsConstants.KEY_CLEAN_SCHEDULE_TYPE,
            defaultType = ScheduleType.DAILY,
            dayPrefix = PrefsConstants.KEY_CLEAN_DAY_PREFIX,
            dayOfMonthKey = PrefsConstants.KEY_CLEAN_DAY_OF_MONTH,
            hourKey = PrefsConstants.KEY_CLEAN_HOUR,
            minuteKey = PrefsConstants.KEY_CLEAN_MINUTE
        )
        if (triggerAt == null) {
            Log.d(TAG, "Auto Clean sem dias selecionados. Nada agendado.")
            return
        }

        setAlarm(alarmManager, triggerAt, pendingIntent)
        Log.d(TAG, "Próxima Auto Clean: ${format(triggerAt)}")
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    // ------------------------------------------------------------------
    // Cálculo do próximo disparo
    // ------------------------------------------------------------------

    /** Retorna o próximo horário de disparo, ou null se for semanal sem nenhum dia marcado. */
    private fun computeNextTrigger(
        prefs: SharedPreferences,
        typeKey: String,
        defaultType: ScheduleType,
        dayPrefix: String,
        dayOfMonthKey: String,
        hourKey: String,
        minuteKey: String,
        now: Long = System.currentTimeMillis()
    ): Long? {
        val type = ScheduleType.fromKey(prefs.getString(typeKey, null), defaultType)
        val hour = prefs.getInt(hourKey, 3)
        val minute = prefs.getInt(minuteKey, 0)

        return when (type) {
            ScheduleType.DAILY -> nextDaily(now, hour, minute)
            ScheduleType.WEEKLY -> {
                val enabledDays = dayKeys.indices
                    .filter { prefs.getBoolean("$dayPrefix${dayKeys[it]}", it == 1) }
                    .map { dayCalendarValues[it] }
                nextWeekly(now, hour, minute, enabledDays)
            }
            ScheduleType.MONTHLY -> {
                val dayOfMonth = prefs.getInt(dayOfMonthKey, 1).coerceIn(1, 31)
                nextMonthly(now, hour, minute, dayOfMonth)
            }
        }
    }

    private fun nextDaily(now: Long, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    private fun nextWeekly(now: Long, hour: Int, minute: Int, weekdays: List<Int>): Long? =
        weekdays.minOfOrNull { weekday ->
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_WEEK, weekday)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.WEEK_OF_YEAR, 1)
            }.timeInMillis
        }

    /** Em meses com menos dias que o escolhido (ex.: dia 31 em abril), dispara no último dia do mês. */
    private fun nextMonthly(now: Long, hour: Int, minute: Int, dayOfMonth: Int): Long {
        fun triggerInMonth(monthOffset: Int): Long =
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1) // evita estouro ao avançar o mês
                add(Calendar.MONTH, monthOffset)
                set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        val thisMonth = triggerInMonth(0)
        return if (thisMonth > now) thisMonth else triggerInMonth(1)
    }

    // ------------------------------------------------------------------
    // Utilitários
    // ------------------------------------------------------------------

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    private fun format(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    private fun rebootPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, AutoRebootService::class.java).apply { action = ACTION_EXECUTE_ALARM }
        return getForegroundPendingIntent(context, requestCode, intent)
    }

    private fun cleanPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, AutoCleanCacheService::class.java).apply { action = ACTION_EXECUTE_ALARM }
        return getForegroundPendingIntent(context, requestCode, intent)
    }

    private fun getForegroundPendingIntent(context: Context, id: Int, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, id, intent, flags)
        } else {
            PendingIntent.getService(context, id, intent, flags)
        }
    }
}
