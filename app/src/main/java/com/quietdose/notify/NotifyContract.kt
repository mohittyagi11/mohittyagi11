package com.quietdose.notify

/** Intent actions and extras shared between the notifier and its receivers. */
object NotifyContract {
    const val ACTION_TAKEN = "com.quietdose.action.TAKEN"
    const val ACTION_SNOOZE = "com.quietdose.action.SNOOZE"

    const val EXTRA_GROUP_ID = "groupId"
    const val EXTRA_EPOCH_DAY = "epochDay"

    /** Stable notification id per group so re-firing updates rather than stacks. */
    fun notificationId(groupId: Long): Int = (10_000 + groupId).toInt()

    fun channelId(groupId: Long): String = "group_$groupId"
}
