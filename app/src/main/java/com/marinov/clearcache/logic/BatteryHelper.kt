package com.marinov.clearcache.logic

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryHelper {
    fun getBatteryLevel(context: Context): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Registrar null retorna o sticky broadcast atual sem precisar de um receiver ativo
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }
}