package com.example.recipe_pocket.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.recipe_pocket.ui.recipe.read.RecipeDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException

/**
 * Runs heavy save jobs on a background scope and posts a notification when finished.
 */
object BgSaves : CoroutineScope {
    private const val CHANNEL_ID = "recipe_background_saves"
    private const val CHANNEL_NAME = "레시피 저장 상태"
    private const val TAG = "BgSaves"

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    private val notificationIdSeed = AtomicInteger()

    fun enqueue(
        context: Context,
        successMessage: String,
        failureMessage: String,
        work: suspend () -> String
    ) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        launch {
            runCatching { work() }
                .onSuccess { recipeId ->
                    val contentIntent = recipeDetailPendingIntent(appContext, recipeId)
                    notify(appContext, "레시피 업로드 완료", successMessage, contentIntent)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Background save failed", throwable)
                    notify(appContext, "레시피 업로드 실패", failureMessage, null)
                }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun notify(
        context: Context,
        title: String,
        message: String,
        contentIntent: PendingIntent?
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .apply {
                if (contentIntent != null) {
                    setContentIntent(contentIntent)
                }
            }
            .build()
        val notificationId = nextNotificationId()
        manager.notify(notificationId, notification)
    }

    private fun nextNotificationId(): Int {
        val id = notificationIdSeed.incrementAndGet()
        return if (id == Int.MAX_VALUE) {
            notificationIdSeed.set((SystemClock.uptimeMillis() % Int.MAX_VALUE).toInt())
            notificationIdSeed.get()
        } else {
            id
        }
    }

    private fun recipeDetailPendingIntent(context: Context, recipeId: String): PendingIntent {
        val intent = Intent(context, RecipeDetailActivity::class.java).apply {
            putExtra("RECIPE_ID", recipeId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, recipeId.hashCode(), intent, flags)
    }
}
