FROM eclipse-temurin:21-jre
WORKDIR /app
COPY ypbin-admin-server/target/ypbin-admin.jar /app/app.jar
# JDK 21 分代 ZGC：低延迟 GC，停顿控制在亚毫秒级；-XX:+ZGenerational 启用新生代分区提升吞吐
# 虚拟线程下请求处理对象短生命周期多，分代 ZGC 可更高效回收新生代对象
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms256m -Xmx512m" \
    TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
