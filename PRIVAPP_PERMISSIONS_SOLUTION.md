# Android系统应用权限白名单配置解决方案

## 🚨 问题分析

根据您提供的错误日志，问题的核心是：

```
java.lang.IllegalStateException: Signature|privileged permissions not in privapp-permissions allowlist: 
{com.example.mymediaplayer (/system_ext/priv-app/MyMediaPlayer): android.permission.CAPTURE_AUDIO_OUTPUT}
```

这表明应用已成功安装到系统特权应用目录 `/system_ext/priv-app/MyMediaPlayer/`，但是 `CAPTURE_AUDIO_OUTPUT` 权限没有被正确添加到特权应用权限白名单中。

## 🔧 解决方案

### 1. 权限配置文件路径问题

**问题**：您将权限文件推送到了 `/system/etc/permissions/`，但对于 `/system_ext/priv-app/` 中的应用，权限文件应该放在不同的位置。

**正确路径**：
- 对于 `/system_ext/priv-app/` 中的应用：`/system_ext/etc/permissions/`
- 或者：`/system_ext/etc/privapp-permissions/`

### 2. 权限文件内容优化

创建正确的权限配置文件 `com.example.mymediaplayer.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <!-- 基础权限 -->
        <permission name="android.permission.INTERNET"/>
        <permission name="android.permission.READ_EXTERNAL_STORAGE"/>
        <permission name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <permission name="android.permission.READ_MEDIA_AUDIO"/>
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        
        <!-- 关键的音频捕获权限 -->
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        
        <!-- 前台服务权限 -->
        <permission name="android.permission.FOREGROUND_SERVICE"/>
        <permission name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
        
        <!-- MTK车载系统可能需要的额外权限 -->
        <permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
        <permission name="android.permission.WAKE_LOCK"/>
        <permission name="android.permission.ACCESS_NETWORK_STATE"/>
        
        <!-- 车载系统特定权限 -->
        <permission name="android.car.permission.CAR_AUDIO"/>
        <permission name="android.car.permission.CAR_CONTROL_AUDIO_VOLUME"/>
    </privapp-permissions>
</permissions>
```

### 3. 完整的安装步骤

#### 步骤1：准备文件
```bash
# 1. 编译release版本APK
./gradlew assembleRelease

# 2. 准备权限配置文件
# 将上述XML内容保存为 com.example.mymediaplayer.xml
```

#### 步骤2：推送到设备
```bash
# 1. 重新挂载系统分区为可写
adb root
adb remount

# 2. 创建应用目录
adb shell mkdir -p /system_ext/priv-app/MyMediaPlayer

# 3. 推送APK
adb push app/build/outputs/apk/release/app-release.apk /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk

# 4. 推送权限文件到正确位置
adb push com.example.mymediaplayer.xml /system_ext/etc/permissions/
# 或者
adb push com.example.mymediaplayer.xml /system_ext/etc/privapp-permissions/

# 5. 设置正确的权限
adb shell chmod 644 /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk
adb shell chmod 644 /system_ext/etc/permissions/com.example.mymediaplayer.xml

# 6. 设置SELinux上下文
adb shell chcon u:object_r:system_file:s0 /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk
adb shell chcon u:object_r:system_file:s0 /system_ext/etc/permissions/com.example.mymediaplayer.xml
```

#### 步骤3：重启设备
```bash
adb reboot
```

### 4. MTK车载系统特殊配置

对于MTK车载系统，可能还需要额外的配置：

#### 4.1 检查vendor分区权限
```bash
# 检查是否需要在vendor分区也添加权限
adb shell ls /vendor/etc/permissions/
adb shell ls /vendor/etc/privapp-permissions/
```

#### 4.2 可能的额外权限文件位置
```bash
# 尝试这些位置
/vendor/etc/permissions/com.example.mymediaplayer.xml
/odm/etc/permissions/com.example.mymediaplayer.xml
/product/etc/permissions/com.example.mymediaplayer.xml
```

### 5. 验证配置

#### 5.1 检查应用安装状态
```bash
# 检查应用是否被识别为系统应用
adb shell pm list packages -s | grep mymediaplayer

# 检查应用权限
adb shell dumpsys package com.example.mymediaplayer | grep permission
```

#### 5.2 检查权限文件是否生效
```bash
# 检查系统是否读取了权限文件
adb shell dumpsys package | grep -A 20 "privapp-permissions"
```

### 6. 常见问题排查

#### 6.1 如果仍然报错
1. **检查包名一致性**：确保XML中的package名与AndroidManifest.xml中的完全一致
2. **检查权限文件格式**：确保XML格式正确，没有语法错误
3. **检查文件权限**：确保权限文件的读取权限正确
4. **检查SELinux**：确保SELinux上下文正确

#### 6.2 备用方案
如果上述方法仍然不行，可以尝试：

```bash
# 方案1：同时在多个位置放置权限文件
adb push com.example.mymediaplayer.xml /system/etc/permissions/
adb push com.example.mymediaplayer.xml /system_ext/etc/permissions/
adb push com.example.mymediaplayer.xml /vendor/etc/permissions/

# 方案2：检查其他系统应用的权限配置作为参考
adb shell ls /system_ext/etc/permissions/
adb shell cat /system_ext/etc/permissions/platform.xml
```

### 7. 最终验证

安装完成后，运行应用并点击"系统应用状态检查"按钮，应该显示：
- ✅ 应用是系统应用
- ✅ 具有系统UID
- ✅ 拥有CAPTURE_AUDIO_OUTPUT权限

## 📝 注意事项

1. **备份系统**：在修改系统分区前，建议备份原始系统
2. **权限最小化**：只添加应用真正需要的权限
3. **测试验证**：每次修改后都要重启设备并验证功能
4. **版本兼容性**：不同Android版本的权限系统可能有差异

按照以上步骤操作，应该能够解决您遇到的权限白名单问题。