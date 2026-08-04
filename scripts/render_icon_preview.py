# -*- coding: utf-8 -*-
"""渲染「温言」C3 暗底发光气泡焰预览（贴合原版版）：
对照 AI 原稿逐特征重绘轮廓：
- 横向椭圆气球（中心 56,50，rx~24 ry~21），饱满
- 左侧火焰从中部(34,46)高高翘起，尖到 (29,24)，锐利有力
- 顶部小凹口（像被咬一口），在 (52,29) 附近
- 底部 S 曲线尾巴，尖到 (48,76)
预览与矢量 XML 共用同一组控制点。
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

# ---------- 轮廓单一事实源 ----------
# 从 AI 原稿 1:1 提取（scripts/extract_icon_outline.py 生成，见 outputs/extracted_outline.py）
import importlib.util as _ilu
_spec = _ilu.spec_from_file_location("eo", r'C:/Users/Khalil/WorkBuddy/apk2/outputs/extracted_outline.py')
_eo = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(_eo)
OUTLINE = _eo.OUTLINE

# 底部尾巴已含在提取轮廓内，无需单独段
TAIL = []

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

# ---------- 背景 ----------
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

# ① 外圈光晕（相对质心外扩 5%，与 XML ① 一致）
glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
dg = ImageDraw.Draw(glow)
dg.polygon(outline_points(OUTLINE, grow=1.1), fill=hex2rgb('#EB8B4D') + (46,))
if TAIL:
    dg.polygon(outline_points(TAIL, grow=1.1), fill=hex2rgb('#EB8B4D') + (46,))
glow = glow.filter(ImageFilter.GaussianBlur(4.2 * S))
fg = Image.alpha_composite(fg, glow)

# ② 主体 mask + 径向渐变
mask = Image.new('L', (W, H), 0)
dm = ImageDraw.Draw(mask)
dm.polygon(outline_points(OUTLINE), fill=255)
if TAIL:
    dm.polygon(outline_points(TAIL), fill=255)
body = radial_grad((W, H), ['#F6A95C', '#EB8B4D', '#D06A1F'],
                   cx_ratio=52/108, cy_ratio=46/108, radius_px=34 * S).convert('RGBA')
fg.paste(body, (0, 0), mask)

# ③ 内芯高光：气球左上柔和光斑
hl = Image.new('RGBA', (W, H), (0, 0, 0, 0))
dh = ImageDraw.Draw(hl)
dh.pieslice([38 * S, 33 * S, 64 * S, 59 * S], start=140, end=295, fill=hex2rgb('#FFD9A8') + (145,))
dh.ellipse([42 * S, 38 * S, 67 * S, 61 * S], fill=(0, 0, 0, 0))
hl = hl.filter(ImageFilter.GaussianBlur(1.8 * S))
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

out = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/wenyan-icon-vector-preview.png'
full.save(out)
print('saved', out)
