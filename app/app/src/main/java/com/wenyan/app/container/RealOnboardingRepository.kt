package com.wenyan.app.container

import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.log.AppLogger
import com.wenyan.app.ui.contract.OnboardingDraft
import com.wenyan.app.ui.contract.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 问卷 Repository 真实实现（AC-01/02/03）
 * 提交：档案落 Room（profile/target），onboardingCompleted=true；
 * 跳过：仅置 onboardingCompleted=true（档案稍后可补录）。
 */
class RealOnboardingRepository(
    private val dataStore: DataStoreSettings,
    private val profileRepository: ProfileRepository,
) : OnboardingRepository {

    override val onboardingCompleted: Flow<Boolean> = dataStore.onboardingCompleted

    override suspend fun submit(draft: OnboardingDraft) {
        val now = System.currentTimeMillis()
        profileRepository.saveProfile(
            ProfileEntity(
                mbti = draft.meMbti,
                score = draft.meScore,
                strengths = draft.strengths.ifBlank { null },
                weaknesses = draft.weaknesses.ifBlank { null },
                createdAt = now,
            )
        )
        if (draft.targetCodeName.isNotBlank()) {
            val targetId = profileRepository.saveTarget(
                TargetEntity(
                    codeName = draft.targetCodeName,
                    mbti = draft.targetMbti,
                    score = draft.targetScore,
                    relationStatus = draft.relationStatus,
                    timeline = buildTimeline(draft),
                    createdAt = now,
                )
            )
            // v1.7.2：首个档案自动激活（当前无激活档案时，新会话默认归属它）
            if (dataStore.getActiveTargetId() == null) {
                dataStore.setActiveTargetId(targetId)
            }
        }
        dataStore.setOnboardingCompleted(true)
        AppLogger.i("onboarding_completed", "mode" to "submit")
    }

    override suspend fun skip() {
        dataStore.setOnboardingCompleted(true)
        AppLogger.i("onboarding_completed", "mode" to "skip")
    }

    private fun buildTimeline(draft: OnboardingDraft): String {
        val arr = JSONArray()
        if (!draft.meetWay.isNullOrBlank()) {
            arr.put(JSONObject().put("time", "认识").put("event", draft.meetWay))
        }
        if (!draft.duration.isNullOrBlank()) {
            arr.put(JSONObject().put("time", "时长").put("event", draft.duration))
        }
        if (draft.keyEvents.isNotBlank()) {
            arr.put(JSONObject().put("time", "关键事件").put("event", draft.keyEvents))
        }
        return arr.toString()
    }
}
