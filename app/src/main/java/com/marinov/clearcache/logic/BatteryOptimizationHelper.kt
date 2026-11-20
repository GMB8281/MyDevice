package com.marinov.clearcache.logic

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log

/**
 * Consulta o status de otimização de bateria (Doze) dos apps instalados.
 * Não requer permissões extras.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"

    /** (a) True se o pacote está como "Não otimizado" perante as otimizações de bateria. */
    @JvmStatic
    fun isNotOptimized(context: Context, packageName: String): Boolean = try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) {
        Log.e(TAG, "Erro ao verificar otimização de $packageName", e)
        false
    }

    /**
     * (b) Percorre TODOS os apps instalados (usuário + sistema) e retorna
     * os pacotes configurados como "Não otimizado".
     * Deve ser chamado fora da main thread.
     */
    @JvmStatic
    fun getNotOptimizedPackages(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packages = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in packages) {
                try {
                    if (powerManager.isIgnoringBatteryOptimizations(app.packageName)) {
                        result.add(app.packageName)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao consultar ${app.packageName}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar apps não otimizados", e)
        }
        return result
    }
}