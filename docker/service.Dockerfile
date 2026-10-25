# 后端服务统一镜像模板：各服务通过 build args 传入模块路径
# 注意：基础镜像用 JDK 21（项目 target 17，21 可运行）；容器内 Linux 的 AF_UNIX/Selector 正常工作，
# 不受宿主机 Windows JDK NIO Pipe 问题影响
FROM eclipse-temurin:21-jre-noble

ARG JAR_FILE
WORKDIR /app
COPY ${JAR_FILE} app.jar

# TZ for Asia/Shanghai datetime columns
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms128m -Xmx320m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
