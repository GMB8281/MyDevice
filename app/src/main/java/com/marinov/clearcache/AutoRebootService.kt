package com.marinov.clearcache

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

class AutoRebootService : Service() {

    override fun onCreate() {
        super.onCreate()
        startPersistentNotification()
    }

    private fun startPersistentNotification() {
        val channelId = "service_channel_clearcache_reboot"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_reboot_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_reboot_title))
            .setContentText(getString(R.string.notification_reboot_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true) // Impede que o usuário limpe a notificação
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

        // Só executa a ação de fato se vier com o gatilho do AlarmManager
        if (intent?.action == "ACTION_EXECUTE_ALARM") {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            val isScreenOff = !powerManager.isInteractive
            val isLocked = keyguardManager.isDeviceLocked

            // Restrição rígida de tela: só acontece se estiver apagada OU bloqueada.
            if (isScreenOff || isLocked) {
                Log.d("ClearCache", "Executando AutoReboot: A tela está desligada ou bloqueada.")
                Thread {
                    try {
                        val su = Runtime.getRuntime().exec("su")
                        val os = su.outputStream
                        os.write("reboot\n".toByteArray())
                        os.write("exit\n".toByteArray())
                        os.flush()
                        su.waitFor()
                    } catch (e: Exception) {
                        Log.e("AutoRebootService", "Erro ao reiniciar", e)
                    }
                }.start()
            } else {
                Log.d("ClearCache", "AutoReboot Ignorado: O dispositivo está em uso (Tela ligada e desbloqueada).")
            }
        }

        // Mantém o serviço VIVO eternamente (persistente)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}