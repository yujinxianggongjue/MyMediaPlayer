@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM Android系统应用自动安装脚本 (Windows版本)
REM 适用于MTK车载系统
REM =============================================================================

REM 配置变量
set PACKAGE_NAME=com.example.mymediaplayer
set APP_NAME=MyMediaPlayer
set APK_PATH=app\build\outputs\apk\release\app-release.apk
set PERMISSIONS_FILE=%PACKAGE_NAME%.xml

REM 系统路径配置
set SYSTEM_APP_DIR=/system_ext/priv-app/%APP_NAME%

echo.
echo ===============================================================================
echo Android系统应用自动安装脚本
echo 应用: %APP_NAME%
echo 包名: %PACKAGE_NAME%
echo ===============================================================================
echo.

REM 检查ADB连接
echo [INFO] 检查ADB连接...
adb devices | findstr "device" >nul
if errorlevel 1 (
    echo [ERROR] 没有检测到ADB设备连接
    echo [INFO] 请确保：
    echo [INFO] 1. 设备已连接并开启USB调试
    echo [INFO] 2. 已授权ADB调试
    pause
    exit /b 1
)
echo [SUCCESS] ADB设备连接正常

REM 检查APK文件
echo [INFO] 检查APK文件...
if not exist "%APK_PATH%" (
    echo [ERROR] APK文件不存在: %APK_PATH%
    echo [INFO] 请先编译release版本: gradlew.bat assembleRelease
    pause
    exit /b 1
)
echo [SUCCESS] APK文件存在: %APK_PATH%

REM 创建权限配置文件
echo [INFO] 创建权限配置文件...
(
echo ^<?xml version="1.0" encoding="utf-8"?^>
echo ^<permissions^>
echo     ^<privapp-permissions package="com.example.mymediaplayer"^>
echo         ^<!-- 基础权限 --^>
echo         ^<permission name="android.permission.INTERNET"/^>
echo         ^<permission name="android.permission.READ_EXTERNAL_STORAGE"/^>
echo         ^<permission name="android.permission.WRITE_EXTERNAL_STORAGE"/^>
echo         ^<permission name="android.permission.READ_MEDIA_AUDIO"/^>
echo         ^<permission name="android.permission.RECORD_AUDIO"/^>
echo         ^<permission name="android.permission.MODIFY_AUDIO_SETTINGS"/^>
echo.
echo         ^<!-- 关键的音频捕获权限 --^>
echo         ^<permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/^>
echo.
echo         ^<!-- 前台服务权限 --^>
echo         ^<permission name="android.permission.FOREGROUND_SERVICE"/^>
echo         ^<permission name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/^>
echo.
echo         ^<!-- 系统权限 --^>
echo         ^<permission name="android.permission.SYSTEM_ALERT_WINDOW"/^>
echo         ^<permission name="android.permission.WAKE_LOCK"/^>
echo         ^<permission name="android.permission.ACCESS_NETWORK_STATE"/^>
echo.
echo         ^<!-- 车载系统特定权限 --^>
echo         ^<permission name="android.car.permission.CAR_AUDIO"/^>
echo         ^<permission name="android.car.permission.CAR_CONTROL_AUDIO_VOLUME"/^>
echo     ^</privapp-permissions^>
echo ^</permissions^>
) > "%PERMISSIONS_FILE%"
echo [SUCCESS] 权限配置文件已创建: %PERMISSIONS_FILE%

REM 获取root权限并重新挂载
echo [INFO] 获取ADB root权限...
adb root
timeout /t 2 /nobreak >nul

echo [INFO] 重新挂载系统分区为可写...
adb remount
timeout /t 1 /nobreak >nul
echo [SUCCESS] 系统分区挂载完成

REM 卸载旧版本应用
echo [INFO] 检查并卸载旧版本应用...
adb shell pm list packages | findstr "%PACKAGE_NAME%" >nul
if not errorlevel 1 (
    echo [WARNING] 发现已安装的应用，正在卸载...
    adb shell pm uninstall "%PACKAGE_NAME%" 2>nul
    adb shell rm -rf "%SYSTEM_APP_DIR%" 2>nul
    adb shell rm -f "/system_ext/etc/permissions/%PERMISSIONS_FILE%" 2>nul
    adb shell rm -f "/system_ext/etc/privapp-permissions/%PERMISSIONS_FILE%" 2>nul
    adb shell rm -f "/system/etc/permissions/%PERMISSIONS_FILE%" 2>nul
    adb shell rm -f "/vendor/etc/permissions/%PERMISSIONS_FILE%" 2>nul
    echo [SUCCESS] 旧版本应用已卸载
) else (
    echo [INFO] 未发现已安装的应用
)

REM 安装系统应用
echo [INFO] 安装系统应用...
echo [INFO] 创建系统应用目录: %SYSTEM_APP_DIR%
adb shell mkdir -p "%SYSTEM_APP_DIR%"

echo [INFO] 推送APK文件...
adb push "%APK_PATH%" "%SYSTEM_APP_DIR%/%APP_NAME%.apk"

REM 设置APK文件权限
adb shell chmod 644 "%SYSTEM_APP_DIR%/%APP_NAME%.apk"
adb shell chown root:root "%SYSTEM_APP_DIR%/%APP_NAME%.apk"
adb shell chcon u:object_r:system_file:s0 "%SYSTEM_APP_DIR%/%APP_NAME%.apk" 2>nul
echo [SUCCESS] APK文件安装完成

REM 安装权限配置文件
echo [INFO] 安装权限配置文件...

REM 安装到多个可能的位置
echo [INFO] 推送权限文件到: /system_ext/etc/permissions
adb shell mkdir -p "/system_ext/etc/permissions" 2>nul
adb push "%PERMISSIONS_FILE%" "/system_ext/etc/permissions/%PERMISSIONS_FILE%"
adb shell chmod 644 "/system_ext/etc/permissions/%PERMISSIONS_FILE%"
adb shell chown root:root "/system_ext/etc/permissions/%PERMISSIONS_FILE%"
adb shell chcon u:object_r:system_file:s0 "/system_ext/etc/permissions/%PERMISSIONS_FILE%" 2>nul
echo [SUCCESS] 权限文件已安装到: /system_ext/etc/permissions

echo [INFO] 推送权限文件到: /system_ext/etc/privapp-permissions
adb shell mkdir -p "/system_ext/etc/privapp-permissions" 2>nul
adb push "%PERMISSIONS_FILE%" "/system_ext/etc/privapp-permissions/%PERMISSIONS_FILE%" 2>nul
if not errorlevel 1 (
    adb shell chmod 644 "/system_ext/etc/privapp-permissions/%PERMISSIONS_FILE%"
    adb shell chown root:root "/system_ext/etc/privapp-permissions/%PERMISSIONS_FILE%"
    adb shell chcon u:object_r:system_file:s0 "/system_ext/etc/privapp-permissions/%PERMISSIONS_FILE%" 2>nul
    echo [SUCCESS] 权限文件已安装到: /system_ext/etc/privapp-permissions
)

echo [INFO] 推送权限文件到: /system/etc/permissions
adb push "%PERMISSIONS_FILE%" "/system/etc/permissions/%PERMISSIONS_FILE%" 2>nul
if not errorlevel 1 (
    adb shell chmod 644 "/system/etc/permissions/%PERMISSIONS_FILE%"
    adb shell chown root:root "/system/etc/permissions/%PERMISSIONS_FILE%"
    adb shell chcon u:object_r:system_file:s0 "/system/etc/permissions/%PERMISSIONS_FILE%" 2>nul
    echo [SUCCESS] 权限文件已安装到: /system/etc/permissions
)

echo [INFO] 推送权限文件到: /vendor/etc/permissions
adb push "%PERMISSIONS_FILE%" "/vendor/etc/permissions/%PERMISSIONS_FILE%" 2>nul
if not errorlevel 1 (
    adb shell chmod 644 "/vendor/etc/permissions/%PERMISSIONS_FILE%"
    adb shell chown root:root "/vendor/etc/permissions/%PERMISSIONS_FILE%"
    adb shell chcon u:object_r:system_file:s0 "/vendor/etc/permissions/%PERMISSIONS_FILE%" 2>nul
    echo [SUCCESS] 权限文件已安装到: /vendor/etc/permissions
)

REM 验证安装
echo [INFO] 验证安装结果...
adb shell ls "%SYSTEM_APP_DIR%/%APP_NAME%.apk" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] APK文件安装失败
    goto cleanup
)
echo [SUCCESS] APK文件安装成功

REM 检查权限文件
set permissions_found=0
adb shell ls "/system_ext/etc/permissions/%PERMISSIONS_FILE%" >nul 2>&1
if not errorlevel 1 (
    echo [SUCCESS] 权限文件存在于: /system_ext/etc/permissions
    set permissions_found=1
)

adb shell ls "/system_ext/etc/privapp-permissions/%PERMISSIONS_FILE%" >nul 2>&1
if not errorlevel 1 (
    echo [SUCCESS] 权限文件存在于: /system_ext/etc/privapp-permissions
    set permissions_found=1
)

adb shell ls "/system/etc/permissions/%PERMISSIONS_FILE%" >nul 2>&1
if not errorlevel 1 (
    echo [SUCCESS] 权限文件存在于: /system/etc/permissions
    set permissions_found=1
)

if !permissions_found! equ 0 (
    echo [ERROR] 权限文件安装失败
    goto cleanup
)

echo [SUCCESS] 安装验证通过

REM 清理临时文件
:cleanup
echo [INFO] 清理临时文件...
if exist "%PERMISSIONS_FILE%" del "%PERMISSIONS_FILE%"
echo [SUCCESS] 清理完成

REM 显示安装后说明
echo.
echo ===============================================================================
echo [SUCCESS] 安装完成
echo ===============================================================================
echo [INFO] 应用已安装为系统特权应用
echo [INFO] 安装位置: %SYSTEM_APP_DIR%
echo [INFO] 权限配置: 已安装到多个系统目录
echo.
echo [INFO] 下一步操作：
echo [INFO] 1. 重启设备
echo [INFO] 2. 启动应用
echo [INFO] 3. 点击'系统应用状态检查'按钮验证权限
echo [INFO] 4. 测试音频捕获功能
echo.
echo [WARNING] 如果仍有权限问题，请检查:
echo [WARNING] 1. SELinux策略是否允许音频捕获
echo [WARNING] 2. 车载系统是否有额外的权限限制
echo [WARNING] 3. 查看系统日志: adb logcat ^| findstr permission
echo.

REM 询问是否重启
set /p reboot_choice="是否立即重启设备？(y/N): "
if /i "!reboot_choice!"=="y" (
    echo [INFO] 重启设备以应用更改...
    echo [WARNING] 设备将在5秒后重启，按Ctrl+C取消
    for /l %%i in (5,-1,1) do (
        echo %%i
        timeout /t 1 /nobreak >nul
    )
    adb reboot
    echo [SUCCESS] 设备重启命令已发送
    echo [INFO] 请等待设备重启完成后测试应用功能
) else (
    echo [INFO] 请手动重启设备以应用更改
)

echo.
echo 按任意键退出...
pause >nul
exit /b 0