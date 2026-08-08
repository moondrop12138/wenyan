package com.wenyan.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.GhostButton
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.PrimaryButton
import com.wenyan.app.ui.components.glass.GlowBackground
import com.wenyan.app.ui.components.glass.rememberGlowState
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.rememberReducedMotion

/**
 * 首启问卷（/onboarding，AC-01/02，design-pages 页面2）：
 * 4 屏向导 + 顶部进度条（200ms 平滑）+ 跳过二次确认 + 底部"下一步"。
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val vm: OnboardingViewModel = rememberViewModel("OnboardingViewModel") {
        OnboardingViewModel(container.onboardingRepository)
    }
    val p = LocalGtjColors.current
    // 系统"移除动画"时进度条 0ms 直达（motion.reducedMotion）
    val reducedMotion = rememberReducedMotion()
    // AC-02/03：提交或跳过完成后立即导航到对话页（与 AppRoot 流监听双保险）
    LaunchedEffect(vm.completed) {
        if (vm.completed) onDone()
    }
    val progress by animateFloatAsState(
        targetValue = (vm.currentStep + 1) / vm.totalSteps.toFloat(),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 200),
        label = "progress",
    )

    // v1.8.0：液态玻璃 2.0 · 光斑状态共享
    val glowState = rememberGlowState()

    // v1.7.1：根 Box 加主题背景（防系统深色下 windowBackground 透出导致浅色模式变暗底）
    Box(Modifier.fillMaxSize().background(p.bg)) {
        GlowBackground(onGlowPositionsChanged = glowState::update)
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 顶部行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            ) {
                if (vm.currentStep > 0) {
                    GtjIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回上一屏",
                        onClick = vm::back,
                    )
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.weight(1f))
                GhostButton(text = "跳过，直接开聊", onClick = vm::requestSkip, minHeight = 48.dp)
            }
            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = p.accent,
                trackColor = p.borderSoft,
            )
            // 内容区
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (vm.currentStep) {
                        0 -> StepOneMe(vm.draft, vm::updateDraft)
                        1 -> StepTwoTarget(vm.draft, vm::updateDraft)
                        2 -> StepThreeHistory(vm.draft, vm::updateDraft)
                        else -> StepFourGoal(vm.draft, vm::updateDraft)
                    }
                }
            }
            // 底部按钮
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton(
                    text = if (vm.currentStep == vm.totalSteps - 1) "完成" else "下一步",
                    onClick = vm::next,
                    loading = vm.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    } // Box（GlowBackground + Scaffold）

    if (vm.showSkipDialog) {
        SkipDialog(
            onContinue = vm::dismissSkip,
            onConfirmSkip = vm::confirmSkip,
        )
    }
}
