package com.marinov.clearcache.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.PrefsConstants
import com.marinov.clearcache.logic.RootHelper

class BatteryProtectionService : Service() {

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                val batteryPct = (level * 100) / scale
                checkBatteryLevel(batteryPct)
            }
        }
    }

    private var hasNotifiedWeak = false
    private var hasNotifiedCritical = false

    override fun onCreate() {
        super.onCreate()
        startPersistentNotification()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun startPersistentNotification() {
        val channelId = "service_channel_clearcache_battery"
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_battery_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_battery_service_title))
            .setContentText(getString(R.string.notification_battery_service_text))
            .setSmallIcon(R.drawable.ic_battery_protection)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1003, notification)
            }
        } catch (e: Exception) {
            Log.e("BatteryService", "Erro ao iniciar notificação persistente.", e)
        }
    }

    private fun checkBatteryLevel(level: Int) {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, MODE_PRIVATE)
        val weak = prefs.getInt(PrefsConstants.KEY_WEAK_BATTERY, 20)
        val critical = prefs.getInt(PrefsConstants.KEY_CRITICAL_BATTERY, 10)
        val reserve = prefs.getInt(PrefsConstants.KEY_ENERGY_RESERVE, 5)

        if (level <= reserve) {
            Log.d("BatteryProtection", "Nível de reserva atingido. Desligando...")
            RootHelper.shutdownDevice()
            return
        }

        if (level <= critical) {
            if (!hasNotifiedCritical) {
                showWarningNotification()
                hasNotifiedCritical = true
                hasNotifiedWeak = true
            }
        } else if (level <= weak) {
            if (!hasNotifiedWeak) {
                showWarningNotification()
                hasNotifiedWeak = true
            }
        } else {
            hasNotifiedWeak = false
            hasNotifiedCritical = false
        }
    }

    private fun showWarningNotification() {
        val channelId = "alert_channel_clearcache_battery"
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_battery_alert_title),
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_battery_alert_title))
            .setContentText(getString(R.string.notification_battery_alert_text))
            .setSmallIcon(R.drawable.ic_low_battery)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager2 = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager2.notify(1004, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
            // Ignora erro se já foi desregistrado
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}