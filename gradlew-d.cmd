@echo off
rem Ejecuta Gradle usando el JDK y la cache instalados en el disco D:
set "JAVA_HOME=D:\DevTools\jdk-25.0.4.1+1"
set "GRADLE_USER_HOME=D:\gradle-home"
call "%~dp0gradlew.bat" %*
