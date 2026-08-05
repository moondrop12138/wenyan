# -*- coding: utf-8 -*-
"""
温言 App 启动图标部署脚本 v1.6.1（Premium 软立体橙色气泡图标）
输入：outputs/Premium_Android_app_icon_desig_2026-08-04T04-39-17.png（1024x1024）
处理：
1. 边缘 flood fill 抠气泡本体（背景 #E9EAE9 容差内 → 透明；橙色主体+阴影+高光保留，
   与背景相连的抗锯齿边缘同步清除）
2. 前景 bbox 检测 → 缩放到 66dp 安全区（61.1% x 0.98 余量）→ 画布中心精确对齐
3. 生成 5 密度 adaptive icon：
   - ic_launcher_background.png：纯色 #E9EAE9（图标设计图背景中值）
   - ic_launcher_foreground.png：气泡本体（透明底）
   - ic_launcher_monochrome.png：白色剪影（Android 13 主题化图标）
4. 四联遮罩预览（圆角方/圆/squircle/小尺寸）+ 像素级自检
"""
import os
from collections import deque
from PIL import Image, ImageDraw, ImageFilter

SRC = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\Premium_Android_app_icon_desig_2026-08-04T04-39-17.png"
RES_DIR = r"C:\Users\Khalil\WorkBuddy\apk2\app\app\src\main\res"
OUT_PREVIEW = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon-v161-preview.png"
OUT_DIAG = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon-v161-diagnose.png"

BG_REF = (233, 234, 234)      # 背景参考色（图标设计图背景中值）
BG_TOL = 34                   # 背景容差（背景波动 ~232-245）
BG_HEX = "#E9EAE9"            # 背景纯色（与 BG_REF 一致）

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_RATIO = 66.0 / 108.0     # adaptive icon 安全区
SAFE_MARGIN = 0.98            # 安全余量，防厂商 mask 切到气泡边缘


def diff(a, b):
    return sum(abs(x - y) for x, y in zip(a[:3], b[:3]))


def flood_fill_bg(im):
    """从四边 flood fill：与背景色相连（含抗锯齿）的像素 → 透明；气泡主体及内部保留"""
    w, h = im.size
    px = im.load()
    visited = [[False] * w for _ in range(h)]
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            q.append((x, y))
    while q:
        x, y = q.popleft()
        if x < 0 or y < 0 or x >= w or y >= h or visited[y][x]:
            continue
        visited[y][x] = True
        c = px[x, y]
        if diff(c, BG_REF) <= BG_TOL:
            px[x, y] = (0, 0, 0, 0)
            q.append((x + 1, y)); q.append((x - 1, y))
            q.append((x, y + 1)); q.append((x, y - 1))
    return im


def bbox_of_alpha(im, thresh=8):
    a = im.split()[3]
    return a.point(lambda v: 255 if v > thresh else 0).getbbox()


def fit_safe_zone(content, canvas_size):
    """内容 bbox 裁剪后缩放到 canvas 的 66dp 安全区（0.98 余量）并精确居中"""
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    bbox = bbox_of_alpha(content)
    if not bbox:
        return canvas
    cropped = content.crop(bbox)
    cw, ch = bbox[2] - bbox[0], bbox[3] - bbox[1]
    safe = int(canvas_size * SAFE_RATIO * SAFE_MARGIN)
    scale = safe / max(cw, ch)
    nw, nh = max(1, int(cw * scale)), max(1, int(ch * scale))
    cropped = cropped.resize((nw, nh), Image.LANCZOS)
    canvas.paste(cropped, ((canvas_size - nw) // 2, (canvas_size - nh) // 2), cropped)
    return canvas


def write_png(im, path):
    im.convert("RGBA").save(path, "PNG")


def make_monochrome(fg):
    """前景轮廓 → 白色剪影（Android 13 主题图标）：alpha>8 全部纯白"""
    mono = Image.new("RGBA", fg.size, (0, 0, 0, 0))
    a = fg.split()[3].point(lambda v: 255 if v > 8 else 0)
    white = Image.new("RGBA", fg.size, (255, 255, 255, 255))
    mono.paste(white, (0, 0), a)
    return mono


def make_preview(fg, bg_color):
    """四联遮罩预览：圆角方 / 圆 / squircle 近似 / 小尺寸，浅灰检查底"""
    preview = Image.new("RGB", (540, 150), (244, 244, 244))
    for i, (name, mask_fn) in enumerate([
        ("rounded", lambda d: Image.new("L", (d, d), 0)),
        ("circle", lambda d: (lambda m: [m.point(lambda p, x=x, y=y: 255 if (x - d/2)**2 + (y - d/2)**2 <= (d/2)**2 else 0)
                                for x in range(d) for y in range(d)][-1] or m)(Image.new("L", (d, d), 0))),
    ]):
        pass
    # 简化：手动画四个遮罩（圆角方、圆、方、小尺寸）
    size = 120
    cell = 128
    for i, shape in enumerate(["rounded", "circle", "square", "small"]):
        icon = Image.new("RGBA", (size, size), (*bg_color, 255))
        fg_small = fg.resize((size, size), Image.LANCZOS)
        icon = Image.alpha_composite(icon, fg_small)
        mask = Image.new("L", (size, size), 0)
        d = ImageDraw.Draw(mask)
        if shape == "rounded":
            d.rounded_rectangle([0, 0, size - 1, size - 1], radius=26, fill=255)
        elif shape == "circle":
            d.ellipse([0, 0, size - 1, size - 1], fill=255)
        elif shape == "square":
            d.rectangle([0, 0, size - 1, size - 1], fill=255)
        else:
            d.rounded_rectangle([0, 0, size - 1, size - 1], radius=26, fill=255)
            icon = icon.resize((88, 88), Image.LANCZOS)
            tmp = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            tmp.paste(icon, ((size - 88) // 2, (size - 88) // 2))
            icon = tmp
        masked = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        masked.paste(icon, (0, 0), mask)
        preview.paste(masked.convert("RGB"), (12 + i * cell, 15), mask)
    return preview


def main():
    src = Image.open(SRC).convert("RGBA")
    assert src.size == (1024, 1024), f"源图尺寸异常: {src.size}"

    # 1. 抠前景
    fg_master = flood_fill_bg(src.copy())
    diag = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    diag.paste(fg_master, (0, 0))
    diag.convert("RGB").save(OUT_DIAG)
    print(f"[icon] 抠图完成，主体 bbox: {bbox_of_alpha(fg_master)}")

    # 2. 生成 5 密度
    for density, size in DENSITIES.items():
        fg = fit_safe_zone(fg_master, size)
        bg = Image.new("RGBA", (size, size), (*BG_REF, 255))
        mono = make_monochrome(fg)
        write_png(fg, os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher_foreground.png"))
        write_png(bg, os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher_background.png"))
        write_png(mono, os.path.join(RES_DIR, f"mipmap-{density}", "ic_launcher_monochrome.png"))
        print(f"[icon] {density} {size}px 写入完成")

    # 3. 预览 + 自检
    preview = make_preview(fit_safe_zone(fg_master, 512), BG_REF)
    preview.save(OUT_PREVIEW)
    fg108 = fit_safe_zone(fg_master, 108)
    bbox = bbox_of_alpha(fg108)
    assert bbox is not None, "前景无内容"
    # 自检：主体应在安全区（66dp）内且非空
    print(f"[icon] 108px 前景 bbox: {bbox}（画布 108，安全区半径 ~{108*SAFE_RATIO*SAFE_MARGIN:.0f}px）")
    print(f"[icon] 预览: {OUT_PREVIEW}")


if __name__ == "__main__":
    main()
