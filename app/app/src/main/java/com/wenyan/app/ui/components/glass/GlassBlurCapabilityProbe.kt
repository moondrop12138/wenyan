package com.wenyan.app.ui.components.glass

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 运行时玻璃模糊能力探测（v1.9.4；10-02 迭代起**探测从门禁降级为观测**——结论不再否决
 * 任何玻璃行为）。
 *
 * 动机：backdrop 模糊可用性此前只看 API 版本（API 31+ 即认定 RenderEffect 可用）。真机反馈
 * 证明 ROM 层仍可能不渲染我们的模糊构造（厂商「关闭动画/模糊」开关、HWUI 实现差异等）——
 * 模糊静默失效时页面只剩半透明填充。本探测在真实渲染管线上验证「RenderNode +
 * RenderEffect.createBlurEffect + drawRenderNode 取样」这条生产同构链路（与
 * [GlassBackdropLayer.recordBlurUnderlay] 的模糊载体同构：renderEffect 挂模糊层、内容经
 * drawRenderNode 取样），失败 = 该设备/ROM 不渲染我们的模糊构造。
 *
 * 形态：一次性探测，结果进程内缓存（[state]+[failure]，Compose 可观察快照态）；页面首次
 * 需要玻璃模糊时懒触发（[rememberGlassBackdrop]）；跑在独立 HandlerThread（HardwareRenderer
 * 要求带 Looper 的线程），不阻塞 UI。**观测语义**：每次结论打 logcat（verdict/边缘能量/
 * 比率/失败原因）+ 设置页「玻璃」卡状态行（[glassProbeStatusText]），不驱动雾化兜底或任何
 * 玻璃行为——31+ 恒真实模糊（判定唯一来源 [glassRenderMode]），探测结论翻转不再触发重组
 * 行为变化。
 *
 * 加固（探测自身可靠性，防「永停 PROBING」与病态管线）：
 * - 总预算 [PROBE_TIMEOUT_MS] 超时兜底——超时也翻转出 FAILED（[ProbeFailure.TIMEOUT]），
 *   结论绝不停在 PROBING；渲染线程结论与超时兜底竞速，经 [tryConclude] 单写者收敛
 *   （synchronized 检查-写入原子），双到无竞争、后到者静默退出；
 * - [grabFrame] 检查 `syncAndDraw()` 返回值（[isSyncSuccess]：AOSP SyncAndDrawResult 约定
 *   SYNC_OK=0 才是干净出帧，非 0 = 重绘请求/surface 丢失/未出帧等）；
 * - `acquireLatestImage()` 空读回短重试（共 [MAX_READ_BACK_ATTEMPTS] 次尝试、间隔
 *   [READ_BACK_RETRY_DELAY_MS]，present fence 未 signal 的窗口由重试吸收）。
 *
 * 可测性：判定逻辑收敛在纯函数（[isSyncSuccess]/[shouldRetryReadBack]/[decideDetail]/
 * [isBlurRendered]/[decide]，整数/像素数组进、布尔出，JVM 可测 GlassBlurCapabilityProbeTest）；
 * 渲染编排（线程/Handler/Log）不做 JVM 单测。
 */
object GlassBlurCapabilityProbe {

    /** logcat tag（对齐仓库惯例：tag = 类名字面量，见 RealChatRepository 的 Log 用法）。 */
    private const val TAG = "GlassBlurCapabilityProbe"

    /** 探测帧尺寸（px）。小画幅足够判定，读回开销低。 */
    private const val FRAME_SIZE = 128

    /** 探测模糊半径（px）：远大于条纹周期一半，真模糊会把边缘能量压到接近零。 */
    private const val BLUR_RADIUS_PX = 8f

    /**
     * 探测总预算（ms）：超时兜底翻转出 FAILED（原因 [ProbeFailure.TIMEOUT]），绝不停在
     * PROBING。覆盖渲染线程挂死/驱动不回调等病态——此时探测线程可能滞留至挂死调用自然
     * 返回（HardwareRenderer 无取消 API；一次性探测的进程内成本可接受），行为侧已被
     * [tryConclude] 守卫不受影响。
     */
    const val PROBE_TIMEOUT_MS = 2_000L

    /** 空读回尝试上限（**含首次**，共 3 次尝试）。 */
    const val MAX_READ_BACK_ATTEMPTS = 3

    /** 空读回重试间隔（ms）。 */
    const val READ_BACK_RETRY_DELAY_MS = 20L

    /** 探测状态机（Compose 可观察，**仅观测**）：PENDING=未探测；PROBING=进行中；CAPABLE/FAILED=结论。 */
    enum class ProbeState { PENDING, PROBING, CAPABLE, FAILED }

    /**
     * 失败原因（仅观测元数据，不参与任何行为判定）：EXCEPTION=渲染管线抛错；
     * READ_BACK_EMPTY=帧读回为空（sync 非 0 / 空读回重试耗尽）；TIMEOUT=超 [PROBE_TIMEOUT_MS] 兜底。
     */
    enum class ProbeFailure { EXCEPTION, READ_BACK_EMPTY, TIMEOUT }

    /**
     * 进程内缓存（Compose 可观察快照态，**仅观测**）：设置页状态行组合期读本状态 → 结论
     * 翻转自动刷新文案；无任何行为消费方（v1.9.4 探测降级为观测）。
     */
    var state by mutableStateOf(ProbeState.PENDING)
        private set

    /** 失败原因快照（CAPABLE=null；FAILED=原因；仅观测元数据）。 */
    var failure by mutableStateOf<ProbeFailure?>(null)
        private set

    /** 布尔结论视图：null=未探测/探测中；true/false=CAPABLE/FAILED（懒触发幂等判据）。 */
    val cachedResult: Boolean?
        get() = when (state) {
            ProbeState.CAPABLE -> true
            ProbeState.FAILED -> false
            else -> null
        }

    /**
     * 懒触发探测（幂等：已出结论直接回调；探测中直接返回——完成路径不派发迟到回调）。
     * 结论写入可观察 [state]（经 [tryConclude] 单写者收敛），每次结论打 logcat（见 [finish]）。
     * 默认回调为空（观测消费方走状态行重组，回调仅为既有签名兼容保留）。
     */
    fun probeAsync(onResult: (Boolean) -> Unit = {}) {
        when (state) {
            ProbeState.CAPABLE -> {
                onResult(true)
                return
            }
            ProbeState.FAILED -> {
                onResult(false)
                return
            }
            ProbeState.PROBING -> return
            ProbeState.PENDING -> {}
        }
        state = ProbeState.PROBING
        val thread = HandlerThread("glass-blur-probe").apply { start() }
        val main = Handler(Looper.getMainLooper())
        // 超时兜底：与渲染线程结论竞速，先到者经 tryConclude 落定，后到者被拒（无双写竞争）。
        // object 表达式自带 this 引用——finish 内 removeCallbacks 需要移除的正是这个 Runnable
        // 本身（val 自引用初始化在 Kotlin 局部变量上不可行）
        val timeout: Runnable = object : Runnable {
            override fun run() {
                finish(main, this, thread, onResult, ProbeVerdict(false, 0.0, 0.0), ProbeFailure.TIMEOUT)
            }
        }
        main.postDelayed(timeout, PROBE_TIMEOUT_MS)
        Handler(thread.looper).post {
            var failure: ProbeFailure? = null
            val frames = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderHardwareFrames(FRAME_SIZE, FRAME_SIZE, BLUR_RADIUS_PX)
                } else {
                    null
                }
            } catch (t: Throwable) {
                failure = ProbeFailure.EXCEPTION
                Log.w(TAG, "probe render pipeline threw", t)
                null
            }
            if (frames == null && failure == null) failure = ProbeFailure.READ_BACK_EMPTY
            finish(main, timeout, thread, onResult, decideDetail(frames, FRAME_SIZE, FRAME_SIZE), failure)
        }
    }

    /**
     * 结论落定（单写者收敛）：[tryConclude] 抢到写权才取消超时兜底、打结论日志、派发回调
     * 并收尾线程；超时兜底与渲染线程结论竞速时后到者静默退出——[state]/[failure] 每进程
     * 至多落定一次（失败结论每进程最多打一次日志的来源）。
     */
    private fun finish(
        main: Handler,
        timeout: Runnable,
        thread: HandlerThread,
        onResult: (Boolean) -> Unit,
        detail: ProbeVerdict,
        failure: ProbeFailure?,
    ) {
        if (!tryConclude(detail.capable, failure)) return
        main.removeCallbacks(timeout)
        val ratio = if (detail.energySharp > 0.0) detail.energyBlurred / detail.energySharp else null
        Log.i(
            TAG,
            "probe verdict=${if (detail.capable) "CAPABLE" else "FAILED"} reason=${failure?.name ?: "none"} " +
                "eSharp=${fmt(detail.energySharp)} eBlurred=${fmt(detail.energyBlurred)} ratio=${ratio?.let { fmt(it) } ?: "n/a"}",
        )
        main.post {
            onResult(detail.capable)
            thread.quitSafely()
        }
    }

    /** 能量/比率的 logcat 科学计数格式（Locale.US 显式，避免 lint DefaultLocale）。 */
    private fun fmt(v: Double): String = String.format(Locale.US, "%.3e", v)

    /**
     * 结论写入（单写者收敛）：仅 PROBING 态可翻转（synchronized 检查-写入原子）——渲染线程
     * 结论与超时兜底竞速时后到者被拒，[state] 每进程至多翻转一次。
     */
    private fun tryConclude(capable: Boolean, failure: ProbeFailure?): Boolean = synchronized(this) {
        if (state != ProbeState.PROBING) return false
        state = if (capable) ProbeState.CAPABLE else ProbeState.FAILED
        this.failure = failure
        true
    }

    /**
     * 判定 + 边缘能量明细（纯函数，JVM 可测）：与 [decide] 同判定，另带 eSharp/eBlurred
     * 供结论 logcat 打比率（探测降级为观测后，这是设备侧唯一证据源）。帧不齐/无对比度等
     * fail-closed 路径能量记 0（capable=false）。
     */
    @VisibleForTesting
    internal fun decideDetail(frames: Pair<IntArray, IntArray>?, width: Int, height: Int): ProbeVerdict {
        val sharp = frames?.first
        val blurred = frames?.second
        if (sharp == null || blurred == null || sharp.size != blurred.size || sharp.isEmpty() || sharp.size != width * height) {
            return ProbeVerdict(false, 0.0, 0.0)
        }
        val energySharp = edgeEnergy(sharp, width, height)
        if (energySharp <= 0.0) return ProbeVerdict(false, energySharp, 0.0)
        val energyBlurred = edgeEnergy(blurred, width, height)
        return ProbeVerdict(energyBlurred > energySharp * 1e-6 && energyBlurred < energySharp * 0.8, energySharp, energyBlurred)
    }

    /** 判定明细（纯数据）：capable + 边缘能量（观测日志的 eSharp/eBlurred/比率来源）。 */
    internal data class ProbeVerdict(val capable: Boolean, val energySharp: Double, val energyBlurred: Double)

    /**
     * 判定（纯逻辑，JVM 可测）：两帧齐备 + 源层确有对比度 + 模糊帧边缘能量显著下降。
     * 任一不满足（含渲染管线抛错、源层没画出来）一律判 false（fail-closed）。
     */
    internal fun decide(frames: Pair<IntArray, IntArray>?, width: Int, height: Int): Boolean =
        decideDetail(frames, width, height).capable

    /**
     * 模糊是否真的渲染了（纯逻辑）：对两帧做灰度化 + Laplacian 边缘能量比对。
     * ROM 无视 RenderEffect 时两帧逐位相同 → 能量比 ≈1 → false；
     * 模糊帧还必须**仍有内容**：全空/全黑帧（取样层没画出来——正是本修复针对的失效形态）
     * 能量恒 0，若只判「比源低」会被误判为成功（GlassBlurCapabilityProbeTest
     * `mismatched frames fail closed` 钉死此路径），故设绝对下限。
     * 真模糊会把高频条纹能量压掉一个量级以上（上限 0.8×源能量；下限 1e-6×源能量 =
     * 模拟器 API 35 实测真实比率 ≈3.8e-4（eSharp 2.6e8 → eBlurred 9.9e4，2026-10-01
     * logcat 探针日志）再降两个数量级的钉值——不误杀真模糊、仍排除零内容帧）。
     * 源层自身无对比度（能量≈0）时探测不可信 → false。
     */
    @VisibleForTesting
    internal fun isBlurRendered(sharp: IntArray, blurred: IntArray, width: Int, height: Int): Boolean =
        decideDetail(sharp to blurred, width, height).capable

    /** Laplacian 能量（灰度二阶差分平方和）：高频越强能量越大；模糊是高频杀手。 */
    private fun edgeEnergy(px: IntArray, width: Int, height: Int): Double {
        val gray = IntArray(px.size) { i ->
            val argb = px[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
        var sum = 0.0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val lap = gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]
                sum += lap.toDouble() * lap
            }
        }
        return sum
    }

    /**
     * [HardwareRenderer.RenderRequest.syncAndDraw] 返回值判定（纯函数，JVM 可测）：
     * AOSP `@SyncAndDrawResult` 约定——**SYNC_OK=0 才是「sync 完成且出帧」**；非 0 位标志 =
     * SYNC_REDRAW_REQUESTED（树内有动画，帧未定）/ SYNC_LOST_SURFACE_REWARD_IF_FOUND
     * （surface 失效）/ SYNC_CONTEXT_IS_STOPPED（sync 了但没出帧）/ SYNC_FRAME_DROPPED
     * （本 vsync 未出帧，渲染器内部重排）——对一次性离屏探测一律按失败处理（android-12 至
     * main 分支语义一致，2026-10 源码核对）。
     */
    @VisibleForTesting
    internal fun isSyncSuccess(syncResult: Int): Boolean = syncResult == 0

    /**
     * 空读回重试判定（纯函数，JVM 可测）：[emptyAttempts] = 已发生的连续空读回次数（0 起），
     * 仍小于 [MAX_READ_BACK_ATTEMPTS] 则再试一次（共至多 3 次尝试、2 次间隔
     * [READ_BACK_RETRY_DELAY_MS]）。
     */
    @VisibleForTesting
    internal fun shouldRetryReadBack(emptyAttempts: Int): Boolean = emptyAttempts < MAX_READ_BACK_ATTEMPTS

    /**
     * 真渲染管线两帧（生产同构）：源层（高对比黑白竖条纹）→ 模糊层（RenderNode 挂
     * RenderEffect.createBlurEffect、record 内 drawRenderNode 取样源层）。
     * HardwareRenderer+ImageReader 离屏渲染后逐像素读回。必须在带 Looper 的线程调用。
     * 任何一步失败抛错/返回 null → 上层判 false（结论 reason 分别为 EXCEPTION/READ_BACK_EMPTY）。
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun renderHardwareFrames(width: Int, height: Int, blurRadiusPx: Float): Pair<IntArray, IntArray>? {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        val renderer = HardwareRenderer().apply {
            setSurface(reader.surface)
            isOpaque = false
        }
        try {
            // 源层：黑白竖条纹（高对比高频图案）
            val source = RenderNode("glass-probe-source").apply { setPosition(0, 0, width, height) }
            val stripe = width / 8
            val sourceCanvas = source.beginRecording(width, height)
            try {
                val white = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
                val black = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
                sourceCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), white)
                var x = 0
                while (x < width) {
                    sourceCanvas.drawRect(x.toFloat(), 0f, (x.toFloat() + stripe / 2f).coerceAtMost(width.toFloat()), height.toFloat(), black)
                    x += stripe
                }
            } finally {
                source.endRecording()
            }

            // 模糊层（生产同构）：renderEffect 挂自身，record 内 drawRenderNode 取样源层
            val blur = RenderNode("glass-probe-blur").apply {
                setPosition(0, 0, width, height)
                setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.DECAL))
            }
            val blurCanvas = blur.beginRecording(width, height)
            try {
                blurCanvas.drawRenderNode(source)
            } finally {
                blur.endRecording()
            }

            val sharp = grabFrame(reader, renderer, source) ?: return null
            val blurred = grabFrame(reader, renderer, blur) ?: return null
            return sharp to blurred
        } finally {
            renderer.setSurface(null)
            reader.close()
        }
    }

    /**
     * 渲染单帧并读回像素（RGBA_8888，注意 rowStride 可能大于 width×4）。
     * v1.9.4 加固：① `syncAndDraw()` 返回值检查（[isSyncSuccess]，非 0 = 未干净出帧 → null，
     * 防读回空帧/旧帧被当真）；② `acquireLatestImage()` 空读回短重试（[shouldRetryReadBack]，
     * 共 [MAX_READ_BACK_ATTEMPTS] 次尝试、间隔 [READ_BACK_RETRY_DELAY_MS]——present fence 未
     * signal 的窗口由重试吸收，单次瞬时空读回不再直接 fail-closed）。
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun grabFrame(reader: ImageReader, renderer: HardwareRenderer, node: RenderNode): IntArray? {
        renderer.setContentRoot(node)
        val request = renderer.createRenderRequest().setWaitForPresent(true)
        if (!isSyncSuccess(request.syncAndDraw())) return null
        var image: Image? = null
        var emptyAttempts = 0
        while (image == null && shouldRetryReadBack(emptyAttempts)) {
            image = reader.acquireLatestImage()
            if (image == null) {
                emptyAttempts++
                if (shouldRetryReadBack(emptyAttempts)) {
                    try {
                        Thread.sleep(READ_BACK_RETRY_DELAY_MS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return null
                    }
                }
            }
        }
        val frame = image ?: return null
        try {
            val plane = frame.planes[0]
            val rowStride = plane.rowStride
            val buffer = plane.buffer
            val width = frame.width
            val height = frame.height
            val out = IntArray(width * height)
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                for (x in 0 until width) {
                    val r = buffer.get().toInt() and 0xFF
                    val g = buffer.get().toInt() and 0xFF
                    val b = buffer.get().toInt() and 0xFF
                    buffer.get() // alpha
                    out[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return out
        } finally {
            frame.close()
        }
    }
}
