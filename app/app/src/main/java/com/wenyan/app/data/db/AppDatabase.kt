package com.wenyan.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库（v6，7 表 + 索引 + 外键）
 * v1→v2：session 表加 stateJson（v1.3 对话状态跟踪）
 * v2→v3：session 表加 title（v1.2.1 主模型拟定会话标题）
 * v3→v4：model 表加 showInSheet（v1.6.3 主页弹层可见性）+ provider 表加 connectionStatus（连接状态灯）
 * v4→v5：target 表加 note（v1.7.2 记忆正文）+ session 表加 targetId（会话档案归属）
 * v5→v6：新建 memory_fact 表（v1.7.3 单条事实管理；只建表，note→facts 数据搬移在业务层惰性迁移）
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
        MemoryFactEntity::class,
    ],
    version = 6,
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
    /** v1.7.3 单条事实管理 */
    abstract fun memoryFactDao(): MemoryFactDao

    companion object {
        private const val DB_NAME = "wenyan.db"

        /** v1→v2：session 加 stateJson 列（默认空串，老数据不丢） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN stateJson TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2→v3：session 加 title 列（默认空串，老数据不丢） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3→v4：model 加 showInSheet（默认 1 展示）+ provider 加 connectionStatus（默认空=未测/失败红灯） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model ADD COLUMN showInSheet INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE provider ADD COLUMN connectionStatus TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4→v5：target 加 note（默认空串，老数据不丢）+ session 加 targetId（可空，无 DEFAULT） */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE session ADD COLUMN targetId INTEGER")
            }
        }

        /**
         * v5→v6：新建 memory_fact 表（v1.7.3 单条事实管理）。
         * 只做结构（建表 + FK CASCADE + 索引）；note→facts 数据搬移在业务层惰性迁移（ProfileRepository.migrateNoteToFactsOnce）。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_fact_targetId` ON `memory_fact` (`targetId`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        /** v1.7.3 T1 迁移测试用：全链路 Migration 数组（供 MigrationTestHelper.runMigrationsAndValidate） */
        @JvmField
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        )

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
