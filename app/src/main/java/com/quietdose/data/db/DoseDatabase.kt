package com.quietdose.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quietdose.data.dao.GroupDao
import com.quietdose.data.dao.IntakeDao
import com.quietdose.data.dao.ItemDao
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.IntakeLogEntity
import com.quietdose.data.entity.ItemEntity

@Database(
    entities = [
        GroupEntity::class,
        ItemEntity::class,
        IntakeLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DoseDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun itemDao(): ItemDao
    abstract fun intakeDao(): IntakeDao

    companion object {
        @Volatile private var INSTANCE: DoseDatabase? = null

        fun get(context: Context): DoseDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DoseDatabase::class.java,
                    "dose.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
