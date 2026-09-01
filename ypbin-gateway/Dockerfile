FROM eclipse-temurin:21-jre
WORKDIR /app
# 可执行 jar（revision 版本名，通配匹配；排除 .original 原始包）
COPY target/ypbin-gateway-*.jar /app/app.jar
# JDK 21 分代 ZGC：低延迟 GC；虚拟线程下请求处理对象短生命周期多，分代 ZGC 更高效
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms256m -Xmx512m" \
    TZ=Asia/Shanghai
EXPOSE 18080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
