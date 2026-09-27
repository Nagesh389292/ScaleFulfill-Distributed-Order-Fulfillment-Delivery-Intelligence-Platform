@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
"%USERPROFILE%\.tools\apache-maven-3.9.9\bin\mvn.cmd" %*
