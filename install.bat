@echo off
REM Scriptum installer for Windows.
REM Run in PowerShell:  irm https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.bat -OutFile install.bat ^& .\install.bat
REM Or download install.bat from GitHub and double-click it.
setlocal EnableDelayedExpansion

set REPO=https://github.com/Zhumanio08/Scriptum.git
set DIR=%USERPROFILE%\AppData\Local\scriptum
set BIN=%USERPROFILE%\AppData\Local\Microsoft\WindowsApps

echo ==^> Checking Java...
where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java not found.
    echo Install JDK 17+ from https://adoptium.net/temurin/releases/ then re-run this script.
    pause
    exit /b 1
)
java -version 2>&1 | findstr /i version

echo ==^> Checking Maven...
where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven not found, downloading portable Maven 3.9.9...
    if not exist "%DIR%\tools" mkdir "%DIR%\tools"
    cd /d "%DIR%\tools"
    if not exist "apache-maven-3.9.9" (
        curl -fSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip -o maven.zip
        powershell -Command "Expand-Archive -Force maven.zip ."
        del maven.zip
    )
    set "MVN=%DIR%\tools\apache-maven-3.9.9\bin\mvn.cmd"
) else (
    set MVN=mvn
)
call "%MVN%" -version 2>&1 | findstr /i "Apache Maven"

echo ==^> Downloading Scriptum to %DIR%\repo ...
where git >nul 2>nul
if errorlevel 1 (
    echo ERROR: git not found. Install it from https://git-scm.com/download/win then re-run.
    pause
    exit /b 1
)
if exist "%DIR%\repo\.git" (
    git -C "%DIR%\repo" pull --ff-only
) else (
    if exist "%DIR%\repo" rmdir /s /q "%DIR%\repo"
    git clone --depth 1 "%REPO%" "%DIR%\repo"
)

echo ==^> Building...
cd /d "%DIR%\repo"
call "%MVN%" -q clean package
if errorlevel 1 (
    echo BUILD FAILED. See output above.
    pause
    exit /b 1
)

echo ==^> Installing scriptum.cmd ...
set JAR=%DIR%\repo\target\scriptum-0.1.0-SNAPSHOT.jar
(
    echo @echo off
    echo java -jar "%JAR%" %%*
) > "%DIR%\scriptum.cmd"

echo Copying launcher to %BIN% ^(already on PATH^)...
copy /y "%DIR%\scriptum.cmd" "%BIN%\scriptum.cmd" >nul

echo.
echo DONE! Testing:
call "%BIN%\scriptum.cmd" "%DIR%\repo\test.md"

echo.
echo You can now run from any folder:  scriptum file.md
pause
