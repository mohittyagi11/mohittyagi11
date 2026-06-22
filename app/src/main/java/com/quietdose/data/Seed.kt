package com.quietdose.data

import com.quietdose.data.dao.GroupDao
import com.quietdose.data.dao.ItemDao
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.FrequencyType
import com.quietdose.data.model.ItemFlags
import com.quietdose.data.model.ItemType
import com.quietdose.data.model.TriggerType
import java.time.LocalDate

/**
 * First-run seed of the owner's actual stack. Everything here is ordinary data
 * the user can edit or delete — the seed just means the app is useful on launch
 * instead of empty. The behavioural rules (fasted mornings, fat-soluble with
 * food, iron away from tea/calcium, folate+B12 paired) are encoded as flags,
 * group membership and notes rather than special-cased logic.
 */
object Seed {

    suspend fun run(groupDao: GroupDao, itemDao: ItemDao) {
        if (groupDao.count() > 0) return // already seeded
        val anchor = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()

        // ---- Morning: fasted, with tea (on waking) ----
        val morning = groupDao.upsert(
            GroupEntity(
                name = "Morning",
                trigger = TriggerType.WAKE,
                iconKey = "sun",
                sortOrder = 0,
            ),
        )
        val fasted = ItemFlags.FASTED
        listOf(
            "NMN" to (1.0 to DoseUnit.UNIT),
            "Ca-AKG" to (1.0 to DoseUnit.UNIT),
            "Spermidine" to (1.0 to DoseUnit.UNIT),
        ).forEachIndexed { i, (name, dose) ->
            itemDao.upsert(
                ItemEntity(
                    groupId = morning, name = name, type = ItemType.CAPSULE,
                    doseAmount = dose.first, doseUnit = dose.second,
                    flags = fasted, sortOrder = i, createdAtEpochMs = now,
                    note = "On waking, fasted, with tea",
                ),
            )
        }
        itemDao.upsert(
            ItemEntity(
                groupId = morning, name = "D3 + K2", brand = "spray",
                type = ItemType.SPRAY, doseAmount = 5.0, doseUnit = DoseUnit.UNIT,
                flags = fasted or ItemFlags.FAT_SOLUBLE, sortOrder = 3, createdAtEpochMs = now,
                note = "5 sprays",
            ),
        )

        // ---- Iron: alternate-day, mid-afternoon, away from tea/calcium ----
        val iron = groupDao.upsert(
            GroupEntity(
                name = "Iron",
                trigger = TriggerType.TIME_WINDOW,
                triggerConfig = """{"startMin":840,"endMin":960}""", // 14:00–16:00
                iconKey = "droplet",
                sortOrder = 1,
            ),
        )
        val ironFlags = ItemFlags.EMPTY_STOMACH or ItemFlags.AVOID_CAFFEINE or ItemFlags.AVOID_CALCIUM
        itemDao.upsert(
            ItemEntity(
                groupId = iron, name = "Iron bisglycinate", type = ItemType.CAPSULE,
                doseAmount = 2.0, doseUnit = DoseUnit.UNIT,
                frequency = FrequencyType.EVERY_N_DAYS, frequencyInterval = 2,
                frequencyAnchorEpochDay = anchor,
                flags = ironFlags, sortOrder = 0, createdAtEpochMs = now,
                note = "Alternate days · empty stomach · 1h clear of tea/coffee · not with calcium",
            ),
        )
        itemDao.upsert(
            ItemEntity(
                groupId = iron, name = "Vitamin C", type = ItemType.CAPSULE,
                doseAmount = 1.0, doseUnit = DoseUnit.UNIT,
                frequency = FrequencyType.EVERY_N_DAYS, frequencyInterval = 2,
                frequencyAnchorEpochDay = anchor,
                flags = ItemFlags.EMPTY_STOMACH, sortOrder = 1, createdAtEpochMs = now,
                note = "Boosts iron absorption",
            ),
        )

        // ---- Evening: main meal (arriving home) ----
        val evening = groupDao.upsert(
            GroupEntity(
                name = "Evening",
                trigger = TriggerType.ARRIVE_HOME,
                iconKey = "home",
                sortOrder = 2,
            ),
        )
        val withFood = ItemFlags.WITH_FOOD
        var s = 0
        itemDao.upsert(ItemEntity(groupId = evening, name = "Omega-3", type = ItemType.SOFTGEL, doseAmount = 1.0, doseUnit = DoseUnit.UNIT, flags = withFood or ItemFlags.FAT_SOLUBLE, sortOrder = s++, createdAtEpochMs = now, note = "With the main meal"))
        itemDao.upsert(ItemEntity(groupId = evening, name = "Selenium", type = ItemType.CAPSULE, doseAmount = 200.0, doseUnit = DoseUnit.MCG, flags = withFood, sortOrder = s++, createdAtEpochMs = now))
        itemDao.upsert(ItemEntity(groupId = evening, name = "Urolithin A", type = ItemType.CAPSULE, doseAmount = 1.0, doseUnit = DoseUnit.UNIT, flags = withFood, sortOrder = s++, createdAtEpochMs = now))
        val folate = itemDao.upsert(ItemEntity(groupId = evening, name = "L-Methylfolate", type = ItemType.CAPSULE, doseAmount = 1.0, doseUnit = DoseUnit.MG, flags = withFood, sortOrder = s++, createdAtEpochMs = now, note = "Take with B12"))
        itemDao.upsert(ItemEntity(groupId = evening, name = "Methyl-B12", type = ItemType.SUBLINGUAL, doseAmount = 1500.0, doseUnit = DoseUnit.MCG, flags = withFood, pairWithItemId = folate, sortOrder = s++, createdAtEpochMs = now, note = "Take with folate"))
        itemDao.upsert(ItemEntity(groupId = evening, name = "D3 + K2", brand = "spray", type = ItemType.SPRAY, doseAmount = 5.0, doseUnit = DoseUnit.UNIT, flags = withFood or ItemFlags.FAT_SOLUBLE, sortOrder = s++, createdAtEpochMs = now, note = "5 sprays"))

        // ---- Night: wind-down (before sleep) ----
        val night = groupDao.upsert(
            GroupEntity(
                name = "Night",
                trigger = TriggerType.BEFORE_SLEEP,
                iconKey = "moon",
                sortOrder = 3,
            ),
        )
        var n = 0
        itemDao.upsert(ItemEntity(groupId = night, name = "Magnesium", brand = "Mag7", type = ItemType.POWDER, doseAmount = 1.0, doseUnit = DoseUnit.SCOOP, sortOrder = n++, createdAtEpochMs = now, note = "Kept away from the afternoon iron"))
        itemDao.upsert(ItemEntity(groupId = night, name = "Glycine + NAC", type = ItemType.CAPSULE, doseAmount = 1.0, doseUnit = DoseUnit.UNIT, sortOrder = n++, createdAtEpochMs = now))
        itemDao.upsert(ItemEntity(groupId = night, name = "Melatonin", type = ItemType.SUBLINGUAL, doseAmount = 1.0, doseUnit = DoseUnit.UNIT, sortOrder = n++, createdAtEpochMs = now))

        // ---- Monthly pulse ----
        val monthly = groupDao.upsert(
            GroupEntity(
                name = "Monthly pulse",
                trigger = TriggerType.MONTHLY,
                triggerConfig = """{"days":[1],"run":3}""",
                iconKey = "calendar",
                quiet = true,
                sortOrder = 4,
            ),
        )
        itemDao.upsert(
            ItemEntity(
                groupId = monthly, name = "SeneVit", type = ItemType.CAPSULE,
                doseAmount = 1.0, doseUnit = DoseUnit.UNIT,
                frequency = FrequencyType.MONTHLY_DAYS, frequencyDaysOfMonth = "1,2,3",
                sortOrder = 0, createdAtEpochMs = now,
                note = "2–3 day run, once a month",
            ),
        )
    }
}
