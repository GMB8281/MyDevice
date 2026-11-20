package com.marinov.clearcache.logic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log

/**
 * Detecção dos apps padrão do sistema.
 * Usa apenas APIs públicas — nenhuma permissão extra é necessária.
 * (RoleManager.getRoleHolders() é @SystemApi e não pode ser usado por apps de terceiros.)
 */
object DefaultAppsHelper {

    private const val TAG = "DefaultAppsHelper"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    /** (a) Discador padrão. */
    @JvmStatic
    fun getDefaultDialerPackage(context: Context): String? = try {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecomManager?.defaultDialerPackage
    } catch (e: Exception) {
        Log.w(TAG, "Sem discador padrão detectável", e)
        null
    }

    /** (b) App de mensagens (SMS) padrão. */
    @JvmStatic
    fun getDefaultSmsPackage(context: Context): String? = try {
        Telephony.Sms.getDefaultSmsPackage(context)
    } catch (e: Exception) {
        Log.w(TAG, "Sem app de SMS padrão detectável", e)
        null
    }

    /** (c) Assistente de voz padrão. */
    @JvmStatic
    fun getDefaultAssistantPackage(context: Context): String? {
        // 1) Serviço de interação por voz ativo: o sistema atualiza este setting
        //    sempre que o assistente padrão muda (Google Assistente, Gemini, Alexa...).
        try {
            val flat = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            if (!flat.isNullOrEmpty()) {
                ComponentName.unflattenFromString(flat)?.packageName?.let { return it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "voice_interaction_service indisponível", e)
        }
        // 2) Setting legado do app de "assist" (Androids mais antigos)
        try {
            val flat = Settings.Secure.getString(context.contentResolver, "assistant")
            if (!flat.isNullOrEmpty()) {
                ComponentName.unflattenFromString(flat)?.packageName?.let { return it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "setting assistant indisponível", e)
        }
        // 3) Resolvedor do ACTION_VOICE_COMMAND
        return try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).addCategory(Intent.CATEGORY_DEFAULT)
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /** (d) Launcher padrão. */
    @JvmStatic
    fun getDefaultLauncherPackage(context: Context): String? = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        // "android" = resolver do sistema (nenhum launcher padrão definido)
        resolveInfo?.activityInfo?.packageName?.takeIf { it != "android" }
    } catch (e: Exception) {
        Log.w(TAG, "Sem launcher padrão detectável", e)
        null
    }

    /** (e) Browser de internet padrão. */
    @JvmStatic
    fun getDefaultBrowserPackage(context: Context): String? = try {
        // URL neutra (domínio reservado da IANA) para evitar que app links verificados
        // de algum app interfiram na resolução; MATCH_DEFAULT_ONLY retorna o handler padrão.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
        val resolveInfo = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        // "android" = resolver do sistema (nenhum browser padrão definido)
        resolveInfo?.activityInfo?.packageName?.takeIf { it != "android" }
    } catch (e: Exception) {
        Log.w(TAG, "Sem browser padrão detectável", e)
        null
    }

    /**
     * (f) Chama todas as detecções e retorna o conjunto de pacotes que a limpeza
     * deve SEMPRE excluir: este app, a interface do sistema e os apps padrão
     * (discador, SMS, assistente, launcher e browser).
     * Usado tanto pelo CacheCleaner quanto pela UI (seleção bloqueada).
     */
    @JvmStatic
    fun getMandatoryExcludedPackages(context: Context): Set<String> {
        val result = mutableSetOf(context.packageName, SYSTEM_UI_PACKAGE)
        getDefaultDialerPackage(context)?.let { result.add(it) }
        getDefaultSmsPackage(context)?.let { result.add(it) }
        getDefaultAssistantPackage(context)?.let { result.add(it) }
        getDefaultLauncherPackage(context)?.let { result.add(it) }
        getDefaultBrowserPackage(context)?.let { result.add(it) }
        return result
    }
}