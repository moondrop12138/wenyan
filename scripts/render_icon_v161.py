# -*- coding: utf-8 -*-
"""
「温言」v1.6.1 启动图标生成：基于 AI 设计原稿（Premium_Android_app_icon_desig_2026-08-04T04-39-17.png）
- foreground：去水印整图缩放至 108dp 画布 66% 居中（气泡收在 66dp 安全区，白框边缘被遮罩裁掉后
  与 background 白色无缝衔接）
- background：外框白 #EAEAEA 纯色（与原稿外框一致）
- monochrome：手绘「气泡 + Q 形微笑」单色轮廓（Android 13 主题图标）
- legacy 各密度：同 foreground 整图（minSdk 26 全 adaptive，legacy 不输出）
水印 bbox：x∈[905,1008] y∈[967,1013]（右下角白框内，填白即可），膨胀至 [880,950]-[1024,1024]。
"""
from PIL import Image, ImageDraw
import os

SRC = r"C:\Users\Khalil\WorkBuddy\apk2\outputs\Premium_Android_app_icon_desig_2026-08-04T04-39-17.png"
OUT_DIR = r"C:\Users\Khalil\WorkBuddy\apk2\app\app\src\main\res"
FRAME_WHITE = (234, 235, 234)  # #EAEAEA 外框平均色

# 密度目录 → 108dp 画布像素
DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}
FG_RATIO = 0.66  # foreground 内容占画布比例（主体收进安全区）


def watermark_bbox(img):
    """定位右下角水印（亮于背景的像素）"""
    W, H = img.size
    pts = []
    for y in range(H - 300, H):
        for x in range(W - 500, W):
            r, g, b = img.getpixel((x, y))
            if (r + g + b) / 3 > 245:
                pts.append((x, y))
    if not pts:
        return None
    return (min(p[0] for p in pts), min(p[1] for p in pts),
            max(p[0] for p in pts), max(p[1] for p in pts))


def remove_watermark(img):
    """水印区域填外框白（膨胀 20px 边缘羽化 10px）"""
    W, H = img.size
    bbox = watermark_bbox(img)
    if bbox is None:
        print("  ! watermark not found, skip")
        return img
    x0, y0, x1, y1 = bbox
    x0 = max(0, x0 - 20); y0 = max(0, y0 - 20)
    x1 = min(W, x1 + 20); y1 = min(H, y1 + 20)
    # 用外框白直接覆盖（水印全在白框内，周边采样已确认同色）
    draw = ImageDraw.Draw(img)
    draw.rectangle([x0, y0, x1, y1], fill=FRAME_WHITE)
    print(f"  watermark filled: ({x0},{y0})-({x1},{y1})")
    return img


def make_foreground(src_img, canvas):
    """去水印整图 → 画布 66% 居中，透明底"""
    target = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    content = int(canvas * FG_RATIO)
    resized = src_img.convert("RGBA").resize((content, content), Image.LANCZOS)
    offset = (canvas - content) // 2
    target.paste(resized, (offset, offset))
    return target


def make_background(canvas):
    """纯外框白背景"""
    return Image.new("RGBA", (canvas, canvas), (*FRAME_WHITE, 255))


def make_monochrome(canvas):
    """手绘单色：气泡圆 + 右下尾巴 + Q 形微笑（微笑用透明弧线挖空）"""
    scale = canvas / 108.0
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def S(v):  # dp → px
        return v * scale

    # 气泡主体（白色实心圆）
    cx, cy, r = S(54), S(50.5), S(21.5)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))
    # 右下尾巴（与主体融合的圆）
    tx, ty, tr = S(67.5), S(61.5), S(7.5)
    d.ellipse([tx - tr, ty - tr, tx + tr, ty + tr], fill=(255, 255, 255, 255))
    # Q 形微笑：透明弧线挖空（PIL arc 角度逆时针：0°=右、90°=下、180°=左、270°=上；
    # 15→165 经过底部 90° = ∪ 开口朝上的微笑）
    d.arc(
        [S(43), S(45.5), S(65), S(60.5)],
        start=15, end=165,
        fill=(0, 0, 0, 0),
        width=max(2, int(S(3.5))),
    )
    return img


def main():
    src = Image.open(SRC).convert("RGB")
    print(f"source: {src.size}")
    clean = remove_watermark(src)

    for folder, canvas in DENSITIES.items():
        out = os.path.join(OUT_DIR, folder)
        os.makedirs(out, exist_ok=True)
        make_foreground(clean, canvas).save(os.path.join(out, "ic_launcher_foreground.png"))
        make_background(canvas).save(os.path.join(out, "ic_launcher_background.png"))
        make_monochrome(canvas).save(os.path.join(out, "ic_launcher_monochrome.png"))
        print(f"  {folder}: {canvas}px written")

    # 预览图（供人工检查）
    prev = Image.new("RGB", (512, 512), FRAME_WHITE)
    clean_rgb = clean.resize((338, 338), Image.LANCZOS)
    prev.paste(clean_rgb, ((512 - 338) // 2, (512 - 338) // 2))
    prev.save(r"C:\Users\Khalil\WorkBuddy\apk2\outputs\icon_v161_preview.png")
    print("preview saved")


if __name__ == "__main__":
    main()
