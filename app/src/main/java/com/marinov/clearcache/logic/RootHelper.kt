package com.marinov.clearcache.logic

import android.util.Log

object RootHelper {
    private const val TAG = "RootHelper"

    @JvmStatic
    fun rebootDevice() {
        Thread {
            try {
                val su = Runtime.getRuntime().exec("su")
                val os = su.outputStream
                os.write("reboot\n".toByteArray())
                os.write("exit\n".toByteArray())
                os.flush()
                su.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reiniciar dispositivo", e)
            }
        }.start()
    }

    fun requestRootPermission() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su -c exit")
                process.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Sem acesso Root", e)
            }
        }.start()
    }
}