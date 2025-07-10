# MTK车载系统 Privapp-Permissions 权限问题排查指南

## 问题描述

当Android系统应用安装到 `/system_ext/priv-app/` 目录后，出现以下错误：

```
java.lang.IllegalStateException: Signature|privileged permissions not in privapp-permissions allowlist: 
{com.example.mymediaplayer (/system_ext/priv-app/MyMediaPlayer): android.permission.CAPTURE_AUDIO_OUTPUT}
```

## 根本原因分析

### 1. 权限白名单机制
Android 8.0+ 引入了 privapp-permissions 白名单机制，要求所有特权应用的敏感权限必须在白名单中明确声明。

### 2. MTK车载系统特殊性
- MTK车载系统使用 `/system_ext` 分区
- 权限配置文件路径与标准Android不同
- 可能存在多个权限配置目录

## 完整解决方案

### 步骤1: 确认正确的权限文件路径

MTK车载系统可能的权限文件路径（按优先级排序）：

1. `/system_ext/etc/permissions/`
2. `/system_ext/etc/privapp-permissions/`
3. `/system/etc/permissions/`
4. `/vendor/etc/permissions/`

### 步骤2: 创建正确的权限配置文件

创建文件：`com.example.mymediaplayer.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <!-- 基础权限 -->
        <permission name="android.permission.INTERNET"/>
        <permission name="android.permission.READ_EXTERNAL_STORAGE"/>
        <permission name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <permission name="android.permission.READ_MEDIA_AUDIO"/>
        
        <!-- 音频相关权限 -->
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        
        <!-- 前台服务权限 -->
        <permission name="android.permission.FOREGROUND_SERVICE"/>
        <permission name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
        
        <!-- 系统级权限 -->
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
        <permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
    </privapp-permissions>
</permissions>
```

### 步骤3: 使用自动化安装脚本

#### Linux/Mac 用户
```bash
./install_system_app.sh
```

#### Windows 用户
```cmd
install_system_app.bat
```

### 步骤4: 手动安装步骤（如果脚本失败）

```bash
# 1. 连接设备并获取root权限
adb root
adb remount

# 2. 卸载旧版本（如果存在）
adb shell pm uninstall com.example.mymediaplayer

# 3. 创建应用目录
adb shell mkdir -p /system_ext/priv-app/MyMediaPlayer

# 4. 推送APK文件
adb push app/build/outputs/apk/debug/app-debug.apk /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk

# 5. 设置APK权限
adb shell chmod 644 /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk
adb shell chown root:root /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk

# 6. 推送权限配置文件到多个位置
adb push com.example.mymediaplayer.xml /system_ext/etc/permissions/
adb push com.example.mymediaplayer.xml /system_ext/etc/privapp-permissions/
adb push com.example.mymediaplayer.xml /system/etc/permissions/

# 7. 设置权限文件权限
adb shell chmod 644 /system_ext/etc/permissions/com.example.mymediaplayer.xml
adb shell chmod 644 /system_ext/etc/privapp-permissions/com.example.mymediaplayer.xml
adb shell chmod 644 /system/etc/permissions/com.example.mymediaplayer.xml

# 8. 设置SELinux上下文
adb shell restorecon -R /system_ext/priv-app/MyMediaPlayer/
adb shell restorecon /system_ext/etc/permissions/com.example.mymediaplayer.xml
adb shell restorecon /system_ext/etc/privapp-permissions/com.example.mymediaplayer.xml

# 9. 重启设备
adb reboot
```

## 验证安装

### 1. 检查应用安装状态
```bash
adb shell pm list packages | grep mymediaplayer
adb shell dumpsys package com.example.mymediaplayer | grep -E "(install|system|priv)"
```

### 2. 检查权限状态
```bash
adb shell dumpsys package com.example.mymediaplayer | grep -A 20 "requested permissions"
```

### 3. 检查文件权限
```bash
adb shell ls -la /system_ext/priv-app/MyMediaPlayer/
adb shell ls -la /system_ext/etc/permissions/com.example.mymediaplayer.xml
adb shell ls -la /system_ext/etc/privapp-permissions/com.example.mymediaplayer.xml
```

### 4. 检查SELinux上下文
```bash
adb shell ls -Z /system_ext/priv-app/MyMediaPlayer/
adb shell ls -Z /system_ext/etc/permissions/com.example.mymediaplayer.xml
```

## 常见问题排查

### 问题1: 权限文件路径错误
**症状**: 仍然出现 privapp-permissions 错误
**解决**: 尝试将权限文件放到所有可能的路径

### 问题2: SELinux上下文错误
**症状**: 应用无法启动或权限被拒绝
**解决**: 
```bash
adb shell restorecon -R /system_ext/priv-app/MyMediaPlayer/
adb shell restorecon -R /system_ext/etc/permissions/
```

### 问题3: 签名不匹配
**症状**: 应用安装失败
**解决**: 使用平台签名重新签名APK

### 问题4: 系统分区只读
**症状**: 无法推送文件
**解决**: 
```bash
adb root
adb disable-verity
adb reboot
adb root
adb remount
```

## MTK车载系统特殊配置

### 1. 检查车载音频服务
```bash
adb shell dumpsys car_audio
```

### 2. 检查音频策略配置
```bash
adb shell cat /vendor/etc/audio_policy_configuration.xml
```

### 3. 检查SELinux策略
```bash
adb shell getenforce
adb shell seinfo -a | grep mediaplayer
```

## 调试命令

### 查看系统日志
```bash
adb logcat | grep -E "(PermissionManager|PackageManager|mymediaplayer)"
```

### 查看权限管理器状态
```bash
adb shell dumpsys permission
```

### 查看包管理器状态
```bash
adb shell dumpsys package
```

## 成功标志

安装成功后，应该看到：
1. 应用出现在系统应用列表中
2. 所有权限都被授予
3. 没有 privapp-permissions 相关错误
4. 应用可以正常捕获系统音频

## 联系支持

如果问题仍然存在，请收集以下信息：
1. 完整的logcat日志
2. `dumpsys package com.example.mymediaplayer` 输出
3. 设备型号和Android版本
4. MTK芯片型号
5. 车载系统版本信息