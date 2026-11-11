package com.zmove.jarvis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Agenda timers com [AlarmManager], sem exigir permissão de alarme exato. */
object TimerScheduler {

    private const val REQUEST_CODE = 4201
    const val EXTRA_SECONDS = "extra_seconds"

    fun schedule(context: Context, seconds: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + seconds * 1000L
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent(context, seconds)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, 0))
    }

    private fun pendingIntent(context: Context, seconds: Int): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
            .putExtra(EXTRA_SECONDS, seconds)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
