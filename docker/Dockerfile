FROM amazoncorretto:17
LABEL org.opencontainers.image.authors="skydrive860@gmail.com"

# docker container의 timezone을 서울로 변경
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY build/libs/growjoy-0.0.1-SNAPSHOT.jar app.jar
CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]