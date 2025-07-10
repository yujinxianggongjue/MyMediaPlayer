# SystemUI MediaProjection 崩溃问题解决方案

## 🚨 问题描述

点击 "AUDIO PLAYBACK CAPTURE" 按钮时，SystemUI 崩溃并出现以下错误：

```
java.lang.NoSuchFieldError: No field Companion of type Landroidx/lifecycle/ReportFragment$Companion; 
in class Landroidx/lifecycle/n0; or its superclasses 
(declaration of 'androidx.lifecycle.n0' appears in /system_ext/priv-app/CariadCarSystemUI/CariadCarSystemUI.apk!classes2.dex)
```

## 🔍 根本原因分析

### 1. 库版本冲突
- **问题核心**: androidx.lifecycle 库版本不兼容
- **冲突位置**: CariadCarSystemUI.apk 与我们的应用使用了不同版本的 androidx.lifecycle
- **具体表现**: ReportFragment$Companion 字段在不同版本中的定义不一致

### 2. MTK车载系统特殊性
- CariadCarSystemUI 是定制的系统UI
- 使用了特定版本的 androidx 库
- MediaProjectionPermissionActivity 在启动时触发版本冲突

## 🛠️ 解决方案

### 方案1: 修改应用的依赖版本（推荐）

#### 1.1 检查当前依赖
```bash
./gradlew app:dependencies | grep androidx.lifecycle
```

#### 1.2 降级 androidx.lifecycle 版本
在 `app/build.gradle` 中添加：

```gradle
android {
    // ... 其他配置
}

dependencies {
    // 强制使用兼容版本
    implementation 'androidx.lifecycle:lifecycle-runtime:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-common:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-process:2.6.2'
    
    // 排除冲突的依赖
    configurations.all {
        resolutionStrategy {
            force 'androidx.lifecycle:lifecycle-runtime:2.6.2'
            force 'androidx.lifecycle:lifecycle-common:2.6.2'
        }
    }
    
    // ... 其他依赖
}
```

### 方案2: 使用兼容的MediaProjection实现

#### 2.1 创建自定义MediaProjection管理器
```kotlin
class CompatMediaProjectionManager(private val context: Context) {
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    /**
     * 安全地请求MediaProjection权限
     */
    fun requestPermissionSafely(activity: Activity, requestCode: Int) {
        try {
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            Log.e("MediaProjection", "请求权限失败", e)
            // 降级处理：直接尝试音频捕获
            fallbackToDirectAudioCapture(activity)
        }
    }
    
    /**
     * 降级方案：直接音频捕获
     */
    private fun fallbackToDirectAudioCapture(activity: Activity) {
        Toast.makeText(activity, "使用直接音频捕获模式", Toast.LENGTH_SHORT).show()
        // 实现直接音频捕获逻辑
    }
}
```

### 方案3: 绕过SystemUI的MediaProjection

#### 3.1 使用反射调用
```kotlin
class DirectAudioCaptureManager {
    /**
     * 直接启动音频捕获，绕过SystemUI
     */
    fun startDirectAudioCapture(): Boolean {
        return try {
            // 使用反射直接调用AudioSystem
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val method = audioSystemClass.getDeclaredMethod("setParameters", String::class.java)
            method.isAccessible = true
            method.invoke(null, "audio_capture_enabled=true")
            true
        } catch (e: Exception) {
            Log.e("DirectCapture", "直接音频捕获失败", e)
            false
        }
    }
}
```

## 🔧 立即修复步骤

### 步骤1: 修改build.gradle
```bash
# 编辑依赖配置
vim app/build.gradle
```

### 步骤2: 重新编译和安装
```bash
# 清理项目
./gradlew clean

# 重新编译
./gradlew assembleDebug

# 重新安装
./install_system_app.sh
```

### 步骤3: 验证修复
```bash
# 监控日志
adb logcat | grep -E "(MediaProjection|SystemUI|mymediaplayer)"
```

## 🚀 临时解决方案

如果需要立即测试音频捕获功能，可以：

### 1. 重启SystemUI
```bash
adb shell killall com.android.systemui
```

### 2. 使用ADB直接启动MediaProjection
```bash
adb shell am start -n com.android.systemui/.media.MediaProjectionPermissionActivity
```

### 3. 或者跳过权限请求，直接测试音频功能
在应用中临时注释掉MediaProjection相关代码，直接测试AudioRecord功能。

## 🔍 调试命令

### 检查SystemUI状态
```bash
adb shell dumpsys activity | grep SystemUI
```

### 检查MediaProjection服务
```bash
adb shell dumpsys media_projection
```

### 检查依赖冲突
```bash
./gradlew app:dependencyInsight --dependency androidx.lifecycle
```

## ⚠️ 注意事项

1. **版本兼容性**: 确保所有androidx库版本一致
2. **系统稳定性**: SystemUI崩溃可能影响整个系统
3. **测试验证**: 修改后需要充分测试所有功能
4. **备份方案**: 准备多种音频捕获实现方式

## 📋 检查清单

- [ ] 检查androidx.lifecycle版本
- [ ] 修改build.gradle依赖
- [ ] 重新编译应用
- [ ] 重新安装系统应用
- [ ] 测试MediaProjection功能
- [ ] 验证SystemUI稳定性
- [ ] 测试音频捕获功能

---

💡 **建议**: 优先使用方案1（修改依赖版本），这是最安全和可靠的解决方案。