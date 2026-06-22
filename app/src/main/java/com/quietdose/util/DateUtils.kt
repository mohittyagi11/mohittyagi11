package com.quietdose.util

import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.FrequencyType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Day math. The "day" an intake belongs to rolls over at [rolloverHour] rather
 * than midnight, so a 1 AM night dose still counts for the night you took it.
 */
object DateUtils {

    /** Local epoch day for [now], honouring the rollover hour. */
    fun epochDayFor(now: LocalDateTime, rolloverHour: Int): Long {
        val adjusted = now.minusHours(rolloverHour.toLong())
        return adjusted.toLocalDate().toEpochDay()
    }

    fun today(rolloverHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        epochDayFor(LocalDateTime.now(zone), rolloverHour)

    /**
     * Is [item] due on [epochDay]? Pure date math from the item's anchor/interval,
     * so a missed dose never silently shifts the cadence — the rhythm self-heals.
     */
    fun isDueOn(item: ItemEntity, epochDay: Long): Boolean = when (item.frequency) {
        FrequencyType.DAILY -> true
        FrequencyType.AS_NEEDED -> false
        FrequencyType.EVERY_N_DAYS -> {
            val n = item.frequencyInterval.coerceAtLeast(1)
            // Kotlin's Long.mod is floored (sign of divisor), so this is correct
            // for days before the anchor too — the cadence self-heals.
            (epochDay - item.frequencyAnchorEpochDay).mod(n.toLong()) == 0L
        }
        FrequencyType.WEEKLY -> {
            // bit 0 = Monday … bit 6 = Sunday
            val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1
            (item.frequencyDaysMask shr dow) and 1 == 1
        }
        FrequencyType.MONTHLY_DAYS -> {
            val dom = LocalDate.ofEpochDay(epochDay).dayOfMonth
            item.frequencyDaysOfMonth.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .contains(dom)
        }
    }
}
