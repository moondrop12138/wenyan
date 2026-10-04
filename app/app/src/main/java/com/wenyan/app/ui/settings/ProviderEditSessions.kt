package com.wenyan.app.ui.settings

import com.wenyan.app.data.security.AesGcmCipher
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 测试连接任务生命周期 Holder（进程单例）。
 *
 * 真价值（静态证真因后定稿）：真 dispose 无泄漏 + 四路并发只插一行 + 全 UUID 防 F46/叠栈误删。
 * 键盘重组复用同一 VM/scope，在途 launch 按静态分析不会被掐，Holder 对键盘冗余——不碰键盘逻辑。
 *
 * 规约：
 * - scope = SupervisorJob() + Dispatchers.IO（业务全在 IO；StateFlow 更新任意线程安全）。
 * - 表键一律 per-VM-instance UUID（键形 pe-<uuid>，新建/编辑同规则；providerId 只进快照不做键）。
 *   根因：AppRoot Crossfade 只渲染 current（AppRoot.kt:69），连续 push 同 id 时旧页 onDispose
 *   晚于新页建表，稳定同键下旧 release 会误删新表项/取消新 job；全 UUID 后 key 全局唯一。
 * - 取消语义：Holder job 内 catch(CancellationException){ 旗由 finally 清；重抛，不进映射 }。
 *   上一版"吞掉永不映射"废弃——吞 CE 会致 cancel 失效、job 挂起、转圈卡死。
 * - 快照 keyToPersist 保持 null=不覆盖语义；Holder 内分流——save 首插 saveKey = null→""，
 *   update 路径原样透传 null（无重加密）。
 * - 锁范围：临界区仅 = 读 effectiveId → saveProvider（本地 Room+Keystore，ms 级，锁内唯一挂起点）
 *   → 写回 effectiveId/persistedKey/lastAccess；updateProvider、testConnection 长网络、
 *   markConnectionStatus 一律锁外。"原子 guard" = withLock 内查旗置旗即释，第二调用短暂排队
 *   后见旗返回，非真 tryLock。Mutex 可跨线程，Main 永不持锁做长 IO。
 * - TTL 兜底：IDLE_TTL=10min、扫步 60s、懒启动单 daemon；仅清 collectors==0 且超期表项
 *   （VM collect 存续即免疫）；过期 = 取消 job + 删表项。正常退出走 Screen DisposableEffect
 *   即时 release，不等 TTL。release = 取消 job + 删表项，outcome 不保留，重进干净。
 * - errorToResult 唯一映射留 VM，Holder 只存原始 outcome（LlmError?/KeyUnavailable 消息），零复制。
 */
object ProviderEditSessions {

    internal const val IDLE_TTL_MILLIS = 10L * 60L * 1000L
    private const val SWEEP_INTERVAL_MILLIS = 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val table = ConcurrentHashMap<String, Session>()

    @Volatile
    private var sweeperStarted = false

    /** per-key 会话：Mutex + testing/saving/outcome/effectiveId 四 StateFlow + 收集器计数 */
    class Session internal constructor(val key: String) {
        val mutex = Mutex()
        val testing = MutableStateFlow(false)
        val saving = MutableStateFlow(false)
        val outcome = MutableStateFlow<ProviderTestOutcome?>(null)

        /** 生效 id 单源（归 Holder；VM 只镜像作 delete 兜底） */
        val effectiveId = MutableStateFlow<Long?>(null)

        @Volatile var job: Job? = null
        @Volatile var persistedKey: String? = null
        @Volatile var lastAccess: Long = System.currentTimeMillis()
        @Volatile var collectors: Int = 0
    }

    /** 取或建表项（附 touch；懒启动 TTL daemon） */
    fun session(key: String): Session {
        maybeStartSweeper()
        val s = table.getOrPut(key) { Session(key) }
        s.lastAccess = System.currentTimeMillis()
        return s
    }

    /** VM collect 前登记（存续即免疫 TTL 清理） */
    fun acquire(key: String): Session {
        val s = session(key)
        s.collectors++
        return s
    }

    /** VM collect 结束注销（不删表项，删表项只走 release/TTL） */
    fun uncollect(key: String) {
        table[key]?.let {
            it.collectors--
            it.lastAccess = System.currentTimeMillis()
        }
    }

    /** effectiveId 同步读（供 VM delete 兜底链首环） */
    fun effectiveIdOf(key: String): Long? = table[key]?.effectiveId?.value

    /** 离页释放：取消 job + 删表项，outcome 不保留，重进干净 */
    fun release(key: String) {
        table.remove(key)?.job?.cancel()
    }

    /** save 系原子 guard：短 withLock 查置即释 */
    suspend fun tryBeginSaving(key: String): Boolean {
        val s = session(key)
        return s.mutex.withLock {
            s.lastAccess = System.currentTimeMillis()
            if (s.saving.value) false else { s.saving.value = true; true }
        }
    }

    fun endSaving(key: String) {
        table[key]?.let {
            it.saving.value = false
            it.lastAccess = System.currentTimeMillis()
        }
    }

    /**
     * 测试连接全流程（Holder IO job 内执行，调用方 fire-and-forget，结果经 outcome StateFlow 镜像）。
     * guard 通过后起手 outcome=null；取消时不清映射、重抛保取消语义，旗由 finally 清。
     */
    fun runTest(key: String, snapshot: ProviderEditSnapshot, repo: SettingsRepository): Job {
        val s = session(key)
        return scope.launch {
            // 原子 guard：短 withLock 查置即释，网络在锁外；第二调用短暂排队后见旗返回
            val entered = s.mutex.withLock {
                s.lastAccess = System.currentTimeMillis()
                if (s.testing.value) false else { s.testing.value = true; true }
            }
            if (!entered) return@launch
            s.job = coroutineContext[Job]
            try {
                s.outcome.value = null
                val id = ensureEffectiveId(key, snapshot, repo)
                // 长网络锁外
                val err = repo.testConnection(id)
                s.outcome.value = if (err == null) ProviderTestOutcome.Success else ProviderTestOutcome.Failed(err)
            } catch (e: CancellationException) {
                // 取消：结果永不由 CE 产生；旗由 finally 清；重抛保取消语义
                throw e
            } catch (e: AesGcmCipher.KeyUnavailableException) {
                s.outcome.value = ProviderTestOutcome.KeyUnavailable(e.message)
            } finally {
                s.testing.value = false
                s.lastAccess = System.currentTimeMillis()
            }
        }
    }

    /**
     * H2/F50 统一 persist 入口（per-session Mutex 单行保证，替代旧 VM 私有 ensurePersisted）。
     * - 编辑：update 语义与旧 VM 内联逐字等价（keyToPersist 原样透传 null=不覆盖）；update 锁外。
     * - 新建：首插 save→写回临界（锁内唯一挂起点）；update 锁外；排队者双检后走 update 分支，
     *   全程只调一次 saveProvider。
     */
    suspend fun ensureEffectiveId(
        key: String,
        snapshot: ProviderEditSnapshot,
        repo: SettingsRepository,
    ): Long {
        val s = session(key)
        if (!snapshot.isNew) {
            repo.updateProvider(snapshot.providerIdArg, snapshot.name, snapshot.baseUrl, snapshot.keyToPersist)
            s.mutex.withLock {
                s.effectiveId.value = snapshot.providerIdArg
                s.lastAccess = System.currentTimeMillis()
            }
            return snapshot.providerIdArg
        }
        while (true) {
            val known = s.mutex.withLock {
                s.lastAccess = System.currentTimeMillis()
                s.effectiveId.value
            }
            if (known != null) {
                // 旧 ensurePersisted update 分支等价：keyToPersist 非空即 isNotBlank 成立，再比 persistedKey
                val updateKey = snapshot.keyToPersist?.takeIf { it != s.persistedKey }
                repo.updateProvider(known, snapshot.name, snapshot.baseUrl, updateKey)
                s.mutex.withLock { s.lastAccess = System.currentTimeMillis() }
                return known
            }
            val inserted = s.mutex.withLock {
                s.lastAccess = System.currentTimeMillis()
                if (s.effectiveId.value != null) return@withLock null // 排队期间已被首插→外层走 update 分支
                // save 首插分流：null→""（saveProvider 签名 apiKey 非空；旧 VM 传原样含空串）
                val newId = repo.saveProvider(
                    snapshot.name.ifBlank { "未命名服务" },
                    snapshot.baseUrl,
                    snapshot.keyToPersist ?: "",
                    false,
                )
                s.effectiveId.value = newId
                s.persistedKey = snapshot.keyToPersist?.takeIf { it.isNotBlank() }
                newId
            }
            if (inserted != null) return inserted
        }
    }

    private fun maybeStartSweeper() {
        if (sweeperStarted) return
        synchronized(this) {
            if (sweeperStarted) return
            sweeperStarted = true
            scope.launch {
                while (true) {
                    delay(SWEEP_INTERVAL_MILLIS)
                    sweep()
                }
            }
        }
    }

    /** TTL 扫步：仅清 collectors==0 且超期表项；过期 = 取消 job + 删表项 */
    private fun sweep() {
        val now = System.currentTimeMillis()
        val it = table.entries.iterator()
        while (it.hasNext()) {
            val s = it.next().value
            if (s.collectors == 0 && now - s.lastAccess > IDLE_TTL_MILLIS) {
                s.job?.cancel()
                it.remove()
            }
        }
    }
}

/**
 * 快照冻结（VM 主线程、normalize 通过后立即执行；Holder 永不读 VM 活字段）。
 * keyToPersist = apiKeyToPersist() 原样（null=不覆盖语义保持）；
 * apiKeyBlank = 点击时输入框是否空白（L30 删 Key 与红绿灯跳测的判定依据，
 * 与 keyToPersist==null 双义解耦：未改 Key 时 keyToPersist 亦 null 但框非空）。
 */
data class ProviderEditSnapshot(
    val name: String,
    val baseUrl: String,
    val keyToPersist: String?,
    val apiKeyBlank: Boolean,
    val providerIdArg: Long,
    val isNew: Boolean,
)

/** 原始测试结果（Holder 存原始 outcome，errorToResult 唯一映射留 VM，零复制） */
sealed interface ProviderTestOutcome {
    data object Success : ProviderTestOutcome
    data class Failed(val error: LlmError) : ProviderTestOutcome

    /** Keystore 不可用：存原始 message（可空），文案兜底留 VM */
    data class KeyUnavailable(val message: String?) : ProviderTestOutcome
}
