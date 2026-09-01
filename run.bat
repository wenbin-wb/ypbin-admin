@echo off
setlocal

set "JAVA_HOME=C:\Users\Administrator\.jdks\azul-21"
set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"

rem 从 .env 加载（或直接填写）
for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    if not "%%A"=="" if not "%%A:~0,1%"=="#" set "%%A=%%B"
)

cd /d "%~dp0"
echo === Starting ypbin-gateway on port 18080 (Java %JAVA_HOME%) ===
call "%MVN%" -f ypbin-gateway/pom.xml spring-boot:run

pause
