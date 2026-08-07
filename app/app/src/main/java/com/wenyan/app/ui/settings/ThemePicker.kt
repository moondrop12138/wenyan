package com.wenyan.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

private data class ThemeOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

private val THEME_OPTIONS = listOf(
    ThemeOption("light", "浅色", Icons.Outlined.WbSunny),
    ThemeOption("dark", "深色", Icons.Outlined.DarkMode),
    ThemeOption("system", "跟随系统", Icons.Outlined.BrightnessAuto),
)

/**
 * 主题单选（design-pages 页面3 分组2）：图标 + label，选中 accent 高亮 + check。
 * 切换即时生效（DataStore.theme → AppViewModel 推送全局，AC-16）。
 */
@Composable
fun ThemePicker(
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        THEME_OPTIONS.forEach { option ->
            val selected = option.key == current
            Surface(
                onClick = { onSelect(option.key) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                shape = GtjShape.md,
                color = if (selected) p.accentSoft else p.surface,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Icon(option.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) p.accent else p.fgSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text(option.label, style = GtjType.Body, color = if (selected) p.accent else p.fg, modifier = Modifier.weight(1f))
                    if (selected) {
                        Icon(Icons.Outlined.Check, contentDescription = "已选择", modifier = Modifier.size(20.dp), tint = p.accent)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
