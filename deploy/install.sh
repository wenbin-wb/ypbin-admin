#!/usr/bin/env bash
# ============================================================
# ypbin-admin 微服务版一键部署脚本（零配置，全自动）
#
# 用法（GitHub 可直连时）：
#   bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)
#
# 国内服务器（GitHub 不可达，推荐走 Gitee 镜像源一键）：
#   bash <(curl -fsSL https://gitee.com/wenbin_wb/ypbin-admin/raw/main/deploy/install.sh)
#   仓库源自动探测：默认直连 GitHub（3s 快超时）；不可达自动降级为 Gitee 同名镜像
#   （gitee.com/wenbin_wb 下 ypbin-starter / ypbin-admin / ypbin-admin-ui，请先在 Gitee 建镜像并开启自动同步）；
#   两者都不可达时按下方 YPBIN_REPO 手工指定镜像/代理前缀后重跑。
#
# 无 Docker 环境（本机/轻量服务器，直接用 java -jar 启动 5 服务）：
#   NO_DOCKER=1 bash deploy/install.sh
#   注意：无 Docker 模式要求外部已有 Nacos/Redis/MySQL，用环境变量指定地址
#
# 阶段总览：
#   [1/7] 环境准备   —— 检查并安装依赖（系统/Docker/JDK21/Maven，Maven 走阿里云镜像）
#   [2/7] 拉取代码   —— starter + admin（main 分支）+ admin-ui（main），源自动探测/降级
#   [3/7] 构建 starter —— mvn install（微服务依赖 starter 2.2.3 及新能力）
#   [4/7] 构建后端   —— Maven 打包 5 个服务可执行 jar
#   [5/7] 生成配置   —— .env 凭据 + Nacos 共享配置提示
#   [6/7] 启动服务   —— Docker: compose up（含基础设施）；NO_DOCKER: java -jar 逐个启动
#   [7/7] 健康检查   —— 验证网关/各服务注册，输出访问地址
#
# 自定义参数（环境变量覆盖）：
#   YPBIN_ROOT=/opt/ypbin/main      部署根目录（默认 /opt/ypbin/main）
#   YPBIN_REPO=https://github.com/wenbin-wb   显式仓库前缀（跳过自动探测；可指向 Gitee
#                                   镜像 gitee.com/wenbin_wb 或 ghproxy 等代理前缀）
#   GITEE_REPO=https://gitee.com/wenbin_wb    自动降级目标（默认 Gitee 同名镜像）
#   BRANCH=main    admin 分支（默认 main）
#   NACOS_ADDR=localhost:8848      Nacos 地址（NO_DOCKER 模式必填）
#   DB_HOST=localhost DB_PORT=3306 DB_NAME=ypbin_admin DB_USER=root DB_PASSWORD=
#   REDIS_HOST=localhost REDIS_PORT=6379
#   REDIS_PASSWORD=                Redis 密码（Docker 模式自动随机生成；NO_DOCKER 用外部 Redis
#                                  有密码时须传入（导入 Nacos 共享配置用），无认证可留空）
#   MYSQL_ROOT_PASSWORD=           Docker 模式内建 MySQL 密码（必填）
#   NACOS_AUTH_TOKEN= NACOS_AUTH_IDENTITY_KEY= NACOS_AUTH_IDENTITY_VALUE=
#                                  Nacos 服务端鉴权凭据（自动随机生成，一般无需手传；
#                                  NACOS_AUTH_TOKEN 需 Base64 且解码后 ≥32 字节）
#   INTERNAL_TOKEN=                /internal/** 服务间 Feign 调用凭证（守卫校验，自动随机生成，
#                                  auth/system/ai 共享一致值，一般无需手传）
#   REGISTRY_PREFIX=               Docker 镜像加速前缀（如 docker.m.daocloud.io/；留空=官方源）
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
GITEE_SCRIPT_URL="https://gitee.com/wenbin_wb/ypbin-admin/raw/main/deploy/install.sh"
# 脚本源连通探测（3s 快超时）：GitHub raw 不可达时降级 Gitee raw（内容同源）
resolve_script_url() {
  if [ -n "${YPBIN_SCRIPT_URL:-}" ]; then printf '%s' "$SCRIPT_URL"; return; fi
  if curl -fsSI -m 3 -o /dev/null "$SCRIPT_URL" 2>/dev/null; then
    printf '%s' "$SCRIPT_URL"
  else
    warn "GitHub raw 不可达，脚本源降级为 Gitee：${GITEE_SCRIPT_URL}"
    printf '%s' "$GITEE_SCRIPT_URL"
  fi
}
if [ "$(id -u)" != "0" ]; then
  if command -v sudo >/dev/null 2>&1; then
    SELF="/tmp/ypbin-install.sh"
    if [ ! -f "$SELF" ] || ! grep -q "SCRIPT_VERSION=\"${SCRIPT_VERSION}\"" "$SELF" 2>/dev/null; then
      echo "非 root 用户，下载脚本并用 sudo 提权执行..."
      curl -fsSL -o "$SELF" "$(resolve_script_url)" || { echo "下载脚本失败（GitHub/Gitee 均不可达，请检查网络或代理）" >&2; exit 1; }
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

# —— 国内服务器 APT/Docker 源自愈（Ubuntu/Debian）——
# 检测官方国外源并备份切换为阿里镜像（sources.list 旧格式 + .sources deb822 均处理）；
# 随后为 docker-compose-plugin 配置阿里 docker-ce 源。备份保留在 /etc/apt/*.bak*，失败不覆盖原配置。
apt_docker_ce_selfheal() {
  [ -f /etc/os-release ] || return 1
  local distro codename id
  id="$(. /etc/os-release && echo "$ID")"
  case "$id" in ubuntu|debian) ;; *) return 1 ;; esac
  codename="$(. /etc/os-release && echo "$VERSION_CODENAME")"
  # 1) 备份并切换官方源 -> 阿里镜像（仅当确实指向官方国外域名且备份不存在时）
  local changed=0
  if [ -f /etc/apt/sources.list ] && grep -qE '(archive\.ubuntu\.com|security\.ubuntu\.com|deb\.debian\.org)' /etc/apt/sources.list; then
    cp -a /etc/apt/sources.list "/etc/apt/sources.list.bak.ypbin" 2>/dev/null || true
    sed -i -E 's|(https?://)archive\.ubuntu\.com|\1mirrors.aliyun.com|g; s|(https?://)security\.ubuntu\.com|\1mirrors.aliyun.com|g; s|(https?://)deb\.debian\.org|\1mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null && changed=1
  fi
  for f in /etc/apt/sources.list.d/*.sources; do
    [ -f "$f" ] || continue
    if grep -qE '(archive\.ubuntu\.com|security\.ubuntu\.com|deb\.debian\.org)' "$f"; then
      cp -a "$f" "$f.bak.ypbin" 2>/dev/null || true
      sed -i -E 's|(https?://)archive\.ubuntu\.com|\1mirrors.aliyun.com|g; s|(https?://)security\.ubuntu\.com|\1mirrors.aliyun.com|g; s|(https?://)deb\.debian\.org|\1mirrors.aliyun.com|g' "$f" 2>/dev/null && changed=1
    fi
  done
  [ "$changed" = "1" ] && { warn "系统 APT 源已切换阿里镜像（原文件备份 .bak.ypbin）"; apt-get update -y >/dev/null 2>&1 || true; }
  # 2) 配置阿里 docker-ce 源（compose 插件所在），再装
  if ! docker compose version >/dev/null 2>&1; then
    if [ ! -f /etc/apt/keyrings/docker.asc ]; then
      install -m 0755 -d /etc/apt/keyrings 2>/dev/null || true
      curl -fsSL "https://mirrors.aliyun.com/docker-ce/linux/${id}/gpg" 2>/dev/null | gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null && {
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/${id} ${codename} stable" > /etc/apt/sources.list.d/docker.list 2>/dev/null || true
        apt-get update -y >/dev/null 2>&1 || true
      }
    fi
    apt-get install -y docker-compose-plugin >/dev/null 2>&1 || apt-get install -y docker-compose-v2 >/dev/null 2>&1 || return 1
  fi
  docker compose version >/dev/null 2>&1
}

# 从 admin pom 提取 ypbin-starter.version（形如 <ypbin-starter.version>2.2.3</...>）
starter_version_from_pom() {
  local pom="$1"
  [ -f "$pom" ] || return 1
  sed -n 's/.*<ypbin-starter.version>\([^<]*\)<\/ypbin-starter.version>.*/\1/p' "$pom" | head -1
}

# ---------- 参数 ----------
# 默认独立目录（与单体版 /opt/ypbin/boot 分开，避免代码互相覆盖/分支冲突，两版本可共存）
ROOT="${YPBIN_ROOT:-/opt/ypbin/main}"
BRANCH="${BRANCH:-main}"
NO_DOCKER="${NO_DOCKER:-0}"
ASSUME_YES="${ASSUME_YES:-0}"
SKIP_FRONTEND="${SKIP_FRONTEND:-0}"
ADMIN_UI_PORT="${ADMIN_UI_PORT:-19000}"
ADMIN_UI_DIST_DIR="${ADMIN_UI_DIST_DIR:-$ROOT/ypbin-admin/admin-ui-dist}"
# starter 版本：从 admin 仓库 pom 的 ypbin-starter.version 自动解析（唯一事实源，
# 与 CI dispatch 自动升级保持一致），无需手工同步；目录未就绪时留空，由 [3/7] 构建前解析。
STARTER_VERSION="${STARTER_VERSION:-}"
# 仓库源（GitHub / Gitee 镜像自动探测；显式 YPBIN_REPO 优先）
REPO_BASE=""
GITEE_REPO="${GITEE_REPO:-https://gitee.com/wenbin_wb}"
GITHUB_REPO="https://github.com/wenbin-wb"
# 探测 GitHub 连通（3s 快超时）；显式指定或探测成功后赋值 REPO_BASE。
# 注意：函数 stdout 只输出 URL（供 $(...) 捕获）；一切提示走 REPO_SWITCHED_NOTE/die(stderr)，
# 避免 ANSI/文案污染被命令替换吞入变量。
resolve_repo_base() {
  REPO_SWITCHED_NOTE=""
  if [ -n "${YPBIN_REPO:-}" ]; then REPO_BASE="$YPBIN_REPO"; echo "$REPO_BASE"; return; fi
  if [ -n "$REPO_BASE" ]; then echo "$REPO_BASE"; return; fi
  if curl -fsSI -m 3 -o /dev/null "https://github.com" 2>/dev/null; then
    REPO_BASE="$GITHUB_REPO"
  elif curl -fsSI -m 3 -o /dev/null "https://gitee.com" 2>/dev/null; then
    REPO_BASE="$GITEE_REPO"
    REPO_SWITCHED_NOTE="GitHub 不可达，仓库源自动降级为 Gitee 镜像：${GITEE_REPO}（请在 Gitee 建同名镜像并开启自动同步）"
  else
    die "GitHub 与 Gitee 均不可达：请配置代理或显式指定 YPBIN_REPO（如 https://ghproxy.com/https://github.com/wenbin-wb）后重跑"
  fi
  echo "$REPO_BASE"
}

# ---------- 交互模式 ----------
# 默认交互（人工确认关键步骤）；-y/--yes 全自动跳过所有确认（CI/无头环境，对齐单体脚本）
while [[ $# -gt 0 ]]; do
  case "$1" in
    -y|--yes) ASSUME_YES=1; shift ;;
    *) shift ;;
  esac
done

# 服务清单（目录名:jar名:端口）
SERVICES="ypbin-gateway:ypbin-gateway:18080
ypbin-auth:ypbin-auth:18081
ypbin-service/ypbin-system:ypbin-system:18082
ypbin-service/ypbin-ai:ypbin-ai:18083"

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

# —— 启用 apt universe/multiverse（maven 等位于 universe，部分镜像默认仅 main/restricted）——
apt_ensure_universe() {
  local touched=0 f
  for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.sources; do
    [ -f "$f" ] || continue
    if grep -qE '^Components:.*universe' "$f" 2>/dev/null; then continue; fi
    if grep -qE '^Components:' "$f" 2>/dev/null; then
      sed -i 's/^Components: \(.*\)$/Components: \1 universe multiverse/' "$f" && touched=1
    elif grep -qE '^[[:space:]]*deb[[:space:]]' "$f" 2>/dev/null; then
      # legacy 单行式（deb uri suite main restricted）
      if ! grep -qE '^deb .*universe' "$f"; then
        sed -i -E 's/^([[:space:]]*deb[[:space:]]+\S+[[:space:]]+\S+[[:space:]]+(main|restricted)([[:space:]]|$))/\1 universe multiverse\3/' "$f" && touched=1
      fi
    fi
  done
  if [ "$touched" = "1" ]; then
    warn "已启用 universe/multiverse 组件（maven 等依赖包）"
    apt-get update -y >/dev/null 2>&1 || true
  fi
}

# —— 阿里 Apache Maven 镜像兜底安装（apt 源缺失/过旧时）——
install_maven_from_mirror() {
  local ver="3.9.9" dest="/opt/apache-maven-${ver}" url
  url="https://mirrors.aliyun.com/apache/maven/maven-3/${ver}/binaries/apache-maven-${ver}-bin.tar.gz"
  warn "apt 安装 Maven 失败，改从阿里镜像下载 Maven ${ver} ..."
  curl -fsSL -o /tmp/apache-maven.tar.gz "$url" || return 1
  tar -xzf /tmp/apache-maven.tar.gz -C /opt 2>/dev/null || return 1
  ln -sf "${dest}/bin/mvn" /usr/local/bin/mvn
  command -v mvn >/dev/null 2>&1
}

# ---------- [1/7] 环境准备 ----------
info "[1/7] 检查并安装依赖"
command -v git >/dev/null 2>&1 || { apt-get update -y && apt-get install -y git; }
if [ "$NO_DOCKER" = "0" ]; then
  command -v docker >/dev/null 2>&1 || die "Docker 未安装（NO_DOCKER=1 可跳过 Docker 用 java -jar 启动）"
  if ! docker compose version >/dev/null 2>&1; then
    warn "Docker Compose 插件缺失，尝试自动安装 docker-compose-plugin ..."
    apt-get update -y >/dev/null 2>&1 || true
    if ! apt-get install -y docker-compose-plugin >/dev/null 2>&1 && ! apt-get install -y docker-compose-v2 >/dev/null 2>&1; then
      warn "常规安装失败，尝试国内 APT/Docker 源自愈（官方国外源在国内常不可达）..."
      if ! apt_docker_ce_selfheal; then
        die "Docker Compose 插件安装失败：国内服务器请先切换 APT 源为国内镜像并配置 docker-ce 源后重跑（详见 README 国内部署说明）"
      fi
    fi
    docker compose version >/dev/null 2>&1 || die "Docker Compose 插件安装后仍不可用，请检查 docker 服务后重跑"
  fi
  ok "Docker $(docker --version | awk '{print $3}') + Compose $(docker compose version --short 2>/dev/null)"
fi
if ! command -v java >/dev/null 2>&1; then
  apt-get install -y openjdk-21-jdk-headless 2>/dev/null || die "JDK 21 安装失败"
fi
if ! command -v mvn >/dev/null 2>&1; then
  if ! apt-get install -y maven >/dev/null 2>&1; then
    # maven 位于 universe 组件：先启用组件再装，仍失败走阿里镜像二进制
    apt_ensure_universe
    if ! apt-get install -y maven >/dev/null 2>&1; then
      install_maven_from_mirror || die "Maven 安装失败：apt 与阿里镜像均不可用（网络？）"
    fi
  fi
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
REPO_BASE="$(resolve_repo_base)"
if [ -n "${REPO_SWITCHED_NOTE:-}" ]; then
  warn "$REPO_SWITCHED_NOTE"
else
  ok "仓库源：$REPO_BASE"
fi
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
  if ! git fetch origin "$branch" 2>/dev/null; then
    # 单源失败自动切换镜像域名后重试（GitHub<->Gitee 同名镜像互换；显式 YPBIN_REPO 时不改 origin）
    local url repo_name
    url="$(git remote get-url origin 2>/dev/null || true)"
    if [ -z "${YPBIN_REPO:-}" ] && [ -n "$url" ]; then
      # 镜像与官方仓库同名（gitee.com/wenbin_wb/ypbin-*），按 URL 域名判定后拼同名镜像 URL
      repo_name="$(basename "$repo")"
      case "$url" in
        *github.com*)
          git remote set-url origin "$GITEE_REPO/$repo_name"
          warn "origin 切换 Gitee 镜像($GITEE_REPO/$repo_name)重试" ;;
        *gitee.com*)
          git remote set-url origin "$GITHUB_REPO/$repo_name"
          warn "origin 切换 GitHub($GITHUB_REPO/$repo_name)重试" ;;
        *) : ;;
      esac
      if git fetch origin "$branch" 2>/dev/null; then return 0; fi
    fi
    die "$repo fetch 失败（GitHub/Gitee 均不可达，请检查网络或指定 YPBIN_REPO 代理）"
  fi
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
  pull_repo "$ROOT/ypbin-admin-ui" main
fi
ok "代码就绪（starter@$(git -C "$ROOT/ypbin-starter" rev-parse --short HEAD)，admin@$(git -C "$ROOT/ypbin-admin" rev-parse --short HEAD)）"
fi

# ---------- [3/7] 构建 starter ----------
# 版本自动解析：优先环境变量，其次 admin pom（与仓库依赖一致，升级自动跟随）
if [ -z "$STARTER_VERSION" ]; then
  STARTER_VERSION="$(starter_version_from_pom "$ROOT/ypbin-admin/pom.xml" || true)"
fi
if [ "${SKIP_BUILD:-0}" = "1" ]; then
  info "[3/7] 跳过构建（restart 模式）"
else
if [ -n "$STARTER_VERSION" ]; then
info "[3/7] 构建 starter $STARTER_VERSION（微服务依赖其新能力）"
else
info "[3/7] 构建 starter（版本自动从 admin pom 解析，未取到将强制构建）"
fi
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
  # 确认本地仓库已有解析出的 starter 版本（没有则强制构建）
  if [ -n "$STARTER_VERSION" ] && [ -d "$HOME/.m2/repository/cn/ypbin/ypbin-starter-core/$STARTER_VERSION" ]; then
    ok "使用本地 Maven 仓库已有 starter $STARTER_VERSION"
  else
    [ -n "$STARTER_VERSION" ] && warn "本地 Maven 仓库无 starter $STARTER_VERSION，强制构建" || warn "未能解析 starter 版本，强制构建最新代码"
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

# 安全随机凭据生成（Nacos JWT 密钥要求 Base64 解码 ≥32 字节；hex 与 base64 字符集对 sed/compose 均安全）
rand_b64_48() { # Base64（解码 48 字节）
  local v
  v="$(openssl rand -base64 48 2>/dev/null | tr -d '\n')"
  [ -n "$v" ] || v="$(head -c 48 /dev/urandom | base64 2>/dev/null | tr -d '\n')"
  [ -n "$v" ] || die "无法生成随机凭据（缺 openssl/base64），请手工 export NACOS_AUTH_TOKEN 后重跑"
  printf '%s' "$v"
}
rand_hex() { # $1=字节数，输出 2 倍长度小写十六进制
  local v
  v="$(openssl rand -hex "$1" 2>/dev/null)"
  [ -n "$v" ] || v="$(head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n')"
  [ -n "$v" ] || die "无法生成随机凭据（缺 openssl/od），请手工 export 对应变量后重跑"
  printf '%s' "$v"
}

if [ ! -f "$ENV_FILE" ]; then
  MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-YpbinRoot$(date +%s)}"
  AI_MODEL_SECRET_KEY="${AI_MODEL_SECRET_KEY:-YpbinAiKey2026_32bytes!!}"
  # Nacos 服务端鉴权凭据：token 与身份标识值随机生成，避免固定默认值入库
  NACOS_AUTH_TOKEN="${NACOS_AUTH_TOKEN:-$(rand_b64_48)}"
  NACOS_AUTH_IDENTITY_KEY="${NACOS_AUTH_IDENTITY_KEY:-serverIdentity}"
  NACOS_AUTH_IDENTITY_VALUE="${NACOS_AUTH_IDENTITY_VALUE:-$(rand_hex 32)}"
  # 内部 Feign 调用凭证（/internal/** 守卫，auth/system/ai 共享一致值），随机生成
  INTERNAL_TOKEN="${INTERNAL_TOKEN:-$(rand_hex 32)}"
  # Redis：Docker 模式随机密码（与 compose requirepass / Nacos 共享配置一致）；
  # NO_DOCKER 用外部 Redis，默认留空=不认证（导入 Nacos 时删 password 行），有密码时以 REDIS_PASSWORD=xxx 传入
  if [ "$NO_DOCKER" = "1" ]; then
    REDIS_PASSWORD="${REDIS_PASSWORD:-}"
  else
    REDIS_PASSWORD="${REDIS_PASSWORD:-$(rand_hex 16)}"
  fi
  cat > "$ENV_FILE" <<EOF
# 由 install.sh 生成
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
AI_MODEL_SECRET_KEY=$AI_MODEL_SECRET_KEY
NACOS_AUTH_TOKEN=$NACOS_AUTH_TOKEN
NACOS_AUTH_IDENTITY_KEY=$NACOS_AUTH_IDENTITY_KEY
NACOS_AUTH_IDENTITY_VALUE=$NACOS_AUTH_IDENTITY_VALUE
INTERNAL_TOKEN=$INTERNAL_TOKEN
REDIS_PASSWORD=$REDIS_PASSWORD
NACOS_ADDR=${NACOS_ADDR:-nacos:8848}
SENTINEL_ADDR=${SENTINEL_ADDR:-sentinel-dashboard:8858}
ADMIN_UI_PORT=$ADMIN_UI_PORT
ADMIN_UI_DIST_DIR=$ADMIN_UI_DIST_DIR
EOF
  chmod 600 "$ENV_FILE"
  ok "已生成 .env（MySQL 密码：$MYSQL_ROOT_PASSWORD，可改 $ENV_FILE）"
else
  warn "复用已有 .env"
  # 复用场景需把 .env 变量载入环境，供后续 Nacos 占位符替换 / compose 使用
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

# 向后兼容：旧 .env 缺新增凭据键时补生成（幂等；避免 compose :? 强制校验失败）
env_key_backfill() { # $1=键名 $2=取值命令（仅缺键时才执行，命令为内部固定串）
  local key="$1" val
  if ! grep -q "^${key}=" "$ENV_FILE"; then
    val="$(eval "$2")"
    printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    export "$key=$val"
    warn "已为旧 .env 补生成 ${key}"
  fi
}
env_key_backfill NACOS_AUTH_TOKEN 'rand_b64_48'
env_key_backfill NACOS_AUTH_IDENTITY_KEY 'printf serverIdentity'
env_key_backfill NACOS_AUTH_IDENTITY_VALUE 'rand_hex 32'
env_key_backfill INTERNAL_TOKEN 'rand_hex 32'
if [ "$NO_DOCKER" = "1" ]; then
  env_key_backfill REDIS_PASSWORD 'printf ""'
else
  env_key_backfill REDIS_PASSWORD 'rand_hex 16'
fi

# ---------- [5.5/7] 启动基础设施并初始化（Nacos 配置 + MySQL 库表）----------
# Docker 模式：先只启动基础设施（nacos/redis/mysql），配置导入和建库完成后再启动业务服务
if [ "$NO_DOCKER" = "1" ]; then
  info "[5.5/7] NO_DOCKER 模式：假定外部 Nacos/Redis/MySQL 已就绪，直接导入 Nacos 配置"
else
  info "[5.5/7] 启动基础设施（Nacos/Redis/MySQL）"
  cd "$ROOT/ypbin-admin/deploy"
  # 官方 Docker Hub 在国内常不可达：REGISTRY_PREFIX 显式指定 → 只试该前缀；
  # 未指定时先试官方源，再对"连通性探测通过"的国内公共镜像加速逐个尝试（首个成功即用）。
  REGISTRY_CANDIDATE_DOMAINS="docker.m.daocloud.io docker.1ms.run docker.1panel.live docker.1panel.top hub.rat.dev dockerpull.org docker.xuanyuan.me dockerproxy.cn docker.rainbond.cc"
  DOCKER_REGISTRY_CANDIDATES=""
  for d in $REGISTRY_CANDIDATE_DOMAINS; do
    # registry v2 探活（5s 快超时）：200/301/302/401 均视为可达（401 为正常未认证响应，
    # 不能用 curl -f——会把 401 误判失败跳过可达源）；其余状态/超时视为不通立即跳过
    code=$(timeout 5 curl -sI -o /dev/null -w '%{http_code}' "https://$d/v2/" 2>/dev/null || true)  # 探活失败不中断(set -e)
    case "$code" in
      200|301|302|401) DOCKER_REGISTRY_CANDIDATES="$DOCKER_REGISTRY_CANDIDATES ${d}/" ;;
      *) warn "镜像加速 ${d} 探活失败(HTTP ${code:-不通})，跳过" ;;
    esac
  done
  [ -n "${REGISTRY_PREFIX:-}" ] && DOCKER_REGISTRY_CANDIDATES="${REGISTRY_PREFIX%/}/"
  infra_up() { # $1=REGISTRY_PREFIX(含尾/或空=官方)
    if [ -z "$1" ]; then
      REGISTRY_PREFIX= docker compose -f docker-compose.yml --env-file "$ENV_FILE" up -d nacos redis mysql
    else
      REGISTRY_PREFIX="$1" docker compose -f docker-compose.yml --env-file "$ENV_FILE" up -d nacos redis mysql
    fi
  }
  infra_ok=0
  # 官方源先试；随后逐个尝试探测通过的国内加速
  for reg in "" $DOCKER_REGISTRY_CANDIDATES; do
    if infra_up "$reg" >/tmp/infra-up.log 2>&1; then
      if [ -n "$reg" ]; then
        ok "基础设施镜像经镜像加速拉取成功：${reg%/}"
        echo "REGISTRY_PREFIX=$reg" >> "$ENV_FILE"
        warn "已将 REGISTRY_PREFIX=$reg 写入 .env（后续 compose up 复用）"
      fi
      infra_ok=1
      break
    fi
    [ -n "$reg" ] && warn "镜像加速 ${reg%/} 拉取失败，尝试下一个..."
  done
  if [ "$infra_ok" != "1" ]; then
    tail -5 /tmp/infra-up.log 2>/dev/null || true
    die "基础设施镜像拉取失败（Docker Hub 与国内加速均不可达）：请手动 export REGISTRY_PREFIX=<可用加速前缀> 后重跑"
  fi
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
  info "导入 Nacos 配置中心（ypbin-common + 4 服务）"
  NACOS_DIR="$ROOT/ypbin-admin/deploy/nacos"
  for cfg in ypbin-common ypbin-gateway ypbin-auth ypbin-system ypbin-ai; do
    if [ -f "$NACOS_DIR/$cfg.yaml" ]; then
      # 占位符替换：仓库 nacos yaml 不提交真实密码/凭证，导入前用 .env 实际值填充
      # （仅 ypbin-common.yaml 使用 ${MYSQL_ROOT_PASSWORD}/${REDIS_PASSWORD}/${INTERNAL_TOKEN}；
      #   替换键名与 yaml 占位符完全一致）
      TMP_CFG="/tmp/nacos-${cfg}.yaml"
      if [ -n "${REDIS_PASSWORD:-}" ]; then
        sed -e "s/\${MYSQL_ROOT_PASSWORD}/${MYSQL_ROOT_PASSWORD}/g" \
            -e "s/\${REDIS_PASSWORD}/${REDIS_PASSWORD}/g" \
            -e "s/\${INTERNAL_TOKEN}/${INTERNAL_TOKEN}/g" \
            "$NACOS_DIR/$cfg.yaml" > "$TMP_CFG"
      else
        # REDIS_PASSWORD 为空（NO_DOCKER 外部 Redis 不认证）→ 删除 password 行，等价不配置密码；
        # INTERNAL_TOKEN 仍无条件替换（缺失/为空时 system 守卫 fail-closed，见 ypbin.internal.token 注释）
        sed -e "s/\${MYSQL_ROOT_PASSWORD}/${MYSQL_ROOT_PASSWORD}/g" \
            -e "s/\${INTERNAL_TOKEN}/${INTERNAL_TOKEN}/g" \
            -e "/password: \${REDIS_PASSWORD}/d" \
            "$NACOS_DIR/$cfg.yaml" > "$TMP_CFG"
      fi
      curl -fsS -X POST "$NACOS_CONSOLE_URL/v3/console/cs/config" \
        -H "accessToken: $NACOS_TOKEN" \
        --data-urlencode "dataId=$cfg.yaml" \
        --data-urlencode "groupName=DEFAULT_GROUP" \
        --data-urlencode "type=yaml" \
        --data-urlencode "namespaceId=" \
        --data-urlencode "content@$TMP_CFG" \
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
      # xxl-job 初始化脚本自带 CREATE DATABASE xxl_job + use，不指定库执行
      if [ "$(basename "$sql")" = "005-xxl-job.sql" ]; then
        docker cp "$sql" ypbin-mysql:/tmp/init-xxl.sql
        docker exec ypbin-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" < /tmp/init-xxl.sql"
      else
        docker cp "$sql" ypbin-mysql:/tmp/init.sql
        docker exec ypbin-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" ypbin_admin < /tmp/init.sql"
      fi
      ok "已执行 $(basename "$sql")"
    done
  else
    ok "MySQL 已初始化，跳过建库脚本"
    # ypbin_admin 已存在时仍确保 xxl_job 库（xxl-job-admin 独立库）就绪
    XXL_TABLE_COUNT=$(docker exec ypbin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='xxl_job';" 2>/dev/null || echo 0)
    if [ "${XXL_TABLE_COUNT:-0}" = "0" ] && [ -f "$ROOT/ypbin-admin/deploy/sql/005-xxl-job.sql" ]; then
      docker cp "$ROOT/ypbin-admin/deploy/sql/005-xxl-job.sql" ypbin-mysql:/tmp/init-xxl.sql
      docker exec ypbin-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" < /tmp/init-xxl.sql"
      ok "已执行 005-xxl-job.sql（xxl_job 库初始化）"
    fi
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
