package com.marinov.clearcache

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

object AlarmScheduler {

    fun scheduleAllAggressively(context: Context) {
        Log.d("ClearCache", "Iniciando agendamento agressivo de serviços...")
        scheduleReboot(context)
        scheduleClean(context)
    }

    fun scheduleReboot(context: Context) {
        val prefs = context.getSharedPreferences(CleanCacheDialogActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val isEnabled = prefs.getBoolean("auto_reboot_enabled", true)

        // Liga ou Desliga o serviço persistente imediatamente ao tocar na chave
        if (isEnabled) {
            ContextCompat.startForegroundService(context, Intent(context, AutoRebootService::class.java))
        } else {
            context.stopService(Intent(context, AutoRebootService::class.java))
        }

        val dayKeys = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        val dayCalendarValues = listOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )

        // Limpa alarmes pendentes
        for (i in dayKeys.indices) {
            val intent = Intent(context, AutoRebootService::class.java).apply {
                action = "ACTION_EXECUTE_ALARM"
            }
            val pendingIntent = getForegroundPendingIntent(context, i, intent)
            alarmManager.cancel(pendingIntent)
        }

        if (!isEnabled) {
            Log.d("ClearCache", "Auto Reboot desativado. Alarmes cancelados.")
            return
        }

        val hour = prefs.getInt("reboot_hour", 3)
        val minute = prefs.getInt("reboot_minute", 0)

        for (i in dayKeys.indices) {
            val isDayEnabled = prefs.getBoolean("reboot_day_${dayKeys[i]}", i == 1)

            if (isDayEnabled) {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_WEEK, dayCalendarValues[i])
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)

                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }

                val intent = Intent(context, AutoRebootService::class.java).apply {
                    action = "ACTION_EXECUTE_ALARM"
                }
                val pendingIntent = getForegroundPendingIntent(context, i, intent)

                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } catch (e: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            }
        }
    }

    fun scheduleClean(context: Context) {
        val prefs = context.getSharedPreferences(CleanCacheDialogActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val isEnabled = prefs.getBoolean("auto_clean_enabled", true)

        // Liga ou Desliga o serviço persistente imediatamente
        if (isEnabled) {
            ContextCompat.startForegroundService(context, Intent(context, AutoCleanCacheService::class.java))
        } else {
            context.stopService(Intent(context, AutoCleanCacheService::class.java))
        }

        val intent = Intent(context, AutoCleanCacheService::class.java).apply {
            action = "ACTION_EXECUTE_ALARM"
        }
        val pendingIntent = getForegroundPendingIntent(context, 100, intent)

        alarmManager.cancel(pendingIntent)

        if (!isEnabled) {
            Log.d("ClearCache", "Auto Clean desativado. Alarmes cancelados.")
            return
        }

        val hour = prefs.getInt("clean_hour", 3)
        val minute = prefs.getInt("clean_minute", 0)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
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