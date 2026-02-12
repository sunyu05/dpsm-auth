# 基础镜像，使用 Amazon ECR Public Gallery 中的 Eclipse Temurin Java 21 JRE
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /dpsm-auth

WORKDIR /dpsm-auth

EXPOSE 8080

ADD ./target/dpsm-auth.jar ./

CMD java $JAVA_OPTS -jar dpsm-auth.jar
