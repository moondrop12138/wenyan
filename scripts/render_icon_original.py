# -*- coding: utf-8 -*-
"""「温言」原创图标设计：气泡 + 火焰尾（言成温）。
概念：气泡=「言」，尾巴向上卷成火苗=「温」——一句话变成一盏灯。
设计原则：单轮廓、无凹口、线条干净、小尺寸可辨。
预览与 XML 共用同一组控制点（本文件 OUTLINE 即事实源）。
"""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import math

S = 4
W = H = 108 * S

def hex2rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

# ================= 原创轮廓：单轮廓闭合曲线 =================
# 气泡：中心(54,46) rx=23 ry=20 居中饱满
# 火焰尾：从气泡底部右 (64,62) 向外鼓再向上卷，尖到 (67,76)，明显上翘的火苗
OUTLINE = [
    # 顶 -> 右上 -> 右侧
    ((54, 26), (62.5, 26), (70.5, 30.5), (74.5, 37.5)),
    # 右侧 -> 右下
    ((74.5, 37.5), (77.5, 44), (76.5, 51.5), (71.5, 57)),
    # 右下 -> 火焰尾：向外下鼓（火苗的"腹"）
    ((71.5, 57), (75.5, 61.5), (77.5, 67), (75.5, 73)),
    # 火焰尾尖：向上勾起到尖点 (65,71)，朝上挑
    ((75.5, 73), (73.5, 78), (69.5, 77), (65, 72)),
    # 火焰尾收回：内凹"S"弧回气泡底（勾身）
    ((65, 72), (68, 71.5), (69.8, 69), (67.5, 65.5)),
    # 气泡底部：火焰尾收点 -> 底部弧线 -> 左下
    ((67.5, 65.5), (64, 64.8), (58.5, 64.3), (53, 64.5)),
    ((54, 64.8), (46, 64.5), (38.5, 61.5), (34, 56)),
    # 左下 -> 左侧 -> 左上
    ((34, 56), (30, 50.5), (29.5, 43.5), (32, 37)),
    # 左上 -> 顶左 -> 回到顶点
    ((32, 37), (34.5, 30.5), (41, 27), (47.5, 26.3)),
    ((47.5, 26.3), (50.5, 26), (52.5, 26), (54, 26)),
]

def bez(p0, c1, c2, p1, n=22):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt*mt*t*c1[0] + 3*mt*t*t*c2[0] + t**3*p1[0]
        y = mt**3*p0[1] + 3*mt*mt*t*c1[1] + 3*mt*t*t*c2[1] + t**3*p1[1]
        pts.append((x, y))
    return pts

def outline_points(segs, grow=0.0):
    pts = []
    for p0, c1, c2, p1 in segs:
        pts += bez(p0, c1, c2, p1)
    if grow:
        cx = sum(p[0] for p in pts) / len(pts)
        cy = sum(p[1] for p in pts) / len(pts)
        pts = [(cx + (x - cx) * (1 + grow / 22), cy + (y - cy) * (1 + grow / 22)) for x, y in pts]
    return [(x * S, y * S) for x, y in pts]

# ---------- 背景：深夜暖棕径向微渐变 ----------
def radial_grad(size, stops, cx_ratio=0.5, cy_ratio=0.46, radius_px=None):
    w, h = size
    cx, cy = w * cx_ratio, h * cy_ratio
    r_max = radius_px or (w * 0.75)
    img = Image.new('RGB', (w, h))
    px = img.load()
    cols = [hex2rgb(s) for s in stops]
    n = len(cols) - 1
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy) / r_max
            t = min(d, 1.0)
            seg = min(int(t * n), n - 1)
            lt = t * n - seg
            px[x, y] = lerp(cols[seg], cols[seg + 1], lt)
    return img

bg = radial_grad((W, H), ['#2A1A0F', '#211510', '#17100A'], radius_px=78 * S).convert('RGBA')

fg = Image.new('RGBA', (W, H), (0, 0, 0, 0))

# ① 外圈光晕
glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
dg = ImageDraw.Draw(glow)
dg.polygon(outline_points(OUTLINE, grow=2.2), fill=hex2rgb('#EB8B4D') + (50,))
glow = glow.filter(ImageFilter.GaussianBlur(3.8 * S))
fg = Image.alpha_composite(fg, glow)

# ② 气泡主体 mask + 径向渐变（中心微偏左上，模拟发光体）
mask = Image.new('L', (W, H), 0)
dm = ImageDraw.Draw(mask)
dm.polygon(outline_points(OUTLINE), fill=255)
body = radial_grad((W, H), ['#F7B573', '#EB8B4D', '#CD6619'],
                   cx_ratio=50/108, cy_ratio=42/108, radius_px=40 * S).convert('RGBA')
fg.paste(body, (0, 0), mask)

# ③ 内芯高光：左上柔光
hl = Image.new('RGBA', (W, H), (0, 0, 0, 0))
dh = ImageDraw.Draw(hl)
dh.pieslice([42 * S, 32 * S, 62 * S, 52 * S], start=150, end=290, fill=hex2rgb('#FFDFB0') + (140,))
dh.ellipse([45 * S, 37 * S, 64 * S, 54 * S], fill=(0, 0, 0, 0))
hl = hl.filter(ImageFilter.GaussianBlur(1.6 * S))
hl.putalpha(ImageChops.multiply(hl.split()[3].point(lambda a: int(a * 0.5)), mask))
fg = Image.alpha_composite(fg, hl)

icon = Image.alpha_composite(bg, fg)

# ---------- 遮罩变体 ----------
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
v_circle = icon.copy(); v_circle.putalpha(circle_mask(W))
v_sq = icon.copy(); v_sq.putalpha(squircle_mask(W))
variants = [
    ('square', icon),
    ('circle', v_circle),
    ('squircle', v_sq),
    ('small', icon.resize((200, 200), Image.LANCZOS)),
]
for i, (name, im) in enumerate(variants):
    full.paste(on_tile(im), (20 + i * 300, 20))

out = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/wenyan-icon-original-preview.png'
full.save(out)
print('saved', out)
