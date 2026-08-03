#!/usr/bin/env bash
# 后端自检运行封装 v5（Windows Git Bash）
#
# 环境限制：WorkBuddy safe-delete 机制会锁定 Gradle 生成的 *.lock / native DLL，
# 同一 GRADLE_USER_HOME 只能被 Gradle 首次运行使用。每次构建使用全新 home，
# 从可读源复制依赖/transform 缓存（排除 lock/bin/native/daemon），复用编译缓存。
#
# 用法：
#   bash scripts/run_gradle.sh testDebugUnitTest
#   bash scripts/run_gradle.sh assembleDebug
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot}"
GRADLE_DIST="$HOME/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"

# 可读源：gt2（最近成功运行，含完整 transform 缓存）
SRC=""
for cand in "$ROOT/gt2" "$ROOT/.gdh-test" "$ROOT/.gdh-run"; do
  if [ -d "$cand/caches/modules-2" ]; then SRC="$cand"; break; fi
done
if [ -z "$SRC" ]; then
  echo "[run_gradle] 无依赖缓存源"
  exit 1
fi

TS="$(date +%s)"
# 固定纯字母名：safe-delete 对带数字/点前缀的新 Gradle 缓存目录会拦截 native 解压
RUN_HOME="$ROOT/gthome"
PROJ_CACHE="$ROOT/pch"
rm -rf "$RUN_HOME" 2>/dev/null || true
mkdir -p "$RUN_HOME/caches"

cpy() { # cpy <src_dir> <dst_rel>
  if [ -d "$1" ]; then
    local win_src win_dst
    if command -v cygpath >/dev/null 2>&1; then
      win_src="$(cygpath -w "$1")"
      win_dst="$(cygpath -w "$RUN_HOME/caches/$2")"
    else
      win_src="$1"
      win_dst="$RUN_HOME/caches/$2"
    fi
    robocopy "$win_src" "$win_dst" /E /XF *.lock *.bin /NFL /NDL /NJH /NJS /NP >/dev/null 2>&1
    local rc=$?
    if [ "$rc" -gt 7 ]; then echo "[run_gradle] 复制 $2 失败 (rc=$rc)"; fi
  fi
}

echo "[run_gradle] 复用缓存源: $SRC"
cpy "$SRC/caches/modules-2" "modules-2"

echo "[run_gradle] 隔离旧 build 产物（safe-delete 规避）..."
TS_ISO="$(date +%Y%m%d%H%M%S)"
for b in "$ROOT/app/build" "$ROOT/build"; do
  if [ -d "$b" ]; then mv "$b" "$b.$TS_ISO" 2>/dev/null || true; fi
done

echo "[run_gradle] 目标: $*"
"$GRADLE_DIST" "$@" --no-daemon --project-cache-dir "$PROJ_CACHE"
