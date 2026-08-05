# -*- coding: utf-8 -*-
"""精确定位水印 bbox：亮度阈值法（文字比浅灰背景暗）"""
from PIL import Image

SRC = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\Premium_Android_app_icon_desig_2026-08-04T04-39-17.png"
img = Image.open(SRC).convert("RGB")
W, H = img.size

# 扫描右下 600x260
x0, y0 = W - 600, H - 260
pts = []
for y in range(y0, H):
    for x in range(x0, W):
        r, g, b = img.getpixel((x, y))
        if (r + g + b) / 3 < 190:  # 暗于背景
            pts.append((x, y))
if pts:
    minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
    print(f"watermark bbox: x={minx}..{maxx} y={miny}..{maxy}  count={len(pts)}")
    # 检查 bbox 内最暗像素颜色（判断文字颜色/透明度）
    darkest = min(pts, key=lambda p: sum(img.getpixel(p)))
    print(f"darkest pixel ({darkest[0]},{darkest[1]}): {img.getpixel(darkest)}")
else:
    print("no dark pixels found")
