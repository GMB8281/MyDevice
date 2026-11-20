package com.marinov.clearcache.logic

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.io.DataOutputStream

/**
 * Detecta apps configurados como ADMINISTRADORES DO DISPOSITIVO ativos.
 * O Android os mantém automaticamente como "não otimizado" e não permite alterar,
 * por isso aparecem bloqueados (cinza) na tela Always-on.
 *
 * Três camadas, da mais barata à mais cara:
 *  1) DevicePolicyManager.getActiveAdmins();
 *  2) varredura dos receivers que declaram a action de device admin no manifesto
 *     + isAdminActive(component), que cobre versões/ROMs em que a lista global
 *     é filtrada para apps comuns;
 *  3) fallback root (dumpsys device_policy), apenas se 1 e 2 não encontraram nada
 *     e existem apps declarando receiver de admin.
 *
 * Deve ser chamado fora da main thread.
 */
object DeviceAdminHelper {

    private const val TAG = "DeviceAdminHelper"

    // DevicePolicyManager.ACTION_DEVICE_ADMIN_ENABLED não é exposto no SDK público
    // (constante oculta/não suportada no android.jar), por isso usamos a string
    // literal da action — valor estável desde o API 8.
    private const val ACTION_DEVICE_ADMIN_ENABLED = "android.app.action.DEVICE_ADMIN_ENABLED"

    @JvmStatic
    fun getActiveAdminPackages(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

        // (1) Lista global de admins ativos
        try {
            dpm?.activeAdmins?.forEach { cn -> result.add(cn.packageName) }
        } catch (e: Exception) {
            Log.w(TAG, "getActiveAdmins indisponível", e)
        }

        // (2) Candidatos declarados no manifesto + checagem individual
        val candidates = findAdminCandidates(context)
        if (dpm != null) {
            for (cn in candidates) {
                try {
                    if (dpm.isAdminActive(cn)) result.add(cn.packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "isAdminActive falhou para $cn", e)
                }
            }
        }

        // (3) Fallback root, somente se necessário
        if (result.isEmpty() && candidates.isNotEmpty()) {
            result.addAll(activeAdminsViaRoot(candidates))
        }

        return result
    }

    /** Receivers que declaram a action de device admin no intent-filter (possíveis admins). */
    private fun findAdminCandidates(context: Context): List<ComponentName> = try {
        val intent = Intent(ACTION_DEVICE_ADMIN_ENABLED)
        val receivers = context.packageManager
            .queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        receivers.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            ComponentName(ai.packageName, ai.name)
        }.distinct()
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao listar receivers de device admin", e)
        emptyList()
    }

    private fun activeAdminsViaRoot(candidates: List<ComponentName>): Set<String> = try {
        val su = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(su.outputStream)
        os.writeBytes("dumpsys device_policy\n")
        os.writeBytes("exit\n")
        os.flush()
        // Lê antes do waitFor para não encher o pipe e travar o processo.
        val dump = su.inputStream.bufferedReader().use { it.readText() }
        su.waitFor()
        val result = mutableSetOf<String>()
        for (cn in candidates) {
            if (dump.contains(cn.flattenToString()) || dump.contains(cn.flattenToShortString())) {
                result.add(cn.packageName)
            }
        }
        result
    } catch (e: Exception) {
        Log.w(TAG, "Fallback root de device admin indisponível", e)
        emptySet()
    }
}