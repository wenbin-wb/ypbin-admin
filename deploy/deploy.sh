#!/usr/bin/env bash
set -euo pipefail

ROOT="${YPBIN_ROOT:-/opt/ypbin}"
CONFIG_DIR="$ROOT/ypbin-admin/deploy"

echo "==> 检查环境"
command -v git >/dev/null || { echo "缺少 git"; exit 1; }
command -v mvn >/dev/null || { echo "缺少 maven,先安装: apt install -y maven"; exit 1; }
command -v docker >/dev/null || { echo "缺少 docker,先安装: curl -fsSL https://get.docker.com | sh"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "缺少 docker compose 插件"; exit 1; }

echo "==> 更新代码"
mkdir -p "$ROOT" && cd "$ROOT"
for repo in ypbin-starter ypbin-admin ypbin-admin-ui; do
  if [ ! -d "$repo/.git" ]; then
    git clone "https://github.com/wenbin-wb/$repo.git"
  else
    (cd "$repo" && git pull --ff-only)
  fi
done

echo "==> 构建 admin jar"
mvn -f "$ROOT/ypbin-starter/pom.xml" -DskipTests install -q
mvn -f "$ROOT/ypbin-admin/ypbin-admin-server/pom.xml" -am -DskipTests package -q

echo "==> 启动服务"
cd "$CONFIG_DIR"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "已生成 .env 模板,请先编辑密码后重跑: nano $CONFIG_DIR/.env"
  exit 1
fi
docker compose up -d --build

echo "✅ 启动完成: 前端 http://服务器IP , 后端 :8080"
