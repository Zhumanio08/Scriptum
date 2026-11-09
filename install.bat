@echo off
REM Scriptum installer for Windows.
REM
REM PowerShell:  irm https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.bat -OutFile install.bat; .\install.bat
REM Or download install.bat from GitHub and double-click it.
REM
REM Builds Scriptum from source and installs:
REM   scriptum.cmd   in %LOCALAPPDATA%\Microsoft\WindowsApps (already on PATH)
REM   Scriptum       shortcut in the Start menu (opens the desktop app)
REM Needs: JDK 17+ and git. Maven is downloaded automatically if missing.
setlocal EnableDelayedExpansion

set "REPO=https://github.com/Zhumanio08/Scriptum.git"
set "DIR=%LOCALAPPDATA%\scriptum"
set "BIN=%LOCALAPPDATA%\Microsoft\WindowsApps"
set "MAVEN_VERSION=3.9.9"

echo ==^> Checking Java...
where java >nul 2>nul
if errorlevel 1 goto :nojava
where javac >nul 2>nul
if errorlevel 1 goto :nojdk
java -version 2>&1 | findstr /i "version"

echo ==^> Checking git...
where git >nul 2>nul
if errorlevel 1 goto :nogit

echo ==^> Checking Maven...
where mvn >nul 2>nul
if errorlevel 1 (
    if not exist "%DIR%\tools\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd" (
        echo     Maven not found, downloading portable Maven %MAVEN_VERSION%...
        if not exist "%DIR%\tools" mkdir "%DIR%\tools"
        curl -fsSL "https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip" -o "%DIR%\tools\maven.zip"
        if errorlevel 1 goto :fail
        powershell -NoProfile -Command "Expand-Archive -Force '%DIR%\tools\maven.zip' '%DIR%\tools'"
        del "%DIR%\tools\maven.zip"
    )
    set "MVN=%DIR%\tools\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"
) else (
    set "MVN=mvn"
)

echo ==^> Getting the source into %DIR%\repo ...
if exist "%DIR%\repo\.git" (
    git -C "%DIR%\repo" fetch --quiet --depth 1 origin master
    git -C "%DIR%\repo" reset --quiet --hard origin/master
) else (
    if exist "%DIR%\repo" rmdir /s /q "%DIR%\repo"
    git clone --quiet --depth 1 "%REPO%" "%DIR%\repo"
)
if errorlevel 1 goto :fail

echo ==^> Building (this takes a minute the first time)...
pushd "%DIR%\repo"
call "%MVN%" -q -B -DskipTests package
if errorlevel 1 (
    popd
    goto :fail
)
popd
set "JAR=%DIR%\repo\target\scriptum.jar"

echo ==^> Installing the scriptum command...
(
    echo @echo off
    echo java -jar "%JAR%" %%*
) > "%BIN%\scriptum.cmd"

echo ==^> Adding Scriptum to the Start menu...
powershell -NoProfile -Command ^
  "$s = (New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Programs') + '\Scriptum.lnk');" ^
  "$s.TargetPath = (Get-Command javaw.exe -ErrorAction SilentlyContinue).Source;" ^
  "if (-not $s.TargetPath) { $s.TargetPath = 'javaw.exe' };" ^
  "$s.Arguments = '-jar \"%JAR%\" gui';" ^
  "$s.WorkingDirectory = $env:USERPROFILE;" ^
  "$s.Description = 'Run the Java inside your Markdown';" ^
  "$s.Save()"

echo ==^> Testing...
call "%BIN%\scriptum.cmd" --version
call "%BIN%\scriptum.cmd" --quiet "%DIR%\repo\examples\hello.md"

echo.
echo Scriptum is installed.
echo     scriptum notes.md    run the Java blocks of a Markdown file
echo     scriptum gui         open the desktop app (also in the Start menu)
pause
exit /b 0

:nojava
echo ERROR: Java not found. Install JDK 17+ (winget install EclipseAdoptium.Temurin.17.JDK) and run this again.
pause
exit /b 1

:nojdk
echo ERROR: javac not found. Scriptum needs a JDK, not only a JRE: winget install EclipseAdoptium.Temurin.17.JDK
pause
exit /b 1

:nogit
echo ERROR: git not found. Install it (winget install Git.Git) and run this again.
pause
exit /b 1

:fail
echo BUILD FAILED. See the output above.
pause
exit /b 1
