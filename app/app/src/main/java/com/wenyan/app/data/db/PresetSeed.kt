package com.wenyan.app.data.db

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 预设提供商种子数据（architecture.md §4.1 模型名单已核实）
 * 首次启动注入：DeepSeek / 智谱 / OpenAI / 通义 / Kimi / MiniMax / MiMo
 * 预设的 API Key 一律为空（用户后补），仅提供 baseUrl 与模型名单
 * v1.3.1 名单更新（2026-08-04 各官方平台核实）：
 * - DeepSeek 增补 deepseek-v4-flash（2026-07-31 正式版公测）
 * - 智谱换 glm-5.2 旗舰（1M 上下文）/ glm-5-turbo / glm-5v-turbo（视觉）
 * - 通义换 qwen3.8-max（2026-08-03 发布，原生视觉语言）/ qwen3.7-plus（视觉）
 * - Kimi 换 kimi-k3（原生视觉理解）
 * - 新增 MiniMax（MiniMax-M3 原生多模态 + MiniMax-M2.7）、MiMo 小米（mimo-v2.5-pro + mimo-v2.5 全模态）
 */
object PresetSeed {

    data class Preset(
        val name: String,
        val baseUrl: String,
        val models: List<ModelDef>,
    )

    data class ModelDef(
        val name: String,
        val supportsVision: Boolean,
        val isDefault: Boolean = false,
    )

    val presets: List<Preset> = listOf(
        Preset(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            models = listOf(
                ModelDef("deepseek-v4-pro", supportsVision = false, isDefault = true),
                ModelDef("deepseek-v4-flash", supportsVision = false),
            ),
        ),
        Preset(
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            models = listOf(
                ModelDef("glm-5.2", supportsVision = false, isDefault = true),
                ModelDef("GLM-4.6V-Flash", supportsVision = true), // v1.6.3 新增：免费视觉模型
                ModelDef("glm-5-turbo", supportsVision = false),
                ModelDef("glm-5v-turbo", supportsVision = true),
            ),
        ),
        Preset(
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            models = listOf(
                ModelDef("gpt-5.6-terra", supportsVision = true, isDefault = true),
                ModelDef("gpt-5.6-sol", supportsVision = true),
                ModelDef("gpt-5.6-luna", supportsVision = true),
            ),
        ),
        Preset(
            name = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            models = listOf(
                ModelDef("qwen3.8-max", supportsVision = true, isDefault = true),
                ModelDef("qwen3.7-plus", supportsVision = true),
            ),
        ),
        Preset(
            name = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            models = listOf(
                ModelDef("kimi-k3", supportsVision = true, isDefault = true),
            ),
        ),
        Preset(
            name = "MiniMax",
            baseUrl = "https://api.minimaxi.com/v1",
            models = listOf(
                ModelDef("MiniMax-M3", supportsVision = true, isDefault = true),
                ModelDef("MiniMax-M2.7", supportsVision = false),
            ),
        ),
        Preset(
            name = "MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            models = listOf(
                ModelDef("mimo-v2.5-pro", supportsVision = false, isDefault = true),
                ModelDef("mimo-v2.5", supportsVision = true),
            ),
        ),
    )

    /**
     * 幂等注入。
     * L23 修复：原判据是「存在任意 provider」（首次启动写一半被杀 → 下次整体跳过 →
     * 预设永久缺失），且无事务。现改为：逐预设按名称判重（缺哪个补哪个）+ withTransaction
     * 原子化，写一半被杀后下次启动自动补齐剩余预设。
     */
    suspend fun seedIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val providerDao = db.providerDao()
            val modelDao = db.modelDao()
            presets.forEachIndexed { order, preset ->
                // L23: 已存在同名提供商（含用户自建同名）→ 视为已注入，仅跳过该预设
                //（注意：此处必须 return@forEachIndexed——return@withTransaction 会中断
                // 整个事务块，导致后续未注入的预设被一并跳过）
                if (providerDao.getByName(preset.name) != null) return@forEachIndexed
                val providerId = providerDao.insert(
                    ProviderEntity(
                        name = preset.name,
                        baseUrl = preset.baseUrl,
                        isPreset = true,
                        sortOrder = order,
                    )
                )
                preset.models.forEachIndexed { mOrder, model ->
                    modelDao.insert(
                        ModelEntity(
                            providerId = providerId,
                            name = model.name,
                            supportsVision = model.supportsVision,
                            isDefault = model.isDefault,
                            sortOrder = mOrder,
                        )
                    )
                }
            }
        }
    }
}
