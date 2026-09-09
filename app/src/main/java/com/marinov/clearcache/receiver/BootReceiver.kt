package com.marinov.clearcache.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.BatteryHelper
import com.marinov.clearcache.logic.PrefsConstants
import com.marinov.clearcache.logic.RootHelper
import com.marinov.clearcache.service.BatteryProtectionService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d("ClearCache", "Dispositivo inicializado. Reagendando serviços...")
            AlarmScheduler.scheduleAllAggressively(context)

            // Verificação de Proteção de Bateria
            val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(PrefsConstants.KEY_BATTERY_PROTECTION_ENABLED, false)

            if (isEnabled) {
                val reserve = prefs.getInt(PrefsConstants.KEY_ENERGY_RESERVE, 5)
                val currentBattery = BatteryHelper.getBatteryLevel(context)

                if (currentBattery != -1 && currentBattery <= reserve) {
                    Log.d("ClearCache", "Bateria abaixo da reserva no boot. Desligando...")
                    RootHelper.shutdownDevice()
                } else {
                    // Inicia o serviço para continuar monitorando
                    val serviceIntent = Intent(context, BatteryProtectionService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}