@echo off
setlocal

set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\jbr"
set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"

set "DB_HOST="
set "DB_PORT="
set "DB_NAME="
set "DB_USER="
set "DB_PASSWORD="
set "REDIS_HOST="
set "REDIS_PORT="
set "REDIS_DB="
set "REDIS_PASSWORD="

cd /d "%~dp0"
echo === Starting ypbin-admin on port 8080 ===
call "%MVN%" -f ypbin-admin-server/pom.xml spring-boot:run

pause
