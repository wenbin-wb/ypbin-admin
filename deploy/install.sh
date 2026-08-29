#!/usr/bin/env bash
# ============================================================
# ypbin-admin 一键部署脚本（零配置，全自动）
#
# 用法（新服务器一键安装）：
#   bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)
#
# 说明：raw.githubusercontent.com 走 Fastly CDN，有 max-age=300（5 分钟）缓存；
#       刚 push 完立即执行可能拉到旧版。本脚本自带版本号与自更新检测，
#       发现远程版本更新时会自动重新拉取并执行，无需人工处理。
#
# 自定义参数（可选，通过环境变量覆盖）：
#   YPBIN_ROOT=/opt/ypbin          部署根目录（默认 /opt/ypbin）
#   YPBIN_REPO=https://github.com/wenbin-wb   仓库前缀
#   ADMIN_PORT=8080                admin 后端端口
#   ADMIN_UI_PORT=18080            admin-ui 前端端口
#   MYSQL_PORT=3306                MySQL 映射端口
#   REDIS_PORT=6379                Redis 映射端口
#   SKIP_FRONTEND=1                跳过前端构建（使用已上传的 admin-ui-dist）
#   ADMIN_BOOTSTRAP_USERNAME=admin 初始管理员账号
#
# 脚本自动完成：
#   1) 环境检测（Linux/Docker/JDK21/Maven/Git）
#   2) 拉取 ypbin-starter / ypbin-admin / ypbin-admin-ui 三仓最新代码
#   3) 构建 admin 后端 jar（Maven）
#   4) 构建 admin-ui 前端（pnpm，可跳过用本地构建产物）
#   5) 生成凭据 .env（随机 MySQL/管理员密码）
#   6) docker compose 启动（MySQL/Redis/admin/admin-ui）
#   7) 健康检查 + 权限修复 + 凭据输出
# ============================================================

set -euo pipefail

# ============================================================
# 脚本版本与自更新检测
# raw.githubusercontent.com 走 Fastly CDN（max-age=300，5 分钟缓存），
# push 后立即执行可能拉到旧版。这里每次运行先比对远程最新版本号，
# 若远程更新则自动重新拉取（带 cache-buster 强制绕过 CDN 缓存）并执行。
# 用法不变：bash <(curl -fsSL ...)
# ============================================================
SCRIPT_VERSION="2026.08.29.3"
SCRIPT_URL="${YPBIN_SCRIPT_URL:-https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh}"

if [ "${YPBIN_SKIP_SELF_UPDATE:-0}" != "1" ]; then
  # 仅当脚本来自管道/进程替换（无真实文件）或文件版本与当前不符时才检查更新
  if [ ! -f /tmp/ypbin-install.sh ] || ! grep -q "SCRIPT_VERSION=\"${SCRIPT_VERSION}\"" /tmp/ypbin-install.sh 2>/dev/null; then
    # 拉取远程最新版本号（带 cache-buster，绕过 CDN 缓存）
    REMOTE_VER=""
    REMOTE_VER=$(curl -fsSL --max-time 15 "${SCRIPT_URL}?_=$(date +%s)" 2>/dev/null \
      | grep -m1 'SCRIPT_VERSION=' | sed 's/SCRIPT_VERSION="\([^"]*\)"/\1/' || true)
    if [ -n "$REMOTE_VER" ] && [ "$REMOTE_VER" != "$SCRIPT_VERSION" ]; then
      echo ""
      echo "  检测到脚本新版本 ($REMOTE_VER > $SCRIPT_VERSION)，自动更新..."
      # 重新下载最新版（cache-buster）并执行
      curl -fsSL --max-time 30 -o /tmp/ypbin-install.sh "${SCRIPT_URL}?_=$(date +%s)"
      chmod +x /tmp/ypbin-install.sh
      exec bash /tmp/ypbin-install.sh "$@"
    fi
    # 缓存当前版到 /tmp，后续同名文件直接复用（避免重复检查）
    if [ ! -f /tmp/ypbin-install.sh ]; then
      curl -fsSL --max-time 30 -o /tmp/ypbin-install.sh "${SCRIPT_URL}?_=$(date +%s)"
      chmod +x /tmp/ypbin-install.sh
    fi
  fi
fi

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; DIM='\033[2m'; NC='\033[0m'
ok()   { echo -e "  ${GREEN}✓${NC}  $*"; }
info() { echo -e "  ${BLUE}·${NC}  $*"; }
warn() { echo -e "  ${YELLOW}!${NC}  $*"; }
die()  { echo -e "\n  ${RED}✗  错误：$*${NC}\n"; exit 1; }
step() { echo -e "\n${BOLD}── $* ──${NC}"; }

# ---------- 参数 ----------
ROOT="${YPBIN_ROOT:-/opt/ypbin}"
REPO_BASE="${YPBIN_REPO:-https://github.com/wenbin-wb}"
ADMIN_PORT="${ADMIN_PORT:-8080}"
ADMIN_UI_PORT="${ADMIN_UI_PORT:-18080}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_PORT="${REDIS_PORT:-6379}"
SKIP_FRONTEND="${SKIP_FRONTEND:-0}"
BOOTSTRAP_USER="${ADMIN_BOOTSTRAP_USERNAME:-admin}"
LOG_FILE="/var/log/ypbin-install.log"

echo "====================================================="
echo "  ypbin-admin 一键部署 @ $(date '+%F %T')"
echo "====================================================="

# ---------- [0/8] 环境检测 ----------
step "[0/8] 环境检测"
[ "$(uname -s)" = "Linux" ] || die "本脚本仅支持 Linux"

# 非 root 自动提权：sudo bash <(curl ...) 的进程替换在 sudo 下会 /dev/fd 失效，
# 这里先把自身落到 /tmp 再用 sudo 执行（只需输入一次 sudo 密码）
if [ "$(id -u)" != "0" ]; then
  if command -v sudo >/dev/null 2>&1; then
    SELF="/tmp/ypbin-install.sh"
    # 若脚本来自管道/进程替换（无真实文件），先下载到 /tmp
    if [ ! -f "$SELF" ] || ! grep -q "ypbin-admin 一键部署" "$SELF" 2>/dev/null; then
      SCRIPT_URL="${YPBIN_SCRIPT_URL:-https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh}"
      info "非 root 用户，自动下载脚本并用 sudo 提权执行..."
      curl -fsSL -o "$SELF" "$SCRIPT_URL" || die "下载脚本失败（网络？）"
    fi
    exec sudo -E bash "$SELF" "$@"
  fi
  die "请用 root 或 sudo 运行本脚本"
fi
ok "以 root 运行"

command -v curl >/dev/null 2>&1 || { info "安装 curl"; apt-get update -y && apt-get install -y curl; }
command -v git >/dev/null 2>&1 || { info "安装 git"; apt-get install -y git; }

# Docker
if ! command -v docker >/dev/null 2>&1; then
  info "安装 Docker（官方脚本）"
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
fi
docker compose version >/dev/null 2>&1 || { info "安装 docker compose 插件"; apt-get install -y docker-compose-plugin; }
ok "Docker $(docker --version | awk '{print $3}' | tr -d ',') + Compose 就绪"

# JDK 21
NEED_JDK=0
if ! command -v java >/dev/null 2>&1; then NEED_JDK=1; else
  JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
  [ "$JAVA_VER" = "21" ] || NEED_JDK=1
fi
if [ "$NEED_JDK" = "1" ]; then
  info "安装 JDK 21（openjdk-21-jdk-headless）"
  apt-get install -y openjdk-21-jdk-headless 2>/dev/null || die "JDK 21 安装失败，请手动安装 openjdk-21-jdk-headless"
fi
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
ok "JDK 21 @ $JAVA_HOME"

# Maven
if ! command -v mvn >/dev/null 2>&1; then
  info "安装 Maven"
  apt-get install -y maven
fi
ok "Maven $(mvn -v 2>/dev/null | head -1 | awk '{print $3}')"

# Maven 镜像：国内服务器访问 repo.maven.apache.org 常被 403/超时，
# 自动写入阿里云镜像（settings.xml 不存在或未含 aliyun 时写入），可显著提升构建成功率
if [ ! -f /root/.m2/settings.xml ] || ! grep -q "maven.aliyun.com" /root/.m2/settings.xml 2>/dev/null; then
  info "配置 Maven 阿里云镜像（国内加速）"
  mkdir -p /root/.m2
  cat > /root/.m2/settings.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF
  ok "Maven 阿里云镜像已配置（/root/.m2/settings.xml）"
else
  ok "Maven 阿里云镜像已存在"
fi

# 网络预检查：构建前验证 Maven Central / 阿里云镜像可达，给出明确诊断而非构建时 403
step "[0.5/8] 网络预检查"
CENTRAL_OK=0; MIRROR_OK=0
curl -s -o /dev/null --max-time 10 \
  "https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom" \
  && CENTRAL_OK=1 || CENTRAL_OK=0
curl -s -o /dev/null --max-time 10 \
  "https://maven.aliyun.com/repository/public/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom" \
  && MIRROR_OK=1 || MIRROR_OK=0
[ "$CENTRAL_OK" = "1" ] && ok "Maven Central 可达" || warn "Maven Central 不可达（403/超时）——已配阿里云镜像兜底"
[ "$MIRROR_OK" = "1" ] && ok "阿里云镜像可达" || warn "阿里云镜像不可达——请检查服务器网络/DNS/防火墙（见下方诊断）"
if [ "$CENTRAL_OK" != "1" ] && [ "$MIRROR_OK" != "1" ]; then
  die "Maven 仓库全部不可达。请先排查网络：curl -v https://repo.maven.apache.org/ 看具体报错；检查 DNS(nslookup repo.maven.apache.org)、防火墙、代理设置"
fi

# ---------- [1/8] 磁盘检测 ----------
step "[1/8] 磁盘检测"
AVAIL=$(df -m / | awk 'NR==2 {print $4}')
echo "  可用磁盘: ${AVAIL}MB"
[ "$AVAIL" -lt 3000 ] && warn "磁盘不足 3GB，构建可能失败（建议 ≥5GB）"

# ---------- [2/8] 拉取代码 ----------
step "[2/8] 拉取代码（starter / admin / admin-ui）"
mkdir -p "$ROOT" && cd "$ROOT"
for repo in ypbin-starter ypbin-admin ypbin-admin-ui; do
  if [ ! -d "$repo/.git" ]; then
    info "clone $repo"
    git clone --quiet "$REPO_BASE/$repo.git"
  else
    info "更新 $repo"
    (cd "$repo" && git stash --quiet 2>/dev/null || true; git pull --ff-only --quiet || warn "$repo 更新失败（本地有改动？）")
  fi
  ok "$repo @ $(cd "$repo" && git log --oneline -1)"
done

# ---------- [3/8] 构建后端 ----------
step "[3/8] 构建 admin 后端 jar（首次约 3-6 分钟）"
mvn -f "$ROOT/ypbin-starter/pom.xml" -DskipTests install -q || die "starter 构建失败（网络/依赖问题？）"
mvn -f "$ROOT/ypbin-admin/pom.xml" -DskipTests install -q || die "admin 构建失败"
JAR="$ROOT/ypbin-admin/ypbin-admin-server/target/ypbin-admin.jar"
[ -f "$JAR" ] || die "未找到构建产物 $JAR"
ok "jar 构建完成: $(du -h "$JAR" | cut -f1)"

# ---------- [4/8] 构建前端 ----------
step "[4/8] 前端处理"
DIST_DIR="$ROOT/admin-ui-dist"
if [ "$SKIP_FRONTEND" = "1" ] || [ -f "$DIST_DIR/index.html" ]; then
  if [ -f "$DIST_DIR/index.html" ]; then
    ok "使用已有前端产物 $DIST_DIR"
  else
    warn "SKIP_FRONTEND=1 但 $DIST_DIR 无 index.html"
    info "请本地构建后上传："
    info "  cd ypbin-admin-ui && pnpm install && pnpm -F @vben/web-antd build"
    info "  scp -r apps/web-antd/dist/* root@<IP>:$DIST_DIR/"
    die "缺少前端产物"
  fi
else
  if command -v pnpm >/dev/null 2>&1; then
    info "构建 admin-ui（pnpm，约 2-5 分钟）"
    (cd "$ROOT/ypbin-admin-ui" && pnpm install --frozen-lockfile >/dev/null 2>&1 || pnpm install >/dev/null 2>&1)
    (cd "$ROOT/ypbin-admin-ui" && pnpm -F @vben/web-antd build >/dev/null 2>&1) \
      && mkdir -p "$DIST_DIR" \
      && cp -r "$ROOT/ypbin-admin-ui/apps/web-antd/dist/"* "$DIST_DIR/" \
      || { warn "前端构建失败（npm 网络问题？）"; die "可改用 SKIP_FRONTEND=1 + 本地构建上传"; }
  else
    warn "服务器无 pnpm，前端需本地构建上传"
    info "  本机: cd ypbin-admin-ui && pnpm install && pnpm -F @vben/web-antd build"
    info "  上传: scp -r apps/web-antd/dist/* root@<IP>:$DIST_DIR/"
    die "缺少前端产物"
  fi
  ok "前端构建完成"
fi

# ⚠️ 权限修复（scp 上传会保留 700 目录权限，nginx worker 读不了 → js 返回 text/html）
find "$DIST_DIR" -type d -exec chmod 755 {} \;
find "$DIST_DIR" -type f -exec chmod 644 {} \;
ok "前端目录权限已修复（755/644）"

# ---------- [5/8] 准备 .env ----------
step "[5/8] 生成 .env 凭据"
DEPLOY_DIR="$ROOT/ypbin-admin/deploy"
mkdir -p "$DEPLOY_DIR"
if [ ! -f "$DEPLOY_DIR/.env" ]; then
  # .env.example 已填好可用默认值，直接复制即启动；敏感三项再随机化增强安全
  cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
  MYSQL_PASS="$(openssl rand -hex 16)"
  ADMIN_PASS="$(openssl rand -base64 12 | tr '+/' 'Aa')"
  AI_KEY="$(openssl rand -base64 24 | tr '+/' 'Aa' | cut -c1-32)"
  sed -i "s/^MYSQL_ROOT_PASSWORD=.*/MYSQL_ROOT_PASSWORD=$MYSQL_PASS/" "$DEPLOY_DIR/.env"
  sed -i "s/^ADMIN_BOOTSTRAP_USERNAME=.*/ADMIN_BOOTSTRAP_USERNAME=$BOOTSTRAP_USER/" "$DEPLOY_DIR/.env"
  sed -i "s/^ADMIN_BOOTSTRAP_PASSWORD=.*/ADMIN_BOOTSTRAP_PASSWORD=$ADMIN_PASS/" "$DEPLOY_DIR/.env"
  sed -i "s/^AI_MODEL_SECRET_KEY=.*/AI_MODEL_SECRET_KEY=$AI_KEY/" "$DEPLOY_DIR/.env"
  # 应用用户自定义端口参数（若与环境变量不同）
  sed -i "s/^MYSQL_PORT=.*/MYSQL_PORT=$MYSQL_PORT/" "$DEPLOY_DIR/.env"
  sed -i "s/^REDIS_PORT=.*/REDIS_PORT=$REDIS_PORT/" "$DEPLOY_DIR/.env"
  sed -i "s/^ADMIN_PORT=.*/ADMIN_PORT=$ADMIN_PORT/" "$DEPLOY_DIR/.env"
  sed -i "s/^ADMIN_UI_PORT=.*/ADMIN_UI_PORT=$ADMIN_UI_PORT/" "$DEPLOY_DIR/.env"
  ok "凭据已生成（复制 .env.example + 随机化密码/密钥）并写入 $DEPLOY_DIR/.env"
  info "License 签发密钥（LICENSE_ISSUER_*）留空可正常启动，签发前在系统内生成后填入"
else
  ok "使用已有 .env（保留原凭据）"
fi

# ---------- [6/8] 启动容器 ----------
step "[6/8] docker compose 启动"
cd "$DEPLOY_DIR"
docker compose up -d --build 2>&1 | tail -5 || die "容器启动失败"

# ---------- [7/8] 健康检查 ----------
step "[7/8] 健康检查（等待 admin 启动，最多 90s）"
ADMIN_OK=0
for i in $(seq 1 18); do
  sleep 5
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:$ADMIN_PORT/actuator/health" 2>/dev/null || echo "000")
  if [ "$CODE" = "200" ]; then ADMIN_OK=1; break; fi
  echo "  等待 admin 启动... ($i/18, HTTP $CODE)"
done
[ "$ADMIN_OK" = "1" ] || { warn "admin 未在预期时间内健康，查看日志:"; docker logs "$(docker ps --filter name=deploy-admin --format '{{.Names}}' | head -1)" --tail 30 2>&1 | tail -30; die "admin 启动失败"; }
ok "admin 后端健康（HTTP 200）"

UI_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:$ADMIN_UI_PORT/" 2>/dev/null || echo "000")
[ "$UI_CODE" = "200" ] && ok "admin-ui 前端可访问（HTTP 200）" || warn "前端未就绪（HTTP $UI_CODE）"

# ---------- [8/8] 输出凭据 ----------
step "[8/8] 部署完成"
IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo ""
echo "  ┌──────────────────────────────────────────────┐"
echo "  │  ypbin-admin 部署完成                         │"
echo "  │  前端: http://${IP:-<服务器IP>}:$ADMIN_UI_PORT        │"
echo "  │  后端: http://${IP:-<服务器IP>}:$ADMIN_PORT          │"
echo "  │  账号: $BOOTSTRAP_USER                                 │"
echo "  │  密码: $(grep '^ADMIN_BOOTSTRAP_PASSWORD=' "$DEPLOY_DIR/.env" | cut -d= -f2) │"
echo "  └──────────────────────────────────────────────┘"
echo ""
echo "  凭据已存: $DEPLOY_DIR/.env（勿提交版本管理）"
echo "  之后更新: bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)"
echo "  或手动:   cd $DEPLOY_DIR && docker compose up -d --build"
echo "  清理空间: bash $ROOT/cleanup.sh --snap"
