package com.goutoujunshi.app.container

import com.goutoujunshi.app.data.datastore.SettingsRepository as DataStoreSettings
import com.goutoujunshi.app.data.db.ProfileEntity
import com.goutoujunshi.app.data.db.TargetEntity
import com.goutoujunshi.app.data.repository.ProfileRepository
import com.goutoujunshi.app.log.AppLogger
import com.goutoujunshi.app.ui.contract.OnboardingDraft
import com.goutoujunshi.app.ui.contract.OnboardingRepository
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
            profileRepository.saveTarget(
                TargetEntity(
                    codeName = draft.targetCodeName,
                    mbti = draft.targetMbti,
                    score = draft.targetScore,
                    relationStatus = draft.relationStatus,
                    timeline = buildTimeline(draft),
                    createdAt = now,
                )
            )
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
