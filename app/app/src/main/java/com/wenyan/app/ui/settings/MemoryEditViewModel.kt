package com.wenyan.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 关键事件条目（time + event 两字段，序列化为 timeline JSON 数组） */
data class TimelineItemUi(
    val time: String,
    val event: String,
)

/**
 * v1.7.3 档案详情页状态（F1+F2 合并）：
 * 加载档案（全字段）+ 已记住事实列表；保存结构化字段；事实单条增删改。
 * 入口 = 设置页记忆分组档案行「编辑」图标 → 跳 MemoryEdit 页。
 */
class MemoryEditViewModel(
    private val repo: SettingsRepository,
    private val targetId: Long,
) : ViewModel() {

    var target by mutableStateOf<TargetUi?>(null)
        private set
    var facts by mutableStateOf<List<MemoryFactUi>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    // ---- 可编辑字段 ----
    var name by mutableStateOf("")
    var mbti by mutableStateOf<String?>(null)
    var score by mutableStateOf(0)
    var relationStatus by mutableStateOf<String?>(null)
    var timeline by mutableStateOf<List<TimelineItemUi>>(emptyList())

    var saving by mutableStateOf(false)
        private set

    // ---- 事实编辑弹窗状态（编辑单条；删除不二次确认） ----
    var showFactDialog by mutableStateOf(false)
        private set
    var editFactId by mutableStateOf<Long?>(null)
        private set
    var factDraft by mutableStateOf("")
        private set

    init {
        // v1.7.4：打开详情页先搬移老 note（merge 幂等）——防「先手工加事实、再首访」时老数据永不搬移；
        // 搬移插入的 facts 会经 observeFacts Flow 自动刷新展示
        viewModelScope.launch {
            repo.ensureMigrated(targetId)
        }
        viewModelScope.launch {
            repo.targets.collectLatest { list ->
                target = list.firstOrNull { it.id == targetId }
                if (target != null) loadFields()
                loading = false
            }
        }
        viewModelScope.launch {
            repo.observeFacts(targetId).collectLatest { list ->
                facts = list
                loading = false
            }
        }
    }

    private fun loadFields() {
        val t = target ?: return
        name = t.name
        mbti = t.mbti
        score = t.score ?: 0
        relationStatus = t.relationStatus
        timeline = parseTimeline(t.timeline)
    }

    private fun parseTimeline(json: String): List<TimelineItemUi> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(TimelineItemUi(time = obj.optString("time", ""), event = obj.optString("event", "")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addTimelineItem() {
        timeline = timeline + TimelineItemUi(time = "", event = "")
    }

    fun updateTimelineItem(index: Int, time: String, event: String) {
        if (index !in timeline.indices) return
        val list = timeline.toMutableList()
        list[index] = TimelineItemUi(time = time, event = event)
        timeline = list
    }

    fun deleteTimelineItem(index: Int) {
        if (index !in timeline.indices) return
        timeline = timeline.filterIndexed { i, _ -> i != index }
    }

    private fun timelineJson(): String {
        val arr = JSONArray()
        timeline.filter { it.time.isNotBlank() || it.event.isNotBlank() }.forEach {
            arr.put(JSONObject().put("time", it.time.trim()).put("event", it.event.trim()))
        }
        return arr.toString()
    }

    /** 保存结构化字段（名称/MBTI/吸引力分/关系状态/关键事件）；保存后回退 */
    fun save(onDone: () -> Unit) {
        val t = target ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            repo.updateTargetDetails(
                id = t.id,
                name = name,
                mbti = mbti,
                score = score.takeIf { it > 0 },
                relationStatus = relationStatus,
                timelineJson = timelineJson(),
            )
            saving = false
            onDone()
        }
    }

    // ---- 事实单条管理 ----

    fun openCreateFact() {
        editFactId = null
        factDraft = ""
        showFactDialog = true
    }

    fun openEditFact(fact: MemoryFactUi) {
        editFactId = fact.id
        factDraft = fact.text
        showFactDialog = true
    }

    fun dismissFactDialog() {
        showFactDialog = false
        editFactId = null
        factDraft = ""
    }

    /** 弹窗确认：新增或更新单条事实（空白输入忽略） */
    fun confirmFact(text: String) {
        val trimmed = text.trim()
        val id = editFactId
        dismissFactDialog()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (id == null) repo.addFact(targetId, trimmed) else repo.updateFact(id, trimmed)
        }
    }

    /** 删除单条事实（不二次确认，R3 决策） */
    fun deleteFact(fact: MemoryFactUi) {
        viewModelScope.launch { repo.deleteFact(fact.id) }
    }

    /** v1.9.1 临时事实转永久（清空到期时间；Flow 自动刷新） */
    fun makePermanent(fact: MemoryFactUi) {
        viewModelScope.launch { repo.makePermanent(fact.id) }
    }
}
