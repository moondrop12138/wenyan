# -*- coding: utf-8 -*-
"""AI 原稿位图版 v2：保留发光光晕。
v1 的坑：HSV 严格阈值(V>=100)只抠出气泡主体，把气泡周围柔和的发光扩散区切掉了，
导致"没有微微发光的感觉"。
v2：双阈值——
  mask_body (严格, V>=100)：气泡主体，alpha=255
  mask_glow (宽松, V>=55 且 S>=28)：发光区（主体+光晕），alpha 羽化渐变
前景 = 原稿像素颜色 + (主体 255 / 光晕渐变压低) alpha。
主体宽度占安全区 ~82%（光晕自然外溢，前景画布留足空间）。
"""
from PIL import Image, ImageDraw, ImageFilter
import cv2
import numpy as np
import math

SRC = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/Premium_Android_app_icon_desig_2026-08-04T04-40-23.png'
OUT_DIR = r'C:/Users/Khalil/WorkBuddy/apk2/outputs'

img_cv = cv2.imread(SRC)
h, w = img_cv.shape[:2]
img_cv[885:1024, 845:1024] = (16, 12, 9)  # 修水印

hsv = cv2.cvtColor(img_cv, cv2.COLOR_BGR2HSV)
Hc, Sc, Vc = cv2.split(hsv)

# 主体 mask：高亮暖橙
body = cv2.inRange(hsv, np.array([5, 60, 100]), np.array([45, 255, 255]))
# 发光区 mask：更宽松（光晕 V 低至 ~55）
glow = cv2.inRange(hsv, np.array([5, 18, 30]), np.array([50, 255, 255]))

k = np.ones((5, 5), np.uint8)
body = cv2.morphologyEx(body, cv2.MORPH_CLOSE, k, iterations=2)
body = cv2.morphologyEx(body, cv2.MORPH_OPEN, k, iterations=1)
glow = cv2.morphologyEx(glow, cv2.MORPH_CLOSE, k, iterations=1)
# 光晕 = glow 区域里去掉 body，平滑过渡
glow_ring = cv2.subtract(glow, body)
glow_ring = cv2.GaussianBlur(glow_ring, (25, 25), 0)

# 最终 alpha：主体 255，光晕按亮度渐变
body_f = cv2.GaussianBlur(body, (5, 5), 0).astype(np.float32)
glow_f = glow_ring.astype(np.float32) / 255.0
alpha = np.clip(np.maximum(body_f / 255.0 * 1.0, glow_f * 0.9), 0, 1)

# 光晕区域像素颜色：直接取原稿（自带明暗），不用额外加深
rgb = cv2.cvtColor(img_cv, cv2.COLOR_BGR2RGB)

# 抠出整个发光区（含光晕）
allmask = (alpha * 255).astype(np.uint8)
cnts, _ = cv2.findContours((glow > 0).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
cnt = max(cnts, key=cv2.contourArea)
x, y, bw, bh = cv2.boundingRect(cnt)
pad = 40
x0, y0 = max(0, x - pad), max(0, y - pad)
x1, y1 = min(w, x + bw + pad), min(h, y + bh + pad)
roi_rgb = rgb[y0:y1, x0:x1]
roi_alpha = allmask[y0:y1, x0:x1]

bubble = Image.fromarray(roi_rgb).convert('RGBA')
bubble.putalpha(Image.fromarray(roi_alpha))
print('glow region bbox:', x0, y0, x1, y1, 'size', x1 - x0, y1 - y0)

# 主体 bbox（用于对齐安全区）
cnts2, _ = cv2.findContours((body > 0).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
cnt2 = max(cnts2, key=cv2.contourArea)
bx, by, bbw, bbh = cv2.boundingRect(cnt2)
print('body bbox:', bx, by, bbw, bbh)

# 缩放：主体宽 -> 安全区 82%（66dp * 0.82 ≈ 54dp @432px = 217px）
FG = 432
target_body_w = FG * (66 / 108) * 0.82
scale = target_body_w / bbw
nw, nh = int(round((x1 - x0) * scale)), int(round((y1 - y0) * scale))
bubble_s = bubble.resize((nw, nh), Image.LANCZOS)

fg = Image.new('RGBA', (FG, FG), (0, 0, 0, 0))
# 对齐：整个发光区（含火焰/光晕）中心 -> 画布中心 (54, 50)
glow_cx = (x0 + (x1 - x0) / 2) * 0 + (x0 + (x1 - x0) / 2)  # 发光区像素中心（原稿坐标）
glow_cy = (y0 + (y1 - y0) / 2)
# 用抠图内坐标
rel_cx = (glow_cx - x0) * scale
rel_cy = (glow_cy - y0) * scale
ox = int(FG * 54 / 108 - rel_cx)
oy = int(FG * 50 / 108 - rel_cy)
fg.paste(bubble_s, (ox, oy), bubble_s)
fg.save(OUT_DIR + r'/ic_foreground.png')
print('foreground v2 saved at', ox, oy)

# 背景（沿用深夜暖棕径向渐变）
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

# 四联预览
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
for i, (name, im) in enumerate([
    ('square', icon), ('circle', v_circle), ('squircle', v_sq),
    ('small', icon.resize((200, 200), Image.LANCZOS)),
]):
    full.paste(on_tile(im), (20 + i * 300, 20))
full.save(OUT_DIR + r'/wenyan-icon-bitmap-preview.png')
print('preview v2 saved')
