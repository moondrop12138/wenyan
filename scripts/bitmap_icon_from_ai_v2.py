# -*- coding: utf-8 -*-
"""AI 原稿位图版 v3：修复不居中 + 模糊。
v2 的坑：
  1. 对齐用"发光区 ROI 几何中心"→ 但 ROI 内内容分布不对称（光晕明暗不均、火焰偏右），
     实际可见图案偏右上（实测 bbox 中心偏右 2dp、偏上 7dp）。
  2. 发光区整体约 75% 画布宽，远超自适应图标 66dp 安全区（61.1%）→ launcher 二次缩放导致糊。
v3 修复：
  1. 粘贴后按"内容 bbox（alpha>10）中心"二次对齐到画布正中心 (54,54)。
  2. 整体缩放使发光区（含光晕）宽 ≤ 66dp 安全区（98% 余量），launcher 不再裁/缩。
  3. 主画布 432 → 648 超采样（1.5x），羽化收敛，输出更清晰。
"""
from PIL import Image, ImageDraw, ImageFilter
import cv2
import numpy as np
import math

SRC = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/Premium_Android_app_icon_desig_2026-08-04T04-40-23.png'
OUT_DIR = r'C:/Users/Khalil/WorkBuddy/apk2/outputs'

# 超采样主画布：108dp * 6x = 648px（xxxhdpi 用满，其余密度按比例）
FG = 648

img_cv = cv2.imread(SRC)
h, w = img_cv.shape[:2]
img_cv[885:1024, 845:1024] = (16, 12, 9)  # 修水印

hsv = cv2.cvtColor(img_cv, cv2.COLOR_BGR2HSV)

# 主体 mask：高亮暖橙
body = cv2.inRange(hsv, np.array([5, 60, 100]), np.array([45, 255, 255]))
# 发光区 mask：更宽松（光晕 V 低至 ~30）
glow = cv2.inRange(hsv, np.array([5, 18, 30]), np.array([50, 255, 255]))

k = np.ones((5, 5), np.uint8)
body = cv2.morphologyEx(body, cv2.MORPH_CLOSE, k, iterations=2)
body = cv2.morphologyEx(body, cv2.MORPH_OPEN, k, iterations=1)
glow = cv2.morphologyEx(glow, cv2.MORPH_CLOSE, k, iterations=1)
# 光晕 = glow 区域里去掉 body，平滑过渡（v3 羽化收敛 25→17，边缘更利落）
glow_ring = cv2.subtract(glow, body)
glow_ring = cv2.GaussianBlur(glow_ring, (17, 17), 0)

# 最终 alpha：主体 255，光晕按亮度渐变
body_f = cv2.GaussianBlur(body, (5, 5), 0).astype(np.float32)
glow_f = glow_ring.astype(np.float32) / 255.0
alpha = np.clip(np.maximum(body_f / 255.0 * 1.0, glow_f * 0.9), 0, 1)

# 光晕区域像素颜色：直接取原稿（自带明暗）
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

# v3：整体缩放使发光区（含光晕）收进 66dp 安全区（98% 余量），launcher 不再裁/缩放
target_glow_w = FG * (66 / 108) * 0.98
scale = target_glow_w / (x1 - x0)
nw, nh = int(round((x1 - x0) * scale)), int(round((y1 - y0) * scale))
bubble_s = bubble.resize((nw, nh), Image.LANCZOS)
print('scale:', round(scale, 3), 'glow size after:', nw, nh)

fg = Image.new('RGBA', (FG, FG), (0, 0, 0, 0))
# 第一轮：发光区 ROI 中心对齐画布中心（近似）
ox = FG // 2 - nw // 2
oy = FG // 2 - nh // 2
fg.paste(bubble_s, (ox, oy), bubble_s)


def content_bbox(im, thr=10):
    """可见内容（alpha>thr）的最小包围盒"""
    a = np.array(im)[:, :, 3]
    ys, xs = np.where(a > thr)
    if len(xs) == 0:
        return None
    return (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))


# v3 二次对齐：以内容 bbox 中心对齐画布正中心 (54,54)，修正 ROI 内分布不对称导致的视觉偏移
bbox = content_bbox(fg)
assert bbox is not None, 'foreground is empty!'
bcx, bcy = (bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2
dx = int(round(FG / 2 - bcx))
dy = int(round(FG / 2 - bcy))
print('v2-style bbox center offset before fix:', (round(bcx - FG / 2, 1), round(bcy - FG / 2, 1)))

# 整体平移（dx, dy）：内容右移 dx、下移 dy（正方向向右向下）
if dx != 0 or dy != 0:
    a = np.array(fg)
    rolled = np.zeros_like(a)
    src_x0 = max(0, -dx)
    src_y0 = max(0, -dy)
    src_x1 = min(FG, FG - dx)
    src_y1 = min(FG, FG - dy)
    dst_x0 = max(0, dx)
    dst_y0 = max(0, dy)
    rolled[dst_y0:dst_y0 + (src_y1 - src_y0), dst_x0:dst_x0 + (src_x1 - src_x0)] = a[src_y0:src_y1, src_x0:src_x1]
    fg = Image.fromarray(rolled, 'RGBA')

bbox2 = content_bbox(fg)
bcx2, bcy2 = (bbox2[0] + bbox2[2]) / 2, (bbox2[1] + bbox2[3]) / 2
print('bbox center after fix:', (round(bcx2, 1), round(bcy2, 1)), 'canvas center:', (FG / 2, FG / 2))
print('content bbox:', bbox2, 'content width ratio:', round((bbox2[2] - bbox2[0]) / FG, 3))

fg.save(OUT_DIR + r'/ic_foreground.png')
print('foreground v3 saved')

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
print('preview v3 saved')
