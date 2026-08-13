FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/ypbin-admin.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
