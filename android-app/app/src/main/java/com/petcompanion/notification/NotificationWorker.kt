package com.petcompanion.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TITLE    = "title"
        const val KEY_BODY     = "body"
        const val KEY_NOTIF_ID = "notif_id"
    }

    override suspend fun doWork(): Result {
        val title   = inputData.getString(KEY_TITLE)   ?: return Result.failure()
        val body    = inputData.getString(KEY_BODY)    ?: return Result.failure()
        val notifId = inputData.getInt(KEY_NOTIF_ID, 0)
        NotificationHelper.showNow(context, notifId, title, body)
        return Result.success()
    }
}
