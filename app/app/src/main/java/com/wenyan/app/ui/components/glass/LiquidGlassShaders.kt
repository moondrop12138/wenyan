package com.wenyan.app.ui.components.glass

import android.os.Build

/**
 * RuntimeShader 能力探测。
 *
 * v1.9.4 扁平化：删除边缘透镜 LENS_EDGE_SHADER 与 createLensEdgeShader（web Mica 没有该层，
 * 「渐变填充 + backdrop 模糊 + 1px 描边 + 顶部内高光 + 柔和投影」五层见 LiquidGlass.kt）；
 * 同步删除无消费方的 isRenderEffectSupported。此前 v1.8.1 B4 已删光斑交互、v1.9.4 已删
 * createRefractionShader/createSpecularShader（REFRACTION_SHADER/SPECULAR_SHADER 死路径）。
 *
 * 现存唯一 AGSL RuntimeShader 是 FluidBackground 的流光 shader，其 API 33 守卫统一走
 * [isRuntimeShaderSupported]。
 */
object LiquidGlassShaders {

    /**
     * 检查当前设备是否支持 RuntimeShader（API 33+）
     */
    fun isRuntimeShaderSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
