package com.petcompanion.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.petcompanion.R
import java.util.concurrent.TimeUnit

object NotificationHelper {

    const val CHANNEL_ID   = "qgrowth_reminders"
    const val CHANNEL_NAME = "Q-Growth Reminders"

    // ── สร้าง Notification Channel (เรียกใน Application.onCreate หรือ MainActivity) ──
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notify_channel_desc)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ── แสดง Notification ทันที (เรียกจาก Worker) ────────────────────────
    fun showNow(context: Context, notifId: Int, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notif)
    }

    // ── ตั้งเวลาแจ้งเตือนล่วงหน้า ─────────────────────────────────────────
    /**
     * @param eventEpochMillis  เวลาของกิจกรรม (milliseconds)
     * @param beforeMinutes     แจ้งเตือนก่อนกี่นาที
     * @param title             หัวข้อ notification
     * @param body              เนื้อหา notification
     * @param tagId             unique tag (ใช้ยกเลิกถ้าต้องการ)
     */
    fun scheduleReminder(
        context: Context,
        eventEpochMillis: Long,
        beforeMinutes: Int,
        title: String,
        body: String,
        tagId: String
    ) {
        val fireAt = eventEpochMillis - (beforeMinutes * 60_000L)
        val delayMs = fireAt - System.currentTimeMillis()
        if (delayMs <= 0) return // เลยเวลาแล้ว ไม่ตั้ง

        val data = workDataOf(
            NotificationWorker.KEY_TITLE  to title,
            NotificationWorker.KEY_BODY   to body,
            NotificationWorker.KEY_NOTIF_ID to tagId.hashCode()
        )
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(tagId)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(tagId, ExistingWorkPolicy.REPLACE, request)
    }

    // ── ยกเลิกการแจ้งเตือน ────────────────────────────────────────────────
    fun cancelReminder(context: Context, tagId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tagId)
    }
}
