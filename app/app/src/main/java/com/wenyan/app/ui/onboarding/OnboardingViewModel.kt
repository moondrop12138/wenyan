package com.wenyan.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.OnboardingDraft
import com.wenyan.app.ui.contract.OnboardingRepository
import kotlinx.coroutines.launch

/**
 * 首启问卷状态（AC-01/02/03）：4 屏向导 + 跳过二次确认 + 提交建档。
 * 每屏"下一步"始终可点（空值允许，符合 SKILL"不知道可留空"）。
 */
class OnboardingViewModel(private val repo: OnboardingRepository) : ViewModel() {

    var currentStep by mutableStateOf(0)
        private set

    var draft by mutableStateOf(OnboardingDraft())
        private set

    var submitting by mutableStateOf(false)
        private set

    var showSkipDialog by mutableStateOf(false)
        private set

    /** 提交或跳过完成后置 true（AC-02/03 → Screen 观察并触发 onDone 导航） */
    var completed by mutableStateOf(false)
        private set

    val totalSteps = 4

    fun updateDraft(newDraft: OnboardingDraft) {
        draft = newDraft
    }

    fun next() {
        if (currentStep < totalSteps - 1) {
            currentStep++
        } else {
            submit()
        }
    }

    fun back() {
        if (currentStep > 0) currentStep--
    }

    fun requestSkip() {
        showSkipDialog = true
    }

    fun dismissSkip() {
        showSkipDialog = false
    }

    /** AC-02：二次确认后跳过，档案稍后可补录（onboardingCompleted=true → AppRoot 自动进对话） */
    fun confirmSkip() {
        showSkipDialog = false
        viewModelScope.launch {
            repo.skip()
            completed = true
        }
    }

    private fun submit() {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            repo.submit(draft)
            submitting = false
            completed = true
        }
    }
}
