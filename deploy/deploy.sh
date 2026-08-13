#!/usr/bin/env bash
set -euo pipefail
trap 'echo "!! 脚本执行失败于第 ${LINENO} 行"' ERR

echo "deploy.sh @ $(date '+%F %T')"

ROOT="${YPBIN_ROOT:-/opt/ypbin}"
REPO_BASE="${YPBIN_REPO:-https://github.com/wenbin-wb}"
DEPLOY_DIR="$ROOT/ypbin-admin/deploy"

echo "==> [1/5] 检查并安装依赖"
command -v git >/dev/null 2>&1 || NEED_GIT=1
command -v mvn >/dev/null 2>&1 || NEED_MVN=1
java -version >/dev/null 2>&1 || NEED_JDK=1
if [ "${NEED_GIT:-0}${NEED_MVN:-0}${NEED_JDK:-0}" != "000" ]; then
  apt-get update -y || { echo "apt update 失败"; exit 1; }
  apt-get install -f -y || { echo "修复依赖失败,请手动执行: apt --fix-broken install -y"; exit 1; }
fi
[ "${NEED_GIT:-0}" != "1" ] || { echo "安装 git"; apt-get install -y git; }
[ "${NEED_MVN:-0}" != "1" ] || { echo "安装 maven"; apt-get install -y maven; }
[ "${NEED_JDK:-0}" != "1" ] || { echo "安装 JDK"; apt-get install -y default-jdk-headless; }

# 探测并导出 JAVA_HOME(多方式兜底,容错避免 pipefail 误判)
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(update-alternatives --list java 2>/dev/null | sed 's|/bin/java$||' | grep 'java-17' | head -1 || true)"
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(ls -d /usr/lib/jvm/java-17* 2>/dev/null | head -1 || true)"
fi
if [ -z "${JAVA_HOME:-}" ] && command -v java >/dev/null 2>&1; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")" 2>/dev/null || true)"
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "!! 未找到 JDK,JAVA_HOME 探测失败"; exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"
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
