#!/usr/bin/env bash
set -euo pipefail

ROOT="${YPBIN_ROOT:-/opt/ypbin}"
REPO_BASE="${YPBIN_REPO:-https://github.com/wenbin-wb}"
DEPLOY_DIR="$ROOT/ypbin-admin/deploy"

echo "==> [1/5] 检查并安装依赖"
if ! command -v git >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq git
fi
if ! command -v java >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless
fi
if ! command -v mvn >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq maven
fi
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export PATH="$JAVA_HOME/bin:$PATH"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
if ! docker compose version >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq docker-compose-plugin
fi

echo "==> [2/5] 拉取或更新代码"
mkdir -p "$ROOT" && cd "$ROOT"
for repo in ypbin-starter ypbin-admin ypbin-admin-ui; do
  if [ ! -d "$repo/.git" ]; then
    git clone --quiet "$REPO_BASE/$repo.git"
  else
    (cd "$repo" && git pull --ff-only --quiet)
  fi
done

echo "==> [3/5] 构建 admin jar(首次约 2-5 分钟)"
mvn -f "$ROOT/ypbin-starter/pom.xml" -DskipTests install -q
mvn -f "$ROOT/ypbin-admin/ypbin-admin-server/pom.xml" -am -DskipTests package -q

echo "==> [4/5] 准备环境变量"
mkdir -p "$DEPLOY_DIR" && cd "$DEPLOY_DIR"
if [ ! -f .env ]; then
  ROOT_PASS="$(openssl rand -hex 16)"
  ADMIN_PASS="$(openssl rand -base64 12 | tr '+/' 'Aa')"
  cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$ROOT_PASS
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=$ADMIN_PASS
EOF
  echo "  ┌──────────────────────────────────────────┐"
  echo "  │  首次部署已生成凭据,请保存              │"
  echo "  │  MySQL root 密码 : $ROOT_PASS        "
  echo "  │  管理员账号      : admin                │"
  echo "  │  管理员密码      : $ADMIN_PASS      "
  echo "  └──────────────────────────────────────────┘"
  echo "  (凭据已写入 $DEPLOY_DIR/.env,可随时修改)"
fi

echo "==> [5/5] 启动服务"
docker compose up -d --build

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo "✅ 部署完成"
echo "   前端: http://${IP:-<服务器IP>}"
echo "   后端: http://${IP:-<服务器IP>}:8080"
echo "   登录: admin / $(grep '^ADMIN_BOOTSTRAP_PASSWORD=' .env | cut -d= -f2)"
echo "   之后更新: bash $DEPLOY_DIR/deploy.sh"
