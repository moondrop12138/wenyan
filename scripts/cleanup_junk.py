#!/usr/bin/env python3
"""清理 apk2/app 下历史构建调试垃圾（safe-delete 锁定目录）。
用法: python cleanup_junk.py [--dry-run]
保留: gt2 .gdh-run .gdh-test pch scripts .kotlin .gradle gradle 及工程文件
"""
import os
import stat
import shutil
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app"))
DRY = "--dry-run" in sys.argv

# 待删目录（相对 app/ 根）
DIRS = [
    "gh20260803024900", "gh20260803024929", "gh20260803032105",
    "gt1785698483", "gt3", "gt5", "gt6", "gt7", ".gtest",
    ".gdh", ".gdh-20260803021438", ".gdh-20260803021459", ".gdh-r3", ".gdh-run2",
    "pc2", "pc3", "pc5", "pc6", "pc7",
    ".proj-cache12", ".proj-cache2", ".proj-cache3", ".proj-cache6-old", ".proj-cache9",
    "build.pre2", "build.pre-assemble", "build-lock1", "build-old-root",
    "gthome",
]
# build.2026* 归档 + 其他匹配模式
PREFIXES = ["build.2026", "build.pre", "build-lock1", "build-old-root"]
FILE_PREFIXES = ["_"]
EXTRA_FILES = ["build_test_out.log"]


def unlock(path):
    try:
        os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
    except OSError:
        pass


def force_rmtree(path):
    # 先递归解锁（只读属性），再删除；失败逐项解锁重试
    for root, dirs, files in os.walk(path, topdown=False):
        for name in dirs + files:
            unlock(os.path.join(root, name))
    shutil.rmtree(path, onerror=lambda f, p, e: (unlock(p), f(p)))


def collect():
    targets = []
    for d in DIRS:
        p = os.path.join(ROOT, d)
        if os.path.lexists(p):
            targets.append(p)
    for name in os.listdir(ROOT):
        p = os.path.join(ROOT, name)
        if os.path.isdir(p) and any(name.startswith(x) for x in PREFIXES) and not os.path.islink(p):
            targets.append(p)
    for name in os.listdir(ROOT):
        if not os.path.isfile(os.path.join(ROOT, name)):
            continue
        if any(name.startswith(x) for x in FILE_PREFIXES) or name in EXTRA_FILES:
            targets.append(os.path.join(ROOT, name))
    return sorted(set(targets))


def main():
    targets = collect()
    print(f"[cleanup] 待清理 {len(targets)} 项")
    ok, fail = [], []
    for p in targets:
        rel = os.path.relpath(p, ROOT)
        if DRY:
            print(f"  [dry-run] {rel}")
            continue
        try:
            if os.path.isdir(p) and not os.path.islink(p):
                force_rmtree(p)
            else:
                unlock(p)
                os.remove(p)
            ok.append(rel)
        except Exception as e:  # noqa: BLE001
            fail.append(f"{rel}: {e}")
    print(f"[cleanup] 完成: 成功 {len(ok)}, 失败 {len(fail)}")
    for f in fail:
        print(f"  [FAIL] {f}")
    with open(os.path.join(ROOT, "_cleanup_result.txt"), "w", encoding="utf-8") as fh:
        fh.write(f"OK({len(ok)}): " + ", ".join(ok) + "\n")
        fh.write(f"FAIL({len(fail)}): " + ", ".join(fail) + "\n")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
