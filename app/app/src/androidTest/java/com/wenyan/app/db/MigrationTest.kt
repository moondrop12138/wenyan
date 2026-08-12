package com.wenyan.app.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyan.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7.3 T1 Room 迁移自动化测试（MigrationTestHelper 读 app/schemas 下 1.json~6.json）：
 * - v1→v2→v3→v4→v5→v6 全链路逐步迁移 + 数据保留断言；
 * - 直接 v1→v6 双路径迁移；
 * - v6 新增 memory_fact 表可查询、target.note 默认空串、session.targetId 可空。
 * 注意：room-testing 2.6.1 的 runMigrationsAndValidate 返回迁移后的数据库（无 getDatabase API）。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun seedV1() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO profile (mbti, score, strengths, weaknesses, createdAt) " +
                    "VALUES ('INTJ', 70, '理性', '慢热', 1000)"
            )
            execSQL(
                "INSERT INTO target (codeName, mbti, score, relationStatus, timeline, createdAt) " +
                    "VALUES ('小A', 'INFJ', 80, '暧昧', '[]', 2000)"
            )
            execSQL(
                "INSERT INTO session (createdAt, scenarioTag, refDocs) " +
                    "VALUES (3000, 'user_question', '[]')"
            )
            close()
        }
    }

    private fun assertRetainedData(db: SupportSQLiteDatabase) {
        db.query("SELECT mbti, score, strengths FROM profile WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("INTJ", c.getString(0))
            assertEquals(70, c.getInt(1))
            assertEquals("理性", c.getString(2))
        }
        db.query("SELECT codeName, mbti, score, relationStatus, timeline, note FROM target WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("小A", c.getString(0))
            assertEquals("INFJ", c.getString(1))
            assertEquals(80, c.getInt(2))
            assertEquals("暧昧", c.getString(3))
            assertEquals("[]", c.getString(4))
            // v5 迁移补默认空串（老数据不丢）
            assertEquals("", c.getString(5))
        }
        db.query("SELECT createdAt, targetId FROM session WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3000L, c.getLong(0))
            // v5 迁移加可空 targetId（老会话 = null）
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrate1To6_stepwise_retainsData() {
        seedV1()
        val db = helper.runMigrationsAndValidate(testDb, 6, true, *AppDatabase.MIGRATIONS)
        assertRetainedData(db)
        // v6 memory_fact 表可查询
        db.query("SELECT COUNT(*) FROM memory_fact").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate1To6_direct_retainsData() {
        seedV1()
        // 直接 v1→v6（一次性跑完全部 Migration），与逐步迁移双路径等价
        val db = helper.runMigrationsAndValidate(testDb, 6, true, *AppDatabase.MIGRATIONS)
        assertRetainedData(db)
        db.close()
    }

    @Test
    fun migrate1To5_legacyPath_stillValid() {
        seedV1()
        // 老版本用户升级到 v5 的路径（v6 之前的历史迁移）依然可迁移且数据保留
        val legacyMigrations = AppDatabase.MIGRATIONS.filter { it.endVersion <= 5 }.toTypedArray()
        val db = helper.runMigrationsAndValidate(testDb, 5, true, *legacyMigrations)
        assertRetainedData(db)
        db.close()
    }

    @Test
    fun migrate1To8_stepwise_retainsData() {
        // v1.9.1：全链路 v1→v8（含 v7→v8 expiresAt/source），数据保留 + 新列默认值正确
        seedV1()
        val db = helper.runMigrationsAndValidate(testDb, 8, true, *AppDatabase.MIGRATIONS)
        assertRetainedData(db)
        db.query("SELECT expiresAt, source FROM memory_fact").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0)) // 老数据无到期时间 = 永久
            assertEquals("manual", c.getString(1)) // 老数据来源统一 manual
        }
        db.close()
    }

    @Test
    fun migrate7To8_keepsKindAndAddsExpiryColumns() {
        // v7（kind 分层）→ v8（expiresAt/source）：kind 数据保留，新列默认值正确
        helper.createDatabase(testDb, 7).apply {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `memory_fact` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`targetId` INTEGER NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL DEFAULT 'fact', " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`targetId`) REFERENCES `target`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            execSQL("INSERT INTO memory_fact (id, targetId, text, kind, createdAt) VALUES (1, 1, '她喜欢猫', 'hypothesis', 1000)")
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 8, true, *AppDatabase.MIGRATIONS)
        db.query("SELECT text, kind, expiresAt, source FROM memory_fact WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("她喜欢猫", c.getString(0))
            assertEquals("hypothesis", c.getString(1)) // kind 保留
            assertTrue(c.isNull(2)) // expiresAt 默认 null
            assertEquals("manual", c.getString(3)) // source 默认 manual
        }
        db.close()
    }
}
