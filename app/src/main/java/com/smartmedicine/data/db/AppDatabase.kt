package com.smartmedicine.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库
 * 
 * 包含传感器历史数据表
 */
@Database(
    entities = [
        SensorDataEntity::class,
        MedicineCompartmentEntity::class,
        MedicinePlanEntity::class,
        MedicineDoseEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun sensorDataDao(): SensorDataDao
    abstract fun medicineCompartmentDao(): MedicineCompartmentDao
    abstract fun medicinePlanDao(): MedicinePlanDao
    abstract fun medicineDoseDao(): MedicineDoseDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * 获取数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
        
        /**
         * 构建数据库
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "smart_medicine_box.db"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .addCallback(DatabaseCallback())
            .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_compartments (
                        boxId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(boxId)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicine_compartments ADD COLUMN stock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medicine_compartments ADD COLUMN dosePerUse INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE medicine_compartments ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 3")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_plans (
                        planId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        boxId INTEGER NOT NULL,
                        medicineName TEXT NOT NULL,
                        doseAmount INTEGER NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        repeatType TEXT NOT NULL,
                        daysOfWeek TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_doses (
                        doseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        planId INTEGER NOT NULL,
                        boxId INTEGER NOT NULL,
                        medicineName TEXT NOT NULL,
                        doseAmount INTEGER NOT NULL,
                        scheduledAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        timerId INTEGER,
                        completedAt INTEGER,
                        note TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicine_doses ADD COLUMN actualTakenAt INTEGER")
                db.execSQL("ALTER TABLE medicine_doses ADD COLUMN riskLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medicine_doses ADD COLUMN reminderCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medicine_doses ADD COLUMN smartReason TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicine_compartments ADD COLUMN active INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE medicine_compartments
                    SET active = 1
                    WHERE stock > 0
                       OR boxId IN (SELECT DISTINCT boxId FROM medicine_plans)
                       OR name NOT LIKE '%号药盒'
                    """.trimIndent()
                )
            }
        }
        
        /**
         * 数据库回调
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 数据库创建时的初始化
            }
        }
    }
}
