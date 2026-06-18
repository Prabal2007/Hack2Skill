package com.example.hackofiesta.Database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VehicleLocationData::class],
    version = 3
)
abstract class OverallDatabase : RoomDatabase() {

    abstract fun vehicleLocationDao(): VehicleLocationDAO

    companion object {

        val migration_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vehicleLocationCountTable ADD currentState TEXT"
                )
            }
        }



        val migration_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicleLocationCountTable ADD COLUMN latitude REAL DEFAULT 0.0")
                db.execSQL("ALTER TABLE vehicleLocationCountTable ADD COLUMN longitude REAL DEFAULT 0.0")
            }
        }

        @Volatile
        private var INSTANCE: OverallDatabase? = null

        fun getDatabase(context: Context): OverallDatabase {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        OverallDatabase::class.java,
                        "overallDB")
                        .addMigrations(migration_1_2, migration_2_3)
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }
            return INSTANCE!!
        }
    }
}