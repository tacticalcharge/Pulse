@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  echo Missing official Gradle wrapper JAR. Run: powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-wrapper.ps1
  exit /b 1
)
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
