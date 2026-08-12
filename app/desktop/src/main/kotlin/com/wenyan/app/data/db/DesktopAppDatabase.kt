package com.wenyan.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * AppDatabase 的桌面（JVM）实现：与 Android 版同名同包同注解（编译期替换）。
 *
 * 与 Android 版（Room 2.6.1）的差异仅两处：
 *  1. Builder：Room.databaseBuilder<AppDatabase>(name=path).setDriver(BundledSQLiteDriver)
 *     （Android 版用 Room.databaseBuilder(context, ...)）
 *  2. Migration 签名：Room 2.7 KMP 用 SQLiteConnection.execSQL（Android 版用 SupportSQLiteDatabase）
 *     —— 6 段迁移 SQL 与 Android 版逐字一致（唯一事实源在 Android 版 AppDatabase.kt，改动需同步）
 *
 * entity/DAO/Converters 全部通过 sourceSet 共享，零改动。
 */
@Database(
    entities = [
        ProfileEntity::class,
        TargetEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        ProviderEntity::class,
        ModelEntity::class,
        MemoryFactEntity::class,
    ],
    version = 8,
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
    abstract fun memoryFactDao(): MemoryFactDao

    companion object {
        private const val DB_NAME = "wenyan.db"

        // ---- 6 段迁移 SQL 与 Android 版 AppDatabase.kt 逐字一致（Room 2.7 KMP 签名）----

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE session ADD COLUMN stateJson TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE session ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE model ADD COLUMN showInSheet INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE provider ADD COLUMN connectionStatus TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE target ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE session ADD COLUMN targetId INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_fact` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `targetId` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`targetId`) REFERENCES `target`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_fact_targetId` ON `memory_fact` (`targetId`)")
            }
        }

        /** v6→v7：memory_fact 加 kind 列（v1.9.0 事实/推断分层，默认 fact 老数据不丢；与 Android 版逐字一致） */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE memory_fact ADD COLUMN kind TEXT NOT NULL DEFAULT 'fact'")
            }
        }

        /** v7→v8：memory_fact 加 expiresAt（可空）/ source（默认 manual）（v1.9.1；与 Android 版逐字一致） */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE memory_fact ADD COLUMN expiresAt INTEGER")
                connection.execSQL("ALTER TABLE memory_fact ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
            }
        }

        @JvmField
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        )

        @Volatile
        private var instance: AppDatabase? = null

        /** 桌面端数据库目录：%APPDATA%\Wenyan\（兜底 user.home/.wenyan） */
        fun dbDir(): File {
            val appData = System.getenv("APPDATA")
            val base = if (!appData.isNullOrBlank()) File(appData, "Wenyan")
                       else File(System.getProperty("user.home"), ".wenyan")
            base.mkdirs()
            return base
        }

        fun get(): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<AppDatabase>(
                    name = File(dbDir(), DB_NAME).absolutePath,
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
