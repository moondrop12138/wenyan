# -*- coding: utf-8 -*-
"""验证从原稿提取的轮廓形状：直接用 extracted_outline.py 的段渲染单色剪影，
确认火焰/气球/凹口/尾巴是否还原，再决定是否进 XML。
"""
from PIL import Image, ImageDraw
import importlib.util
import sys

spec = importlib.util.spec_from_file_location("eo", r'C:/Users/Khalil/WorkBuddy/apk2/outputs/extracted_outline.py')
eo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(eo)
OUTLINE = eo.OUTLINE

S = 4
W = H = 108 * S

def bez(p0, c1, c2, p1, n=22):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt*mt*t*c1[0] + 3*mt*t*t*c2[0] + t**3*p1[0]
        y = mt**3*p0[1] + 3*mt*mt*t*c1[1] + 3*mt*t*t*c2[1] + t**3*p1[1]
        pts.append((x, y))
    return pts

pts = []
for p0, c1, c2, p1 in OUTLINE:
    pts += bez(p0, c1, c2, p1)
pts = [(x * S, y * S) for x, y in pts]

img = Image.new('RGB', (W, H), (26, 16, 10))
d = ImageDraw.Draw(img)
d.polygon(pts, fill=(235, 139, 77))
img.save(r'C:/Users/Khalil/WorkBuddy/apk2/outputs/extracted_silhouette.png')
print('saved silhouette, points:', len(pts))
