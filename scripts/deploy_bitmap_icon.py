# -*- coding: utf-8 -*-
"""把 AI 原稿气泡焰落地为 Android 自适应图标位图资源。
输入：outputs/ic_foreground.png(648) + outputs/ic_background.png(648)（由 bitmap_icon_from_ai_v2.py v3 生成）
输出：app/app/src/main/res/mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png + ic_launcher_background.png
密度系数（相对 108dp 基准）：mdpi=0.75, hdpi=1.125, xhdpi=1.5, xxhdpi=2.25, xxxhdpi=3
v3 超采样：全部档位 x1.5（xxxhdpi 648px = 108dp*6），高密度屏显示更锐利。
"""
from PIL import Image
import os

OUT = r'C:/Users/Khalil/WorkBuddy/apk2/outputs'
RES = r'C:/Users/Khalil/WorkBuddy/apk2/app/app/src/main/res'

fg = Image.open(OUT + r'/ic_foreground.png').convert('RGBA')
bg = Image.open(OUT + r'/ic_background.png').convert('RGBA')

# v3：648 主画布按 108dp 基准换算，再 x1.5 超采样
DENSITIES = {
    'mdpi': 162,
    'hdpi': 243,
    'xhdpi': 324,
    'xxhdpi': 486,
    'xxxhdpi': 648,
}

for name, px in DENSITIES.items():
    d = os.path.join(RES, f'mipmap-{name}')
    os.makedirs(d, exist_ok=True)
    fg.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher_foreground.png'))
    bg.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher_background.png'))
    print(f'{name}: {px}px')

print('done')
