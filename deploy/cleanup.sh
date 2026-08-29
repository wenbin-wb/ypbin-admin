#!/usr/bin/env bash
# ============================================================
# ypbin 服务器磁盘清理脚本
# 安全清理：未使用 docker 镜像/构建缓存/悬空卷、journal 日志、apt 缓存、旧 snap
# 用法：bash /opt/ypbin/cleanup.sh [--dry-run] [--journal-days N] [--snap]
#   --dry-run        只统计不清理
#   --journal-days N  journal 日志保留天数（默认 3）
#   --snap           额外清理未使用 snap（lxd 等，需确认未使用）
# ============================================================
set -uo pipefail

DRY_RUN=0
JOURNAL_DAYS=3
CLEAN_SNAP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --journal-days) JOURNAL_DAYS="$2"; shift 2 ;;
    --snap) CLEAN_SNAP=1; shift ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

before() {
  df -h / | awk 'NR==2 {print $2, $3, $4, $5}'
}

echo "=============================================="
echo " ypbin 服务器磁盘清理 @ $(date '+%F %T')"
[[ $DRY_RUN -eq 1 ]] && echo " [DRY-RUN] 仅统计，不执行清理"
echo "=============================================="
echo "清理前根分区: $(before)"

# ---------- 1. 悬空 docker 镜像 ----------
echo ""
echo "==> [1/6] 悬空 docker 镜像"
if [[ $DRY_RUN -eq 1 ]]; then
  CNT=$(docker images -q --filter "dangling=true" 2>/dev/null | wc -l)
  echo "  悬空镜像 $CNT 个"
else
  docker image prune -f 2>&1 | tail -1
fi

# ---------- 2. 未运行容器使用的具名镜像 ----------
echo ""
echo "==> [2/6] 未运行容器使用的具名镜像"
RUNNING_TAGS=""
for c in $(docker ps --format '{{.Names}}' 2>/dev/null); do
  TAG=$(docker inspect "$c" --format '{{.Config.Image}}' 2>/dev/null)
  [[ "$TAG" != *:* ]] && TAG="${TAG}:latest"
  [[ -n "$TAG" ]] && RUNNING_TAGS="$RUNNING_TAGS
$TAG"
done
while IFS= read -r line; do
  REPO_TAG=$(echo "$line" | awk '{print $1}')
  IMG_ID=$(echo "$line" | awk '{print $2}')
  SIZE=$(echo "$line" | awk '{print $3}')
  [[ -z "$REPO_TAG" || "$REPO_TAG" = "<none>" ]] && continue
  if echo "$RUNNING_TAGS" | grep -qxF "$REPO_TAG"; then
    continue
  fi
  echo "  未使用镜像: $REPO_TAG (${SIZE})"
  if [[ $DRY_RUN -eq 0 ]]; then
    docker rmi "$IMG_ID" >/dev/null 2>&1 && echo "    -> 已删除"
  fi
done < <(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}} {{.Size}}' 2>/dev/null)
if [[ $DRY_RUN -eq 1 ]]; then echo "  [DRY-RUN] 以上镜像可删除"; fi

# ---------- 3. docker 构建缓存 ----------
echo ""
echo "==> [3/6] docker 构建缓存"
if [[ $DRY_RUN -eq 1 ]]; then
  docker system df 2>/dev/null | grep "Build Cache"
else
  docker builder prune -f 2>&1 | tail -1
fi

# ---------- 4. 未使用 docker 卷 ----------
echo ""
echo "==> [4/6] 未使用 docker 卷"
if [[ $DRY_RUN -eq 1 ]]; then
  docker system df 2>/dev/null | grep "Local Volumes"
else
  docker volume prune -f 2>&1 | tail -1
fi

# ---------- 5. journal 日志 + apt 缓存 + 旧 syslog ----------
echo ""
echo "==> [5/6] 系统日志与包缓存（journal 保留 ${JOURNAL_DAYS} 天）"
if [[ $DRY_RUN -eq 1 ]]; then
  journalctl --disk-usage 2>&1
else
  journalctl --vacuum-time=${JOURNAL_DAYS}d 2>&1 | tail -1
  apt-get clean -y 2>/dev/null && echo "  apt 缓存已清理"
  find /var/log -name "*.gz" -o -name "*.1" 2>/dev/null | while read -r f; do
    rm -f "$f" 2>/dev/null && echo "  删除旧日志: $f"
  done
fi

# ---------- 6. 未使用 snap（可选，需 --snap）----------
if [[ $CLEAN_SNAP -eq 1 ]]; then
  echo ""
  echo "==> [6/6] 未使用 snap 应用（lxd 等）"
  if [[ $DRY_RUN -eq 1 ]]; then
    snap list 2>&1 | head -8
    echo "  [DRY-RUN] 检查上述 snap 是否有未使用的（如 lxd inactive）"
  else
    # 仅移除确认未使用的 lxd（如存在）
    if snap list 2>/dev/null | grep -q '^lxd ' && ! systemctl is-active lxd >/dev/null 2>&1; then
      echo "  移除未使用的 lxd"
      snap remove lxd 2>&1 | tail -1
    fi
    # 清理已 disabled 的旧修订
    snap list --all 2>&1 | awk '/disabled/{print $1, $3}' | while read -r name rev; do
      snap remove "$name" --revision="$rev" 2>/dev/null && echo "  移除 $name rev=$rev"
    done
    # 清理 snap 数据快照残留
    rm -rf /var/lib/snapd/snapshots/* 2>/dev/null && echo "  snap 快照已清理"
  fi
else
  echo ""
  echo "==> [6/6] 跳过 snap 清理（加 --snap 启用）"
fi

# ---------- 汇总 ----------
echo ""
echo "=============================================="
echo "清理后根分区: $(before)"
echo "=============================================="
echo "提示: 定期执行可加 cron，如每周日 03:00:"
echo "  0 3 * * 0 bash /opt/ypbin/cleanup.sh --snap >> /var/log/ypbin-cleanup.log 2>&1"