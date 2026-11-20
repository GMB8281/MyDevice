package com.marinov.clearcache.logic

import android.content.Context
import android.util.Log
import java.io.DataOutputStream

/**
 * "Always-on apps" = apps isentos da otimização de bateria (whitelist do Doze).
 *
 * NÃO há registro local: o estado é sempre lido do sistema via
 * BatteryOptimizationHelper (a mesma lógica usada na limpeza de cache) e cada
 * alteração é aplicada na hora via root:
 *   dumpsys deviceidle whitelist +pacote  -> "Não otimizado"
 *   dumpsys deviceidle whitelist -pacote  -> volta a ser otimizado
 */
object AlwaysOnHelper {

    private const val TAG = "AlwaysOnHelper"

    // Chave usada por versões antigas do app (registro local abolido neste contexto).
    private const val LEGACY_KEY_ALWAYS_ON_PACKAGES = "always_on_packages"

    enum class ApplyResult { SUCCESS, ROOT_ERROR, SYSTEM_PROTECTED }

    /** Remove o registro salvo por versões antigas do app (limpeza única, idempotente). */
    @JvmStatic
    fun clearLegacyRecord(context: Context) {
        val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(LEGACY_KEY_ALWAYS_ON_PACKAGES)) {
            prefs.edit().remove(LEGACY_KEY_ALWAYS_ON_PACKAGES).apply()
        }
    }

    /**
     * Aplica o estado desejado para um pacote e VERIFICA o resultado no sistema.
     * Deve ser chamado fora da main thread.
     *
     * SUCCESS: estado final == desejado (inclui no-op quando já estava assim, sem root).
     * ROOT_ERROR: su falhou ou foi negado.
     * SYSTEM_PROTECTED: comando executado, mas o sistema manteve o estado (app na
     * whitelist de sistema do OEM, que o comando -pkg não remove).
     */
    @JvmStatic
    fun setNotOptimized(context: Context, packageName: String, notOptimized: Boolean): ApplyResult {
        if (!packageName.matches(Regex("^[a-zA-Z0-9._-]+$"))) return ApplyResult.ROOT_ERROR

        // Já está no estado desejado: nada a fazer (não pede root).
        if (BatteryOptimizationHelper.isNotOptimized(context, packageName) == notOptimized) {
            return ApplyResult.SUCCESS
        }

        return try {
            val su = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(su.outputStream)
            val sign = if (notOptimized) "+" else "-"
            os.writeBytes("dumpsys deviceidle whitelist $sign$packageName\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = su.waitFor()
            when {
                exitCode != 0 -> {
                    Log.e(TAG, "dumpsys deviceidle whitelist retornou $exitCode para $packageName")
                    ApplyResult.ROOT_ERROR
                }
                BatteryOptimizationHelper.isNotOptimized(context, packageName) == notOptimized -> {
                    ApplyResult.SUCCESS
                }
                else -> ApplyResult.SYSTEM_PROTECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar whitelist de bateria de $packageName", e)
            ApplyResult.ROOT_ERROR
        }
    }
}