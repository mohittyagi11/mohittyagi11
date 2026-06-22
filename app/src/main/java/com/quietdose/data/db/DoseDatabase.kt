package com.quietdose.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DoseDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun itemDao(): ItemDao
    abstract fun intakeDao(): IntakeDao

    companion object {
        @Volatile private var INSTANCE: DoseDatabase? = null

        /** v1 → v2: add the per-item `ingredients` column. Preserves all existing data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN ingredients TEXT")
            }
        }

        fun get(context: Context): DoseDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DoseDatabase::class.java,
                    "dose.db",
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // last resort only; the migration above is the real path
                    .build().also { INSTANCE = it }
            }
    }
}
