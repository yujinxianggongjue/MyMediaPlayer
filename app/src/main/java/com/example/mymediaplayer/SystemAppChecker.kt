package com.example.mymediaplayer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import java.security.MessageDigest

/**
 * 系统应用状态检查工具类
 * 用于验证应用是否被正确识别为系统应用
 */
class SystemAppChecker {
    
    companion object {
        private const val TAG = "zqqtestSystemAppChecker"
        
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
                val isSystem = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                Log.d(TAG, "Application flags: ${applicationInfo.flags}")
                Log.d(TAG, "FLAG_SYSTEM: $isSystem")
                Log.d(TAG, "FLAG_UPDATED_SYSTEM_APP: $isUpdatedSystem")
                
                isSystem || isUpdatedSystem
            } catch (e: Exception) {
                Log.e(TAG, "Error checking system app status", e)
                false
            }
        }
        
        /**
         * 检查UID是否为系统UID
         */
        fun isSystemUid(): Boolean {
            val myUid = Process.myUid()
            val isSystem = myUid == Process.SYSTEM_UID
            Log.d(TAG, "Current UID: $myUid, System UID: ${Process.SYSTEM_UID}, Is System: $isSystem")
            return isSystem
        }
        
        /**
         * 检查是否有CAPTURE_AUDIO_OUTPUT权限
         */
        fun hasCaptureAudioOutputPermission(context: Context): Boolean {
            return try {
                val result = context.checkSelfPermission("android.permission.CAPTURE_AUDIO_OUTPUT")
                val hasPermission = result == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "CAPTURE_AUDIO_OUTPUT permission: $hasPermission")
                hasPermission
            } catch (e: Exception) {
                Log.e(TAG, "Error checking CAPTURE_AUDIO_OUTPUT permission", e)
                false
            }
        }
        
        /**
         * 获取应用签名信息
         */
        fun getSignatureInfo(context: Context): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                
                val signatures = packageInfo.signatures
                if (signatures.isNotEmpty()) {
                    val signature = signatures[0]
                    val md = MessageDigest.getInstance("SHA-256")
                    val digest = md.digest(signature.toByteArray())
                    val hexString = digest.joinToString("") { "%02x".format(it) }
                    Log.d(TAG, "Signature SHA-256: $hexString")
                    hexString
                } else {
                    "No signatures found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting signature info", e)
                "Error: ${e.message}"
            }
        }
        
        /**
         * 获取应用安装位置
         */
        fun getInstallLocation(context: Context): String {
            return try {
                val applicationInfo = context.applicationInfo
                val sourceDir = applicationInfo.sourceDir
                Log.d(TAG, "Install location: $sourceDir")
                
                when {
                    sourceDir.startsWith("/system/app/") -> "System App (/system/app/)"
                    sourceDir.startsWith("/system/priv-app/") -> "Privileged System App (/system/priv-app/)"
                    sourceDir.startsWith("/data/app/") -> "User App (/data/app/)"
                    sourceDir.startsWith("/vendor/app/") -> "Vendor App (/vendor/app/)"
                    else -> "Unknown location: $sourceDir"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting install location", e)
                "Error: ${e.message}"
            }
        }
        
        /**
         * 执行完整的系统应用状态检查
         */
        fun performFullCheck(context: Context): SystemAppStatus {
            val isSystemApp = isSystemApp(context)
            val isSystemUid = isSystemUid()
            val hasCapturePermission = hasCaptureAudioOutputPermission(context)
            val signatureInfo = getSignatureInfo(context)
            val installLocation = getInstallLocation(context)
            
            val status = SystemAppStatus(
                isSystemApp = isSystemApp,
                isSystemUid = isSystemUid,
                hasCaptureAudioOutputPermission = hasCapturePermission,
                signatureHash = signatureInfo,
                installLocation = installLocation
            )
            
            Log.d(TAG, "Full system app check result: $status")
            return status
        }
        
        /**
         * 获取诊断建议
         */
        fun getDiagnosticAdvice(status: SystemAppStatus): String {
            return buildString {
                appendLine("=== 系统应用状态诊断 ===")
                appendLine("系统应用标志: ${if (status.isSystemApp) "✓" else "✗"}")
                appendLine("系统UID: ${if (status.isSystemUid) "✓" else "✗"}")
                appendLine("音频捕获权限: ${if (status.hasCaptureAudioOutputPermission) "✓" else "✗"}")
                appendLine("安装位置: ${status.installLocation}")
                appendLine("签名哈希: ${status.signatureHash.take(16)}...")
                appendLine()
                
                if (!status.isSystemApp && !status.isSystemUid) {
                    appendLine("❌ 问题：应用未被识别为系统应用")
                    appendLine("解决方案：")
                    appendLine("1. 检查是否使用了正确的平台签名")
                    appendLine("2. 确认AndroidManifest.xml中的sharedUserId配置")
                    appendLine("3. 将APK安装到系统分区(/system/app/)")
                    appendLine("4. 联系MTK获取正确的平台签名文件")
                } else if (status.isSystemApp && !status.hasCaptureAudioOutputPermission) {
                    appendLine("⚠️ 问题：系统应用但缺少音频捕获权限")
                    appendLine("解决方案：")
                    appendLine("1. 检查SELinux策略是否允许音频捕获")
                    appendLine("2. 确认车载系统的音频权限配置")
                    appendLine("3. 重启设备后重新测试")
                } else if (status.isSystemApp && status.hasCaptureAudioOutputPermission) {
                    appendLine("✅ 状态正常：应用已正确配置为系统应用")
                } else {
                    appendLine("🔍 需要进一步诊断，请查看详细日志")
                }
                
                // MTK车载系统特定建议
                appendLine()
                appendLine("🚗 MTK车载系统特别提示：")
                appendLine("1. 使用提供的自动安装脚本: ./install_system_app.sh")
                appendLine("2. 确保权限文件安装到正确位置: /system_ext/etc/permissions/")
                appendLine("3. 检查车载音频服务是否正常运行")
                appendLine("4. 验证SELinux上下文: ls -Z /system_ext/priv-app/MyMediaPlayer/")
            }
        }
        
        /**
         * 检查MTK车载系统特定配置
         */
        fun checkMtkCarSystemConfig(context: Context): String {
            return buildString {
                try {
                    appendLine("🚗 MTK车载系统配置检查:")
                    
                    // 检查车载服务
                    val carAudioManager = try {
                        context.getSystemService("car_audio")
                    } catch (e: Exception) {
                        null
                    }
                    appendLine("• 车载音频服务: ${if (carAudioManager != null) "✅ 可用" else "❌ 不可用"}")
                    
                    // 检查音频策略
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    if (audioManager != null) {
                        appendLine("• 音频管理器: ✅ 可用")
                        appendLine("• 音频模式: ${getAudioModeString(audioManager.mode)}")
                    } else {
                        appendLine("• 音频管理器: ❌ 不可用")
                    }
                    
                    // 检查系统属性
                    appendLine()
                    appendLine("📱 系统信息:")
                    append(getBuildInfo())
                    
                    // 检查SELinux状态
                    appendLine()
                    appendLine("🔒 SELinux信息:")
                    append(getSelinuxInfo(context))
                    
                } catch (e: Exception) {
                    appendLine("❌ 检查过程中出现错误: ${e.message}")
                }
            }
        }
        
        /**
         * 获取音频模式字符串
         */
        private fun getAudioModeString(mode: Int): String {
            return when (mode) {
                0 -> "正常模式"
                1 -> "铃声模式"
                2 -> "通话模式"
                3 -> "通信模式"
                else -> "未知模式($mode)"
            }
        }
        
        /**
         * 获取系统构建信息
         */
        private fun getBuildInfo(): String {
            return buildString {
                appendLine("• 制造商: ${android.os.Build.MANUFACTURER}")
                appendLine("• 型号: ${android.os.Build.MODEL}")
                appendLine("• Android版本: ${android.os.Build.VERSION.RELEASE}")
                appendLine("• API级别: ${android.os.Build.VERSION.SDK_INT}")
                appendLine("• 构建类型: ${android.os.Build.TYPE}")
                appendLine("• 构建标签: ${android.os.Build.TAGS}")
            }
        }
        
        /**
         * 获取SELinux信息
         */
        private fun getSelinuxInfo(context: Context): String {
            return buildString {
                try {
                    // 检查SELinux状态
                    val process = Runtime.getRuntime().exec("getenforce")
                    val reader = process.inputStream.bufferedReader()
                    val selinuxStatus = reader.readLine() ?: "未知"
                    appendLine("• SELinux状态: $selinuxStatus")
                    
                    // 检查应用的SELinux上下文
                    val appInfo = context.applicationInfo
                    appendLine("• 应用路径: ${appInfo.sourceDir}")
                    appendLine("• 数据目录: ${appInfo.dataDir}")
                    
                } catch (e: Exception) {
                    appendLine("• SELinux检查失败: ${e.message}")
                }
            }
        }
        
        /**
         * 生成完整的系统报告
         */
        fun generateSystemReport(context: Context): String {
            return buildString {
                appendLine("=== 系统应用诊断报告 ===")
                appendLine()
                
                // 基础检查
                val basicResult = performFullCheck(context)
                appendLine("📋 基础检查结果:")
                appendLine(basicResult.toString())
                appendLine()
                
                // MTK车载系统检查
                val mtkResult = checkMtkCarSystemConfig(context)
                append(mtkResult)
                appendLine()
                
                // 诊断建议
                val advice = getDiagnosticAdvice(basicResult)
                appendLine("💡 诊断建议:")
                append(advice)
                
                appendLine()
                appendLine("=== 报告结束 ===")
            }
        }
    }
}

/**
 * 系统应用状态数据类
 */
data class SystemAppStatus(
    val isSystemApp: Boolean,
    val isSystemUid: Boolean,
    val hasCaptureAudioOutputPermission: Boolean,
    val signatureHash: String,
    val installLocation: String
) {
    override fun toString(): String {
        return "SystemAppStatus(systemApp=$isSystemApp, systemUid=$isSystemUid, " +
                "capturePermission=$hasCaptureAudioOutputPermission, " +
                "location='$installLocation', signature='${signatureHash.take(16)}...')"
    }
}