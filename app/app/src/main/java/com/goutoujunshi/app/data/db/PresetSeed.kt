package com.goutoujunshi.app.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 预设提供商种子数据（architecture.md §4.1 模型名单已核实）
 * 首次启动注入：DeepSeek / 智谱 / OpenAI / 通义 / Kimi
 * 预设的 API Key 一律为空（用户后补），仅提供 baseUrl 与模型名单
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
            ),
        ),
        Preset(
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            models = listOf(
                ModelDef("glm-4-plus", supportsVision = false, isDefault = true),
                ModelDef("glm-4v", supportsVision = true),
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
                ModelDef("qwen-plus", supportsVision = false, isDefault = true),
                ModelDef("qwen-vl-plus", supportsVision = true),
            ),
        ),
        Preset(
            name = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            models = listOf(
                ModelDef("moonshot-v1-32k", supportsVision = false, isDefault = true),
            ),
        ),
    )

    /**
     * 幂等注入：已存在同名提供商则跳过
     */
    suspend fun seedIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val providerDao = db.providerDao()
        val modelDao = db.modelDao()
        if (providerDao.listAll().isNotEmpty()) return@withContext

        presets.forEachIndexed { order, preset ->
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
