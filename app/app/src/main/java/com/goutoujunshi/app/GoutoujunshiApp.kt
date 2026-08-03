package com.goutoujunshi.app

import android.app.Application
import com.goutoujunshi.app.container.RealAppContainer
import com.goutoujunshi.app.data.db.AppDatabase
import com.goutoujunshi.app.data.db.PresetSeed
import com.goutoujunshi.app.log.AppLogger
import com.goutoujunshi.app.ui.contract.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：只装配真实 AppContainer，零业务逻辑。
 * 预设提供商种子在后台幂等注入（首次启动）。
 */
class GoutoujunshiApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: AppContainer by lazy { RealAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        appScope.launch { PresetSeed.seedIfEmpty(AppDatabase.get(this@GoutoujunshiApp)) }
    }

    /** 崩溃兜底：全局未捕获异常写日志后转交原 handler（可观测性埋点，不含用户内容） */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e(
                "app_crash",
                throwable,
                "thread" to thread.name,
                "class" to throwable.javaClass.simpleName,
            )
            previous?.uncaughtException(thread, throwable)
        }
    }
}
