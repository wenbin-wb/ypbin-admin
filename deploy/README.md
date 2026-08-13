# ypbin-admin 部署指南

基于 Docker Compose 部署 admin 后端 + admin-ui 前端 + MySQL + Redis。

## 架构

| 服务 | 镜像 | 端口(默认) | 说明 |
|---|---|---|---|
| `admin` | 自建(Java 17) | 8080 | Spring Boot 后端,Flyway 自动建表 |
| `admin-ui` | nginx | 18080 | 前端静态文件(本地构建上传),`/api/` 代理到 admin |
| `mysql` | mysql:8.4 | 内部 | 数据库,数据卷持久化 |
| `redis` | redis:7-alpine | 内部 | 缓存 |

## 前置条件

- Ubuntu/Debian 服务器,root 权限
- 网络可访问 GitHub(拉代码)、Maven Central(构建)、Docker Hub(拉镜像)
- 服务器能出网访问 npm registry(仅构建前端时需要,见下方"前端构建")

## 一键部署

```bash
wget -qO- https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/deploy.sh | bash
```

脚本自动:安装依赖(git/maven/JDK/docker)→ 拉取三仓代码 → 构建 admin jar → 生成凭据 `.env` → 启动容器。

之后更新也执行同一条命令。

## 前端构建(重要)

服务器若无法访问 npm registry(国内常见),前端需**本地构建上传**,不构建镜像:

```bash
# 本机
cd ypbin-admin-ui
git pull --ff-only
pnpm install
pnpm -F @vben/web-antd build      # 产物在 apps/web-antd/dist
scp -r apps/web-antd/dist/* root@<服务器IP>:/opt/ypbin/admin-ui-dist/
```

前端 API 地址在 `.env.production` 的 `VITE_GLOB_API_URL`(默认 `/api`,走 nginx 同源代理)。改完前端上传后,nginx 直接读新文件,无需重启。

## 配置 `.env`

编辑 `/opt/ypbin/ypbin-admin/deploy/.env`:

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码(必改) |
| `ADMIN_BOOTSTRAP_USERNAME/PASSWORD` | 首次登录创建的管理员(登录后建议关 Bootstrap) |
| `ADMIN_PORT` / `ADMIN_UI_PORT` | 端口(默认 8080 / 18080,被占用可改) |
| `YPBIN_CORS_ORIGINS` | **CORS 允许的来源,逗号分隔**(生产必配,见下) |

## CORS 配置(容易踩坑)

admin 后端默认只允许 `http://localhost:*`。浏览器从其他域名/IP 访问时,POST 会报 `403 Invalid CORS request`。

在 `.env` 配置(逗号分隔):
```
YPBIN_CORS_ORIGINS=http://localhost:*,https://admin.你的域名.com,http://你的服务器IP:*
```
改后重跑 deploy.sh(重建 jar 生效)。

## 对外访问(宝塔 nginx)

服务器已有宝塔占用了 80/443,admin-ui 容器用 18080 避开。宝塔站点 nginx 配置:

```nginx
server {
    listen 80;
    server_name admin.你的域名.com;

    location / {
        proxy_pass http://127.0.0.1:18080;   # admin-ui 前端
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;   # 去掉 /api 前缀(后端接口无 /api)
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
    }
}
```

> 关键:`location /api/` 的 `proxy_pass http://127.0.0.1:8080/` 末尾斜杠会**去掉 `/api` 前缀**。admin 接口路径是 `/auth/login`、`/system/xxx`(无 `/api`),若保留前缀会返回 404"接口不存在"。

## 常见坑排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `JAVA_HOME` 未定义 | 服务器只装了 maven 没设 JAVA_HOME | 脚本已自动装 JDK17 + 探测 JAVA_HOME |
| `apt: Unmet dependencies` | 之前的安装中断留 broken 状态 | `apt --fix-broken install -y` 后重跑 |
| 容器内通、宿主机不通 | 宝塔清掉了 Docker 转发规则 | `systemctl restart docker` 重建规则 |
| 端口被占用 | 宝塔等已占 80/8080/8081 | 改 `.env` 的 `ADMIN_PORT`/`ADMIN_UI_PORT` |
| `403 Invalid CORS` | 后端 CORS 未配访问源 | `.env` 配 `YPBIN_CORS_ORIGINS` |
| `404 接口不存在` | nginx 代理保留了 `/api` 前缀 | `proxy_pass` 加末尾斜杠去掉前缀 |
| 前端请求 localhost | 构建时 `VITE_GLOB_API_URL` 是 localhost | 改 `.env.production` 为 `/api` 后重建前端 |
| corepack 下载 pnpm 失败 | 服务器访问 npmjs 受限 | 前端本地构建上传,见上文 |

## 首次登录

访问 `http://<域名或IP>` ,用 `.env` 的 `ADMIN_BOOTSTRAP_USERNAME/PASSWORD` 登录。登录后把 `.env` 的 Bootstrap 相关关闭并重跑。

## 数据

MySQL 数据在 `deploy_mysql-data` 卷,`docker compose down` 不删数据,重建不丢。
