package com.marinov.clearcache.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.DeviceActivityChecker
import com.marinov.clearcache.logic.RootHelper

class AutoRebootService : Service() {
    override fun onCreate() {
        super.onCreate()
        startPersistentNotification()
    }

    private fun startPersistentNotification() {
        val channelId = "service_channel_clearcache_reboot"
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_reboot_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_reboot_title))
            .setContentText(getString(R.string.notification_reboot_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("AutoRebootService", "Erro ao iniciar notificação persistente.", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ClearCache", "Serviço AutoReboot ativo e monitorando...")

        if (intent?.action == AlarmScheduler.ACTION_EXECUTE_ALARM) {
            // Alarmes exatos são de disparo único: arma o próximo ANTES de qualquer verificação.
            AlarmScheduler.scheduleNextReboot(this)

            runScheduledReboot()
        }
        return START_STICKY
    }

    private fun runScheduledReboot() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val isScreenOff = !powerManager.isInteractive
        val isLocked = keyguardManager.isDeviceLocked

        if (!(isScreenOff || isLocked)) {
            Log.d("ClearCache", "AutoReboot Ignorado: O dispositivo está em uso (Tela ligada e desbloqueada).")
            return
        }

        if (DeviceActivityChecker.isInCall(this)) {
            Log.d("ClearCache", "AutoReboot Ignorado: usuário em ligação.")
            return
        }

        if (DeviceActivityChecker.isMediaPlaying(this)) {
            Log.d("ClearCache", "AutoReboot Ignorado: mídia sendo reproduzida.")
            return
        }

        Log.d("ClearCache", "Executando AutoReboot: todas as condições atendidas.")
        RootHelper.rebootDevice()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}