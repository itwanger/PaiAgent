FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /backend

# Configure Maven to use Aliyun mirror for faster downloads in China
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>\n\
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"\n\
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n\
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0\n\
          https://maven.apache.org/xsd/settings-1.2.0.xsd">\n\
  <mirrors>\n\
    <mirror>\n\
      <id>aliyun-central</id>\n\
      <mirrorOf>central</mirrorOf>\n\
      <name>Aliyun Maven Central Mirror</name>\n\
      <url>https://maven.aliyun.com/repository/central</url>\n\
    </mirror>\n\
    <mirror>\n\
      <id>aliyun-public</id>\n\
      <mirrorOf>*</mirrorOf>\n\
      <name>Aliyun Maven Public Mirror</name>\n\
      <url>https://maven.aliyun.com/repository/public</url>\n\
    </mirror>\n\
  </mirrors>\n\
</settings>' > /root/.m2/settings.xml

COPY console/backend/pom.xml ./
COPY console/backend/commons/pom.xml commons/pom.xml
COPY console/backend/hub/pom.xml hub/pom.xml
COPY console/backend/toolkit/pom.xml toolkit/pom.xml

RUN mvn -pl hub -am dependency:go-offline

COPY console/backend/ .
RUN mvn -DskipTests package -pl hub -am

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && \
    apt-get install -y locales tzdata && \
    locale-gen zh_CN.UTF-8 && \
    ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai
ENV LANG=zh_CN.UTF-8
ENV LANGUAGE=zh_CN:zh
ENV LC_ALL=zh_CN.UTF-8

COPY --from=build /backend/hub/target/hub-server.jar /app/app.jar
EXPOSE 8080

# Optimized JVM parameters for reduced memory usage:
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-jar", "/app/app.jar"]
