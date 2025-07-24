# Android SystemApi 和 Framework.jar 使用指南

## 问题描述

在开发 Android 车载系统应用时，遇到了以下编译错误：
```
e: file:///Users/simple/AndroidStudioProjects/MyMediaPlayer/app/src/main/java/com/example/mymediaplayer/MainActivity.kt:130:70 Unresolved reference 'USER_SETUP_COMPLETE'.
```

## @SystemApi 注解含义

### 什么是 @SystemApi

`@SystemApi` 是 Android 框架中用于标记系统级 API 的注解，具有以下特点：

1. **系统级访问**：只允许系统应用或具有系统签名的应用访问
2. **稳定性**：比一般的 `@hide` 方法更稳定，可能在未来版本中保持兼容性
3. **内部使用**：主要供 OEM 厂商、系统应用和内部团队使用
4. **权限要求**：通常需要特殊权限或系统签名才能正常使用

### @SystemApi 与 @hide 的区别

- `@SystemApi` 是 `@hide` 的子集
- `@SystemApi` 方法比 `@hide` 方法更稳定
- `@SystemApi` 旨在提供更可靠的内部 API 接口
- 访问 `@SystemApi` 通常需要系统签名或反射调用

## USER_SETUP_COMPLETE 变量使用

### 变量定义
```java
@SystemApi 
@Readable 
public static final String USER_SETUP_COMPLETE = "user_setup_complete";
```

### 功能说明
`USER_SETUP_COMPLETE` 是 `android.provider.Settings.Secure` 类中的常量，用于：
- 表示用户设置是否完成
- 检查设备首次设置向导是否已完成
- 系统级应用状态管理

## 解决方案

### 方案一：配置 Framework.jar（推荐）

#### 1. 添加 Framework.jar 到项目
将平台的 `framework.jar` 文件放置到 `app/libs/` 目录下。

#### 2. 修改 build.gradle 配置
```gradle
android {
    namespace 'com.example.mymediaplayer'
    compileSdk 34
    apply plugin: 'kotlin-kapt'
    
    // 配置使用私有平台API
    useLibrary 'org.apache.http.legacy'
    
    // ... 其他配置
}

// 配置使用framework.jar进行编译，解决SystemApi访问问题
allprojects {
    gradle.projectsEvaluated {
        tasks.withType(JavaCompile) {
            options.compilerArgs.add('-Xbootclasspath/p:' + file('libs/framework.jar').absolutePath)
        }
    }
}

dependencies {
    // 添加framework.jar以访问SystemApi
    compileOnly files('libs/framework.jar')
    
    // ... 其他依赖
}
```

### 方案二：使用反射访问（备用方案）

如果 Framework.jar 配置遇到问题，可以使用反射方式：

```kotlin
// 使用反射访问SystemApi中的USER_SETUP_COMPLETE
try {
    val userSetupCompleteField = Settings.Secure::class.java.getDeclaredField("USER_SETUP_COMPLETE")
    val userSetupComplete = userSetupCompleteField.get(null) as String
    Settings.Secure.putInt(getContentResolver(), userSetupComplete, 1)
    Log.d("zqqtest", "成功设置USER_SETUP_COMPLETE")
} catch (e: Exception) {
    Log.e("zqqtest", "设置USER_SETUP_COMPLETE失败，使用硬编码值", e)
    // 如果反射失败，使用硬编码的字符串值
    Settings.Secure.putInt(getContentResolver(), "user_setup_complete", 1)
}
```

### 方案三：直接使用字符串常量（最简单）

```kotlin
// 直接使用字符串常量
Settings.Secure.putInt(getContentResolver(), "user_setup_complete", 1)
```

## 使用示例

### 读取用户设置状态
```kotlin
/**
 * 检查用户设置是否完成
 * @return true 如果用户设置已完成，false 否则
 */
fun isUserSetupComplete(): Boolean {
    return try {
        Settings.Secure.getInt(contentResolver, "user_setup_complete", 0) == 1
    } catch (e: Exception) {
        Log.e(TAG, "检查用户设置状态失败", e)
        false
    }
}
```

### 设置用户设置完成状态
```kotlin
/**
 * 设置用户设置完成状态
 * @param completed true 表示设置完成，false 表示未完成
 */
fun setUserSetupComplete(completed: Boolean) {
    try {
        val value = if (completed) 1 else 0
        Settings.Secure.putInt(contentResolver, "user_setup_complete", value)
        Log.d(TAG, "用户设置状态已更新: $completed")
    } catch (e: Exception) {
        Log.e(TAG, "设置用户设置状态失败", e)
    }
}
```

## 注意事项

### 权限要求
1. **系统签名**：应用需要使用平台签名
2. **系统权限**：可能需要 `android.permission.WRITE_SECURE_SETTINGS` 权限
3. **系统应用**：建议将应用安装为系统应用

### MTK 车载系统特殊配置

对于 MTK 车载系统，可能需要额外配置：

```gradle
signingConfigs {
    create("release") {
        storeFile = file("${rootDir.absolutePath}/app/libs/platform_mtk.jks")
        storePassword = "your_password"
        keyAlias = "platform_key"
        keyPassword = "your_password"
    }
}
```

### 编译警告处理

编译过程中可能出现的警告：
- `Kapt currently doesn't support language version 2.0+`
- 已弃用 API 的使用警告
- 这些警告不影响功能，但建议在后续版本中更新

## 故障排除

### 常见问题

1. **编译错误**：确保 framework.jar 路径正确
2. **运行时权限错误**：检查应用签名和权限配置
3. **反射失败**：使用硬编码字符串作为备用方案

### 调试建议

1. 使用 `adb shell` 检查系统设置：
   ```bash
   adb shell settings get secure user_setup_complete
   ```

2. 检查应用签名：
   ```bash
   adb shell pm list packages -f | grep your.package.name
   ```

3. 查看系统日志：
   ```bash
   adb logcat | grep "your_tag"
   ```

## 总结

通过配置 Framework.jar 和使用反射机制，成功解决了 `USER_SETUP_COMPLETE` 的编译错误。这种方法适用于 Android 车载系统开发中需要访问 SystemApi 的场景。建议优先使用 Framework.jar 配置方案，反射方案作为备用选择。

## 相关文档

- [Android SystemApi 开发指南](https://source.android.com/docs)
- [MTK 车载系统开发文档](https://docs.mediatek.com/)
- [Android Settings Provider 文档](https://developer.android.com/reference/android/provider/Settings)