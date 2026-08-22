package com.wenyan.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.domain.MemoryConflictDetector
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

/** O2: 一对冲突事实（记忆页标红 + 用户裁决） */
data class ConflictPairUi(
    val a: MemoryFactUi,
    val b: MemoryFactUi,
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

    // ---- O2: 冲突检测与裁决 ----
    var conflictPairs by mutableStateOf<List<ConflictPairUi>>(emptyList())
        private set
    var conflictFactIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var selectedConflict by mutableStateOf<ConflictPairUi?>(null)
        private set

    /** H3 修复：表单字段只做一次库值加载（true 后流重发射不再覆盖未保存编辑） */
    private var fieldsLoaded = false

    init {
        // v1.7.4：打开详情页先搬移老 note（merge 幂等）——防「先手工加事实、再首访」时老数据永不搬移；
        // 搬移插入的 facts 会经 observeFacts Flow 自动刷新展示
        viewModelScope.launch {
            repo.ensureMigrated(targetId)
        }
        viewModelScope.launch {
            repo.targets.collectLatest { list ->
                target = list.firstOrNull { it.id == targetId }
                // H3 修复：仅首次加载用库值填充表单——原每次发射都 loadFields() 无条件覆盖
                // name/mbti/score/relationStatus/timeline，用户改名/调滑块未保存时
                // 增删一条事实 → 编辑内容丢失。
                if (!fieldsLoaded && target != null) {
                    loadFields()
                    fieldsLoaded = true
                }
                loading = false
            }
        }
        viewModelScope.launch {
            repo.observeFacts(targetId).collectLatest { list ->
                facts = list
                recomputeConflicts()
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
        timeline = parseTimeline(t.timeline).sortedBy { it.time.ifBlank { "9999" } }
    }

    /** O2: 两两启发式矛盾判定（同档案内），标红冲突对供用户裁决 */
    private fun recomputeConflicts() {
        val list = facts
        val pairs = mutableListOf<ConflictPairUi>()
        for (i in 0 until list.size) {
            for (j in i + 1 until list.size) {
                if (MemoryConflictDetector.conflicts(list[i].text, list[j].text)) {
                    pairs.add(ConflictPairUi(list[i], list[j]))
                }
            }
        }
        conflictPairs = pairs
        conflictFactIds = pairs.flatMap { listOf(it.a.id, it.b.id) }.toSet()
    }

    // ---- O2: 冲突裁决 ----

    fun openConflict(pair: ConflictPairUi) {
        selectedConflict = pair
    }

    fun dismissConflict() {
        selectedConflict = null
    }

    /** 保留 A，删除 B */
    fun resolveConflictKeepA() {
        val pair = selectedConflict ?: return
        selectedConflict = null
        viewModelScope.launch { repo.deleteFact(pair.b.id) }
    }

    /** 保留 B，删除 A */
    fun resolveConflictKeepB() {
        val pair = selectedConflict ?: return
        selectedConflict = null
        viewModelScope.launch { repo.deleteFact(pair.a.id) }
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
