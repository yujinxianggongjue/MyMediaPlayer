# 音频捕获功能故障排除指南

## 问题描述
当前应用在尝试捕获系统音频时遇到以下错误：
```
java.lang.UnsupportedOperationException: Error: could not register audio policy
```

## 根本原因
这个错误通常由以下原因引起：

### 1. 权限不足
- `CAPTURE_AUDIO_OUTPUT` 权限是受保护的系统权限
- 只有系统应用或具有系统签名的应用才能使用此权限
- 普通第三方应用无法获得此权限

### 2. 应用签名问题
- 应用需要使用系统签名进行签名
- 或者需要将应用安装为系统应用

### 3. 设备限制
- 某些设备制造商可能禁用了音频捕获功能
- 车载系统可能有额外的安全限制

## 解决方案

### 方案1：系统应用签名
1. 获取设备的系统签名证书
2. 使用系统证书重新签名应用
3. 将应用推送到 `/system/app/` 目录

### 方案2：Root权限安装
```bash
# 需要root权限
adb root
adb remount
adb push app-debug.apk /system/app/MyMediaPlayer.apk
adb reboot
```

### 方案3：替代方案
如果无法获得系统权限，考虑以下替代方案：

#### A. 麦克风录音
- 使用 `RECORD_AUDIO` 权限录制麦克风音频
- 虽然不能直接捕获系统音频，但可以录制环境声音

#### B. MediaRecorder屏幕录制
- 使用 `MediaRecorder` 进行屏幕录制（包含音频）
- 然后从视频文件中提取音频轨道

#### C. 音频焦点监听
- 监听音频焦点变化
- 检测何时有音频播放，但无法捕获实际音频内容

## 当前实现的改进

### 1. 增强的错误处理
- 添加了详细的异常捕获和日志记录
- 提供用户友好的错误通知
- 区分不同类型的错误（权限、设备支持等）

### 2. 权限检查
- 在尝试创建AudioRecord之前检查MediaProjection状态
- 提供清晰的错误信息指导用户

### 3. 服务稳定性
- 确保前台服务在MediaProjection创建之前启动
- 在错误发生时正确清理资源

## 测试建议

### 1. 检查设备兼容性
```kotlin
// 检查设备是否支持音频捕获
fun isAudioCaptureSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
```

### 2. 权限验证
```kotlin
// 检查应用是否具有系统权限
fun hasSystemPermissions(): Boolean {
    return checkSelfPermission(android.Manifest.permission.CAPTURE_AUDIO_OUTPUT) == 
           PackageManager.PERMISSION_GRANTED
}
```

### 3. 日志监控
使用以下命令监控应用日志：
```bash
adb logcat | grep -E "(AudioCaptureService|AudioPlaybackCapture|AudioRecord)"
```

## 下一步行动

1. **确认设备类型**：确定目标设备是否为车载系统或特殊定制设备
2. **获取系统权限**：联系设备制造商获取系统签名或root权限
3. **实现替代方案**：如果无法获得系统权限，实现麦克风录音作为备选
4. **用户体验优化**：提供清晰的错误提示和操作指导

## 相关资源

- [Android Audio Capture Documentation](https://developer.android.com/guide/topics/media/playback-capture)
- [MediaProjection API Guide](https://developer.android.com/reference/android/media/projection/MediaProjection)
- [System App Development](https://source.android.com/docs/core/permissions/perms-allowlist)