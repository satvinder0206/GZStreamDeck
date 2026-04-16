@echo off
title GZ Stream Deck Native Builder
echo ---------------------------------------------------
echo GZ Stream Deck - True Native Builder (Fixes Task Manager)
echo ---------------------------------------------------

IF NOT EXIST "target\GZStreamDeckApp-3.0.jar" (
    echo ERROR: Please click "Clean and Build" in NetBeans first!
    pause
    exit /b
)

echo Cleaning old builds...
rmdir /s /q "target\native-app" 2>nul
mkdir "target\jpackage-input" 2>nul
copy "target\GZStreamDeckApp-3.0.jar" "target\jpackage-input\" /Y >nul

echo Building true gzstreamdeck.exe...
"C:\Program Files\Apache NetBeans\jdk\bin\jpackage.exe" --type app-image --name gzstreamdeck --input target\jpackage-input --main-jar GZStreamDeckApp-3.0.jar --main-class com.singhgz.gzstreamdeck.GZStreamDeckApp --icon src\main\resources\app_icon.ico --dest target\native-app

echo.
echo SUCCESS! Your app is now a true standalone Windows executable.
echo Location: target\native-app\gzstreamdeck\gzstreamdeck.exe
pause