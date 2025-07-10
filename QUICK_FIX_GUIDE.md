# 🚗 MTK车载系统权限问题快速修复指南

## ⚡ 一键解决方案

### 方法1: 使用自动化脚本（推荐）
```bash
# Linux/Mac
./install_system_app.sh

# Windows
install_system_app.bat
```

### 方法2: 手动快速修复
```bash
# 1. 获取root权限
adb root && adb remount

# 2. 创建权限文件
echo '<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        <permission name="android.permission.FOREGROUND_SERVICE"/>
        <permission name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
    </privapp-permissions>
</permissions>' > com.example.mymediaplayer.xml

# 3. 推送到所有可能的位置
adb push com.example.mymediaplayer.xml /system_ext/etc/permissions/
adb push com.example.mymediaplayer.xml /system_ext/etc/privapp-permissions/
adb push com.example.mymediaplayer.xml /system/etc/permissions/

# 4. 安装APK
adb shell mkdir -p /system_ext/priv-app/MyMediaPlayer
adb push app/build/outputs/apk/debug/app-debug.apk /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk

# 5. 设置权限
adb shell chmod 644 /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk
adb shell restorecon -R /system_ext/priv-app/MyMediaPlayer/

# 6. 重启
adb reboot
```

## 🔍 问题诊断

### 检查当前状态
```bash
# 检查应用是否为系统应用
adb shell dumpsys package com.example.mymediaplayer | grep -E "(install|system|priv)"

# 检查权限状态
adb shell dumpsys package com.example.mymediaplayer | grep -A 10 "requested permissions"

# 查看错误日志
adb logcat | grep -E "(PermissionManager|privapp-permissions|mymediaplayer)"
```

## 📱 应用内诊断

1. 打开应用
2. 点击 "系统应用状态检查" 按钮
3. 查看完整诊断报告
4. 点击 "查看MTK配置" 获取详细信息

## ⚠️ 常见错误及解决

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `privapp-permissions allowlist` | 权限文件路径错误 | 推送到多个路径 |
| `Permission denied` | SELinux上下文错误 | 执行 `restorecon` |
| `Installation failed` | 签名问题 | 使用平台签名 |
| `Read-only file system` | 分区未重新挂载 | `adb remount` |

## 🎯 关键路径（MTK车载系统）

```
优先级1: /system_ext/etc/permissions/
优先级2: /system_ext/etc/privapp-permissions/
优先级3: /system/etc/permissions/
```

## ✅ 验证成功

成功安装后应该看到：
- ✅ 应用显示为系统应用
- ✅ 所有权限已授予
- ✅ 无 privapp-permissions 错误
- ✅ 可以捕获系统音频

## 🆘 紧急联系

如果问题仍然存在，请提供：
1. `adb logcat` 完整日志
2. `dumpsys package com.example.mymediaplayer` 输出
3. 设备型号和Android版本
4. MTK芯片型号

---

💡 **提示**: 大多数问题都是权限文件路径不正确导致的，尝试推送到所有可能的路径通常能解决问题。