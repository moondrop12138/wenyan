# -*- coding: utf-8 -*-
"""浅色版「温言」图标落地：修水印 → 抠气泡 → 生成前景/背景 → 部署五档 → 四联预览。
背景取自原稿（暖米色微渐变），前景为抠出的暖橙气泡（居中，宽占安全区 86%）。
"""
from PIL import Image, ImageDraw, ImageFilter
import cv2
import numpy as np
import math
import os

SRC = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/Premium_minimal_Android_app_ic_2026-08-04T08-46-09.png'
OUT = r'C:/Users/Khalil/WorkBuddy/apk2/outputs'
RES = r'C:/Users/Khalil/WorkBuddy/apk2/app/app/src/main/res'

img = cv2.imread(SRC)
h, w = img.shape[:2]

# ---------- 1) 修水印（右下角） ----------
# 用水印左侧相邻背景估计背景色
bg_col = img[880:1000, 660:720].mean(axis=(0, 1))
# 水印区域：右下角大面积
wm_y0, wm_y1, wm_x0, wm_x1 = 830, 1024, 730, 1024
roi = img[wm_y0:wm_y1, wm_x0:wm_x1].astype(int)
diff = np.abs(roi - bg_col.astype(int)).max(axis=2)
mask = (diff > 25).astype(np.uint8) * 255
# 膨胀 mask 覆盖文字边缘
mask = cv2.dilate(mask, np.ones((3, 3), np.uint8), iterations=1)
roi_inp = cv2.inpaint(img[wm_y0:wm_y1, wm_x0:wm_x1], mask, 5, cv2.INPAINT_TELEA)
img[wm_y0:wm_y1, wm_x0:wm_x1] = roi_inp
print('watermark pixels fixed:', int(mask.sum() / 255))

# ---------- 2) 抠气泡 ----------
hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
body = cv2.inRange(hsv, np.array([5, 55, 100]), np.array([45, 255, 255]))
k = np.ones((5, 5), np.uint8)
body = cv2.morphologyEx(body, cv2.MORPH_CLOSE, k, iterations=2)
body = cv2.morphologyEx(body, cv2.MORPH_OPEN, k, iterations=1)
cnts, _ = cv2.findContours(body, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
cnt = max(cnts, key=cv2.contourArea)
x, y, bw, bh = cv2.boundingRect(cnt)
print('bubble bbox:', x, y, bw, bh)

# 羽化 alpha
alpha = cv2.GaussianBlur(body, (7, 7), 0).astype(np.float32) / 255.0

rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
pad = 40
x0, y0 = max(0, x - pad), max(0, y - pad)
x1, y1 = min(w, x + bw + pad), min(h, y + bh + pad)
roi_rgb = rgb[y0:y1, x0:x1]
roi_alpha = (alpha[y0:y1, x0:x1] * 255).astype(np.uint8)
bubble = Image.fromarray(roi_rgb).convert('RGBA')
bubble.putalpha(Image.fromarray(roi_alpha))

# ---------- 3) 前景：108dp 画布，气泡宽占安全区 86%，居中 ----------
FG = 432
target_w = FG * (66 / 108) * 0.86
scale = target_w / bw
nw, nh = int(round((x1 - x0) * scale)), int(round((y1 - y0) * scale))
bubble_s = bubble.resize((nw, nh), Image.LANCZOS)
fg = Image.new('RGBA', (FG, FG), (0, 0, 0, 0))
rel_cx = (x + bw / 2 - x0) * scale
rel_cy = (y + bh / 2 - y0) * scale
ox = int(FG * 54 / 108 - rel_cx)
oy = int(FG * 50 / 108 - rel_cy)
fg.paste(bubble_s, (ox, oy), bubble_s)
fg.save(OUT + r'/ic_foreground.png')
print('foreground saved at', ox, oy)

# ---------- 4) 背景：原稿暖米色微渐变（顶略亮） ----------
def hex2rgb(hx):
    hx = hx.lstrip('#')
    return tuple(int(hx[i:i+2], 16) for i in (0, 2, 4))
def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

bg = Image.new('RGB', (FG, FG))
d = ImageDraw.Draw(bg)
top = hex2rgb('#EBE3CF')   # 顶略亮（AI 原稿米色提亮档）
bot = hex2rgb('#E3DBC6')   # 底略沉
for yy in range(FG):
    t = yy / (FG - 1)
    d.line([(0, yy), (FG, yy)], fill=lerp(top, bot, t))
bg = bg.convert('RGBA')
bg.save(OUT + r'/ic_background.png')
print('background saved')

# ---------- 5) 部署五档 ----------
DENSITIES = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
for name, px in DENSITIES.items():
    dd = os.path.join(RES, f'mipmap-{name}')
    os.makedirs(dd, exist_ok=True)
    fg.resize((px, px), Image.LANCZOS).save(os.path.join(dd, 'ic_launcher_foreground.png'))
    bg.resize((px, px), Image.LANCZOS).save(os.path.join(dd, 'ic_launcher_background.png'))
print('deployed 5 densities')

# ---------- 6) 四联预览 ----------
icon = Image.alpha_composite(bg, fg)
def circle_mask(size):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size, size], fill=255)
    return m
def squircle_mask(size, ratio=0.42):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size, size], radius=size * ratio, fill=255)
    return m
def on_tile(im, tile=280, bg_col2=(250, 245, 239)):
    out = Image.new('RGB', (tile, tile), bg_col2)
    im2 = im.resize((int(tile * 0.86), int(tile * 0.86)), Image.LANCZOS)
    off = (tile - im2.width) // 2
    out.paste(im2, (off, off), im2)
    return out
full = Image.new('RGB', (4 * 300 + 40, 340), (255, 255, 255))
v_circle = icon.copy(); v_circle.putalpha(circle_mask(FG))
v_sq = icon.copy(); v_sq.putalpha(squircle_mask(FG))
for i, (name, im) in enumerate([
    ('square', icon), ('circle', v_circle), ('squircle', v_sq),
    ('small', icon.resize((200, 200), Image.LANCZOS)),
]):
    full.paste(on_tile(im), (20 + i * 300, 20))
full.save(OUT + r'/wenyan-icon-v2-final-preview.png')
print('preview saved')
