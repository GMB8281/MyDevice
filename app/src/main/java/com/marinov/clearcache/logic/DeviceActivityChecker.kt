package com.marinov.clearcache.logic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build

/**
 * Verifica se o usuário está ocupado com ligação ou mídia.
 * Usa apenas o AudioManager, então não exige nenhuma permissão extra e
 * cobre tanto o discador do sistema quanto apps de chamada (WhatsApp, Telegram, Meet...).
 */
object DeviceActivityChecker {

    /** Ligação em andamento, chamada tocando ou chamada por app (VoIP). */
    @JvmStatic
    fun isInCall(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (audioManager.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_RINGTONE -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    audioManager.mode == AudioManager.MODE_CALL_SCREENING
        }
    }

    /** Música, vídeo, podcast ou jogo emitindo áudio neste momento. */
    @JvmStatic
    fun isMediaPlaying(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isMusicActive) return true

        return try {
            audioManager.activePlaybackConfigurations.any { config ->
                val usage = config.audioAttributes.usage
                usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME
            }
        } catch (e: Exception) {
            false
        }
    }
}
