#!/usr/bin/env bash
# ============================================================
# ypbin-admin 微服务版一键部署脚本（零配置，全自动）
#
# 用法（新服务器一键安装，Docker 模式）：
#   bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)
#
# 无 Docker 环境（本机/轻量服务器，直接用 java -jar 启动 5 服务）：
#   NO_DOCKER=1 bash deploy/install.sh
#   注意：无 Docker 模式要求外部已有 Nacos/Redis/MySQL，用环境变量指定地址
#
# 阶段总览：
#   [1/7] 环境准备   —— 检查并安装依赖（系统/Docker/JDK21/Maven）
#   [2/7] 拉取代码   —— starter（构建到本地 Maven 仓库）+ admin（main 分支）
#   [3/7] 构建 starter —— mvn install（微服务依赖 starter 2.1.1 及新能力）
#   [4/7] 构建后端   —— Maven 打包 5 个服务可执行 jar
#   [5/7] 生成配置   —— .env 凭据 + Nacos 共享配置提示
#   [6/7] 启动服务   —— Docker: compose up（含基础设施）；NO_DOCKER: java -jar 逐个启动
#   [7/7] 健康检查   —— 验证网关/各服务注册，输出访问地址
#
# 自定义参数（环境变量覆盖）：
#   YPBIN_ROOT=/opt/ypbin/main      部署根目录（默认 /opt/ypbin/main）
#   YPBIN_REPO=https://github.com/wenbin-wb   仓库前缀
#   BRANCH=main    admin 分支（默认 main）
#   NACOS_ADDR=localhost:8848      Nacos 地址（NO_DOCKER 模式必填）
#   DB_HOST=localhost DB_PORT=3306 DB_NAME=ypbin_admin DB_USER=root DB_PASSWORD=
#   REDIS_HOST=localhost REDIS_PORT=6379
#   MYSQL_ROOT_PASSWORD=           Docker 模式内建 MySQL 密码（必填）
#   NO_DOCKER=1                    无 Docker 模式：java -jar 直接启动
# ============================================================

set -euo pipefail
trap 'echo "!! 脚本执行失败于第 ${LINENO} 行"' ERR

# ---------- 非 root 自提权（对齐单体脚本）----------
# /opt/ypbin 由 root 创建（部署目录），非 root 用户构建会因写 target/ 权限失败；
# 自动 sudo -E 以 root 重新执行本脚本。管道执行（bash <(curl ...)）时脚本无真实文件，
# 先下载到 /tmp 再 sudo 执行（与单体脚本一致）。
SCRIPT_VERSION="2026.09.01.1"
SCRIPT_URL="${YPBIN_SCRIPT_URL:-https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh}"
if [ "$(id -u)" != "0" ]; then
  if command -v sudo >/dev/null 2>&1; then
    SELF="/tmp/ypbin-install.sh"
    if [ ! -f "$SELF" ] || ! grep -q "SCRIPT_VERSION=\"${SCRIPT_VERSION}\"" "$SELF" 2>/dev/null; then
      echo "非 root 用户，下载脚本并用 sudo 提权执行..."
      curl -fsSL -o "$SELF" "$SCRIPT_URL" || { echo "下载脚本失败（网络？）" >&2; exit 1; }
      chmod +x "$SELF"
    fi
    exec sudo -E bash "$SELF" "$@"
  fi
  echo "请用 root 或 sudo 运行本脚本" >&2
  exit 1
fi

# ---------- 工具函数 ----------
info() { echo -e "\033[36m==> $*\033[0m"; }
ok()   { echo -e "\033[32m✓  $*\033[0m"; }
warn() { echo -e "\033[33m!  $*\033[0m"; }
die()  { echo -e "\033[31m✗  $*\033[0m" >&2; exit 1; }

# ---------- 参数 ----------
# 默认独立目录（与单体版 /opt/ypbin/boot 分开，避免代码互相覆盖/分支冲突，两版本可共存）
ROOT="${YPBIN_ROOT:-/opt/ypbin/main}"
REPO_BASE="${YPBIN_REPO:-https://github.com/wenbin-wb}"
BRANCH="${BRANCH:-main}"
NO_DOCKER="${NO_DOCKER:-0}"
ASSUME_YES="${ASSUME_YES:-0}"
SKIP_FRONTEND="${SKIP_FRONTEND:-0}"
ADMIN_UI_PORT="${ADMIN_UI_PORT:-19000}"
ADMIN_UI_DIST_DIR="${ADMIN_UI_DIST_DIR:-$ROOT/ypbin-admin/admin-ui-dist}"
STARTER_VERSION="2.1.1"

# 服务清单（目录名:jar名:端口）
SERVICES="ypbin-gateway:ypbin-gateway:18080
ypbin-auth:ypbin-auth:18081
ypbin-service/ypbin-system:ypbin-system:18082
ypbin-service/ypbin-ai:ypbin-ai:18083
ypbin-service/ypbin-job:ypbin-job:18084"

# 交互确认：Y/n；-y 或 ASSUME_YES=1 时直接 yes（对齐单体脚本）
confirm() {
  local answer
  if [ "$ASSUME_YES" = "1" ]; then
    return 0
  fi
  while true; do
    read -rp "  ${1} [Y/n]: " answer
    case "${answer:-Y}" in
      Y|y|yes|YES) return 0 ;;
      N|n|no|NO) return 1 ;;
      *) warn "请输入 y 或 n" ;;
    esac
  done
}

info "部署参数：ROOT=$ROOT 分支=$BRANCH NO_DOCKER=$NO_DOCKER"

# ---------- 操作模式选择（对齐单体脚本；-y 跳过）----------
# full=全新部署/完整更新（拉代码+构建+启动） backend=只更新后端（构建+重启）
# restart=只重启服务（不拉代码不构建）      exit=退出
MODE="full"
if [ "$ASSUME_YES" != "1" ]; then
  echo ""
  echo "  请选择操作模式:"
  echo "    1) 全新部署/完整更新（拉代码 + 构建 starter/后端 + 启动）"
  echo "    2) 只更新后端（拉代码 + 构建 + 重启服务）"
  echo "    3) 只重启服务（不拉代码不构建）"
  echo "    4) 退出"
  while true; do
    read -rp "  输入序号 [1]: " mode_choice
    case "${mode_choice:-1}" in
      1) MODE="full"; break ;;
      2) MODE="backend"; break ;;
      3) MODE="restart"; break ;;
      4) echo "  已退出"; exit 0 ;;
      *) warn "请输入 1-4" ;;
    esac
  done
  echo "  → 模式: ${MODE}"
fi

# restart 模式：跳过拉代码/构建，直接启动
if [ "$MODE" = "restart" ]; then
  info "只重启服务模式，跳过拉取与构建"
  SKIP_PULL=1 SKIP_BUILD=1
else
  SKIP_PULL=0 SKIP_BUILD=0
fi

# ---------- [1/7] 环境准备 ----------
info "[1/7] 检查并安装依赖"
command -v git >/dev/null 2>&1 || { apt-get update -y && apt-get install -y git; }
if [ "$NO_DOCKER" = "0" ]; then
  command -v docker >/dev/null 2>&1 || die "Docker 未安装（NO_DOCKER=1 可跳过 Docker 用 java -jar 启动）"
  docker compose version >/dev/null 2>&1 || die "Docker Compose 插件未安装"
fi
if ! command -v java >/dev/null 2>&1; then
  apt-get install -y openjdk-21-jdk-headless 2>/dev/null || die "JDK 21 安装失败"
fi
if ! command -v mvn >/dev/null 2>&1; then
  apt-get install -y maven 2>/dev/null || die "Maven 安装失败"
fi
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export JAVA_HOME
ok "环境就绪：$(java -version 2>&1 | head -1)，Maven $(mvn -v 2>/dev/null | head -1 | awk '{print $3}')"

# --- Maven 阿里云镜像（国内服务器访问 Central 常 403/超时，与单体脚本一致）---
if [ ! -f "$HOME/.m2/settings.xml" ] || ! grep -q "maven.aliyun.com" "$HOME/.m2/settings.xml" 2>/dev/null; then
  info "配置 Maven 阿里云镜像（国内加速）"
  mkdir -p "$HOME/.m2"
  cat > "$HOME/.m2/settings.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Central Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF
  ok "Maven 阿里云镜像已配置"
fi

# ---------- [2/7] 拉取代码 ----------
if [ "${SKIP_PULL:-0}" = "1" ]; then
  info "[2/7] 跳过拉取代码（restart 模式）"
else
info "[2/7] 拉取代码"
mkdir -p "$ROOT"
cd "$ROOT"
# 仓库可能由不同用户/上次部署创建，root 操作需豁免 dubious ownership
git config --global --add safe.directory "$ROOT/ypbin-starter" 2>/dev/null || true
git config --global --add safe.directory "$ROOT/ypbin-admin" 2>/dev/null || true
[ -d ypbin-starter/.git ] || git clone -b master "$REPO_BASE/ypbin-starter.git"
[ -d ypbin-admin/.git ]   || git clone -b "$BRANCH" "$REPO_BASE/ypbin-admin.git"
if [ "$SKIP_FRONTEND" = "0" ]; then
  [ -d ypbin-admin-ui/.git ] || git clone -b main "$REPO_BASE/ypbin-admin-ui.git"
fi
# 拉取最新：分叉时强制对齐远程（部署目录无本地修改，直接 reset --hard 到远程）
pull_repo() { # $1=仓库目录 $2=分支
  local repo="$1" branch="$2"
  cd "$repo"
  git fetch origin "$branch" 2>/dev/null || die "$repo fetch 失败（检查网络）"
  if git merge-base --is-ancestor "origin/$branch" HEAD 2>/dev/null; then
    git checkout "$branch" 2>/dev/null && git merge --ff-only "origin/$branch" 2>/dev/null \
      || git reset --hard "origin/$branch"
  else
    # 分叉（如历史改写）：直接强对齐远程
    warn "$repo 与远程分叉，强制对齐 origin/$branch"
    git checkout -f "$branch" 2>/dev/null || git checkout -b "$branch" "origin/$branch"
    git reset --hard "origin/$branch"
  fi
}
pull_repo "$ROOT/ypbin-starter" master
pull_repo "$ROOT/ypbin-admin" "$BRANCH"
if [ "$SKIP_FRONTEND" = "0" ]; then
  pull_repo "$ROOT/ypbin-admin-ui" master
fi
ok "代码就绪（starter@$(git -C "$ROOT/ypbin-starter" rev-parse --short HEAD)，admin@$(git -C "$ROOT/ypbin-admin" rev-parse --short HEAD)）"
fi

# ---------- [3/7] 构建 starter ----------
if [ "${SKIP_BUILD:-0}" = "1" ]; then
  info "[3/7] 跳过构建（restart 模式）"
else
info "[3/7] 构建 starter $STARTER_VERSION（微服务依赖其新能力）"
# 交互询问是否重构建 starter（对齐单体；-y 或已有构建产物时可选跳过）
if [ "$ASSUME_YES" != "1" ]; then
  if ! confirm "重新构建 starter（最新代码，约 3-6 分钟）？选 n 则用 .m2 已有包"; then
    info "跳过 starter 构建（使用 .m2 已有包）"
    SKIP_STARTER_BUILD=1
  fi
fi
if [ "${SKIP_STARTER_BUILD:-0}" != "1" ]; then
  cd "$ROOT/ypbin-starter"
  # 完整输出错误（不吞日志）：失败时打印 maven 日志尾部
  if ! mvn -DskipTests -Djacoco.skip=true install 2>&1 | tee /tmp/starter-build.log | tail -20; then
    die "starter 构建失败（完整日志 /tmp/starter-build.log）"
  fi
  ok "starter $STARTER_VERSION 已装入本地 Maven 仓库"
else
  # 确认本地仓库有 2.1.1（没有则强制构建）
  if [ ! -d "$HOME/.m2/repository/cn/ypbin/ypbin-starter-core/2.1.1" ]; then
    warn "本地 Maven 仓库无 starter 2.1.1，强制构建"
    cd "$ROOT/ypbin-starter"
    mvn -DskipTests -Djacoco.skip=true install 2>&1 | tee /tmp/starter-build.log | tail -20 \
      || die "starter 构建失败（完整日志 /tmp/starter-build.log）"
  fi
fi
fi

# ---------- [4/7] 构建后端 5 服务 ----------
if [ "${SKIP_BUILD:-0}" = "1" ]; then
  info "[4/7] 跳过构建（restart 模式，复用已有 jar）"
  JAR_DIR="$ROOT/ypbin-admin/target/microservice-jars"
else
info "[4/7] 构建后端 5 个服务"
cd "$ROOT/ypbin-admin"
# 完整输出错误（不吞日志）
if ! mvn -DskipTests clean package 2>&1 | tee /tmp/admin-build.log | tail -20; then
  die "admin 构建失败（完整日志 /tmp/admin-build.log）"
fi
JAR_DIR="$ROOT/ypbin-admin/target/microservice-jars"
mkdir -p "$JAR_DIR"
while IFS=: read -r dir jar port; do
  find "$dir" -name "*.jar" -path "*target*" ! -name "*sources*" ! -name "*javadoc*" ! -name "*.original" | head -1 | xargs -I{} cp "{}" "$JAR_DIR/$jar.jar"
  ok "打包 $jar.jar（端口 $port）"
done <<< "$SERVICES"
fi

# ---------- [5/7] 生成配置 ----------
info "[5/7] 生成 .env 配置"
ENV_FILE="$ROOT/ypbin-admin/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-YpbinRoot$(date +%s)}"
  AI_MODEL_SECRET_KEY="${AI_MODEL_SECRET_KEY:-YpbinAiKey2026_32bytes!!}"
  cat > "$ENV_FILE" <<EOF
# 由 install.sh 生成
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
AI_MODEL_SECRET_KEY=$AI_MODEL_SECRET_KEY
NACOS_ADDR=${NACOS_ADDR:-nacos:8848}
SENTINEL_ADDR=${SENTINEL_ADDR:-sentinel-dashboard:8858}
ADMIN_UI_PORT=$ADMIN_UI_PORT
ADMIN_UI_DIST_DIR=$ADMIN_UI_DIST_DIR
EOF
  chmod 600 "$ENV_FILE"
  ok "已生成 .env（MySQL 密码：$MYSQL_ROOT_PASSWORD，可改 $ENV_FILE）"
else
  warn "复用已有 .env"
fi

# ---------- [5.5/7] 启动基础设施并初始化（Nacos 配置 + MySQL 库表）----------
# Docker 模式：先只启动基础设施（nacos/redis/mysql），配置导入和建库完成后再启动业务服务
if [ "$NO_DOCKER" = "1" ]; then
  info "[5.5/7] NO_DOCKER 模式：假定外部 Nacos/Redis/MySQL 已就绪，直接导入 Nacos 配置"
else
  info "[5.5/7] 启动基础设施（Nacos/Redis/MySQL）"
  cd "$ROOT/ypbin-admin/deploy"
  docker compose -f docker-compose.yml --env-file "$ENV_FILE" up -d nacos redis mysql 2>&1 | tail -20
fi

NACOS_CONSOLE_URL="${NACOS_CONSOLE_URL:-http://localhost:8080}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

# 等待 Nacos Console 就绪（v3 独立 Console 端口）
for i in $(seq 1 60); do
  if curl -fsS "$NACOS_CONSOLE_URL/v3/console/health/readiness" >/dev/null 2>&1; then
    break
  fi
  if [ "$i" = "60" ]; then
    warn "Nacos Console 未就绪，跳过配置导入"
  fi
  sleep 2
done

# 初始化 Nacos 管理员（幂等；已有管理员时忽略失败）
curl -fsS -X POST "$NACOS_CONSOLE_URL/v3/auth/user/admin" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$NACOS_USERNAME" \
  --data-urlencode "password=$NACOS_PASSWORD" >/dev/null 2>&1 || true

# 登录获取 accessToken
NACOS_TOKEN=$(curl -fsS -X POST "$NACOS_CONSOLE_URL/v3/auth/user/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$NACOS_USERNAME" \
  --data-urlencode "password=$NACOS_PASSWORD" 2>/dev/null | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' || true)

# 发布 6 个 Nacos 配置（幂等：已存在则覆盖；使用 Nacos 3 Console 新 API）
if [ -n "$NACOS_TOKEN" ]; then
  info "导入 Nacos 配置中心（ypbin-common + 5 服务）"
  NACOS_DIR="$ROOT/ypbin-admin/deploy/nacos"
  for cfg in ypbin-common ypbin-gateway ypbin-auth ypbin-system ypbin-ai ypbin-job; do
    if [ -f "$NACOS_DIR/$cfg.yaml" ]; then
      curl -fsS -X POST "$NACOS_CONSOLE_URL/v3/console/cs/config" \
        -H "accessToken: $NACOS_TOKEN" \
        --data-urlencode "dataId=$cfg.yaml" \
        --data-urlencode "groupName=DEFAULT_GROUP" \
        --data-urlencode "type=yaml" \
        --data-urlencode "namespaceId=" \
        --data-urlencode "content@$NACOS_DIR/$cfg.yaml" \
        >/dev/null 2>&1 && ok "已导入 $cfg.yaml" || warn "$cfg.yaml 导入失败"
    fi
  done
else
  warn "Nacos 登录失败，跳过配置导入"
fi

# 初始化 MySQL 库表（仅在数据库不存在表时执行；使用 deploy/sql 下的 V1-V4 等价脚本）
if [ "$NO_DOCKER" != "1" ]; then
  info "初始化 MySQL 库表（如已初始化会自动跳过）"
  # 等待 MySQL 健康
  for i in $(seq 1 30); do
    if [ "$(docker inspect -f '{{.State.Health.Status}}' ypbin-mysql 2>/dev/null)" = "healthy" ]; then
      break
    fi
    sleep 2
  done
  DB_HOST=localhost
  DB_PORT=3306
  TABLE_COUNT=$(docker exec ypbin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ypbin_admin';" 2>/dev/null || echo 0)
  if [ "${TABLE_COUNT:-0}" = "0" ]; then
    docker exec ypbin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
      "CREATE DATABASE IF NOT EXISTS ypbin_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    for sql in "$ROOT/ypbin-admin/deploy/sql/"*.sql; do
      docker cp "$sql" ypbin-mysql:/tmp/init.sql
      docker exec ypbin-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" ypbin_admin < /tmp/init.sql"
      ok "已执行 $(basename "$sql")"
    done
  else
    ok "MySQL 已初始化，跳过建库脚本"
  fi
fi

# ---------- [5.6/7] 构建前端（可 SKIP_FRONTEND=1 跳过） ----------
if [ "$NO_DOCKER" = "1" ]; then
  # NO_DOCKER 模式目前只部署后端，前端需另行部署；跳过构建避免误导
  info "[5.6/7] NO_DOCKER 模式跳过前端构建"
elif [ "$SKIP_FRONTEND" = "1" ] || [ -f "$ADMIN_UI_DIST_DIR/index.html" ]; then
  if [ -f "$ADMIN_UI_DIST_DIR/index.html" ]; then
    ok "使用已有前端产物 $ADMIN_UI_DIST_DIR"
  else
    warn "SKIP_FRONTEND=1 但 $ADMIN_UI_DIST_DIR 无 index.html"
    info "请本地构建后上传：cd ypbin-admin-ui && pnpm install && pnpm -F @vben/web-antd build"
    info "上传：scp -r apps/web-antd/dist/* root@<IP>:$ADMIN_UI_DIST_DIR/"
    die "缺少前端产物"
  fi
else
  info "[5.6/7] 构建前端 admin-ui（约 2-10 分钟）"
  export PATH="/usr/local/lib/nodejs/bin:$PATH"
  if ! command -v node >/dev/null 2>&1 || ! command -v pnpm >/dev/null 2>&1; then
    info "未检测到 node/pnpm，尝试自动安装 Node 22"
    ARCH=$(uname -m)
    case "$ARCH" in
      x86_64) NODE_ARCH="x64" ;;
      aarch64|arm64) NODE_ARCH="arm64" ;;
      *) die "不支持的架构 $ARCH，请本地构建后上传" ;;
    esac
    curl -fsSL --max-time 120 -o /tmp/node.tar.xz \
      "https://npmmirror.com/mirrors/node/v22.18.0/node-v22.18.0-linux-${NODE_ARCH}.tar.xz" \
      || die "Node 下载失败"
    mkdir -p /usr/local/lib/nodejs
    tar -xJf /tmp/node.tar.xz -C /usr/local/lib/nodejs --strip-components=1
    export PATH="/usr/local/lib/nodejs/bin:$PATH"
    npm install -g pnpm@latest --registry=https://registry.npmmirror.com >/dev/null 2>&1 || true
  fi
  pnpm config set registry https://registry.npmmirror.com >/dev/null 2>&1 || true
  (cd "$ROOT/ypbin-admin-ui" && pnpm install --frozen-lockfile 2>&1 || pnpm install 2>&1) \
    || die "前端依赖安装失败"
  (cd "$ROOT/ypbin-admin-ui" && pnpm -F @vben/web-antd build 2>&1) \
    || die "前端构建失败"
  mkdir -p "$ADMIN_UI_DIST_DIR"
  cp -r "$ROOT/ypbin-admin-ui/apps/web-antd/dist/"* "$ADMIN_UI_DIST_DIR/"
  find "$ADMIN_UI_DIST_DIR" -type d -exec chmod 755 {} \;
  find "$ADMIN_UI_DIST_DIR" -type f -exec chmod 644 {} \;
  ok "前端构建完成：$ADMIN_UI_DIST_DIR"
fi

# ---------- [6/7] 启动服务 ----------
if [ "$NO_DOCKER" = "1" ]; then
  info "[6/7] 无 Docker 模式：java -jar 启动 5 服务（需外部 Nacos/Redis/MySQL）"
  [ -n "${NACOS_ADDR:-}" ] || die "NO_DOCKER 模式需设置 NACOS_ADDR"
  mkdir -p "$ROOT/logs"
  while IFS=: read -r dir jar port; do
    if [ "$dir" = "ypbin-gateway" ]; then
      EXTRA="--spring.cloud.nacos.server-addr=$NACOS_ADDR"
    else
      EXTRA="--spring.cloud.nacos.server-addr=$NACOS_ADDR
             --spring.datasource.url=jdbc:mysql://${DB_HOST:-localhost}:${DB_PORT:-3306}/${DB_NAME:-ypbin_admin}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
             --spring.datasource.username=${DB_USER:-root}
             --spring.datasource.password=${DB_PASSWORD:-}
             --spring.data.redis.host=${REDIS_HOST:-localhost}
             --spring.data.redis.port=${REDIS_PORT:-6379}"
    fi
    # shellcheck disable=SC2086
    nohup java -Xms256m -Xmx512m -jar "$JAR_DIR/$jar.jar" $EXTRA \
      > "$ROOT/logs/$jar.log" 2>&1 &
    ok "已启动 $jar（端口 $port，日志 $ROOT/logs/$jar.log）"
  done <<< "$SERVICES"
else
  info "[6/7] Docker 模式：compose 启动（含 Nacos/Redis/MySQL 基础设施）"
  cd "$ROOT/ypbin-admin/deploy"
  # 微服务 compose 已内嵌基础设施（nacos/redis/mysql），单文件拉起全链路
  docker compose -f docker-compose.yml --env-file "$ENV_FILE" up -d --build
fi

# ---------- [7/7] 健康检查 ----------
info "[7/7] 健康检查（等待服务就绪，最多 120 秒）"
GATEWAY_PORT=18080
for i in $(seq 1 24); do
  if curl -fsS "http://localhost:$GATEWAY_PORT/actuator/health" >/dev/null 2>&1; then
    ok "网关健康检查通过"
    break
  fi
  [ "$i" = "24" ] && warn "网关健康检查超时（服务可能仍在启动，查看 $ROOT/logs/ 或 docker compose logs）"
  sleep 5
done

echo ""
echo "================================================"
echo "  ypbin-admin 微服务版部署完成"
echo "  网关入口:   http://localhost:$GATEWAY_PORT"
echo "  Nacos 控制台: http://${NACOS_ADDR:-localhost:8848}/nacos （默认 nacos/nacos）"
echo "  登录接口:   POST http://localhost:$GATEWAY_PORT/auth/login"
echo "  部署目录:   $ROOT"
if [ "$NO_DOCKER" = "1" ]; then
  echo "  服务日志:   $ROOT/logs/*.log"
else
  echo "  管理:       cd $ROOT/ypbin-admin/deploy && docker compose -f docker-compose.yml logs -f"
fi
echo "================================================"
