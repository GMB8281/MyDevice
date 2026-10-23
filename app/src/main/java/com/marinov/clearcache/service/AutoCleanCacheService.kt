package com.marinov.clearcache.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.CacheCleaner
import com.marinov.clearcache.logic.DeviceActivityChecker
import com.marinov.clearcache.logic.PrefsConstants

class AutoCleanCacheService : Service() {
    override fun onCreate() {
        super.onCreate()
        startPersistentNotification()
    }

    private fun startPersistentNotification() {
        val channelId = "service_channel_clearcache_clean"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_clean_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_clean_title))
            .setContentText(getString(R.string.notification_clean_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1002, notification)
            }
        } catch (e: Exception) {
            Log.e("AutoCleanService", "Erro ao iniciar notificação persistente.", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ClearCache", "Serviço AutoClean ativo e monitorando...")

        if (intent?.action == AlarmScheduler.ACTION_EXECUTE_ALARM) {
            // Alarmes exatos são de disparo único: arma o próximo ANTES de qualquer verificação,
            // para que um adiamento (ligação, mídia, tela em uso) não perca o agendamento.
            AlarmScheduler.scheduleNextClean(this)
            runScheduledClean()
        }
        return START_STICKY
    }

    private fun runScheduledClean() {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val isConfigured = prefs.getBoolean(PrefsConstants.KEY_EXCLUDE_CONFIGURED, false)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isScreenOff = !powerManager.isInteractive
        val isLocked = keyguardManager.isDeviceLocked

        if (!isConfigured) {
            Log.d("AutoClean", "Limpeza ignorada: exceções não configuradas.")
            return
        }

        if (!(isScreenOff || isLocked)) {
            Log.d("AutoClean", "Limpeza ignorada: tela ligada e desbloqueada.")
            return
        }

        if (DeviceActivityChecker.isInCall(this)) {
            Log.d("AutoClean", "Limpeza ignorada: usuário em ligação.")
            return
        }

        if (DeviceActivityChecker.isMediaPlaying(this)) {
            Log.d("AutoClean", "Limpeza ignorada: mídia sendo reproduzida.")
            return
        }

        Log.d("AutoClean", "Iniciando limpeza silenciosa: todas as condições atendidas.")
        CacheCleaner.executeCleanCacheSilent(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
