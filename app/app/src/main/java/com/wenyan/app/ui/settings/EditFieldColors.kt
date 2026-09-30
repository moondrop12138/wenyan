package com.wenyan.app.ui.settings

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * F53 精简：ui.settings 包内三份逐字相同的输入框配色配方收敛为单一实现——
 * 原 MemoryDialogs.memoryFieldColors() / MemoryEditScreen.editFieldColors()（注释自称
 * 「对齐 ProviderEditScreen.EditField」）/ ProviderEditScreen.EditField 内联，实为三份拷贝
 * 需人工保持同步。配方：accent 聚焦边 + surface 底 + accent 光标。
 */
@Composable
internal fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalGtjColors.current.accent,
    unfocusedBorderColor = LocalGtjColors.current.border,
    focusedContainerColor = LocalGtjColors.current.surface,
    unfocusedContainerColor = LocalGtjColors.current.surface,
    cursorColor = LocalGtjColors.current.accent,
)
