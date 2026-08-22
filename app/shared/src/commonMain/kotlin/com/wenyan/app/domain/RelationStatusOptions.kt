package com.wenyan.app.domain

/**
 * L31 修复：关系状态选项集此前在 OnboardingSteps 与 MemoryEditScreen 各写一份且内容不一致
 * （「暧昧」vs「暧昧中」等）——引导页选的状态在记忆编辑页对不上，反之亦然。
 * 现抽为共享常量，两端引用同一份；顺序即 UI 展示顺序。
 */
val RELATION_STATUS_OPTIONS: List<String> = listOf(
    "暗恋", "暧昧", "追求中", "热恋", "约会", "确定关系", "冷淡", "冲突", "异地", "前任", "已分手", "单恋", "同事", "其他",
)
