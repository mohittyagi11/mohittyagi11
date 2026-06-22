package com.quietdose.data.db

import androidx.room.TypeConverter
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.FrequencyType
import com.quietdose.data.model.IntakeSource
import com.quietdose.data.model.ItemType
import com.quietdose.data.model.TriggerType

/**
 * Enums are stored by name (stable, human-readable in the DB) rather than
 * ordinal, so reordering an enum never corrupts existing rows.
 */
class Converters {
    @TypeConverter fun triggerToString(v: TriggerType): String = v.name
    @TypeConverter fun stringToTrigger(v: String): TriggerType = TriggerType.valueOf(v)

    @TypeConverter fun itemTypeToString(v: ItemType): String = v.name
    @TypeConverter fun stringToItemType(v: String): ItemType = ItemType.valueOf(v)

    @TypeConverter fun doseUnitToString(v: DoseUnit): String = v.name
    @TypeConverter fun stringToDoseUnit(v: String): DoseUnit = DoseUnit.valueOf(v)

    @TypeConverter fun freqToString(v: FrequencyType): String = v.name
    @TypeConverter fun stringToFreq(v: String): FrequencyType = FrequencyType.valueOf(v)

    @TypeConverter fun sourceToString(v: IntakeSource): String = v.name
    @TypeConverter fun stringToSource(v: String): IntakeSource = IntakeSource.valueOf(v)
}
