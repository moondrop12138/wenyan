package com.wenyan.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库（v2，6 表 + 索引 + 外键）
 * v1→v2：session 表加 stateJson（v1.3 对话状态跟踪）
 * exportSchema=true，schema JSON 提交入库（app/schemas/）
 */
@Database(
    entities = [
        ProfileEntity::class,
        TargetEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        ProviderEntity::class,
        ModelEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun targetDao(): TargetDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao

    companion object {
        private const val DB_NAME = "wenyan.db"

        /** v1→v2：session 加 stateJson 列（默认空串，老数据不丢） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN stateJson TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
