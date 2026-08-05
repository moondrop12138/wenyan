# -*- coding: utf-8 -*-
"""
温言 App 启动图标部署脚本 v1.5.1（墨绿×宣纸「气泡焰」）
输入：Ardot 画布导出的三张 648x648 母版（outputs/ardot_export/）：
  - fg.png  ：气泡焰前景（透明底，宣纸白气泡 + 赭石火苗 + 深松绿微笑弧）
  - bg.png  ：墨绿径向渐变背景
  - mono.png：monochrome 单色剪影（白色气泡焰，透明底）
处理：
1. 前景 bbox 检测 → 缩放到 66dp 安全区（61.1% x 0.98 余量）→ 画布中心精确对齐
2. 生成 5 密度 adaptive icon（mdpi108/hdpi162/xhdpi216/xxhdpi324/xxxhdpi432）
   - ic_launcher_background.png / ic_launcher_foreground.png（覆盖）
   - ic_launcher_monochrome.png（新增，Android 13 主题化图标）
3. 四联遮罩预览（圆角方/圆/squircle/小尺寸）+ 像素级自检
经验来源：v1.2.2 居中/模糊踩坑（648 超采样、bbox 中心对齐、0.98 余量）、
        v1.3.1 抠图色差踩坑（像素验证比视觉模型可靠）
"""
import os
from PIL import Image, ImageDraw, ImageFilter

SRC_DIR = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\ardot_export"
RES_DIR = r"C:\Users\Khalil\WorkBuddy\apk2\app\app\src\main\res"
OUT_PREVIEW = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon-v151-preview.png"
OUT_DIAG = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon-v151-diagnose.png"

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_RATIO = 66.0 / 108.0   # adaptive icon 安全区
SAFE_MARGIN = 0.98          # 安全余量，防厂商 mask 切到火苗尾
MASTER = 648                # 母版尺寸（1.5x 超采样，防缩放糊）


def load_master(name):
    p = os.path.join(SRC_DIR, name)
    im = Image.open(p).convert("RGBA")
    assert im.size == (MASTER, MASTER), f"{name} 尺寸异常: {im.size}"
    return im


def bbox_of_alpha(im, thresh=8):
    """alpha>thresh 像素的外接框；无内容返回 None"""
    a = im.split()[3]
    bbox = a.point(lambda v: 255 if v > thresh else 0).getbbox()
    return bbox


def fit_safe_zone(content, canvas_size):
    """内容 bbox 裁剪后缩放到 canvas 的 66dp 安全区（0.98 余量）并精确居中。
    注意：必须先 crop bbox 再缩放——直接缩放整图会把画布留白一起缩进来（历史教训）。"""
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
    # 中心对齐：内容 bbox 中心 → 画布中心（(S-n)//2 取整，偏差 <=1px）
    canvas.paste(cropped, ((canvas_size - nw) // 2, (canvas_size - nh) // 2), cropped)
    return canvas


def render_density(fg, bg, mono, size, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    # 超采样：母版 648 -> size（各密度最终尺寸），直接 LANCZOS
    bg_s = bg.resize((size, size), Image.LANCZOS)
    fg_s = fit_safe_zone(fg, size)
    mono_s = fit_safe_zone(mono, size)
    bg_s.save(os.path.join(out_dir, "ic_launcher_background.png"))
    fg_s.save(os.path.join(out_dir, "ic_launcher_foreground.png"))
    mono_s.save(os.path.join(out_dir, "ic_launcher_monochrome.png"))
    return bg_s, fg_s, mono_s


def mask_preview(comp, size=216, kind="squircle"):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if kind == "circle":
        d.ellipse([0, 0, size - 1, size - 1], fill=255)
    elif kind == "squircle":
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.42), fill=255)
    elif kind == "rounded":
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.20), fill=255)
    else:  # square
        d.rectangle([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(comp, (0, 0), m)
    return out


def self_check(fg_108, bg_108, fg_master):
    """像素级自检：四角透明 / 中心色 / bbox 对称 / 内容占比"""
    ok = True
    # 1. 前景四角 alpha=0
    w, h = fg_108.size
    corners = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
    for x, y in corners:
        if fg_108.getpixel((x, y))[3] != 0:
            print(f"[FAIL] 前景角 ({x},{y}) 不透明"); ok = False
    # 2. 前景 bbox 中心与画布中心偏差
    bbox = bbox_of_alpha(fg_108)
    if bbox:
        cx = (bbox[0] + bbox[2]) / 2 - w / 2
        cy = (bbox[1] + bbox[3]) / 2 - h / 2
        print(f"bbox 中心偏差: dx={cx:.1f}px dy={cy:.1f}px")
        if abs(cx) > 1 or abs(cy) > 1:
            print("[FAIL] 前景未精确居中"); ok = False
        cw, ch = bbox[2] - bbox[0], bbox[3] - bbox[1]
        ratio = max(cw, ch) / w
        print(f"内容占比: {ratio:.1%} (安全区 61.1% 内)")
        if ratio > SAFE_RATIO:
            print("[FAIL] 内容超出安全区"); ok = False
    # 3. 背景中心色采样（应为渐变中心亮部，非纯黑）
    c = bg_108.getpixel((w // 2, int(h * 0.46)))
    print(f"背景中心色: {c}")
    if c[0] < 30 or c[1] < 40 or c[2] < 30:
        print("[WARN] 背景中心过暗，检查渐变")
    # 4. 最小密度下气泡中心应有内容（宣纸白）
    c2 = fg_108.getpixel((w // 2, h // 2))
    print(f"mdpi 画布中心像素: {c2}")
    if c2[3] < 200:
        print("[WARN] 画布中心透明度低——气泡中心可能偏离，检查构图")
    return ok


def main():
    fg = load_master("fg.png")
    bg = load_master("bg.png")
    mono = load_master("mono.png")

    # 母版自检：导出后四角 alpha
    for name, im in (("fg", fg), ("bg", bg), ("mono", mono)):
        for x, y in [(0, 0), (im.width - 1, 0), (0, im.height - 1), (im.width - 1, im.height - 1)]:
            a = im.getpixel((x, y))[3]
            if a != 0:
                print(f"[WARN] {name} 母版角 ({x},{y}) alpha={a}（背景帧允许）")

    # 各密度部署
    fg_108 = None
    for density, size in DENSITIES.items():
        d = os.path.join(RES_DIR, f"mipmap-{density}")
        b, f, m = render_density(fg, bg, mono, size, d)
        if density == "mdpi":
            fg_108 = f
            bg_108 = b
        print(f"[ok] {density}: {size}x{size}")

    # 像素自检
    print("---- 自检 ----")
    ok = self_check(fg_108, bg_108, fg)
    print("自检:", "PASS" if ok else "FAIL")

    # 四联遮罩预览（216px 合成）
    comp = Image.alpha_composite(bg_108.resize((216, 216), Image.LANCZOS),
                                 fit_safe_zone(fg, 216))
    # 深色底对照（墨黑）
    dark = Image.new("RGBA", (216, 216), (19, 22, 19, 255))
    fg216 = fit_safe_zone(fg, 216)
    comp_dark = Image.alpha_composite(dark, fg216)

    tile = 260
    full = Image.new("RGBA", (5 * tile + 60, tile + 60), (240, 238, 232, 255))
    variants = [
        ("rounded", comp), ("circle", comp), ("squircle", comp), ("small", comp), ("dark", comp_dark),
    ]
    for i, (kind, im) in enumerate(variants):
        v = mask_preview(im, 216, kind)
        if kind == "small":
            v = v.resize((150, 150), Image.LANCZOS)
            # 居中放置
            full.paste(v, (30 + i * tile + (216 - 150) // 2, 30 + (216 - 150) // 2), v)
            continue
        full.paste(v, (30 + i * tile, 30), v)
    full.save(OUT_PREVIEW)
    print("[ok] preview:", OUT_PREVIEW)

    # 诊断图：mdpi 前景放大（黑底，透明区域黑底验证更可靠）
    diag = Image.new("RGBA", (108 * 2, 108 * 2), (0, 0, 0, 255))
    d2 = fg_108.resize((216, 216), Image.NEAREST)
    diag.paste(d2, (0, 0), d2)
    diag.save(OUT_DIAG)
    print("[ok] diagnose:", OUT_DIAG)


if __name__ == "__main__":
    main()
