package com.wenyan.app

import android.app.Application
import com.wenyan.app.container.RealAppContainer
import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.db.PresetSeed
import com.wenyan.app.log.AppLogger
import com.wenyan.app.log.CrashLogStore
import com.wenyan.app.ui.contract.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：只装配真实 AppContainer，零业务逻辑。
 * 预设提供商种子在后台幂等注入（首次启动）。
 * v1.7.3：崩溃兜底升级——环形缓冲 + 崩溃落盘 last_crash.txt（设置页可导出诊断日志）。
 */
class WenyanApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** v1.7.3 崩溃日志存储（AppLogger 缓冲 + 崩溃落盘 + 设置页导出共用同一实例） */
    private val crashLogStore: CrashLogStore by lazy { CrashLogStore(this) }

    val container: AppContainer by lazy { RealAppContainer(this, crashLogStore) }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        appScope.launch { PresetSeed.seedIfEmpty(AppDatabase.get(this@WenyanApp)) }
    }

    /** 崩溃兜底：全局未捕获异常 → 缓冲落盘 last_crash.txt → 转交原 handler（可观测性埋点，不含用户内容） */
    private fun installCrashLogger() {
        AppLogger.crashStore = crashLogStore
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("app_crash", throwable, "thread" to thread.name, "class" to throwable.javaClass.simpleName)
            crashLogStore.writeCrash(thread, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
