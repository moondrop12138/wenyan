# -*- coding: utf-8 -*-
"""
温言 App 启动图标替换脚本 v3（v1.3.1 最终版：去方框 + 保微笑）
输入：outputs/Premium_minimal_Android_app_ic_2026-08-04T08-46-09.png（1024x1024）
处理：
1. 去除右下角 "AI生成 WORKBUDDY" 水印（跳过棕色像素，保护气泡尾巴）
2. 抠气泡本体（flood fill）：棕色像素 + 被棕色完全包围的内部区域（白色微笑弧线）保留；
   外部白色卡片方框块 → 透明
3. 生成 5 密度 adaptive icon：
   - background：纯色米白 #E7E1CC
   - foreground：气泡本体（透明底）缩放至 66dp 安全区居中
4. 输出 mipmap PNG + 诊断图 + 预览图
"""
import os
from collections import deque
from PIL import Image, ImageDraw

SRC = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\Premium_minimal_Android_app_ic_2026-08-04T08-46-09.png"
RES_DIR = r"C:\Users\Khalil\WorkBuddy\apk2\app\app\src\main\res"
OUT_PREVIEW = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon-v131-preview.png"
OUT_DIAG = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon-v131-diagnose.png"

BG_COLOR = (231, 225, 204)               # 背景米色 #E7E1CC
WATERMARK_BBOX = (665, 665, 1013, 1013)  # 水印区域

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_RATIO = 66.0 / 108.0


def diff(a, b):
    return sum(abs(x - y) for x, y in zip(a[:3], b[:3]))


def is_brown(c):
    r, g, b = c[:3]
    return r > 160 and 60 < g < 150 and 20 < b < 90 and (r - g) > 40 and (g - b) > 20


def remove_watermark(im):
    """水印区域内非背景像素 → 背景色；跳过棕色像素（水印 bbox 与气泡右下角重叠，保护气泡尾巴）"""
    px = im.load()
    x0, y0, x1, y1 = WATERMARK_BBOX
    for y in range(y0, min(y1, im.height)):
        for x in range(x0, min(x1, im.width)):
            c = px[x, y]
            if not is_brown(c) and diff(c, BG_COLOR) > 40:
                px[x, y] = (*BG_COLOR, 255)
    return im


def extract_bubble(im):
    """抠气泡（flood fill）：棕色 + 被棕色完全包围的内部区域（微笑弧线）保留，外部透明。
    微笑是细弧线，若用距离阈值会误删；四边 BFS 标记外部，内部空洞与外部不连通，天然保留。"""
    w, h = im.size
    px = im.load()
    brown = [[is_brown(px[x, y]) for x in range(w)] for y in range(h)]
    xs = [x for y in range(h) for x in range(w) if brown[y][x]]
    ys = [y for y in range(h) for x in range(w) if brown[y][x]]
    x0, y0, x1, y1 = min(xs), min(ys), max(xs) + 1, max(ys) + 1
    x0, y0 = max(0, x0 - 2), max(0, y0 - 2)
    x1, y1 = min(w, x1 + 2), min(h, y1 + 2)
    cw, ch = x1 - x0, y1 - y0

    # 从裁剪区域四边 BFS 标记"外部"（可经非棕像素到达）。
    # 用 8 邻域：尾巴尖端旁的浅色伪影块通过对角缝隙与外部连通 → 被正确清除；
    # 微笑弧线被棕色完全包围（8 连通也穿不过）→ 天然保留。
    outside = [[False] * cw for _ in range(ch)]
    dq = deque()
    for xx in range(cw):
        for yy in (0, ch - 1):
            if not brown[y0 + yy][x0 + xx]:
                outside[yy][xx] = True
                dq.append((xx, yy))
    for yy in range(ch):
        for xx in (0, cw - 1):
            if not brown[y0 + yy][x0 + xx] and not outside[yy][xx]:
                outside[yy][xx] = True
                dq.append((xx, yy))
    while dq:
        xx, yy = dq.popleft()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                nx, ny = xx + dx, yy + dy
                if 0 <= nx < cw and 0 <= ny < ch and not outside[ny][nx] and not brown[y0 + ny][x0 + nx]:
                    outside[ny][nx] = True
                    dq.append((nx, ny))

    out = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    opx = out.load()
    for yy in range(ch):
        for xx in range(cw):
            # brown 是全图 mask，必须用整图坐标 (y0+yy, x0+xx)；此前误用裁剪坐标 brown[yy][xx]
            # 导致右下角楔形卡片色残留（错位引用整图其他区域的棕色状态）
            if brown[y0 + yy][x0 + xx] or not outside[yy][xx]:
                opx[xx, yy] = px[x0 + xx, y0 + yy]
    return out


def fit_safe_zone(content, canvas_size):
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    safe = int(canvas_size * SAFE_RATIO * 0.90)
    cw, ch = content.size
    scale = safe / max(cw, ch)
    nw, nh = max(1, int(cw * scale)), max(1, int(ch * scale))
    content = content.resize((nw, nh), Image.LANCZOS)
    canvas.paste(content, ((canvas_size - nw) // 2, (canvas_size - nh) // 2), content)
    return canvas


def rounded_rect_mask(size, radius_ratio=0.25):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    return mask


def main():
    im = Image.open(SRC).convert("RGBA")
    im = remove_watermark(im)
    bubble = extract_bubble(im)
    print("bubble size:", bubble.size)

    # 诊断图：抠出的气泡放大 2 倍在灰色棋盘底上
    diag = Image.new("RGBA", (bubble.width, bubble.height), (200, 200, 200, 255))
    diag.paste(bubble, (0, 0), bubble)
    diag = diag.resize((bubble.width * 2, bubble.height * 2), Image.NEAREST)
    diag.save(OUT_DIAG)
    print("[ok] diagnose:", OUT_DIAG)

    for density, size in DENSITIES.items():
        d = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        bg = Image.new("RGBA", (size, size), (*BG_COLOR, 255))
        fg = fit_safe_zone(bubble, size)
        bg.save(os.path.join(d, "ic_launcher_background.png"))
        fg.save(os.path.join(d, "ic_launcher_foreground.png"))
        print(f"[ok] {density}: {size}x{size}")

    size = 216
    fg = fit_safe_zone(bubble, size)
    bg = Image.new("RGBA", (size, size), (*BG_COLOR, 255))
    comp = Image.alpha_composite(bg, fg)
    preview = Image.new("RGBA", (size * 2 + 40, size + 40), (255, 255, 255, 255))
    c = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c.paste(comp, (0, 0), rounded_rect_mask(size, 1.0))
    preview.paste(c, (20, 20), c)
    r = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    r.paste(comp, (0, 0), rounded_rect_mask(size, 0.25))
    preview.paste(r, (size + 60, 20), r)
    preview.save(OUT_PREVIEW)
    print("[ok] preview:", OUT_PREVIEW)


if __name__ == "__main__":
    main()
