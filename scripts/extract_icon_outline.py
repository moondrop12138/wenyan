# -*- coding: utf-8 -*-
"""从 AI 原稿提取气泡焰轮廓 -> 拟合贝塞尔 -> 生成 Android VectorDrawable pathData + PIL 预览点列。
流程：
1) 读 C3 原稿，裁掉右下角"AI生成"水印
2) 气泡区域比背景亮很多 -> HSV 阈值分割出发光体 mask
3) 找最大轮廓 -> 简化(Ramer-Douglas) -> Catmull-Rom 转三次贝塞尔
4) 把 1024px 坐标归一到 108dp 视口（图标主体在原稿约占中央 40%，映射到安全区）
5) 输出：pathData 字符串（可直接粘进 XML）+ 供预览脚本复用的 OUTLINE/TAIL 段
"""
import cv2
import numpy as np
import math

SRC = r'C:/Users/Khalil/WorkBuddy/apk2/outputs/Premium_Android_app_icon_desig_2026-08-04T04-40-23.png'

img = cv2.imread(SRC)
h, w = img.shape[:2]
# 裁掉右下角水印区域（约 x>880, y>900）
img[900:1024, 860:1024] = (20, 15, 10)

hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
# 发光气泡：高饱和 + 高亮度（暖橙），背景暗棕 V 很低
mask = cv2.inRange(hsv, np.array([5, 80, 120]), np.array([45, 255, 255]))
# 形态学清理
k = np.ones((5, 5), np.uint8)
mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, k, iterations=2)
mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, k, iterations=1)

cnts, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
cnt = max(cnts, key=cv2.contourArea)
area = cv2.contourArea(cnt)
print('contour area px^2:', area, ' bbox:', cv2.boundingRect(cnt))

# 简化轮廓
peri = cv2.arcLength(cnt, True)
approx = cv2.approxPolyDP(cnt, 0.008 * peri, True).reshape(-1, 2)
print('simplified points:', len(approx))

x, y, bw, bh = cv2.boundingRect(cnt)
# 映射：原稿气泡 bbox -> 108dp 视口，让主体宽占 ~55dp（安全区内偏饱满），居中
# 提取的是发光体外缘，略瘦，目标宽度放大到 55dp 补偿
scale = 55.0 / bw
cx, cy = x + bw / 2, y + bh / 2
def to_dp(px, py):
    dx = (px - cx) * scale + 54
    dy = (py - cy) * scale + 50
    return (round(dx, 1), round(dy, 1))

pts = [to_dp(px, py) for px, py in approx]

# Catmull-Rom -> 三次贝塞尔（闭合曲线）
def catmull_rom_closed(pts):
    n = len(pts)
    segs = []
    for i in range(n):
        p0 = pts[(i - 1) % n]
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        segs.append((p1, c1, c2, p2))
    return segs

segs = catmull_rom_closed(pts)

def fmt(v):
    s = f'{v:.1f}'.rstrip('0').rstrip('.')
    return s if s else '0'

# 生成 pathData
d = [f'M{fmt(segs[0][0][0])},{fmt(segs[0][0][1])}']
for p0, c1, c2, p1 in segs:
    d.append(f'C{fmt(c1[0])},{fmt(c1[1])} {fmt(c2[0])},{fmt(c2[1])} {fmt(p1[0])},{fmt(p1[1])}')
d.append('Z')
path_data = '\n            '.join(d)

with open(r'C:/Users/Khalil/WorkBuddy/apk2/outputs/extracted_path.txt', 'w', encoding='utf-8') as f:
    f.write(path_data)

# 同步生成预览脚本的 OUTLINE（段列表，dp 浮点原值）
with open(r'C:/Users/Khalil/WorkBuddy/apk2/outputs/extracted_outline.py', 'w', encoding='utf-8') as f:
    f.write('OUTLINE = [\n')
    for p0, c1, c2, p1 in segs:
        row = [tuple(round(float(v), 2) for v in pt) for pt in (p0, c1, c2, p1)]
        f.write(f'    ({row[0]}, {row[1]}, {row[2]}, {row[3]}),\n')
    f.write(']\n')

print('segments:', len(segs))
print('--- pathData head ---')
print(path_data[:300])
print('bbox dp:', min(p[0] for p in pts), max(p[0] for p in pts), min(p[1] for p in pts), max(p[1] for p in pts))
