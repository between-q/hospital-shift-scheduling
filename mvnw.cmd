@echo off
set "PROJECT_DIR=%~dp0"
set "WRAPPER_JAR=%PROJECT_DIR%.mvn\wrapper\maven-wrapper.jar"
java -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
