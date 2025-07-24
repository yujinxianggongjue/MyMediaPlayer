# Android 系统应用权限白名单配置解决方案

## 问题描述

在 MTK 车载系统中安装系统应用时遇到以下致命错误：

```
07-23 10:55:33.968  1273  1273 E Zygote  : System zygote died with fatal exception 
07-23 10:55:33.968  1273  1273 E Zygote  : java.lang.IllegalStateException: Signature|privileged permissions not in privapp-permissions allowlist: {com.example.mymediaplayer (/system_ext/priv-app/MyMediaPlayer): android.permission.CAPTURE_AUDIO_OUTPUT} 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.server.pm.permission.PermissionManagerServiceImpl.onSystemReady(PermissionManagerServiceImpl.java:4469) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.server.pm.permission.PermissionManagerService$PermissionManagerServiceInternalImpl.onSystemReady(PermissionManagerService.java:760) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.server.pm.PackageManagerService.systemReady(PackageManagerService.java:4342) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.server.SystemServer.startOtherServices(SystemServer.java:2841) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.server.SystemServer.run(SystemServer.java:959) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.server.SystemServer.main(SystemServer.java:675) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at java.lang.reflect.Method.invoke(Native Method) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:573) 
07-23 10:55:33.968  1273  1273 E Zygote  : 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1029) 
07-23 10:55:33.968  1273  1273 D AndroidRuntime: Shutting down VM
```

## 错误分析

### 核心问题
- **权限类型**：`android.permission.CAPTURE_AUDIO_OUTPUT`
- **应用包名**：`com.example.mymediaplayer`
- **安装位置**：`/system_ext/priv-app/MyMediaPlayer`
- **错误原因**：特权应用权限未在 privapp-permissions 白名单中

### 权限说明
`CAPTURE_AUDIO_OUTPUT` 是一个系统级权限，用于：
- 捕获系统音频输出
- 录制其他应用的音频
- 实现音频监控功能
- 车载系统音频处理

## 解决方案

### 方案一：创建 privapp-permissions 配置文件（推荐）

#### 1. 创建权限配置文件

在系统分区创建权限配置文件：
```bash
# MTK 车载系统路径
/system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
```

#### 2. 权限配置文件内容

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 
    特权应用权限配置文件
    应用包名: com.example.mymediaplayer
    用途: 车载媒体播放器系统应用
-->
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <!-- 音频捕获权限 - 用于录制系统音频输出 -->
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        
        <!-- 其他可能需要的系统权限 -->
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
        <permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
        <permission name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <permission name="android.permission.READ_EXTERNAL_STORAGE"/>
        
        <!-- 媒体投影相关权限 -->
        <permission name="android.permission.MEDIA_CONTENT_CONTROL"/>
        <permission name="android.permission.BIND_MEDIA_ROUTE_SERVICE"/>
        
        <!-- 车载系统特定权限 -->
        <permission name="android.car.permission.CAR_AUDIO"/>
        <permission name="android.car.permission.CAR_CONTROL_AUDIO_VOLUME"/>
    </privapp-permissions>
</permissions>
```

#### 3. 文件权限设置

```bash
# 设置正确的文件权限
chmod 644 /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
chown root:root /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
```

### 方案二：修改现有权限配置文件

#### 1. 查找现有配置文件

```bash
# 查找系统中的 privapp-permissions 文件
find /system* -name "*privapp-permissions*" -type f

# 常见位置
/system/etc/permissions/privapp-permissions-platform.xml
/system_ext/etc/permissions/privapp-permissions-*.xml
/vendor/etc/permissions/privapp-permissions-*.xml
```

#### 2. 编辑现有文件

在适当的 privapp-permissions 文件中添加：

```xml
<privapp-permissions package="com.example.mymediaplayer">
    <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
</privapp-permissions>
```

### 方案三：使用 Android.mk 构建配置

#### 1. 创建 Android.mk 文件

```makefile
# Android.mk
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp-permissions-com.example.mymediaplayer.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_SYSTEM_EXT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)
```

#### 2. 添加到设备配置

在设备的 `device.mk` 文件中添加：

```makefile
# 添加特权应用权限配置
PRODUCT_COPY_FILES += \
    device/mtk/your_device/permissions/privapp-permissions-com.example.mymediaplayer.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
```

## 实施步骤

### 步骤 1：准备权限配置文件

```bash
# 1. 创建权限配置目录
mkdir -p /tmp/permissions

# 2. 创建权限配置文件
cat > /tmp/permissions/privapp-permissions-com.example.mymediaplayer.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
    </privapp-permissions>
</permissions>
EOF
```

### 步骤 2：部署到系统分区

```bash
# 1. 重新挂载系统分区为可写
adb root
adb remount

# 2. 推送权限配置文件
adb push /tmp/permissions/privapp-permissions-com.example.mymediaplayer.xml /system_ext/etc/permissions/

# 3. 设置正确权限
adb shell chmod 644 /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
adb shell chown root:root /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
```

### 步骤 3：重新安装应用

```bash
# 1. 卸载现有应用（如果已安装）
adb uninstall com.example.mymediaplayer

# 2. 重新安装为系统应用
adb push MyMediaPlayer.apk /system_ext/priv-app/MyMediaPlayer/
adb shell chmod 644 /system_ext/priv-app/MyMediaPlayer/MyMediaPlayer.apk

# 3. 重启系统
adb reboot
```

## 验证方法

### 1. 检查权限配置

```bash
# 检查权限文件是否存在
adb shell ls -la /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml

# 检查文件内容
adb shell cat /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml
```

### 2. 检查应用权限

```bash
# 检查应用是否正确安装
adb shell pm list packages | grep mymediaplayer

# 检查应用权限
adb shell dumpsys package com.example.mymediaplayer | grep permission
```

### 3. 检查系统日志

```bash
# 监控系统启动日志
adb logcat | grep -E "(Zygote|PermissionManager|mymediaplayer)"

# 检查权限相关错误
adb logcat | grep "privapp-permissions"
```

## 常见问题排查

### 问题 1：权限文件不生效

**可能原因：**
- 文件路径错误
- 文件权限不正确
- XML 格式错误
- 系统未重启

**解决方法：**
```bash
# 检查文件格式
xmllint --noout /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml

# 重新设置权限
adb shell chmod 644 /system_ext/etc/permissions/privapp-permissions-com.example.mymediaplayer.xml

# 重启系统
adb reboot
```

### 问题 2：系统分区只读

**解决方法：**
```bash
# 方法 1：使用 adb remount
adb root
adb remount

# 方法 2：手动重新挂载
adb shell mount -o rw,remount /system_ext

# 方法 3：使用 Magisk（如果可用）
# 通过 Magisk 模块方式添加权限文件
```

### 问题 3：MTK 特定问题

**MTK 车载系统特殊配置：**
```bash
# MTK 可能需要额外的权限路径
/vendor/etc/permissions/
/odm/etc/permissions/

# 检查 MTK 特定的权限配置
adb shell find /vendor -name "*privapp*" -type f
adb shell find /odm -name "*privapp*" -type f
```

## 自动化脚本

### 权限配置部署脚本

```bash
#!/bin/bash
# deploy_privapp_permissions.sh

APP_PACKAGE="com.example.mymediaplayer"
PERMISSION_FILE="privapp-permissions-${APP_PACKAGE}.xml"
TARGET_PATH="/system_ext/etc/permissions/${PERMISSION_FILE}"

echo "部署 privapp-permissions 配置..."

# 1. 检查 ADB 连接
if ! adb devices | grep -q "device$"; then
    echo "错误：未检测到 ADB 设备连接"
    exit 1
fi

# 2. 获取 root 权限
adb root
sleep 2

# 3. 重新挂载系统分区
echo "重新挂载系统分区..."
adb remount

# 4. 创建权限配置文件
echo "创建权限配置文件..."
cat > "/tmp/${PERMISSION_FILE}" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
    </privapp-permissions>
</permissions>
EOF

# 5. 推送到设备
echo "推送权限配置文件到设备..."
adb push "/tmp/${PERMISSION_FILE}" "${TARGET_PATH}"

# 6. 设置正确权限
echo "设置文件权限..."
adb shell chmod 644 "${TARGET_PATH}"
adb shell chown root:root "${TARGET_PATH}"

# 7. 验证部署
echo "验证部署结果..."
if adb shell test -f "${TARGET_PATH}"; then
    echo "✓ 权限配置文件部署成功"
    echo "文件路径：${TARGET_PATH}"
    adb shell ls -la "${TARGET_PATH}"
else
    echo "✗ 权限配置文件部署失败"
    exit 1
fi

echo "请重启设备以使配置生效：adb reboot"
```

### 使用方法

```bash
# 给脚本执行权限
chmod +x deploy_privapp_permissions.sh

# 运行脚本
./deploy_privapp_permissions.sh

# 重启设备
adb reboot
```

## 最佳实践

### 1. 权限最小化原则
- 只添加应用实际需要的权限
- 避免添加过多不必要的系统权限
- 定期审查权限配置

### 2. 文档化
- 记录每个权限的用途
- 维护权限变更历史
- 提供权限配置的注释说明

### 3. 测试验证
- 在开发环境充分测试
- 验证权限配置的有效性
- 监控系统日志确认无错误

### 4. 版本管理
- 将权限配置文件纳入版本控制
- 为不同 Android 版本维护不同配置
- 建立配置文件的备份机制

## 相关权限说明

### 音频相关权限

| 权限名称 | 用途 | 风险级别 |
|---------|------|----------|
| `CAPTURE_AUDIO_OUTPUT` | 捕获系统音频输出 | 高 |
| `RECORD_AUDIO` | 录制音频 | 中 |
| `MODIFY_AUDIO_SETTINGS` | 修改音频设置 | 中 |
| `WRITE_SECURE_SETTINGS` | 写入安全设置 | 高 |

### 车载系统权限

| 权限名称 | 用途 | 适用场景 |
|---------|------|----------|
| `android.car.permission.CAR_AUDIO` | 车载音频控制 | 车载系统 |
| `android.car.permission.CAR_CONTROL_AUDIO_VOLUME` | 音量控制 | 车载系统 |
| `android.car.permission.CAR_MEDIA` | 车载媒体 | 车载系统 |

## 总结

通过正确配置 privapp-permissions 白名单，可以解决 `CAPTURE_AUDIO_OUTPUT` 权限导致的系统启动失败问题。关键步骤包括：

1. **创建权限配置文件**：定义应用所需的特权权限
2. **部署到正确位置**：确保文件在系统权限目录中
3. **设置正确权限**：文件权限必须为 644，所有者为 root
4. **重启系统**：使权限配置生效
5. **验证结果**：确认应用正常运行且无权限错误

这个解决方案适用于 MTK 车载系统和其他 Android 系统应用开发场景。

## 参考资料

- [Android 特权应用权限文档](https://source.android.com/docs/core/permissions/privapp-allowlist)
- [MTK 车载系统开发指南](https://docs.mediatek.com/)
- [Android 权限系统详解](https://developer.android.com/guide/topics/permissions/overview)
- [系统应用开发最佳实践](https://source.android.com/docs/core/architecture/bootloader)