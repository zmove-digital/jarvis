package com.zmove.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Reinicia a escuta em tempo integral após o aparelho ligar, caso o usuário
 * tenha deixado a opção ativada.
 *
 * No Android 14+ o sistema bloqueia iniciar um serviço de microfone a partir
 * do boot; nesse caso a falha é registrada e o usuário precisa abrir o app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = WakeWordPreferences(context)
        if (!preferences.alwaysListening) return

        val serviceIntent = Intent(context, JarvisService::class.java)
            .setAction(JarvisService.ACTION_START)

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (exception: Exception) {
            Log.w(TAG, "Não foi possível retomar a escuta após o boot", exception)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
