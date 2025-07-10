# 系统应用权限问题排查指南

## 问题现象
应用仍然提示"不是系统应用"，无法获取 `CAPTURE_AUDIO_OUTPUT` 权限。

## 根本原因分析

### 1. 签名不匹配问题
系统应用需要使用**平台签名**（platform signature），而不是普通的开发者签名。

### 2. 当前配置问题
```xml
<!-- AndroidManifest.xml 中的配置 -->
<manifest android:sharedUserId="android.uid.system">
```

虽然设置了 `sharedUserId="android.uid.system"`，但如果签名不是平台签名，系统仍然不会将应用识别为系统应用。

## 解决方案

### 方案一：使用正确的平台签名（推荐）

#### 1.1 获取系统平台签名
```bash
# 从Android源码中提取平台签名
# 通常位于：build/target/product/security/
# 文件：platform.pk8 和 platform.x509.pem
```

#### 1.2 生成平台keystore
```bash
# 使用openssl转换平台证书
openssl pkcs8 -inform DER -nocrypt -in platform.pk8 -out platform.key
openssl x509 -inform DER -in platform.x509.pem -out platform.crt

# 生成PKCS12格式
openssl pkcs12 -export -in platform.crt -inkey platform.key -out platform.p12 -name platform

# 转换为Java keystore
keytool -importkeystore -deststorepass 123456 -destkeypass 123456 -destkeystore platform.keystore -srckeystore platform.p12 -srcstoretype PKCS12 -srcstorepass 123456 -alias platform
```

#### 1.3 更新build.gradle配置
```gradle
signingConfigs {
    create("platform") {
        storeFile = file("${rootDir.absolutePath}/app/libs/platform_system.keystore")
        storePassword = "android"
        keyAlias = "platform"
        keyPassword = "android"
    }
}

buildTypes {
    debug {
        signingConfig = signingConfigs.getByName("platform")
    }
    release {
        signingConfig = signingConfigs.getByName("platform")
    }
}
```

### 方案二：MTK车载系统特定配置

#### 2.1 MTK平台签名
```bash
# MTK车载系统通常有自己的平台签名
# 联系MTK获取正确的platform.keystore
# 或从车载系统ROM中提取
```

#### 2.2 车载系统权限配置
```xml
<!-- 添加车载系统特定权限 -->
<uses-permission android:name="android.car.permission.CAR_AUDIO" />
<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS" />
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
```

### 方案三：系统应用安装方式

#### 3.1 推送到系统分区
```bash
# 将APK推送到系统应用目录
adb root
adb remount
adb push app-release.apk /system/app/MyMediaPlayer/
adb shell chmod 644 /system/app/MyMediaPlayer/MyMediaPlayer.apk
adb reboot
```

#### 3.2 使用系统签名安装
```bash
# 使用平台签名重新签名APK
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore platform.keystore app-release-unsigned.apk platform
zipalign -v 4 app-release-unsigned.apk app-release-signed.apk
```

## 验证方法

### 1. 检查应用UID
```bash
# 查看应用的UID
adb shell ps | grep com.example.mymediaplayer
# 系统应用的UID应该是1000（system）
```

### 2. 检查权限状态
```bash
# 查看应用权限
adb shell dumpsys package com.example.mymediaplayer | grep permission
```

### 3. 代码中验证
```kotlin
/**
 * 检查是否为系统应用
 */
fun isSystemApp(context: Context): Boolean {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName, 
            PackageManager.GET_SIGNATURES
        )
        val applicationInfo = packageInfo.applicationInfo
        
        // 检查是否有系统应用标志
        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: Exception) {
        false
    }
}

/**
 * 检查UID是否为系统UID
 */
fun isSystemUid(): Boolean {
    return Process.myUid() == Process.SYSTEM_UID
}
```

## 常见错误和解决方案

### 错误1：签名冲突
```
Installation failed with message INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
```
**解决方案**：确保使用正确的平台签名

### 错误2：权限被拒绝
```
java.lang.SecurityException: Permission Denial
```
**解决方案**：检查AndroidManifest.xml中的权限声明和sharedUserId配置

### 错误3：无法获取CAPTURE_AUDIO_OUTPUT权限
```
UnsupportedOperationException: Error: could not register audio policy
```
**解决方案**：
1. 确保使用平台签名
2. 将应用安装到系统分区
3. 检查SELinux策略

## MTK车载系统特殊注意事项

### 1. SELinux策略
```bash
# 检查SELinux状态
adb shell getenforce

# 临时关闭SELinux（仅用于测试）
adb shell setenforce 0
```

### 2. 车载系统权限
```xml
<!-- 添加到AndroidManifest.xml -->
<uses-permission android:name="android.car.permission.CAR_AUDIO" />
<uses-permission android:name="android.car.permission.AUDIO_SETTINGS" />
```

### 3. 系统属性配置
```bash
# 设置音频捕获相关属性
adb shell setprop ro.config.media_vol_steps 25
adb shell setprop ro.config.vc_call_vol_steps 7
```

## 推荐的完整解决流程

1. **获取正确的平台签名文件**
2. **更新build.gradle签名配置**
3. **清理并重新编译项目**
4. **使用平台签名安装APK**
5. **验证系统应用状态**
6. **测试音频捕获功能**

## 联系MTK技术支持

如果以上方案都无法解决问题，建议：
1. 联系MTK技术支持获取正确的平台签名
2. 确认车载系统的音频权限策略
3. 获取系统应用开发指南

---

**注意**：系统应用开发需要严格遵循安全规范，确保不会影响系统稳定性。