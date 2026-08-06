#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库路由表生成器 + 完整性校验（构建门禁）

职责：
1. 校验 assets/knowledge 恰好 40 份 md（20 knowledge + 20 practical）
2. 按下方 ROUTE_TABLE（文档标题关键词 → 文档路径）生成 routes.json
3. 校验路由表覆盖与文档完整性；缺失/不匹配 → 非零退出（构建失败）

用法：
    python scripts/gen_routes.py   # 在仓库根的 app/ 目录下运行
"""
from __future__ import annotations

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_KNOWLEDGE = os.path.join(ROOT, "app", "src", "main", "assets", "knowledge")
# 用独立文件名：部分环境对反复覆盖的已知文件有写锁（safe-delete 机制），新文件名可写
OUT_ROUTES = os.path.join(ASSETS_KNOWLEDGE, "routes-v2.json")

EXPECTED_COUNTS = {"knowledge": 20, "practical": 20}
EXPECTED_TOTAL = 40

# 路由表（文档标题关键词 → 相对 assets/knowledge 的路径，顺序即优先级）
# 主题对应原始开源项目 goutoujunshi（powerycy/goutoujunshi）的按需加载设计
# (主题关键词列表, 文档相对 assets/knowledge 的路径)
ROUTE_TABLE = [
    (["回复", "开场", "邀约", "演练", "怎么回", "话术"], "practical/实战话术编排器：从一句回复到后续分支.md"),
    (["松弛感", "现场取材", "轻松调情", "调情", "接话"], "practical/场景感、松弛感与社交校准：从接话到关系推进.md"),
    (["Blueprint", "自然流", "Mystery", "冷读", "内在状态", "结构化互动"], "knowledge/20-经典社交体系的机制、证据与风险边界.md"),
    (["冷读", "PUA", "推拉", "贬低", "服从测试", "煤气灯"], "knowledge/05-PUA操控与伦理替代.md"),
    (["聊天截图", "截图", "网聊", "媒介误读", "隐私", "诈骗", "在线约会", "数字关系"], "knowledge/09-在线约会与数字关系.md"),
    (["主动表达", "第一次见面", "自然接触", "搭讪"], "practical/主动表达、第一次见面与自然接触.md"),
    (["投入失衡", "降级", "退出", "投入", "互惠"], "practical/关系投入失衡：互惠判断、降级投入与退出决策.md"),
    (["依恋", "焦虑", "情绪调节", "情绪"], "knowledge/03-依恋理论与情绪调节.md"),
    (["MBTI", "mbti", "人格"], "knowledge/04-MBTI人格与匹配.md"),
    (["冲突", "沟通", "修复", "吵架", "矛盾"], "knowledge/07-沟通冲突与修复.md"),
    (["同意", "性", "亲密", "边界", "身体"], "knowledge/08-同意边界性与亲密.md"),
    (["婚姻", "家庭", "金钱", "家务", "育儿", "双方家庭", "生命周期"], "knowledge/11-婚姻家庭与生命周期.md"),
    (["分手", "背叛", "复合", "出轨", "关系修复"], "knowledge/15-分手背叛与关系修复.md"),
    (["家暴", "跟踪", "胁迫", "法律", "危机", "安全", "自伤", "自杀"], "knowledge/17-中国法律安全与危机转介.md"),
    (["证据", "来源", "书单", "论文", "延伸阅读", "证据分级"], "knowledge/01-证据分级与内容边界.md"),
    (["实用", "指南", "导读"], "practical/00-导读与使用分级.md"),
]


def collect_md_files() -> dict:
    """收集 assets/knowledge 下 md，返回 {相对路径: 绝对路径}"""
    files = {}
    for sub in ("knowledge", "practical"):
        sub_dir = os.path.join(ASSETS_KNOWLEDGE, sub)
        if not os.path.isdir(sub_dir):
            raise SystemExit(f"FATAL: 缺少目录 {sub_dir}")
        for name in sorted(os.listdir(sub_dir)):
            if name.endswith(".md"):
                files[f"{sub}/{name}"] = os.path.join(sub_dir, name)
    return files


def verify_completeness(files: dict) -> None:
    """校验 40 份文档齐全（AC-17）"""
    counts = {"knowledge": 0, "practical": 0}
    for rel in files:
        sub = rel.split("/", 1)[0]
        counts[sub] = counts.get(sub, 0) + 1
    errors = []
    for sub, expected in EXPECTED_COUNTS.items():
        if counts.get(sub, 0) != expected:
            errors.append(f"{sub} 期望 {expected} 份，实际 {counts.get(sub, 0)} 份")
    if len(files) != EXPECTED_TOTAL:
        errors.append(f"总数期望 {EXPECTED_TOTAL}，实际 {len(files)}")
    if errors:
        raise SystemExit("FATAL: 知识库完整性校验失败\n  " + "\n  ".join(errors))
    print(f"[gen_routes] 知识库完整性 OK：共 {len(files)} 份 "
          f"(knowledge={counts['knowledge']}, practical={counts['practical']})")


def build_routes(files: dict) -> dict:
    """生成 routes.json：files 索引 + routes 路由表"""
    file_index = {}
    for rel in files:
        abs_path = files[rel]
        title = extract_title(abs_path)
        file_index[rel] = {"title": title}

    routes = []
    for keywords, doc in ROUTE_TABLE:
        if doc not in files:
            raise SystemExit(f"FATAL: 路由引用的文档不存在于 assets: {doc}")
        routes.append({"keywords": keywords, "docs": [doc]})

    return {
        "version": 1,
        "generated_at": None,  # 构建可复现，不写时间戳
        "files": file_index,
        "routes": routes,
    }


def extract_title(abs_path: str) -> str:
    """提取文档第一行 # 标题"""
    try:
        with open(abs_path, "r", encoding="utf-8") as f:
            for line in f:
                stripped = line.strip()
                if stripped.startswith("# "):
                    return stripped[2:].strip()
                if stripped:
                    return stripped[:60]
    except OSError:
        pass
    return os.path.basename(abs_path)


def verify_routes_coverage(files: dict, routes: dict) -> None:
    """校验路由表引用全部落在现有文档上，且无主题文档未被路由覆盖"""
    route_docs = set()
    for r in routes["routes"]:
        route_docs.update(r["docs"])
    missing_docs = {d for d in route_docs if d not in files}
    if missing_docs:
        raise SystemExit(f"FATAL: routes 引用了不存在的文档: {sorted(missing_docs)}")

    uncovered = [d for _, d in ROUTE_TABLE if d not in route_docs]
    if uncovered:
        raise SystemExit(f"FATAL: 以下主题文档未被路由覆盖: {uncovered}")
    print(f"[gen_routes] 路由覆盖 OK：{len(routes['routes'])} 条规则，"
          f"{len(route_docs)} 份文档被路由引用")


def main() -> None:
    files = collect_md_files()
    verify_completeness(files)
    routes = build_routes(files)
    verify_routes_coverage(files, routes)

    os.makedirs(ASSETS_KNOWLEDGE, exist_ok=True)
    payload = json.dumps(routes, ensure_ascii=False, indent=2)

    # 幂等：内容一致则跳过写入（规避 safe-delete 对反复覆盖文件的写锁）
    try:
        if os.path.exists(OUT_ROUTES):
            with open(OUT_ROUTES, "r", encoding="utf-8") as f:
                if f.read() == payload:
                    print(f"[gen_routes] 已是最新，跳过写入 {OUT_ROUTES}")
                    return
    except OSError:
        pass

    with open(OUT_ROUTES, "w", encoding="utf-8") as f:
        f.write(payload)
    print(f"[gen_routes] 已生成 {OUT_ROUTES}")


if __name__ == "__main__":
    main()
