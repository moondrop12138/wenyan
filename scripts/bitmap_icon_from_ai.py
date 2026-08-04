# -*- coding: utf-8 -*-
"""把 AI 原稿做成 Android 自适应图标（前景+背景分离方案）。
流程：
1) 读 C3 原稿，修掉右下角"AI生成"水印
2) 用 HSV 阈值抠出发光气泡 mask
3) 前景 = 气泡抠图（带羽化）缩放到收进 66dp 安全区，置于 108dp 透明画布
4) 背景 = 深夜暖棕径向渐变（满幅 108dp）
5) 导出前景/背景 PNG（432px）+ 四联遮罩预览
"""
from PIL import Image, ImageDraw, ImageFilter
import cv2
import numpy as np
import math

SRC = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/Premium_Android_app_icon_desig_2026-08-04T04-40-23.png'
OUT_DIR = r'C:/Users/Khalil/WorkBuddy/apk2/outputs'

# ---------- 1) 读图 + 修水印 ----------
img_cv = cv2.imread(SRC)
h, w = img_cv.shape[:2]
# 右下角水印区域（约 x>850,y>890）：用周围深色均值覆盖
img_cv[885:1024, 845:1024] = (16, 12, 9)

# ---------- 2) 抠气泡 mask ----------
hsv = cv2.cvtColor(img_cv, cv2.COLOR_BGR2HSV)
mask = cv2.inRange(hsv, np.array([5, 60, 100]), np.array([45, 255, 255]))
k = np.ones((5, 5), np.uint8)
mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, k, iterations=2)
mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, k, iterations=1)
cnts, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
cnt = max(cnts, key=cv2.contourArea)
full_mask = np.zeros_like(mask)
cv2.drawContours(full_mask, [cnt], -1, 255, -1)
# 羽化边缘
full_mask = cv2.GaussianBlur(full_mask, (9, 9), 0)

# ---------- 3) 提取气泡区域（带 mask） ----------
x, y, bw, bh = cv2.boundingRect(cnt)
pad = 30
x0, y0 = max(0, x - pad), max(0, y - pad)
x1, y1 = min(w, x + bw + pad), min(h, y + bh + pad)
roi_bgr = img_cv[y0:y1, x0:x1]
roi_mask = full_mask[y0:y1, x0:x1]
roi_rgb = cv2.cvtColor(roi_bgr, cv2.COLOR_BGR2RGB)
bubble = Image.fromarray(roi_rgb).convert('RGBA')
bubble.putalpha(Image.fromarray(roi_mask))

# ---------- 4) 前景：气泡缩放到收进安全区 ----------
FG = 432          # 108dp * 4
SAFE = 66 / 108   # 安全区比例（直径）
target_w = FG * SAFE * 0.98   # 气泡宽占安全区 98%
scale = target_w / bubble.width
nw, nh = int(bubble.width * scale), int(bubble.height * scale)
bubble_s = bubble.resize((nw, nh), Image.LANCZOS)
fg = Image.new('RGBA', (FG, FG), (0, 0, 0, 0))
# 居中，微偏上（气泡在 50/108 高度）
ox = (FG - nw) // 2
oy = int(FG * 0.50 - nh / 2)
fg.paste(bubble_s, (ox, oy), bubble_s)
fg.save(OUT_DIR + r'/ic_foreground.png')
print('foreground saved, bubble', nw, 'x', nh, 'at', ox, oy)

# ---------- 5) 背景：深夜暖棕径向渐变 ----------
def hex2rgb(hx):
    hx = hx.lstrip('#')
    return tuple(int(hx[i:i+2], 16) for i in (0, 2, 4))
def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

def radial_grad(size, stops, cx_ratio=0.5, cy_ratio=0.46, radius_ratio=0.75):
    wpx, hpx = size
    cx, cy = wpx * cx_ratio, hpx * cy_ratio
    r_max = wpx * radius_ratio
    im = Image.new('RGB', (wpx, hpx))
    px = im.load()
    cols = [hex2rgb(s) for s in stops]
    n = len(cols) - 1
    for yy in range(hpx):
        for xx in range(wpx):
            d = math.hypot(xx - cx, yy - cy) / r_max
            t = min(d, 1.0)
            seg = min(int(t * n), n - 1)
            lt = t * n - seg
            px[xx, yy] = lerp(cols[seg], cols[seg + 1], lt)
    return im

bg = radial_grad((FG, FG), ['#2A1A0F', '#211510', '#17100A']).convert('RGBA')
bg.save(OUT_DIR + r'/ic_background.png')
print('background saved')

# ---------- 6) 合成 + 四联遮罩预览 ----------
icon = Image.alpha_composite(bg, fg)

def circle_mask(size):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size, size], fill=255)
    return m
def squircle_mask(size, ratio=0.42):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size, size], radius=size * ratio, fill=255)
    return m
def on_tile(im, tile=280, bg_col=(250, 245, 239)):
    out = Image.new('RGB', (tile, tile), bg_col)
    im2 = im.resize((int(tile * 0.86), int(tile * 0.86)), Image.LANCZOS)
    off = (tile - im2.width) // 2
    out.paste(im2, (off, off), im2)
    return out

full = Image.new('RGB', (4 * 300 + 40, 340), (255, 255, 255))
v_circle = icon.copy(); v_circle.putalpha(circle_mask(FG))
v_sq = icon.copy(); v_sq.putalpha(squircle_mask(FG))
variants = [
    ('square', icon),
    ('circle', v_circle),
    ('squircle', v_sq),
    ('small', icon.resize((200, 200), Image.LANCZOS)),
]
for i, (name, im) in enumerate(variants):
    full.paste(on_tile(im), (20 + i * 300, 20))
full.save(OUT_DIR + r'/wenyan-icon-bitmap-preview.png')
print('preview saved')
