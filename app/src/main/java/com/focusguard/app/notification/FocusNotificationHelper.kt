package com.focusguard.app.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.MainActivity
import com.focusguard.app.R
import com.focusguard.app.domain.notification.NotificationType
import kotlin.math.absoluteValue

class FocusNotificationHelper(
    private val context: Context
) {

    fun showSmartNotification(
        type: NotificationType,
        message: String
    ): Boolean {
        if (!canPostNotifications()) return false

        val notification = NotificationCompat.Builder(context, FocusGuardApp.CHANNEL_SMART)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleFor(type))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(createPyqPendingIntent(type))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()


        if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(notificationId(type), notification)
            return true
        }
        return false
    }

    private fun createPyqPendingIntent(type: NotificationType): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("focusguard://pyq")
            putExtra(EXTRA_FROM_SMART_NOTIFICATION, true)
            putExtra(EXTRA_NOTIFICATION_TYPE, type.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            REQUEST_PYQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun titleFor(type: NotificationType): String {
        return when (type) {
            NotificationType.PRAISE -> "Focus streak spotted"
            NotificationType.STRUGGLE -> "Weak spot detected"
            NotificationType.COMEBACK -> "Comeback time"
            NotificationType.BLOCK_TRIGGER -> "One PYQ first"
            NotificationType.IMPROVEMENT -> "Progress detected"
        }
    }

    private fun notificationId(type: NotificationType): Int {
        return "${context.packageName}:${type.name}".hashCode().absoluteValue
            .coerceAtLeast(1)
    }

    companion object {
        const val EXTRA_FROM_SMART_NOTIFICATION = "from_smart_notification"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        private const val REQUEST_PYQ = 7001
    }
}
