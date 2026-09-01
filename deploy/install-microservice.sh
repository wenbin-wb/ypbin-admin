#!/usr/bin/env bash
# ============================================================
# ypbin-admin 微服务版一键部署脚本（零配置，全自动）
#
# 用法（新服务器一键安装，Docker 模式）：
#   bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/feature/microservice/deploy/install-microservice.sh)
#
# 无 Docker 环境（本机/轻量服务器，直接用 java -jar 启动 5 服务）：
#   NO_DOCKER=1 bash deploy/install-microservice.sh
#   注意：无 Docker 模式要求外部已有 Nacos/Redis/MySQL，用环境变量指定地址
#
# 阶段总览：
#   [1/7] 环境准备   —— 检查并安装依赖（系统/Docker/JDK21/Maven）
#   [2/7] 拉取代码   —— starter（构建到本地 Maven 仓库）+ admin（feature/microservice 分支）
#   [3/7] 构建 starter —— mvn install（微服务依赖 starter 2.1.0 及新能力）
#   [4/7] 构建后端   —— Maven 打包 5 个服务可执行 jar
#   [5/7] 生成配置   —— .env 凭据 + Nacos 共享配置提示
#   [6/7] 启动服务   —— Docker: compose up（含基础设施）；NO_DOCKER: java -jar 逐个启动
#   [7/7] 健康检查   —— 验证网关/各服务注册，输出访问地址
#
# 自定义参数（环境变量覆盖）：
#   YPBIN_ROOT=/opt/ypbin          部署根目录（默认 /opt/ypbin）
#   YPBIN_REPO=https://github.com/wenbin-wb   仓库前缀
#   BRANCH=feature/microservice    admin 分支（默认 feature/microservice）
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
SCRIPT_URL="${YPBIN_SCRIPT_URL:-https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/feature/microservice/deploy/install-microservice.sh}"
if [ "$(id -u)" != "0" ]; then
  if command -v sudo >/dev/null 2>&1; then
    SELF="/tmp/ypbin-install-microservice.sh"
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
ROOT="${YPBIN_ROOT:-/opt/ypbin}"
REPO_BASE="${YPBIN_REPO:-https://github.com/wenbin-wb}"
BRANCH="${BRANCH:-feature/microservice}"
NO_DOCKER="${NO_DOCKER:-0}"
STARTER_VERSION="2.1.0"

# 服务清单（目录名:jar名:端口）
SERVICES="ypbin-gateway:ypbin-gateway:18080
ypbin-auth:ypbin-auth:18081
ypbin-system:ypbin-system:18082
ypbin-ai:ypbin-ai:18083
ypbin-job:ypbin-job:18084"

info "部署参数：ROOT=$ROOT 分支=$BRANCH NO_DOCKER=$NO_DOCKER"

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
info "[2/7] 拉取代码"
mkdir -p "$ROOT"
cd "$ROOT"
# 仓库可能由不同用户/上次部署创建，root 操作需豁免 dubious ownership
git config --global --add safe.directory "$ROOT/ypbin-starter" 2>/dev/null || true
git config --global --add safe.directory "$ROOT/ypbin-admin" 2>/dev/null || true
[ -d ypbin-starter/.git ] || git clone -b master "$REPO_BASE/ypbin-starter.git"
[ -d ypbin-admin/.git ]   || git clone -b "$BRANCH" "$REPO_BASE/ypbin-admin.git"
# 拉取最新（失败必须报错，避免用旧代码构建出莫名编译错误）
cd "$ROOT/ypbin-starter" && git checkout master 2>/dev/null && git pull --ff-only || die "ypbin-starter 拉取失败（检查网络或分支）"
cd "$ROOT/ypbin-admin" && git checkout "$BRANCH" 2>/dev/null && git pull --ff-only || die "ypbin-admin 拉取失败（检查网络或分支）"
ok "代码就绪（starter@$(git -C "$ROOT/ypbin-starter" rev-parse --short HEAD)，admin@$(git -C "$ROOT/ypbin-admin" rev-parse --short HEAD)）"

# ---------- [3/7] 构建 starter ----------
info "[3/7] 构建 starter $STARTER_VERSION（微服务依赖其新能力）"
cd "$ROOT/ypbin-starter"
# 完整输出错误（不吞日志）：失败时打印 maven 日志尾部
if ! mvn -DskipTests install 2>&1 | tee /tmp/starter-build.log | tail -20; then
  die "starter 构建失败（完整日志 /tmp/starter-build.log）"
fi
ok "starter $STARTER_VERSION 已装入本地 Maven 仓库"

# ---------- [4/7] 构建后端 5 服务 ----------
info "[4/7] 构建后端 5 个服务"
cd "$ROOT/ypbin-admin"
mvn -q -DskipTests clean package 2>&1 | tail -3 || die "admin 构建失败"
JAR_DIR="$ROOT/ypbin-admin/target/microservice-jars"
mkdir -p "$JAR_DIR"
while IFS=: read -r dir jar port; do
  find "ypbin-$dir" -name "*.jar" -path "*target*" ! -name "*sources*" ! -name "*javadoc*" ! -name "*.original" | head -1 | xargs -I{} cp "{}" "$JAR_DIR/$jar.jar"
  ok "打包 $jar.jar（端口 $port）"
done <<< "$SERVICES"

# ---------- [5/7] 生成配置 ----------
info "[5/7] 生成 .env 配置"
ENV_FILE="$ROOT/ypbin-admin/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-YpbinRoot$(date +%s)}"
  cat > "$ENV_FILE" <<EOF
# 由 install-microservice.sh 生成
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
NACOS_ADDR=${NACOS_ADDR:-nacos:8848}
SENTINEL_ADDR=${SENTINEL_ADDR:-sentinel-dashboard:8858}
EOF
  chmod 600 "$ENV_FILE"
  ok "已生成 .env（MySQL 密码：$MYSQL_ROOT_PASSWORD，可改 $ENV_FILE）"
else
  warn "复用已有 .env"
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
  # 基础设施 compose（Nacos/Redis/MySQL/Sentinel）由 starter 提供
  [ -f "$ROOT/ypbin-starter/deploy/docker-compose.yml" ] \
    && docker compose -f "$ROOT/ypbin-starter/deploy/docker-compose.yml" --env-file "$ENV_FILE" up -d
  # 微服务 5 件套
  docker compose -f docker-compose.microservice.yml --env-file "$ENV_FILE" up -d --build
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
  echo "  管理:       cd $ROOT/ypbin-admin/deploy && docker compose -f docker-compose.microservice.yml logs -f"
fi
echo "================================================"
