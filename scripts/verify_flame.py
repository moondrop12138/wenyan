# -*- coding: utf-8 -*-
"""火焰 D 版（勾玉形：右鼓肚+圆润底+左上细尖）渲染与像素验证"""
from PIL import Image, ImageDraw
import re

S = 8

def bez3(p0, c1, c2, p1, n=60):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt*mt*t*c1[0] + 3*mt*t*t*c2[0] + t**3*p1[0]
        y = mt**3*p0[1] + 3*mt*mt*t*c1[1] + 3*mt*t*t*c2[1] + t**3*p1[1]
        pts.append((x * S, y * S))
    return pts

def path_points(d):
    tokens = re.findall(r'[MCZ]|[-+]?\d*\.?\d+', d)
    pts, i, cur = [], 0, None
    while i < len(tokens):
        t = tokens[i]
        if t == 'M':
            cur = (float(tokens[i+1]), float(tokens[i+2])); i += 3
        elif t == 'C':
            c1 = (float(tokens[i+1]), float(tokens[i+2]))
            c2 = (float(tokens[i+3]), float(tokens[i+4]))
            p1 = (float(tokens[i+5]), float(tokens[i+6]))
            pts += bez3(cur, c1, c2, p1)
            cur = p1
            i += 7
        elif t == 'Z':
            i += 1
        else:
            i += 1
    return pts

FLAME_D = "M 67.3 65.0 C 67.1 68.2, 67.6 71.8, 69.7 74.8 C 72 77.2, 75.8 77.5, 78.2 75.5 C 80.5 73.5, 80.8 70, 80.3 67.3 C 79.8 66.7, 78.9 66.5, 77.7 66.5 C 75.2 66.6, 72.2 66.2, 69.6 65.7 C 68.6 65.5, 67.9 65.3, 67.3 65.0 Z"

W = H = 108 * S
canvas = Image.new("RGB", (W, H), (30, 30, 30, 255))
d = ImageDraw.Draw(canvas)
d.ellipse([54*S - 22.5*S, 47*S - 19.5*S, 54*S + 22.5*S, 47*S + 19.5*S], outline=(120, 160, 130, 255), width=3)
d.polygon([(x, y) for x, y in path_points(FLAME_D)], fill=(192, 116, 63, 255))
d.ellipse([54*S - 33*S, 54*S - 33*S, 54*S + 33*S, 54*S + 33*S], outline=(255, 220, 150, 255), width=2)
canvas.save(r"C:\Users\Khalil\WorkBuddy\apk2\outputs\flame-d-full.png")

crop = canvas.crop((58*S, 48*S, 90*S, 84*S))
crop = crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS)
crop.save(r"C:\Users\Khalil\WorkBuddy\apk2\outputs\flame-d-zoom.png")

# 像素验证：逐行宽度，找尖
im = Image.open(r"C:\Users\Khalil\WorkBuddy\apk2\outputs\flame-d-full.png").convert("RGB")
import numpy as np
a = np.array(im)
r, g, b = a[...,0].astype(int), a[...,1].astype(int), a[...,2].astype(int)
mask = (r>150)&(r<225)&(g>70)&(g<155)&(b>30)&(b<110)
ys, xs = np.where(mask)
rows = {}
for y, x in zip(ys, xs):
    rows.setdefault(y, []).append(x)
print("bbox: x", xs.min(), xs.max(), "y", ys.min(), ys.max())
for y in sorted(rows):
    arr = sorted(rows[y])
    w = arr[-1]-arr[0]
    if y % 8 in (0, 4) or w <= 10:
        print(f"  y={y:4d} ({y/S:5.1f})  x {arr[0]:4d}-{arr[-1]:4d}  宽{w:3d}")
