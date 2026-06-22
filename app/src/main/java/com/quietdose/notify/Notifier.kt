package com.quietdose.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quietdose.R
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.di.ServiceLocator
import com.quietdose.receiver.TakenActionReceiver
import com.quietdose.ui.MainActivity
import com.quietdose.util.DateUtils
import com.quietdose.util.Format
import kotlinx.coroutines.flow.first

/**
 * Turns a group into a calm, actionable reminder. The whole point is the single
 * tap: "Taken" confirms the dose straight from the shade — the app never has to
 * open. Quiet groups whisper (low importance); the rest speak at default.
 *
 * This is the payload every trigger (wake, geofence, cadence…) delivers, so the
 * trigger code stays a one-liner: [fireGroup].
 */
object Notifier {

    /** Build + show the reminder for [groupId], listing only the items due & not yet taken. */
    suspend fun fireGroup(context: Context, groupId: Long) {
        val repo = ServiceLocator.repository(context)
        val settings = ServiceLocator.settings(context).settings.first()
        val group = repo.group(groupId) ?: return
        if (!group.enabled) return

        val epochDay = DateUtils.today(settings.dayRolloverHour)
        val due = repo.itemsFor(groupId).filter { DateUtils.isDueOn(it, epochDay) }
        if (due.isEmpty()) return

        val taken = repo.observeTakenToday(epochDay).first().toSet()
        val remaining = due.filterNot { it.id in taken }
        if (remaining.isEmpty()) return // already done for today — stay quiet

        show(context, group, remaining, epochDay)
    }

    private fun show(
        context: Context,
        group: GroupEntity,
        items: List<ItemEntity>,
        epochDay: Long,
    ) {
        if (!hasPermission(context)) return
        ensureChannel(context, group)

        val takenIntent = PendingIntent.getBroadcast(
            context,
            NotifyContract.notificationId(group.id),
            Intent(context, TakenActionReceiver::class.java).apply {
                action = NotifyContract.ACTION_TAKEN
                putExtra(NotifyContract.EXTRA_GROUP_ID, group.id)
                putExtra(NotifyContract.EXTRA_EPOCH_DAY, epochDay)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openIntent = PendingIntent.getActivity(
            context,
            NotifyContract.notificationId(group.id),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val style = NotificationCompat.InboxStyle()
        items.forEach { style.addLine(Format.line(it)) }

        val builder = NotificationCompat.Builder(context, NotifyContract.channelId(group.id))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(group.name)
            .setContentText(items.joinToString(" · ") { it.name })
            .setStyle(style)
            .setNumber(items.size)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (group.quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Taken", takenIntent)

        NotificationManagerCompat.from(context)
            .notify(NotifyContract.notificationId(group.id), builder.build())
    }

    fun cancel(context: Context, groupId: Long) {
        NotificationManagerCompat.from(context).cancel(NotifyContract.notificationId(groupId))
    }

    private fun ensureChannel(context: Context, group: GroupEntity) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val importance = if (group.quiet) {
            NotificationManager.IMPORTANCE_LOW
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
        val channel = NotificationChannel(
            NotifyContract.channelId(group.id),
            group.name,
            importance,
        ).apply { description = "Reminders for ${group.name}" }
        mgr.createNotificationChannel(channel)
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}
