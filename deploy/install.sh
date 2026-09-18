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
#   AI_MODEL_SECRET_KEY=           AI 模型 API Key 的加密密钥（16/24/32 字节）。
#                                  首次全新部署未显式提供时自动随机生成并写入 .env（请妥善保存）；
#                                  复用旧 .env 时必须沿用旧值（换新值后旧密文无法解密）；
#                                  多分支共用同一套密文时须显式传相同值。
#   NACOS_AUTH_TOKEN= NACOS_AUTH_IDENTITY_KEY= NACOS_AUTH_IDENTITY_VALUE=
#                                  Nacos 服务端鉴权凭据（自动随机生成，一般无需手传；
#                                  NACOS_AUTH_TOKEN 需 Base64 且解码后 ≥32 字节）
#   INTERNAL_TOKEN=                /internal/** 服务间 Feign 调用凭证（守卫校验，自动随机生成，
#                                  auth/system/ai 共享一致值，一般无需手传）
#   GATEWAY_SIGN_TOKEN=            网关身份头签名标记（防伪造，自动随机生成；gateway 签发、
#                                  auth/system/ai 校验，一般无需手传）
#   REBUILD_FRONTEND=1             强制重新构建前端。默认：产物比源码新则复用、否则自动重建
#                                  （改了前端源码或 apps/web-antd/.env* 后重跑即可，无需先删 admin-ui-dist）；
#                                  SKIP_FRONTEND=1 则永不构建（必须已有产物）
#   REGISTRY_PREFIX=               Docker 镜像前缀（如加速源 docker.m.daocloud.io/）。留空=用机器默认：
#                                  直接走 Docker 守护进程配置的 registry-mirrors，无配置即官方 Docker Hub；
#                                  脚本不做任何镜像源探测/遍历。需要加速源（或强制官方源 docker.io/）时显式设置：
#                                  export REGISTRY_PREFIX=<前缀>/，或写入 .env 的 REGISTRY_PREFIX=...
#   NO_DOCKER=1                    无 Docker 模式：java -jar 直接启动
# ============================================================

set -euo pipefail
trap 'echo "!! 脚本执行失败于第 ${LINENO} 行"' ERR

# ---------- 非 root 自提权（对齐单体脚本）----------
# /opt/ypbin 由 root 创建（部署目录），非 root 用户构建会因写 target/ 权限失败；
# 自动 sudo -E 以 root 重新执行本脚本。管道执行（bash <(curl ...)）时脚本无真实文件，
# 先下载到 /tmp 再 sudo 执行（与单体脚本一致）。
SCRIPT_VERSION="2026.09.16.1"
SCRIPT_URL="${YPBIN_SCRIPT_URL:-https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh}"
GITEE_SCRIPT_URL="${GITEE_SCRIPT_URL:-https://gitee.com/wenbin_wb/ypbin-admin/raw/main/deploy/install.sh}"
# 所有对外 curl 一律带硬超时：libcurl 默认连接超时为 300 秒，国内网络对 GitHub 常见
# 「SYN 黑洞」（既不 reset 也不响应），无超时的 curl 会静默挂起约 5 分钟后才报
# `curl: (28) Failed to connect to raw.githubusercontent.com port 443 after ~279000 ms`。
CURL_CONNECT_TIMEOUT=8
CURL_MAX_TIME=60
# 脚本源连通探测（3s 快超时）：GitHub raw 不可达时降级 Gitee raw（内容同源）。
# 注意：本函数在「工具函数」区之前执行，warn 尚未定义，提示必须直接写 stderr。
# stdout 只输出 URL，避免 ANSI/文案被 $( ) 吞进变量。
resolve_script_url() {
  if [ -n "${YPBIN_SCRIPT_URL:-}" ]; then printf '%s' "$SCRIPT_URL"; return 0; fi
  if curl -fsSI --connect-timeout 3 --max-time 5 -o /dev/null "$SCRIPT_URL" 2>/dev/null; then
    printf '%s' "$SCRIPT_URL"
  else
    printf '!  GitHub raw 不可达（3s 探测超时），脚本源降级为 Gitee：%s\n' "$GITEE_SCRIPT_URL" >&2
    printf '%s' "$GITEE_SCRIPT_URL"
  fi
}
# 备用脚本源：$1 为一个源，返回另一个源（两源互为镜像，用于下载失败兜底重试）
alternate_script_url() {
  if [ "$1" = "$SCRIPT_URL" ]; then printf '%s' "$GITEE_SCRIPT_URL"; else printf '%s' "$SCRIPT_URL"; fi
}
# 下载脚本自身到 $1，双源兜底；成功返回 0，两源均失败返回 1
download_self() {
  local dest="$1" src alt
  src="$(resolve_script_url)"
  if curl -fsSL --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_MAX_TIME" -o "$dest" "$src" 2>/dev/null; then
    return 0
  fi
  rm -f "$dest"
  alt="$(alternate_script_url "$src")"
  printf '!  脚本源 %s 下载失败，改用备用源重试：%s\n' "$src" "$alt" >&2
  curl -fsSL --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_MAX_TIME" -o "$dest" "$alt"
}
if [ "$(id -u)" != "0" ]; then
  if command -v sudo >/dev/null 2>&1; then
    SELF="/tmp/ypbin-install.sh"
    if [ ! -f "$SELF" ] || ! grep -q "SCRIPT_VERSION=\"${SCRIPT_VERSION}\"" "$SELF" 2>/dev/null; then
      echo "非 root 用户，下载脚本并用 sudo 提权执行..."
      download_self "$SELF" || { rm -f "$SELF"; echo "下载脚本失败（GitHub/Gitee 均不可达，请检查网络或代理）" >&2; exit 1; }
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
      curl -fsSL --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time 30 "https://mirrors.aliyun.com/docker-ce/linux/${id}/gpg" 2>/dev/null | gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null && {
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/${id} ${codename} stable" > /etc/apt/sources.list.d/docker.list 2>/dev/null || true
        apt-get update -y >/dev/null 2>&1 || true
      }
    fi
    # 自愈分支的 apt 尝试同样留档：这才是「切到国内源 + docker-ce 源之后」的真实报错，最值得回显
    { apt-get install -y docker-compose-plugin 2>&1 | tee -a /tmp/docker-compose-install.log >/dev/null; } \
      || { apt-get install -y docker-compose-v2 2>&1 | tee -a /tmp/docker-compose-install.log >/dev/null; } \
      || return 1
  fi
  docker compose version >/dev/null 2>&1
}

# 从 admin pom 提取 ypbin-starter.version（形如 <ypbin-starter.version>2.2.3</...>）
starter_version_from_pom() {
  local pom="$1"
  [ -f "$pom" ] || return 1
  sed -n 's/.*<ypbin-starter.version>\([^<]*\)<\/ypbin-starter.version>.*/\1/p' "$pom" | head -1
}

# 读取 starter 仓库根 pom 的 revision。
# starter 用 flatten-maven-plugin 的 ${revision} 统一版本，与 admin 侧的
# <ypbin-starter.version> 是两个不同的标签名——不能复用 starter_version_from_pom，
# 否则读不到值（本次就是因此把「实际构建出的版本」打成了未知）。
starter_revision_from_pom() {
  local pom="$1"
  [ -f "$pom" ] || return 1
  local rev
  rev="$(sed -n 's|.*<revision>\([^<]*\)</revision>.*|\1|p' "$pom" | head -1)"
  [ -n "$rev" ] || return 1
  printf '%s' "$rev"
}

# 选择构建 starter 用的代码引用：优先与 admin 依赖版本一致的 tag。
# 背景：admin 固定的是「已发布版本」，而 starter 的默认分支在发布后会推进到下一个开发版本
# （x.y.z-SNAPSHOT）。若直接构建默认分支，装进 .m2 的是 SNAPSHOT，而 admin 需要的那个正式版
# 只能改从远程仓库取——远程尚未同步（刚发布）或上次失败被 Maven 缓存时，就会直接构建失败。
# 结果写入全局 STARTER_BUILD_REF（不用命令替换回显：warn 写的是 stdout，回显会把告警混进变量）。
is_release_version() { # 判定是否为「发布版」（非 SNAPSHOT、非空）
  case "$1" in
    ""|*-SNAPSHOT) return 1 ;;
    *) return 0 ;;
  esac
}

resolve_starter_build_ref() {
  local repo="$1"
  local tag="v${STARTER_VERSION}"
  # detached 下 rev-parse --abbrev-ref HEAD 返回字面量 HEAD，用 symbolic-ref 才能得到可读状态
  STARTER_BUILD_REF="$(git -C "$repo" symbolic-ref -q --short HEAD 2>/dev/null || echo detached)"
  if [ -z "$STARTER_VERSION" ]; then
    return 0
  fi
  local fetch_err
  if ! fetch_err="$(git -C "$repo" fetch --tags --quiet 2>&1)"; then
    # 与「检出失败」同理：不许把 git 的真实原因用 2>/dev/null 吞掉后拿"离线或远端不可达"顶替
    # （认证失败、远端未配置、DNS 失败等都会走到这里，表现相同）。
    warn "无法从远程更新 tag（改用本地已有 tag）。git fetch 的原始输出："
    # 脱敏：远端 URL 若内嵌了 user:token@ 形式的凭据，git 的报错会原样回显，这里先抹掉再打印
    printf '%s\n' "$fetch_err" | head -n 3 \
      | sed -E 's#(https?://)[^@/[:space:]]+@#\1<redacted>@#g' | sed 's/^/    /'
    info "  自查：git -C $repo remote -v；git -C $repo fetch --tags   # 亲手复现并看真实报错"
  fi
  if git -C "$repo" rev-parse -q --verify "refs/tags/$tag" >/dev/null 2>&1; then
    local checkout_err
    if checkout_err="$(git -C "$repo" checkout -q "refs/tags/$tag" 2>&1)"; then
      STARTER_BUILD_REF="$tag"
      return 0
    fi
    # 检出失败必须带上 git 的真实原因（原来 2>/dev/null 把它吞了，只剩一句「检出失败」）
    local reason
    reason="$(printf '%s' "$checkout_err" | head -1)"
    if is_release_version "$STARTER_VERSION"; then
      die "无法检出 tag $tag：$reason
     admin 固定的 starter 版本是发布版 $STARTER_VERSION，必须构建该版本才能把它装进本地仓库；
     继续按分支构建只会产出别的版本，第 [4/7] 步必然失败（且报错点在依赖解析，难以回溯到这里）。
     请先处理工作区后重跑：git -C $repo status --short"
    fi
    warn "找到 tag $tag 但检出失败（$reason），改为构建当前分支 $STARTER_BUILD_REF"
    return 0
  fi
  if is_release_version "$STARTER_VERSION"; then
    die "未找到 tag $tag（当前引用 $STARTER_BUILD_REF）。
     admin 固定的 starter 版本是发布版 $STARTER_VERSION；而 Maven 镜像可能只同步了 pom、缺 jar
     （该现象已实测存在），所以必须用 tag 构建才能把全部产物装进本地仓库。
     请确认 tag 是否存在并已同步：git -C $repo fetch --tags && git -C $repo tag -l $tag"
  fi
  warn "未找到 tag $tag，改为构建当前分支 $STARTER_BUILD_REF（其 revision 未必是 admin 依赖的 $STARTER_VERSION）"
  return 0
}

# Docker 根目录磁盘空间预检（仅告警，不阻断——空间紧张但仍可能构建成功）。
# 现场教训：构建/拉取镜像写到一半报 "no space left on device"，报错点在 containerd 写镜像层，
# 看上去像镜像或构建问题，实际是磁盘不足。
check_docker_disk_space() {
  local min_gb="${1:-5}" root_dir avail_kb avail_gb
  root_dir="$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || true)"
  [ -n "$root_dir" ] || root_dir=/var/lib/docker
  avail_kb="$(df -Pk "$root_dir" 2>/dev/null | awk 'NR==2 {print $4}')"
  [ -n "$avail_kb" ] || return 0
  avail_gb=$((avail_kb / 1024 / 1024))
  if [ "$avail_gb" -lt "$min_gb" ]; then
    warn "Docker 根目录 $root_dir 所在磁盘仅剩约 ${avail_gb}GB（建议 ≥${min_gb}GB）"
    warn "构建 4 个服务镜像 + 前端依赖都会显著占空间；不足时会在写镜像层时报 no space left on device"
    warn "清理：docker builder prune -af && docker image prune -f && docker system df"
    return 1
  fi
  return 0
}

# compose 启动/构建失败的原因分类与处置提示（读取 /tmp/compose-up.log）
compose_up_diagnose() {
  local log="$1"
  if grep -q "no space left on device" "$log" 2>/dev/null; then
    warn "真因是【磁盘空间不足】（写镜像层/构建缓存失败），不是 compose 配置或镜像源问题"
    warn "处置：docker builder prune -af && docker image prune -f；仍不足则扩容或迁移 Docker 数据目录"
    warn "      前端产物已拷到 admin-ui-dist，可安全删除 ypbin-admin-ui/node_modules 释放数 GB"
    df -h 2>/dev/null | head -5 || true
    return 10
  fi
  if grep -qE "port is already allocated|Bind for [^ ]* failed" "$log" 2>/dev/null; then
    # 这里原本写「处置见上方 [5.5/7] 同类提示」——但 [5.5/7] 基础设施那一步可能根本没报过错，
    # 上方并不存在该提示（悬空指引），故把判据与处置就地写全，不依赖别的阶段是否打印过。
    local port holder
    port="$(sed -n 's/.*Bind for [^:]*:\([0-9][0-9]*\) failed.*/\1/p' "$log" | head -1)"
    holder="$(docker ps -a --format '{{.Names}}|{{.Ports}}' 2>/dev/null | grep -F ":${port}->" | head -1 || true)"
    warn "真因是【宿主机端口被占用】（与镜像无关和 compose 配置无关），端口 ${port:-?}"
    [ -n "$holder" ] && warn "占用者疑似容器：${holder}"
    warn "自查：sudo ss -ltnp | grep :${port:-8080}；docker ps -a --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'"
    warn "处置：停止占用该端口的进程/容器（本套残留容器可 docker rm -f <容器名>），或改端口后重跑"
    return 11
  fi
  warn "compose 启动失败，完整日志：$log"
  return 12
}

# 逐个校验基础设施与核心服务容器是否真的在运行。
# 现场教训：[7/7] 原本只探测网关 /actuator/health——网关起来了就打印「部署完成」，
# 而 ypbin-system/auth/ai 可能因依赖未就绪处于崩溃重启循环，使用者直到打开页面才发现。
check_service_containers() {
  local spec container status restarts failed=0
  for container in ypbin-mysql ypbin-redis ypbin-nacos; do
    status="$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || echo missing)"
    if [ "$status" != "running" ]; then
      warn "$container 容器状态异常：$status"
      docker logs --tail 15 "$container" 2>&1 | sed 's/^/    /' || true
      failed=1
    fi
  done
  for spec in $SERVICES; do
    # $SERVICES 的第二个字段就是 container_name（compose 里显式声明为 ypbin-xxx），不要再加前缀
    container="$(printf '%s' "$spec" | cut -d: -f2)"
    status="$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || echo missing)"
    restarts="$(docker inspect -f '{{.RestartCount}}' "$container" 2>/dev/null || echo 0)"
    if [ "$status" != "running" ]; then
      warn "$container 容器状态异常：$status（重启 $restarts 次）"
      docker logs --tail 15 "$container" 2>&1 | sed 's/^/    /' || true
      failed=1
    elif [ "${restarts:-0}" -gt 0 ] 2>/dev/null; then
      # 重启计数只证明「发生过重启」，不构成任何原因判据（依赖未就绪、OOM、配置错、崩溃循环……表现相同），
      # 故不写"可能因依赖未就绪"这类结论，改为回显容器日志实际内容 + 给自查命令。
      warn "$container 运行中，但重启过 $restarts 次（重启计数只说明发生过重启，**不能据此判断原因**）。容器日志尾部："
      docker logs --tail 15 "$container" 2>&1 | sed 's/^/    /' || true
      info "  自查：docker inspect $container --format '{{.State.StartedAt}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}'"
      info "        docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 $container"
      info "  具体原因需按上面日志判断（退出码 137/OOMKilled=true 与依赖未就绪的日志形态完全不同）。"
    else
      ok "$container 运行中"
    fi
  done
  if [ "$failed" != "0" ]; then
    warn "有容器未处于运行状态。排查：docker logs --tail 50 <容器名>（注意 compose 服务名与容器名在基础设施上不同：服务名 mysql/nacos/redis ↔ 容器名 ypbin-mysql/ypbin-nacos/ypbin-redis）"
  fi
  return 0
}

# ---------- pnpm 可用性预检与失败分类诊断 ----------
# 现场教训（一手证据）：pnpm 的单文件可执行版（@pnpm/exe）依赖系统 libatomic.so.1，缺库时 pnpm
# **连 `--version` 都跑不起来**，原始输出：
#   .../@pnpm/exe/11.16.0/.../node_modules/@pnpm/exe/pnpm:
#     error while loading shared libraries: libatomic.so.1: cannot open shared object file: No such file or directory
# 而旧逻辑把这类失败一律猜成「最常见原因：大包拉取超时 [23]」，让现场把排查方向全押在网络/超时上，
# 只能翻原始日志才看到真因。故此处改为：**先预检**并拿住 pnpm 的输出与退出码，再按输出里的
# **具体证据**分类诊断；判不出来就原样回显关键错误行 + 给自查命令，绝不再给"可能的原因"。
PNPM_LOG=/tmp/ypbin-pnpm-install.log          # 本次 install 的完整输出（供失败后回显关键行）
PNPM_PRECHECK_LOG=/tmp/ypbin-pnpm-precheck.log # pnpm --version 的退出码 + 原始输出
PNPM_APT_LOG=/tmp/ypbin-pnpm-apt.log          # 自动安装 libatomic1 的输出

# 预检：pnpm 本身能否被加载并执行。输出与退出码都留档，失败时不丢原始证据。
pnpm_precheck() {
  local out rc
  out="$(pnpm --version 2>&1)" && rc=0 || rc=$?
  printf 'exit=%s\n%s\n' "$rc" "$out" > "$PNPM_PRECHECK_LOG"
  if [ "$rc" = "0" ]; then
    ok "pnpm 可用性预检通过：pnpm $(printf '%s' "$out" | head -n1)"
    return 0
  fi
  warn "pnpm 可用性预检失败：\"pnpm --version\" 退出码 $rc，原始输出如下——"
  printf '%s\n' "$out" | sed 's/^/    /'
  return 1
}

# —— 分类依据一律是工具自己打印的原文特征串，不是"经验上最常见" ——
# 缺系统运行库（动态链接器报的错）
pnpm_output_is_missing_lib() {
  grep -qE 'error while loading shared libraries|cannot open shared object file' "$1" 2>/dev/null
}
# 网络/超时类（pnpm 与 Node 的取数失败特征串）
pnpm_output_is_network() {
  grep -qE 'aborted due to timeout|ERR_PNPM_FETCH|ETIMEDOUT|ESOCKETTIMEDOUT|\[23\]|ECONNRESET|ECONNREFUSED|ENOTFOUND|EAI_AGAIN|socket hang up|Failed to fetch|request to .* failed' "$1" 2>/dev/null
}
# 从报错里取出**具体缺哪个库**（形如 libatomic.so.1），据此给精确命令而不是笼统"装 gcc"
pnpm_missing_lib_name() {
  grep -oE '[A-Za-z0-9_.+-]+\.so(\.[0-9]+)*' "$1" 2>/dev/null | head -n1
}

# 缺系统库的修复指引（返回 0 = 已自动修好，可继续；1 = 需人工处理）。
# 已核实（一手）：Debian/Ubuntu 上 libatomic.so.1 由 libatomic1 提供（实测下载 noble-updates 的
# libatomic1_14.2.0-4ubuntu2~24.04.1_amd64.deb，内含 /usr/lib/x86_64-linux-gnu/libatomic.so.1）；
# rpm 系（RHEL/CentOS/Alma/Rocky/Fedora）包名为 libatomic，提供 libatomic.so.1()(64bit)
# （实测 AlmaLinux 9 BaseOS repodata primary.xml：libatomic-11.5.0-14.el9.alma.x86_64）。
#
# 自动修的判断：**只**在「缺失库就是 libatomic.so.*」且「root」且「Debian 系且 apt-get 可用」时动手。
# 理由：libatomic1 是发行版官方仓库里 10KB 级的运行库、无服务重启/数据副作用，装完 pnpm 即可用，
# 收益（现场不必人工介入）明显大于风险；而 rpm 系包名与命令都不同、apt 锁竞争、缺的是别的库
# 这三种情况一律不动手，只打印命令 —— 猜错包名去装比不装更糟。
pnpm_report_missing_lib() {
  local lib="$1"
  warn "判定依据（pnpm 原文特征）：error while loading shared libraries / cannot open shared object file"
  warn "这是【系统运行库缺失】：pnpm 可执行文件根本没被加载起来，**不是网络/超时问题**——"
  warn "放宽 fetch-timeout、增加 fetch-retries 这类处置对它完全无效。缺失的库：${lib:-（未能从输出解析出库名）}"
  info "自查（可复算）：ldd \"\$(command -v pnpm)\" | grep 'not found'"
  case "$lib" in
    libatomic.so*)
      local apt_cmd="apt-get update && apt-get install -y libatomic1"
      local rpm_cmd="dnf install -y libatomic   # rpm 系：yum install -y libatomic 亦可"
      info "Debian/Ubuntu 修复命令：$apt_cmd"
      info "rpm 系（RHEL/CentOS/Alma/Rocky/Fedora）包名不同：$rpm_cmd"
      ;;
    *)
      info "本脚本不代为猜测包名。请用包管理器反查该库由哪个包提供："
      info "  Debian/Ubuntu：apt-get install -y apt-file && apt-file search ${lib:-<库名>}"
      info "  rpm 系：dnf provides '*/${lib:-<库名>}'"
      return 1
      ;;
  esac
  if [ "$(id -u)" != "0" ]; then
    warn "当前不是 root，不自动安装；请以 root 执行上面打印的命令后重跑本脚本。"
    return 1
  fi
  if ! command -v apt-get >/dev/null 2>&1; then
    warn "未找到 apt-get（非 Debian 系），不自动安装；请按上面打印的命令人工处理。"
    return 1
  fi
  local distro_id="unknown"
  [ -f /etc/os-release ] && distro_id="$(. /etc/os-release && echo "${ID:-unknown}")"
  case "$distro_id" in
    ubuntu|debian|linuxmint|pop|zorin) ;;
    *)
      warn "当前发行版 $distro_id 非 Debian 系，不自动执行 apt（避免装错包）；请人工处理。"
      return 1
      ;;
  esac
  info "自动修复：即将以 root 执行 → $apt_cmd （完整输出见 $PNPM_APT_LOG）"
  if apt-get install -y libatomic1 >"$PNPM_APT_LOG" 2>&1 \
    || { apt-get update -y >>"$PNPM_APT_LOG" 2>&1 && apt-get install -y libatomic1 >>"$PNPM_APT_LOG" 2>&1; }; then
    ok "libatomic1 安装完成"
    if pnpm --version >/dev/null 2>&1; then
      ok "pnpm 预检复验通过：pnpm $(pnpm --version 2>/dev/null | head -n1)"
      return 0
    fi
    warn "已装 libatomic1，但 pnpm 仍起不来（缺的库可能不止这一个，按下面命令看实际缺哪些，不猜）："
    warn "自查：ldd \"\$(command -v pnpm)\" | grep 'not found'"
    return 1
  fi
  # 自动安装失败的原因不做断言：apt 锁被占用、源不可达、包名在该发行版不同……表现都在下面的输出里，
  # 故原样回显 apt 输出尾部（这是判据），只给可执行的下一步。
  warn "自动安装失败，原因见下面原样回显的 apt 输出尾部（apt 锁被占用 / 源不可达 / 包名与发行版不符都会走到这里，具体以输出为准）："
  tail -n 15 "$PNPM_APT_LOG" 2>/dev/null | sed 's/^/    /'
  warn "请手工执行：$apt_cmd"
  return 1
}

# 无法判定原因时的自查清单（只给命令，不给结论）
pnpm_selfcheck_hint() {
  info "自查命令（逐条在服务器上执行，按真实报错定位）："
  info "  1) pnpm --version                                # pnpm 能否被加载执行"
  info "  2) ldd \"\$(command -v pnpm)\" | grep 'not found'   # 有输出即为缺系统库"
  info "  3) df -h \"$ROOT\"                                  # 磁盘空间（前端依赖需数 GB）"
  info "  4) tail -n 50 $PNPM_LOG                           # 本次安装的原始日志尾部"
}

# 预检失败的诊断入口
pnpm_diagnose_precheck_failure() {
  if pnpm_output_is_missing_lib "$PNPM_PRECHECK_LOG"; then
    pnpm_report_missing_lib "$(pnpm_missing_lib_name "$PNPM_PRECHECK_LOG")" && return 0
    return 1
  fi
  if ! command -v pnpm >/dev/null 2>&1; then
    warn "判定依据：command -v pnpm 无输出 —— pnpm 未安装或不在 PATH，同样不是库缺失/网络问题。"
    info "自查：command -v pnpm；echo \"\$PATH\""
    info "处置：npm install -g pnpm（或把 pnpm 所在目录并入 PATH）后重跑本脚本。"
    return 1
  fi
  warn "pnpm 起不来，但其输出中没有可识别的失败特征，**无法判定原因**（不做猜测）。"
  pnpm_selfcheck_hint
  return 1
}

# install 仍失败后的诊断入口（保留重试一次之后的收尾）
pnpm_diagnose_install_failure() {
  local log="$1"
  if pnpm_output_is_missing_lib "$log"; then
    pnpm_report_missing_lib "$(pnpm_missing_lib_name "$log")" && return 0
    return 1
  fi
  if pnpm_output_is_network "$log"; then
    warn "判定依据（日志原文特征）：timeout / ERR_PNPM_FETCH / [23] 等 —— 归为【网络/超时】类。"
    info "本脚本已放宽 fetch-timeout=600000、fetch-retries=5、network-concurrency=8 并已重试一次；"
    info "如需再试，在服务器手工重试（不改包管理器、不改 pnpm 版本）："
    info "  cd $ROOT/ypbin-admin-ui && pnpm config set fetch-timeout 600000 && pnpm install"
    info "自查：df -h \"$ROOT\"（磁盘）；curl -fsSI --connect-timeout 8 https://registry.npmmirror.com（registry 连通性）"
    return 1
  fi
  warn "无法判定具体原因（日志里没有可识别的失败特征）——不猜测，下面原样回显最后 30 行："
  tail -n 30 "$log" 2>/dev/null | sed 's/^/    /'
  pnpm_selfcheck_hint
  return 1
}

# 安装前端依赖：放宽 pnpm 拉取超时并自动重试一次。
# 现场教训：大包（@iconify/json ~95MB、@turbo/linux-64 ~19MB）在 pnpm 默认 60s 拉取超时下会中断，
# 报 [23] The operation was aborted due to timeout；此时即使已复用 1600+ 个包，整个安装仍算失败。
# 放宽 fetch-timeout / 重试次数并下调并发，比"推倒重来"更符合现场（失败一次即整体失败，重试代价低）。
# ⚠️ 那只是**其中一类**失败；pnpm 自身起不来（缺系统库等）必须先按证据识别，见上方预检区。
install_frontend_deps() {
  local ui_dir="$ROOT/ypbin-admin-ui"
  # ① 预检 pnpm 可用性：起不来就立刻精确报因并返回，不再往下装依赖
  #   （否则加载错误会被 install 的噪音淹没 —— 现场正是这样被误报成"拉包超时"的）
  if ! pnpm_precheck; then
    # 诊断返回 0 = 已按证据精确报因且自动修好，继续装依赖；否则立即失败（不再往下误导）
    pnpm_diagnose_precheck_failure || { return 1; }
  fi
  pnpm config set registry https://registry.npmmirror.com >/dev/null 2>&1 || true
  pnpm config set fetch-timeout 600000 >/dev/null 2>&1 || true
  pnpm config set fetch-retries 5 >/dev/null 2>&1 || true
  pnpm config set fetch-retry-maxtimeout 600000 >/dev/null 2>&1 || true
  pnpm config set network-concurrency 8 >/dev/null 2>&1 || true
  # tee 仅为留档；脚本已 set -o pipefail，故 if 判定的仍是 pnpm 子 shell 的退出码（语义不变）
  if (cd "$ui_dir" && pnpm install --frozen-lockfile 2>&1 || pnpm install 2>&1) | tee "$PNPM_LOG"; then
    return 0
  fi
  # 重试口径说明（避免"说会重试"却和首次一模一样）：放宽 fetch-timeout/重试次数/并发是在**首次尝试之前**
  # 就已生效的配置，本步骤不再改任何参数、也不清理 node_modules/store，即**原样重跑**。
  # 因此它只对「瞬时网络抖动」有意义；确定性失败（磁盘不足、registry 不可达、lockfile 与 package.json
  # 不一致、需重新编译的原生依赖等）会必然复现——下面按这个口径如实说明，不夸大成"已修复"。
  warn "前端依赖安装失败。已完成 fetch-timeout/fetch-retries/network-concurrency 放宽（首次尝试前即已生效），现原样重试一次 pnpm install……"
  warn "（该重试仅对瞬时网络抖动有效；若为确定性失败会再次失败，原因按随后打印的日志与自查命令判断）"
  if (cd "$ui_dir" && pnpm install 2>&1) | tee "$PNPM_LOG"; then
    return 0
  fi
  warn "前端依赖安装仍失败（含重试一次 pnpm install，均返回非 0）"
  pnpm_diagnose_install_failure "$PNPM_LOG" || true
  return 1
}

# 校验前端产物在容器内真的可见，必要时强制重建前端容器（自愈）。
# 现场教训：产物目录通过 **bind mount** 进容器，而 bind mount 绑定的是"挂载那一刻的目录 inode"。
# 若宿主机目录被删除后重建（例如按旧指引 rm -rf admin-ui-dist），运行中的容器仍指向那个已被删除的
# 目录、看到的是空目录 → nginx 对无 index 的目录返回 403；而 `docker compose up -d --build`
# **不会重建"配置未变"的容器**，于是脚本打印"部署完成"、页面却是 403。
# 这里做一次可见性校验：不可见就 force-recreate 一次再校验，仍不可见则明确失败。
verify_frontend_mount() {
  [ "$NO_DOCKER" = "1" ] && return 0
  local container=ypbin-admin-ui attempt
  for attempt in 1 2; do
    if docker exec "$container" test -r /usr/share/nginx/html/index.html 2>/dev/null; then
      ok "前端产物在容器内可见（$container）"
      return 0
    fi
    if [ "$attempt" = "1" ]; then
      warn "前端容器读不到 /usr/share/nginx/html/index.html。容器日志尾部（本处实际看到的关键行）："
      docker logs --tail 20 "$container" 2>&1 | sed 's/^/    /' || true
      # 不做原因断言：容器内无 index.html 的成因不止一种（nginx 配置错、dist 为空、compose 未挂载、
      # SELinux 拒绝、容器仍指向被删除后重建的旧目录……），这些在本步骤的判定上表现完全相同。
      # 唯一可按证据推进的一步：bind mount 绑的是「挂载那一刻的目录 inode」，旧目录被删后重建时
      # 容器仍指向已删目录——重挂载是低代价、幂等的一步，故先试一次；成不成由下方复验与自查命令说话。
      warn "先强制重建前端容器以重新绑定挂载（bind mount 绑的是挂载那一刻的目录 inode）……"
      (cd "$ROOT/ypbin-admin/deploy" && docker compose -f docker-compose.yml --env-file "$ENV_FILE" \
        up -d --force-recreate "$container" >/tmp/frontend-recreate.log 2>&1) || true
      sleep 3
    fi
  done
  warn "强制重建后仍读不到 index.html —— 这类现象的成因不止一种，**具体原因需按下面命令的实际输出判断**，脚本不代下结论："
  warn "宿主机产物：$(ls -l "$ADMIN_UI_DIST_DIR/index.html" 2>/dev/null || echo '不存在')"
  info "  1) docker inspect $container --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{\"\\n\"}}{{end}}'"
  info "     # 挂载源/目标是否就是预期目录：compose 未挂载或挂错时这里一眼可见"
  info "  2) docker exec $container ls -l /usr/share/nginx/html   # 容器内实际内容（空目录 vs 有文件）"
  info "  3) docker exec $container grep -RE 'root|alias' /etc/nginx/conf.d/   # nginx 的 root/alias 与本挂载点是否一致"
  info "  4) docker logs --tail 50 $container   # nginx 报错原文（403 / 404 / permission denied 各自指向不同成因）"
  info "  5) ls -ld $ADMIN_UI_DIST_DIR $ADMIN_UI_DIST_DIR/index.html   # 权限面：nginx worker 非 root，目录/文件不可读同样读不到"
  info "  6) getenforce 2>/dev/null; ausearch -m avc -ts recent 2>/dev/null | tail -20   # SELinux 拒绝的表现与此完全相同"
  info "  7) ls -A $ADMIN_UI_DIST_DIR | head; du -sh $ADMIN_UI_DIST_DIR   # 产物是不是空壳/半成品"
  warn "完整日志：/tmp/frontend-recreate.log；容器重建命令：docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml up -d --force-recreate $container"
  die "前端产物在容器内不可见（页面会返回 403），已停止而不是假装部署成功；原因请按上面 1)~7) 的实际输出判断"
}

# 判断已有前端产物是否仍然"新鲜"：产物存在，且没有任何比它更新的构建输入。
# 现场教训：原先只看"index.html 存在就复用"，于是改了前端源码（例如埋点 SDK）或
# .env.production 后重新部署时仍复用旧产物——等于静默部署了旧前端。
# 现在按时间戳判断（构建输入 = 前端源码 + 应用级 .env*），并保留两个显式开关：
# SKIP_FRONTEND=1 永不构建；REBUILD_FRONTEND=1 强制构建。
frontend_dist_is_fresh() {
  local dist_index="$ADMIN_UI_DIST_DIR/index.html"
  [ -f "$dist_index" ] || return 1
  local newer
  newer="$(find "$ROOT/ypbin-admin-ui/apps/web-antd/src" \
                "$ROOT/ypbin-admin-ui/packages" \
                "$ROOT/ypbin-admin-ui/apps/web-antd"/.env* \
                -type f -newer "$dist_index" -print -quit 2>/dev/null || true)"
  [ -z "$newer" ]
}

# MySQL 认证探测：把「密码与数据卷不一致」与「还没就绪」区分开。
# 现场教训：MySQL 镜像仅在【空数据卷】时应用 MYSQL_ROOT_PASSWORD，卷已存在则忽略该环境变量；
# 于是 .env 与卷里初始化的密码不一致 → healthcheck 永远不 healthy → 脚本等满 60 秒后继续往下走，
# 最后在 CREATE DATABASE 处报 Access denied，报错点远离真因。
ensure_mysql_auth() {
  local err
  if err="$(docker exec ypbin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT 1;" 2>&1)"; then
    return 0
  fi
  if printf '%s' "$err" | grep -q "Access denied"; then
    warn "MySQL 认证失败（本次探测的原始输出，逐行回显，不做裁剪）："
    printf '%s\n' "$err" | sed 's/^/    /'
    # Access denied 的可判据只有「它确实拒绝了」；导致拒绝的原因不止一种（卷里初始化的密码与 .env 不一致、
    # 认证插件/账号 host 限制、容器尚未就绪即被探测……），它们在客户端输出上表现完全相同，故此处不写死原因。
    warn "Access denied 的成因不止一种，**具体原因需按下面命令的实际输出判断**，脚本不代下结论："
    info "  1) docker inspect ypbin-mysql --format '{{.State.Health.Status}}'"
    info "     # 长期不 healthy ⇒ 服务端自己也没起来，与密码对错是两回事"
    info "  2) docker logs --tail 50 ypbin-mysql   # 或 docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 mysql"
    info "     # 卷已存在时初始化日志会体现「跳过密码应用」；出现账号/host 相关告警则指向权限面"
    info "  3) docker volume ls | grep ypbin-mysql-data   # 数据卷是否早已存在"
    info "  4) grep -q '^MYSQL_ROOT_PASSWORD=..' $ENV_FILE && echo '.env 中该密码已设置' || echo '.env 中该密码为空或缺失'"
    info "     # 只报「是否已设置」，不打印真值（避免密码进入终端回滚/日志）"
    info "  条件式判读（按上面实际输出对号，条件不成立就不要照做下方处置）："
    info "  · 若 3) 显示卷早已存在、且 .env 的密码是后来重新生成的 ⇒ 卷内初始化的密码与 .env 不一致（下方处置适用）"
    info "  · 若 2) 的日志出现 Access denied 且伴随账号/host 字样 ⇒ 属账号权限面，需按 mysql.user 实际内容修正"
    info "  · 若 1) 显示容器非 running/健康检查未通过 ⇒ 先按 2) 的日志解决启动问题，此时认证探测本就可能失败"
    warn "处置（**仅在上面第一条条件成立、即全新部署且无业务数据时**）：docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml down -v 后重跑"
    warn "      down -v 会删除 ypbin-mysql-data / ypbin-redis-data / ypbin-nacos-data 三个卷；Nacos 配置会在重跑时重新导入"
    die "MySQL 认证探测失败，已停止（避免继续以错误密码初始化库表）；具体原因见上方命令与日志"
  fi
  warn "MySQL 尚不可用（非认证问题，继续尝试）：$(printf '%s' "$err" | head -1)"
  return 0
}

# 判定「compose 启动失败」是否与镜像仓库无关。
# 现场教训：Nacos 需要宿主机 8080，端口被占用时 compose 整体退出非零，脚本却把真因报成了
# 镜像源不可达（现已不再探测镜像源），把排查方向带偏——所以要按日志分类，并给出真因与处置。
infra_failure_reason() {
  if grep -qE "port is already allocated|Bind for [^ ]+ failed" /tmp/infra-up.log 2>/dev/null; then
    local port holder
    port="$(sed -n 's/.*Bind for [^:]*:\([0-9][0-9]*\) failed.*/\1/p' /tmp/infra-up.log | head -1)"
    holder="$(docker ps -a --format '{{.Names}}|{{.Ports}}' 2>/dev/null | grep -F ":${port}->" | head -1 || true)"
    warn "真因不是镜像仓库：宿主机端口 ${port:-?} 已被占用（Nacos 控制台需要它）"
    [ -n "$holder" ] && warn "占用者疑似容器：${holder}"
    warn "自查：docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep ${port:-8080}；sudo ss -ltnp | grep :${port:-8080}"
    warn "处置：若是本套残留容器 → docker rm -f <容器名>（或 docker compose -f deploy/docker-compose.yml down）后重跑"
    return 10
  fi
  if grep -q "no space left on device" /tmp/infra-up.log 2>/dev/null; then
    warn "真因是【磁盘空间不足】（写镜像层失败），与镜像源无关"
    warn "处置：docker builder prune -af && docker image prune -f；或扩容/迁移 Docker 数据目录"
    return 13
  fi
  if grep -qE "pull access denied|manifest unknown|not found: manifest|i/o timeout|TLS handshake timeout|no such host|connection refused" /tmp/infra-up.log 2>/dev/null; then
    warn "真因是镜像拉取失败（网络或仓库侧）"
    return 11
  fi
  warn "compose 启动失败，原因见 /tmp/infra-up.log 尾部"
  return 12
}

# 构建 starter 并装入本地 Maven 仓库（构建前先用 resolve_starter_build_ref 选好代码引用）。
build_starter() {
  resolve_starter_build_ref "$ROOT/ypbin-starter"
  if [ -n "$STARTER_VERSION" ] && [ "$STARTER_BUILD_REF" = "v$STARTER_VERSION" ]; then
    info "已切到 tag $STARTER_BUILD_REF 构建（与 admin 依赖的版本一致）"
  fi
  cd "$ROOT/ypbin-starter"
  # 完整输出错误（不吞日志）：失败时打印 maven 日志尾部。构建期间 stdout 被 tee|tail 接管，
  # 终端会长时间无输出（约 3-6 分钟）——先提示，避免被误判为「脚本卡死/网络挂起」。
  info "开始构建 starter（约 3-6 分钟，期间本终端无输出属正常；日志文件 /tmp/starter-build.log）"
  if ! mvn -DskipTests -Djacoco.skip=true install 2>&1 | tee /tmp/starter-build.log | tail -20; then
    die "starter 构建失败（完整日志 /tmp/starter-build.log）"
  fi
  # 版本号取「实际构建出来的 revision」，不再用 admin 解析出的版本冒充（那是误导）
  local built_version
  built_version="$(starter_revision_from_pom "$ROOT/ypbin-starter/pom.xml" || true)"
  [ -n "$built_version" ] || built_version="未知"
  # 构建产物与 admin 依赖的版本不一致时，第 [4/7] 步会以「解析不到该坐标」的形式失败，而根因在这里；
  # 所以在源头就明确失败，别让使用者去 [4/7] 的报错里倒推。
  if [ -n "$STARTER_VERSION" ] && [ "$built_version" != "$STARTER_VERSION" ]; then
    die "构建出的 starter 版本是 $built_version，而 admin 依赖的是 $STARTER_VERSION（构建自 $STARTER_BUILD_REF）。
     两者不一致时第 [4/7] 步解析不到 cn.ypbin:ypbin-starter-bom:$STARTER_VERSION。
     请用对应 tag 构建该发布版，或把 admin 的 ypbin-starter.version 改成与之一致。
     starter 仓库当前状态：$(git -C "$ROOT/ypbin-starter" status --short | head -5)"
  fi
  ok "starter $built_version 已装入本地 Maven 仓库（构建自 $STARTER_BUILD_REF）"
}

# ---------- 参数 ----------
# BRANCH/ROOT：支持 -b/--branch 指定分支；ROOT 默认按分支隔离(/opt/ypbin/<分支>,
# main 保持 /opt/ypbin/main)，与单体版 /opt/ypbin/boot 等分开，避免代码互相覆盖/分支冲突，
# 多分支可共存；可用 --root 或 YPBIN_ROOT 显式覆盖。
BRANCH="${BRANCH:-main}"
ROOT=""
ROOT_CLI=""
NO_DOCKER="${NO_DOCKER:-0}"
ASSUME_YES="${ASSUME_YES:-0}"
SKIP_FRONTEND="${SKIP_FRONTEND:-0}"
# 强制重新构建前端（默认按“产物是否比源码新”自动判断，见 frontend_dist_is_fresh）
REBUILD_FRONTEND="${REBUILD_FRONTEND:-0}"
ADMIN_UI_PORT="${ADMIN_UI_PORT:-19000}"
# starter 版本：从 admin 仓库 pom 的 ypbin-starter.version 自动解析（唯一事实源，
# 与 CI dispatch 自动升级保持一致），无需手工同步；目录未就绪时留空，由 [3/7] 构建前解析。
STARTER_VERSION="${STARTER_VERSION:-}"
# 构建 starter 时实际采用的代码引用与构建出的 revision（由 build_starter 写入，仅供日志与校验）
STARTER_BUILD_REF=""

# 仓库源（GitHub / Gitee 镜像自动探测；显式 YPBIN_REPO 优先）
REPO_BASE=""
GITEE_REPO="${GITEE_REPO:-https://gitee.com/wenbin_wb}"
GITHUB_REPO="https://github.com/wenbin-wb"
# 探测 GitHub 连通（3s 快超时）；显式指定或探测成功后赋值 REPO_BASE。
# 注意：本函数直接改调用方变量（REPO_BASE / REPO_SWITCHED_NOTE），**不要**用 $( ) 捕获——
# 命令替换会开子 shell，函数内赋的 REPO_SWITCHED_NOTE 传不出来（降级提示会静默丢失）。
resolve_repo_base() {
  REPO_SWITCHED_NOTE=""
  if [ -n "${YPBIN_REPO:-}" ]; then REPO_BASE="$YPBIN_REPO"; return 0; fi
  if [ -n "$REPO_BASE" ]; then return 0; fi
  if curl -fsSI --connect-timeout 3 --max-time 5 -o /dev/null "https://github.com" 2>/dev/null; then
    REPO_BASE="$GITHUB_REPO"
  elif curl -fsSI --connect-timeout 3 --max-time 5 -o /dev/null "https://gitee.com" 2>/dev/null; then
    REPO_BASE="$GITEE_REPO"
    REPO_SWITCHED_NOTE="GitHub 不可达，仓库源自动降级为 Gitee 镜像：${GITEE_REPO}（请在 Gitee 建同名镜像并开启自动同步）"
  else
    die "GitHub 与 Gitee 均不可达：请配置代理或显式指定 YPBIN_REPO（如 https://ghproxy.com/https://github.com/wenbin-wb）后重跑"
  fi
}

# ---------- 交互模式 ----------
# 默认交互（人工确认关键步骤）；-y/--yes 全自动跳过所有确认（CI/无头环境，对齐单体脚本）
usage() {
  echo "ypbin-admin 一键部署脚本"
  echo "用法: bash install.sh [选项]"
  echo "  -b, --branch <name>   部署分支（默认 main；自动隔离目录 /opt/ypbin/<分支>）"
  echo "  --root <dir>          部署目录（默认按分支隔离；main 为 /opt/ypbin/main）"
  echo "  -y, --yes             全自动跳过所有交互确认"
  echo "环境变量：YPBIN_REPO / GITEE_REPO / REGISTRY_PREFIX / NO_DOCKER 等（见脚本头部注释）"
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    -y|--yes) ASSUME_YES=1; shift ;;
    -b|--branch) BRANCH="${2:-$BRANCH}"; shift 2 ;;
    --root) ROOT_CLI="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) warn "未知参数忽略: $1"; shift ;;
  esac
done
# ROOT 计算：--root > YPBIN_ROOT > 分支隔离默认(分支内 / 替换为 - 防路径嵌套)
if [ -n "$ROOT_CLI" ]; then ROOT="$ROOT_CLI"
elif [ -n "${YPBIN_ROOT:-}" ]; then ROOT="$YPBIN_ROOT"
else
  _safe="${BRANCH//\//-}"
  ROOT="/opt/ypbin/${_safe}"
fi
ADMIN_UI_DIST_DIR="${ADMIN_UI_DIST_DIR:-$ROOT/ypbin-admin/admin-ui-dist}"
# 分支/独立目录部署：docker compose 项目名必须唯一——不同分支的 compose 若同在
# "deploy" 目录名下会共用同一项目名，导致 MySQL/Redis 等命名卷与网络互相串用
# （典型事故：feature 部署复用了 main 的 MySQL 卷 → root 密码不匹配 Access denied）。
# main 部署保持默认（无前缀），其余按 ROOT 生成唯一前缀；仍注意同机并行需端口/容器名不冲突。
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
if [ -z "$COMPOSE_PROJECT_NAME" ] && [ "$ROOT" != "/opt/ypbin/main" ]; then
  COMPOSE_PROJECT_NAME="ypbin$(printf '%s' "$ROOT" | md5sum 2>/dev/null | cut -c1-10)"
fi
[ -n "$COMPOSE_PROJECT_NAME" ] && export COMPOSE_PROJECT_NAME

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

info "部署参数：ROOT=$ROOT 分支=$BRANCH NO_DOCKER=$NO_DOCKER（脚本版本 $SCRIPT_VERSION）"

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
  curl -fsSL --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time 300 -o /tmp/apache-maven.tar.gz "$url" || return 1
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
    # 两次 apt 尝试的输出都留档（终端仍保持原本的安静）；否则失败时只能凭"经验"给结论（现场正是这么被带偏的）
    if ! { apt-get install -y docker-compose-plugin 2>&1 | tee /tmp/docker-compose-install.log >/dev/null; } \
       && ! { apt-get install -y docker-compose-v2 2>&1 | tee -a /tmp/docker-compose-install.log >/dev/null; }; then
      warn "常规安装失败，尝试国内 APT/Docker 源自愈（官方国外源在国内常不可达）..."
      if ! apt_docker_ce_selfheal; then
        warn "自愈（切换国内 APT 源 + 配置 docker-ce 源）后仍装不上 —— 本次 apt 输出的关键错误行："
        grep -nE 'E:|Err:|Could not|Unable to|Temporary failure|Cannot|dpkg: error|lock' \
          /tmp/docker-compose-install.log 2>/dev/null | tail -n 20 | sed 's/^/    /' || true
        warn "**具体原因需按下面命令的实际输出判断**（APT 源不通只是其中一种，脚本不代下结论）："
        info "  1) tail -n 50 /tmp/docker-compose-install.log   # 本次 apt 完整输出"
        info "  2) apt-get update 2>&1 | tail -n 20   # 源可达性/签名（源 404、GPG 过期常见于此）"
        info "  3) ls -l /var/lib/dpkg/lock* /var/lib/apt/lists/lock   # 是否有其它 apt/dpkg 进程持锁"
        info "  4) cat /etc/os-release; cat /etc/apt/sources.list.d/docker.list 2>/dev/null   # 发行版与 docker-ce 源是否匹配本版本"
        info "  5) docker --version; systemctl is-active docker   # Docker 守护进程自身状态"
        info "  6) 备选路径：手工安装 compose v2 插件（官方发布包 docker-compose-linux-<arch> 放入 ~/.docker/cli-plugins/ 并 chmod +x），需能访问对应下载渠道"
        die "Docker Compose 插件安装失败（apt 常规安装与国内源自愈均失败）——原因见上方日志与 1)~6) 的实际输出"
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
resolve_repo_base
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
  if ! git symbolic-ref -q HEAD >/dev/null 2>&1; then
    # detached HEAD：本脚本 [3/7] 会按 tag 构建，留下的正是这个状态，直接恢复到分支即可，
    # 不是「与远程分叉」——原来会打一条误导性的分叉告警
    info "$repo 当前为 detached HEAD（上次按 tag 构建所致），恢复到分支 $branch"
    git checkout -f "$branch" 2>/dev/null || git checkout -b "$branch" "origin/$branch"
    git reset --hard "origin/$branch"
  elif git merge-base --is-ancestor "origin/$branch" HEAD 2>/dev/null; then
    git checkout "$branch" 2>/dev/null && git merge --ff-only "origin/$branch" 2>/dev/null \
      || git reset --hard "origin/$branch"
  else
    # 分叉（如历史改写）：直接强对齐远程
    warn "$repo 与远程分叉，强制对齐 origin/$branch"
    git checkout -f "$branch" 2>/dev/null || git checkout -b "$branch" "origin/$branch"
    git reset --hard "origin/$branch"
  fi
  local current_branch
  current_branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo detached)"
  if [ "$current_branch" != "$branch" ]; then
    warn "$repo 未能恢复到分支 $branch（当前 $current_branch），后续构建可能用到非预期代码"
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
  build_starter
else
  # 确认本地仓库已有解析出的 starter 版本（没有则强制构建）
  if [ -n "$STARTER_VERSION" ] && [ -d "$HOME/.m2/repository/cn/ypbin/ypbin-starter-core/$STARTER_VERSION" ]; then
    ok "使用本地 Maven 仓库已有 starter $STARTER_VERSION"
  else
    [ -n "$STARTER_VERSION" ] && warn "本地 Maven 仓库无 starter $STARTER_VERSION，强制构建" || warn "未能解析 starter 版本，强制构建最新代码"
    build_starter
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
  # 只有「依赖解析失败」才值得重试：刚发布的版本在镜像上尚未同步时，本地会留下 *.lastUpdated
  # 失败缓存（release 版本默认不再自动重试），-U 可强制刷新后重新解析；编译错误等重试无意义。
  if grep -qE "Could not (find|resolve)|Non-resolvable|could not be resolved" /tmp/admin-build.log; then
    # 这里能可靠判定的是「属依赖解析失败这一类」（判定依据＝日志特征串），**不能**判定解析为何失败；
    # 因此只回显实际命中的错误行 + 给条件式判读与自查命令，不写"镜像未同步/被缓存"这类断言。
    warn "admin 构建失败于【依赖解析】类（判定依据：日志出现 Could not find/resolve、Non-resolvable 等特征串，不是编译错误）。"
    warn "本次实际命中的错误行："
    grep -nE "Could not (find|resolve)|Non-resolvable|could not be resolved|Failed to read artifact|\.lastUpdated|Could not transfer" /tmp/admin-build.log | tail -n 15 | sed 's/^/    /' || true
    warn "解析失败用 -U（强制刷新远程元数据/失败缓存）重试一次 —— 编译类失败重试无意义，解析类才有意义，故仅此处重试。"
    info "  条件式判读（重试后仍失败时按下面实际输出对号，脚本不代下结论）："
    info "  · 若日志出现 Could not find artifact cn.ypbin:ypbin-starter-*:$STARTER_VERSION ⇒ 该坐标在所用仓库确实不存在，见下方 die 提示"
    info "  · 若日志出现 Could not transfer / status code 401 / 403 / Unknown host ⇒ 属仓库地址或凭据面，非缓存问题"
    info "  自查命令："
    info "  1) ls -l ~/.m2/repository/cn/ypbin/ypbin-starter-core/$STARTER_VERSION/ 2>/dev/null   # 有 *.lastUpdated 即上次失败的缓存凭证"
    info "  2) mvn -U -DskipTests clean package -X 2>&1 | grep -iE 'repository|resolution' | tail -n 20   # 打印实际解析的仓库与结果"
    info "  3) grep -n 'ypbin-starter.version' $ROOT/ypbin-admin/pom.xml   # admin 实际固定的版本号（唯一事实源）"
    info "  4) ls -l ~/.m2/settings.xml; grep -c '<mirror>' ~/.m2/settings.xml   # 镜像配置；镜像缺该版本时只能本机构建 starter"
    # 重试写独立日志：否则 die 指向的文件里已经没有首次失败的证据了
    if ! mvn -U -DskipTests clean package 2>&1 | tee /tmp/admin-build-retry.log | tail -20; then
      die "admin 构建失败（首次日志 /tmp/admin-build.log，重试日志 /tmp/admin-build-retry.log）。
     若报的是 Could not find artifact cn.ypbin:ypbin-starter-*:$STARTER_VERSION，则**确定**的是「该坐标在所用仓库里取不到」；
     取不到的原因不止一种（镜像未同步该版本 / 镜像只有 pom 缺 jar / 第 [3/7] 步没把它装进本地仓库，三者表现相同），
     请在服务器上按下面命令区分，不要直接假定是哪一种：
       · ls -l ~/.m2/repository/cn/ypbin/ypbin-starter-core/$STARTER_VERSION/   # 本地仓库里到底有没有（有 jar 即非本因）
       · git -C $ROOT/ypbin-starter tag -l v$STARTER_VERSION                    # 是否有该 tag（脚本第 [3/7] 步按 tag 构建）
       · grep -n 'ypbin-starter.version' $ROOT/ypbin-admin/pom.xml             # admin 固定的版本
     若不是上述坐标错误，请按上方 1)~4) 的自查命令判断具体原因（脚本不代下结论）。"
    fi
    ok "依赖解析在 -U 重试后成功"
  else
    die "admin 构建失败（完整日志 /tmp/admin-build.log）"
  fi
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
  # AI 模型密钥的加密密钥：不接受内置默认值——公开已知的默认值等同未加密。
  # 首次全新部署（.env 不存在、无旧密文）时自动随机生成并写入 .env，同时醒目提示妥善保存；
  # 复用旧 .env 时绝不自动生成（换值即旧密文永久不可解密），见下方 check_required_key 前置校验。
  if [ -n "${AI_MODEL_SECRET_KEY:-}" ]; then
    : # 运维显式提供（多分支共用密文场景）则原样采用
  else
    AI_MODEL_SECRET_KEY="$(rand_hex 16)"
    warn "已自动生成 AI_MODEL_SECRET_KEY（AI 模型 API Key 的加密密钥）。"
    warn ">>> 请立即抄写并妥善保存该密钥（见 .env 的 AI_MODEL_SECRET_KEY）：$AI_MODEL_SECRET_KEY"
    warn ">>> 它用于加解密库内已存的模型 API Key，必须长期保持不变——更换或丢失后，已保存的模型密钥将永久无法解密。"
  fi
  # Nacos 服务端鉴权凭据：token 与身份标识值随机生成，避免固定默认值入库
  NACOS_AUTH_TOKEN="${NACOS_AUTH_TOKEN:-$(rand_b64_48)}"
  NACOS_AUTH_IDENTITY_KEY="${NACOS_AUTH_IDENTITY_KEY:-serverIdentity}"
  NACOS_AUTH_IDENTITY_VALUE="${NACOS_AUTH_IDENTITY_VALUE:-$(rand_hex 32)}"
  # 内部 Feign 调用凭证（/internal/** 守卫，auth/system/ai 共享一致值），随机生成
  INTERNAL_TOKEN="${INTERNAL_TOKEN:-$(rand_hex 32)}"
  GATEWAY_SIGN_TOKEN="${GATEWAY_SIGN_TOKEN:-$(rand_hex 32)}"
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
GATEWAY_SIGN_TOKEN=$GATEWAY_SIGN_TOKEN
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

# REGISTRY_PREFIX 全局归一化：Docker 镜像引用是「前缀 + 官方镜像名」的字符串拼接，缺尾斜杠会拼出
# `docker.m.daocloud.iomysql:8.4` / `docker.m.daocloud.ionginx:alpine` 这类无效引用（拉取必然失败）。
# 在 .env 载入之后统一补一次并 export，使后续所有 compose 调用（基础设施、[6/7] 业务服务与
# xxl-job/nginx 镜像、前端容器重建）口径一致；shell 变量优先于 --env-file，故 .env 里的原样值也被覆盖。
if [ -n "${REGISTRY_PREFIX:-}" ]; then
  export REGISTRY_PREFIX="${REGISTRY_PREFIX%/}/"
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
env_key_backfill GATEWAY_SIGN_TOKEN 'rand_hex 32'
if [ "$NO_DOCKER" = "1" ]; then
  env_key_backfill REDIS_PASSWORD 'printf ""'
else
  env_key_backfill REDIS_PASSWORD 'rand_hex 16'
fi

# AI_MODEL_SECRET_KEY 不在「补生成」范围内：全新部署已在上面自动生成并写入 .env；
# 此处补生成仅作用于「复用旧 .env」场景，此时库里可能已有旧密文，自动生成新值=旧密文永久不可解密，
# 故必须沿用旧值（或由运维显式传入），缺失即报错而非静默换新。
# 这里做「存在性 + 长度」前置校验，覆盖「旧 .env 尚未包含该键」「未通过环境变量传入」「长度非法」三种情况，
# 把失败点从第 6 步 compose 的 :? 与更晚的 AI 服务启动，提前到配置阶段；也避免再次退化成公开默认值。
check_required_key() { # $1=键名 $2=生成命令提示 $3=允许的字节长度（空格分隔）
  local key="$1" hint="$2" allowed="$3" val bytes
  # 环境变量优先（与脚本其它键一致：显式 export 的应生效），其次读 .env
  val="${!key:-}"
  if [ -z "$val" ]; then
    val="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true)"
  fi
  if [ -z "$val" ]; then
    die "${key} 未配置（${hint}）。请写入 ${ENV_FILE}，或在执行本脚本前 export ${key}=... 后重跑"
  fi
  bytes="$(printf '%s' "$val" | wc -c | tr -d ' ')"
  case " $allowed " in
    *" $bytes "*) ;;
    *) die "${key} 长度必须为 ${allowed} 字节（当前 ${bytes} 字节，${hint}）" ;;
  esac
}
check_required_key AI_MODEL_SECRET_KEY '生成：openssl rand -hex 16（32 字节；-hex 12 → 24 字节、-hex 8 → 16 字节）' '16 24 32'

# ---------- [5.5/7] 启动基础设施并初始化（Nacos 配置 + MySQL 库表）----------
# Docker 模式：先只启动基础设施（nacos/redis/mysql），配置导入和建库完成后再启动业务服务
if [ "$NO_DOCKER" = "1" ]; then
  info "[5.5/7] NO_DOCKER 模式：假定外部 Nacos/Redis/MySQL 已就绪，直接导入 Nacos 配置"
else
  info "[5.5/7] 启动基础设施（Nacos/Redis/MySQL）"
  cd "$ROOT/ypbin-admin/deploy"
  # 基础设施镜像只走「一次 compose up」，不做任何镜像源探测/遍历：
  # REGISTRY_PREFIX 有值（使用者显式 export，或已在复用的 .env 中设置；尾斜杠已在 [5/7] 全局归一化）
  # → 按该前缀拉取；为空 → 不带前缀，直接用 Docker 守护进程默认源
  # （其配置的 registry-mirrors；无配置即官方 Docker Hub）。
  infra_registry_prefix="${REGISTRY_PREFIX:-}"
  if [ -n "$infra_registry_prefix" ]; then
    infra_registry_desc="显式前缀 ${infra_registry_prefix}"
    info "基础设施镜像按显式 REGISTRY_PREFIX=${infra_registry_prefix} 拉取"
  else
    infra_registry_desc="机器默认（Docker 守护进程 registry-mirrors，无配置即官方 Docker Hub）"
    info "基础设施镜像走机器默认源，不做镜像加速探测"
  fi
  infra_reason=0
  if ! REGISTRY_PREFIX="$infra_registry_prefix" docker compose -f docker-compose.yml --env-file "$ENV_FILE" up -d nacos redis mysql >/tmp/infra-up.log 2>&1; then
    tail -5 /tmp/infra-up.log 2>/dev/null || true
    # 按日志分类真因（端口占用/磁盘不足/镜像拉取失败），避免一律归咎于镜像源
    infra_failure_reason || infra_reason=$?
    if [ "$infra_reason" = "10" ]; then
      die "基础设施启动失败：宿主机端口被占用（与镜像仓库无关），处置见上方提示后重跑"
    fi
    if [ "$infra_reason" = "13" ]; then
      die "基础设施启动失败：磁盘空间不足（写镜像层失败），与镜像仓库无关，处置见上方提示后重跑（完整日志 /tmp/infra-up.log）"
    fi
    if [ "$infra_reason" = "11" ]; then
      # 这一类有可靠判据（日志里的 pull access denied / manifest unknown / no such host 等特征串），
      # 故保留结论并给对应处置；镜像源口径按实际使用的前缀打印（显式前缀时不再谎称"机器默认源"）。
      die "基础设施启动失败：镜像拉取失败（判定依据见上方日志特征），本次镜像源为「${infra_registry_desc}」。三条可执行路径：
     ① 该源不通时显式指定镜像前缀后重跑：export REGISTRY_PREFIX=docker.io/（强制官方源）
        或 export REGISTRY_PREFIX=<你的加速前缀>/（如 docker.m.daocloud.io/）；有值即只按该前缀拉取
     ② 完全离线：在可联网机器 docker pull/save 三个镜像（mysql:8.4、nacos/nacos-server:v3.2.4、
        redis:7-alpine），传上来 docker load，compose up 会直接用本地镜像、不再拉取
     ③ 若日志提示自定义网络网段与残留旧网络重叠：docker network prune -f 后重跑
     （完整日志 /tmp/infra-up.log）"
    fi
    # 其余情况：日志里没有可识别的失败特征 → 不猜原因，原样回显日志尾部 + 给按可能性排序的自查命令。
    warn "基础设施启动失败，且日志中没有可识别的失败特征 —— **原因未判定**（脚本不猜）。日志最后 30 行："
    tail -n 30 /tmp/infra-up.log 2>/dev/null | sed 's/^/    /' || true
    warn "本次镜像源为「${infra_registry_desc}」；**具体原因需按下面命令的实际输出判断**："
    info "  1) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml ps -a   # 三个容器各自的状态与退出码"
    info "  2) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 nacos redis mysql"
    info "  3) docker info 2>&1 | tail -20; df -h \"\$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || echo /var/lib/docker)\""
    info "     # Docker 守护进程可用性与数据目录磁盘余量"
    info "  4) docker network ls; docker network prune -f   # 残留网络与网段冲突"
    info "  5) docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E '8080|8848|3306|6379'   # 端口占用"
    die "基础设施启动失败（完整日志 /tmp/infra-up.log）——原因见上方日志与 1)~5) 的实际输出"
  fi
  ok "基础设施已启动（Nacos/Redis/MySQL，镜像源：${infra_registry_desc}）"
fi

NACOS_CONSOLE_URL="${NACOS_CONSOLE_URL:-http://localhost:8080}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

# 等待 Nacos Console 就绪（v3 独立 Console 端口）
NACOS_READY=0
for i in $(seq 1 60); do
  if curl -fsS --connect-timeout 3 --max-time 5 "$NACOS_CONSOLE_URL/v3/console/health/readiness" >/dev/null 2>&1; then
    NACOS_READY=1
    break
  fi
  sleep 2
done
if [ "$NACOS_READY" != "1" ]; then
  # 探测失败只说明「这次请求没拿到就绪响应」，成因可能是 Console 未起、端口不是 $NACOS_CONSOLE_URL
  # 所指、或被反向代理/防火墙拦掉——表现相同，故回显 curl 原始输出并给自查命令，不写死原因。
  warn "Nacos Console 就绪探测在 120 秒内未通过（$NACOS_CONSOLE_URL/v3/console/health/readiness）。原始输出："
  curl -sS -o /dev/null -w '    http_code=%{http_code} connect=%{time_connect}s total=%{time_total}s\n' \
    --connect-timeout 3 --max-time 5 "$NACOS_CONSOLE_URL/v3/console/health/readiness" 2>&1 | sed 's/^/    /' || true
  warn "**具体原因需按下面命令的实际输出判断**（脚本不代下结论）："
  info "  1) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml ps -a   # nacos 容器状态"
  info "  2) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 nacos"
  info "  3) ss -ltnp | grep ':8080'   # Console 端口是否有人监听（NACOS_CONSOLE_URL 是否指向本套 Nacos）"
  info "  4) curl -v --connect-timeout 3 $NACOS_CONSOLE_URL/v3/console/health/readiness   # 连接被拒 vs 连上但非 200"
  warn "后果（不是原因断言，是事实）：本次将跳过 Nacos 配置导入，各服务会按本地/默认配置启动，"
  warn "      很可能因连不上库或注册中心而反复重启——若后续步骤出现此类现象，请回到这里先修好 Nacos。"
fi

# 初始化 Nacos 管理员（幂等；已有管理员时忽略失败）
curl -fsS --connect-timeout 5 --max-time 30 -X POST "$NACOS_CONSOLE_URL/v3/auth/user/admin" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$NACOS_USERNAME" \
  --data-urlencode "password=$NACOS_PASSWORD" >/dev/null 2>&1 || true

# 登录获取 accessToken
NACOS_TOKEN=$(curl -fsS --connect-timeout 5 --max-time 30 -X POST "$NACOS_CONSOLE_URL/v3/auth/user/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$NACOS_USERNAME" \
  --data-urlencode "password=$NACOS_PASSWORD" 2>/dev/null | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' || true)

# 发布 Nacos 配置（共 5 个：ypbin-common + 4 服务；幂等：已存在则覆盖；使用 Nacos 3 Console 新 API）
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
            -e "s/\${GATEWAY_SIGN_TOKEN}/${GATEWAY_SIGN_TOKEN}/g" \
            "$NACOS_DIR/$cfg.yaml" > "$TMP_CFG"
      else
        # REDIS_PASSWORD 为空（NO_DOCKER 外部 Redis 不认证）→ 删除 password 行，等价不配置密码；
        # INTERNAL_TOKEN 仍无条件替换（缺失/为空时 system 守卫 fail-closed，见 ypbin.internal.token 注释）
        sed -e "s/\${MYSQL_ROOT_PASSWORD}/${MYSQL_ROOT_PASSWORD}/g" \
            -e "s/\${INTERNAL_TOKEN}/${INTERNAL_TOKEN}/g" \
            -e "s/\${GATEWAY_SIGN_TOKEN}/${GATEWAY_SIGN_TOKEN}/g" \
            -e "/password: \${REDIS_PASSWORD}/d" \
            "$NACOS_DIR/$cfg.yaml" > "$TMP_CFG"
      fi
      curl -fsS --connect-timeout 5 --max-time 60 -X POST "$NACOS_CONSOLE_URL/v3/console/cs/config" \
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
  # 复现一次登录请求只为拿到「HTTP 状态 + 响应正文」这一手判据；accessToken 一律脱敏后再打印
  # （正常情况下此处不会有 token，但若是响应格式变化导致上面解析失败，脱敏可避免把令牌打到终端）。
  warn "Nacos 登录未拿到 accessToken，跳过配置导入。登录请求的 HTTP 状态与响应（accessToken 已脱敏）："
  printf '%s\n' "$(curl -sS -w '\n    http_code=%{http_code}' \
    -X POST "$NACOS_CONSOLE_URL/v3/auth/user/login" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "username=$NACOS_USERNAME" \
    --data-urlencode "password=$NACOS_PASSWORD" 2>&1 || true)" \
    | sed -E 's/("(access)?[Tt]oken"[[:space:]]*:[[:space:]]*")[^"]*/\1<redacted>/g' \
    | head -c 800 | sed 's/^/    /'
  info "  自查：用户名/密码是否与 Nacos 实际一致（默认 nacos/nacos，本次用 $NACOS_USERNAME）；"
  info "  1) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 nacos | grep -i -E 'auth|user|login'"
  info "  2) 上述 http_code 与响应正文即判据：401/403 属凭据面，5xx/连接失败属服务面，请按其实际内容判断（脚本不代下结论）。"
  warn "后果（事实）：配置未导入，各服务会按本地/默认配置启动，很可能因连不上库或注册中心而反复重启。"
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
  ensure_mysql_auth
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
elif [ "$SKIP_FRONTEND" = "1" ]; then
  if [ -f "$ADMIN_UI_DIST_DIR/index.html" ]; then
    ok "使用已有前端产物 $ADMIN_UI_DIST_DIR（SKIP_FRONTEND=1）"
  else
    warn "SKIP_FRONTEND=1 但 $ADMIN_UI_DIST_DIR 无 index.html"
    info "请本地构建后上传：cd ypbin-admin-ui && pnpm install && pnpm -F @vben/web-antd build"
    info "上传：scp -r apps/web-antd/dist/* root@<IP>:$ADMIN_UI_DIST_DIR/"
    die "缺少前端产物"
  fi
elif [ "$REBUILD_FRONTEND" != "1" ] && frontend_dist_is_fresh; then
  ok "复用已有前端产物（比源码新）：$ADMIN_UI_DIST_DIR"
  info "如需强制重建：REBUILD_FRONTEND=1（改了前端源码或 apps/web-antd/.env* 时会自动重建）"
else
  # 前端构建（重装依赖 + 构建）需要数 GB，磁盘不足会产出半成品
  check_docker_disk_space 3 || true
  if [ "$REBUILD_FRONTEND" = "1" ]; then
    info "[5.6/7] 按 REBUILD_FRONTEND=1 强制重新构建前端"
  elif [ ! -f "$ADMIN_UI_DIST_DIR/index.html" ]; then
    info "[5.6/7] 尚无前端产物，开始构建"
  else
    info "[5.6/7] 前端源码比产物新（或构建输入有变化），重新构建以避免复用旧前端"
  fi
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
    curl -fsSL --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time 120 -o /tmp/node.tar.xz \
      "https://npmmirror.com/mirrors/node/v22.18.0/node-v22.18.0-linux-${NODE_ARCH}.tar.xz" \
      || die "Node 下载失败"
    mkdir -p /usr/local/lib/nodejs
    tar -xJf /tmp/node.tar.xz -C /usr/local/lib/nodejs --strip-components=1
    export PATH="/usr/local/lib/nodejs/bin:$PATH"
    npm install -g pnpm@latest --registry=https://registry.npmmirror.com >/dev/null 2>&1 || true
  fi
  install_frontend_deps || die "前端依赖安装失败（详见上方提示）"
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
  # 时区必须显式传给 JVM：Docker 模式靠镜像 ENV TZ=Asia/Shanghai 生效，java -jar 直启没有这层保障，
  # 若宿主机是 UTC（云主机常见），LocalDate.now() 会按 UTC 取日，埋点「按 GMT+8 分桶」的口径即失效
  # （跨天边界会错）。默认与被覆盖的应用配置一致，仍允许外部 TZ 覆盖。
  APP_TZ="${TZ:-Asia/Shanghai}"
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
    TZ="$APP_TZ" nohup java -Xms256m -Xmx512m -jar "$JAR_DIR/$jar.jar" $EXTRA \
      > "$ROOT/logs/$jar.log" 2>&1 &
    ok "已启动 $jar（端口 $port，时区 $APP_TZ，日志 $ROOT/logs/$jar.log）"
  done <<< "$SERVICES"
else
  info "[6/7] Docker 模式：compose 启动（含 Nacos/Redis/MySQL 基础设施）"
  check_docker_disk_space 5 || true
  cd "$ROOT/ypbin-admin/deploy"
  # 微服务 compose 已内嵌基础设施（nacos/redis/mysql），单文件拉起全链路
  export DOCKER_BUILDKIT=0
  # legacy builder: FROM 基础镜像优先取本地 docker images(离线/受限环境可先 docker load 再构建,避免 buildkit 联网解析元数据卡死)
  # 输出落日志以便失败时分类（终端仍实时显示尾部 20 行）
  if ! docker compose -f docker-compose.yml --env-file "$ENV_FILE" up -d --build 2>&1 \
      | tee /tmp/compose-up.log | tail -20; then
    compose_up_diagnose /tmp/compose-up.log || true
    die "compose 启动失败（完整日志 /tmp/compose-up.log）"
  fi
  # 容器起来了不等于页面能打开：前端是 bind mount，必须校验产物在容器内可见
  verify_frontend_mount
fi

# ---------- [7/7] 健康检查 ----------
info "[7/7] 健康检查（等待服务就绪，最多 120 秒）"
GATEWAY_PORT=18080
GATEWAY_HEALTHY=0
for i in $(seq 1 24); do
  if curl -fsS --connect-timeout 3 --max-time 10 "http://localhost:$GATEWAY_PORT/actuator/health" >/dev/null 2>&1; then
    GATEWAY_HEALTHY=1
    ok "网关健康检查通过"
    break
  fi
  sleep 5
done
if [ "$GATEWAY_HEALTHY" != "1" ]; then
  # 探测失败 ＝ 只证明「这次请求没拿到健康的 200」，成因可能是未就绪、崩溃、端口未监听或被占用……
  # 状态码/连接阶段就能区分一部分，故回显 curl 的**原始 stderr 与 HTTP 状态**而不是断言原因。
  warn "网关健康检查在 120 秒内未通过。最近一次探测的原始输出（stderr）与状态码："
  curl -sS -o /dev/null -w '    http_code=%{http_code} connect=%{time_connect}s total=%{time_total}s\n' \
    --connect-timeout 3 --max-time 10 "http://localhost:$GATEWAY_PORT/actuator/health" 2>&1 | sed 's/^/    /' || true
  warn "该现象成因不止一种，**具体原因需按下面命令的实际输出判断**，脚本不代下结论："
  info "  1) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml ps   # 网关容器是 running / restarting / exited"
  info "  2) docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 ypbin-gateway"
  info "  3) curl -v --connect-timeout 3 http://localhost:$GATEWAY_PORT/actuator/health   # 连接被拒 vs 连上但非 200，指向不同成因"
  info "  4) docker logs --tail 50 ypbin-nacos   # 或 docker compose -f $ROOT/ypbin-admin/deploy/docker-compose.yml logs --tail 50 nacos"
  info "     # 网关注册依赖 Nacos；Nacos 未就绪时网关会持续重试（日志里是可识别的重试行）"
  info "  5) ss -ltnp | grep \":$GATEWAY_PORT\"   # 端口是否有人监听、监听者是不是本套网关容器"
  info "  6) tail -n 50 $ROOT/logs/ypbin-gateway.log   # NO_DOCKER=1 模式下网关日志在此"
  info "  本步骤不自动判定成败：未通过不等于部署失败，也不等于「仍在启动」，请按上面输出确认。"
fi
# 网关 HTTP 通过 ≠ 其它服务起来了：再逐个核对容器状态（崩溃/重启循环会在这里暴露）
if [ "$NO_DOCKER" != "1" ]; then
  check_service_containers
fi

echo ""
if [ "${GATEWAY_HEALTHY:-0}" != "1" ]; then
  # 文案与行为对齐：本步骤不改退出码（仍按既有行为走完并打印结束横幅），但必须明确说明
  # 「跑完了」不等于「服务已就绪」，否则使用者会以横幅为准去排查业务问题。
  warn "[7/7] 网关健康检查未通过（详见上方原始输出与自查命令）——下面的横幅只代表脚本执行完毕，不代表服务已就绪"
fi
echo "================================================"
echo "  ypbin-admin 微服务版部署完成"
# 访问地址用「默认出口 IP」而不是 localhost：远程部署时 localhost 毫无用处；
# 也不用 hostname -I 的第一个（它常把 docker0 的 172.x 排在前面）
ACCESS_HOST="$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") print $(i + 1)}' | head -1)"
[ -n "$ACCESS_HOST" ] || ACCESS_HOST="$(hostname -I 2>/dev/null | awk '{print $1}')"
[ -n "$ACCESS_HOST" ] || ACCESS_HOST="localhost"
NAT_HINT=""
case "$ACCESS_HOST" in
  10.*|172.1[6-9].*|172.2[0-9].*|172.3[01].*|192.168.*) NAT_HINT="  ← 检测到内网 IP，外网访问请换成公网 IP" ;;
esac
echo "  前端（管理后台）: http://$ACCESS_HOST:${ADMIN_UI_PORT:-19000}$NAT_HINT"
echo "  网关入口:        http://$ACCESS_HOST:$GATEWAY_PORT"
echo "  登录接口:        POST http://$ACCESS_HOST:$GATEWAY_PORT/auth/login"
echo "  Nacos 控制台:    http://$ACCESS_HOST:8848/nacos （默认 nacos/nacos）"
echo "  MySQL:           $ACCESS_HOST:3306/ypbin_admin  用户 root"
echo "  数据库密码:      见 $ENV_FILE 的 MYSQL_ROOT_PASSWORD（读取命令）"
echo "                   grep ^MYSQL_ROOT_PASSWORD= $ENV_FILE"
echo "  部署目录:        $ROOT"
if [ "$NO_DOCKER" = "1" ]; then
  echo "  服务日志:        $ROOT/logs/*.log"
else
  echo "  XXL-Job 控制台:  http://$ACCESS_HOST:18085/xxl-job-admin （默认 admin/123456）"
  echo "  管理:            cd $ROOT/ypbin-admin/deploy && docker compose -f docker-compose.yml logs -f"
fi
echo "================================================"
