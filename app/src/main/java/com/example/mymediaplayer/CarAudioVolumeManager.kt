package com.example.mymediaplayer

import android.content.Context
import android.util.Log
import android.car.Car
import android.car.CarNotConnectedException
import android.car.media.CarAudioManager
import android.media.AudioManager

/**
 * 车载音频音量管理器
 * 用于车载环境下的音量控制，支持CarAudioManager.setGroupVolume()接口
 * 
 * 功能特性：
 * - 车载音频服务集成
 * - 音量组控制
 * - 降级策略支持
 * - 资源管理
 * 
 * @author MediaPlayer Team
 * @since 1.0
 */
class CarAudioVolumeManager(
    /** 应用上下文，用于获取车载服务 */
    private val context: Context
) {
    
    companion object {
        /** 日志标签 */
        private const val TAG = "zqqtest-CarAudioVolumeManager"
        
        /** 主音频区域ID */
        private const val PRIMARY_AUDIO_ZONE = 0
        
        /** 媒体音量组ID */
        private const val MEDIA_VOLUME_GROUP = 0
        
        /** 音量设置标志 - 不显示UI */
        private const val VOLUME_FLAG_NO_UI = 0
    }
    
    /** Car实例，用于连接车载服务 */
    private var car: Car? = null
    
    /** 车载音频管理器实例 */
    private var carAudioManager: CarAudioManager? = null
    
    /** 标准音频管理器实例（回退方案） */
    private var audioManager: AudioManager? = null
    
    /** 车载音频是否可用标志 */
    private var isCarAudioAvailable = false
    
    /** 音频控制模式标志 - true:车载模式, false:标准模式 */
    private var useCarAudio = false
    
    /**
     * 音量变化监听器列表
     * 用于通知UI组件音量变化
     */
    private val volumeChangeListeners = mutableListOf<VolumeChangeListener>()
    
    /**
     * 初始化音频管理器
     * 优先尝试车载音频服务，失败时回退到标准音频管理
     * 
     * @return Boolean 初始化是否成功
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "开始初始化音频管理器")
            Log.d(TAG, "当前应用包名: ${context.packageName}")
            Log.d(TAG, "当前进程UID: ${android.os.Process.myUid()}")
            
            // 首先尝试初始化车载音频服务
            if (initializeCarAudio()) {
                useCarAudio = true
                Log.i(TAG, "车载音频服务初始化成功")
                return true
            }
            
            // 车载服务不可用，尝试回退到标准音频管理
            Log.w(TAG, "车载服务不可用，尝试回退到标准音频管理")
            if (initializeFallbackAudio()) {
                useCarAudio = false
                Log.i(TAG, "标准音频管理器初始化成功，使用回退模式")
                return true
            }
            
            Log.e(TAG, "车载音频和标准音频初始化均失败")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化音频管理器失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 初始化车载音频服务
     * 使用完整的Car Audio API接口
     * @return Boolean 车载音频服务初始化是否成功
     */
    private fun initializeCarAudio(): Boolean {
        return try {
            // 检查车载服务是否可用
            if (!isCarServiceAvailable()) {
                Log.w(TAG, "车载服务不可用")
                return false
            }
            
            Log.d(TAG, "车载服务可用，开始创建Car实例")
            
            // 创建Car实例
            car = Car.createCar(context)
            if (car == null) {
                Log.e(TAG, "创建Car实例失败")
                return false
            }
            
            Log.d(TAG, "Car实例创建成功: ${car?.javaClass?.name}")
            
            // 连接Car服务
            try {
                car?.connect()
                Log.d(TAG, "Car服务连接成功")
            } catch (e: Exception) {
                Log.e(TAG, "Car服务连接失败: ${e.message}", e)
                return false
            }
            
            Log.d(TAG, "Car连接状态: ${car?.isConnected}")
            
            // 获取CarAudioManager
            Log.d(TAG, "开始获取CarAudioManager")
            try {
                val audioService = car?.getCarManager(Car.AUDIO_SERVICE)
                Log.d(TAG, "获取到音频服务: ${audioService?.javaClass?.name}")
                
                carAudioManager = audioService as? CarAudioManager
                Log.d(TAG, "CarAudioManager转换结果: ${carAudioManager != null}")
                
                // 验证CarAudioManager功能
                if (carAudioManager != null) {
                    validateCarAudioManager()
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取CarAudioManager异常: ${e.message}", e)
                carAudioManager = null
            }
            
            isCarAudioAvailable = carAudioManager != null
            
            if (isCarAudioAvailable) {
                Log.d(TAG, "车载音频管理器初始化成功")
                logAudioConfiguration()
            } else {
                Log.e(TAG, "获取CarAudioManager失败")
            }
            
            Log.d(TAG, "车载音频初始化完成，可用: $isCarAudioAvailable")
            isCarAudioAvailable
        } catch (e: SecurityException) {
            Log.e(TAG, "车载音频权限不足: ${e.message}", e)
            isCarAudioAvailable = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "车载音频管理器初始化失败: ${e.message}", e)
            isCarAudioAvailable = false
            false
        }
    }
    
    /**
     * 初始化标准音频管理器（回退方案）
     * 当车载音频服务不可用时使用标准AudioManager
     * @return Boolean 标准音频管理器初始化是否成功
     */
    private fun initializeFallbackAudio(): Boolean {
        return try {
            Log.d(TAG, "开始初始化标准音频管理器")
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                Log.i(TAG, "标准AudioManager初始化成功")
                logFallbackAudioConfiguration()
                true
            } else {
                Log.e(TAG, "标准AudioManager获取失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "标准音频管理器初始化失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 检查车载服务是否可用
     * 通过多种方式验证车载音频服务的可用性
     * @return Boolean 车载服务是否可用
     */
    private fun isCarServiceAvailable(): Boolean {
        return try {
            Log.d(TAG, "=== 开始详细检查车载服务可用性 ===")
            
            // 1. 检查Car类是否可用
            try {
                Class.forName("android.car.Car")
                Log.d(TAG, "✓ Car类可用")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "✗ Car类不可用: ${e.message}")
                return false
            }
            
            // 2. 检查车载相关权限
            val permissions = listOf(
                "android.car.permission.CAR_AUDIO",
                "android.car.permission.ACCESS_CAR_AUDIO",
                "android.car.permission.CAR_CONTROL_AUDIO_VOLUME",
                "android.car.permission.CAR_CONTROL_AUDIO_SETTINGS"
            )
            
            val permissionResults = mutableMapOf<String, Boolean>()
            permissions.forEach { permission ->
                val granted = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                permissionResults[permission] = granted
                Log.d(TAG, "权限检查 $permission: ${if (granted) "✓ 已授权" else "✗ 未授权"}")
            }
            
            // 3. 检查硬件特性
            val features = listOf(
                "android.hardware.type.automotive",
                "android.software.car.templates_host",
                "android.hardware.audio.output"
            )
            
            val featureResults = mutableMapOf<String, Boolean>()
            features.forEach { feature ->
                val available = try {
                    context.packageManager.hasSystemFeature(feature)
                } catch (e: Exception) {
                    Log.w(TAG, "检查特性 $feature 失败: ${e.message}")
                    false
                }
                featureResults[feature] = available
                Log.d(TAG, "硬件特性 $feature: ${if (available) "✓ 支持" else "✗ 不支持"}")
            }
            
            // 4. 检查系统服务
            val services = listOf(
                "car_audio",
                "car",
                "audio"
            )
            
            val serviceResults = mutableMapOf<String, Boolean>()
            services.forEach { service ->
                val available = try {
                    context.getSystemService(service) != null
                } catch (e: Exception) {
                    Log.w(TAG, "检查服务 $service 失败: ${e.message}")
                    false
                }
                serviceResults[service] = available
                Log.d(TAG, "系统服务 $service: ${if (available) "✓ 可用" else "✗ 不可用"}")
            }
            
            // 5. 检查系统属性
            val properties = listOf(
                "ro.build.characteristics",
                "ro.car.enabled",
                "ro.build.type",
                "ro.product.device",
                "ro.hardware",
                "ro.build.version.sdk"
            )
            
            val propertyResults = mutableMapOf<String, String?>()
            properties.forEach { property ->
                val value = try {
                    System.getProperty(property)
                } catch (e: Exception) {
                    Log.w(TAG, "获取属性 $property 失败: ${e.message}")
                    null
                }
                propertyResults[property] = value
                Log.d(TAG, "系统属性 $property: ${value ?: "未设置"}")
            }
            
            // 6. 环境分析
            val buildCharacteristics = propertyResults["ro.build.characteristics"]
            val carEnabled = propertyResults["ro.car.enabled"]
            val buildType = propertyResults["ro.build.type"]
            val device = propertyResults["ro.product.device"]
            
            val isAutomotiveByCharacteristics = buildCharacteristics?.contains("automotive") == true
            val isAutomotiveByProperty = carEnabled == "true"
            val isAutomotiveByFeature = featureResults["android.hardware.type.automotive"] == true
            val isDevelopmentBuild = buildType in listOf("eng", "userdebug")
            val isEmulator = device?.contains("emulator") == true || device?.contains("goldfish") == true
            
            Log.d(TAG, "环境分析:")
            Log.d(TAG, "  - 车载特性标识: $isAutomotiveByCharacteristics")
            Log.d(TAG, "  - 车载属性启用: $isAutomotiveByProperty")
            Log.d(TAG, "  - 车载硬件特性: $isAutomotiveByFeature")
            Log.d(TAG, "  - 开发构建: $isDevelopmentBuild")
            Log.d(TAG, "  - 模拟器环境: $isEmulator")
            
            // 7. 综合判断
            val hasBasicCarPermission = permissionResults["android.car.permission.CAR_AUDIO"] == true ||
                                      permissionResults["android.car.permission.ACCESS_CAR_AUDIO"] == true
            
            val hasCarService = serviceResults["car_audio"] == true || serviceResults["car"] == true
            
            val isAutomotiveEnvironment = isAutomotiveByCharacteristics || isAutomotiveByProperty || isAutomotiveByFeature
            
            // 在开发环境或模拟器中，如果有基本权限和服务，也认为可用
            val isDevelopmentEnvironment = isDevelopmentBuild || isEmulator
            
            val result = hasCarService && (isAutomotiveEnvironment || (isDevelopmentEnvironment && hasBasicCarPermission))
            
            Log.d(TAG, "=== 车载服务可用性判断结果 ===")
            Log.d(TAG, "  - 车载服务存在: $hasCarService")
            Log.d(TAG, "  - 车载环境: $isAutomotiveEnvironment")
            Log.d(TAG, "  - 开发环境: $isDevelopmentEnvironment")
            Log.d(TAG, "  - 基本权限: $hasBasicCarPermission")
            Log.d(TAG, "  - 最终结果: ${if (result) "✓ 可用" else "✗ 不可用"}")
            
            if (!result) {
                Log.w(TAG, "车载服务不可用的可能原因:")
                if (!hasCarService) Log.w(TAG, "  - 缺少车载音频服务")
                if (!isAutomotiveEnvironment && !isDevelopmentEnvironment) Log.w(TAG, "  - 非车载环境且非开发环境")
                if (!hasBasicCarPermission && isDevelopmentEnvironment) Log.w(TAG, "  - 开发环境中缺少基本车载权限")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "检查车载服务可用性时发生异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 验证CarAudioManager功能
     * 检查关键API是否可用
     */
    private fun validateCarAudioManager() {
        try {
            val carAudioMgr = carAudioManager ?: return
            
            // 测试基本API调用
            val maxVolume = carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            val currentVolume = carAudioMgr.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            
            Log.d(TAG, "CarAudioManager验证成功 - 最大音量: $maxVolume, 当前音量: $currentVolume")
            
        } catch (e: Exception) {
            Log.w(TAG, "CarAudioManager验证失败: ${e.message}")
            carAudioManager = null
        }
    }
    
    /**
     * 记录车载音频配置信息
     * 用于调试和问题排查
     */
    private fun logAudioConfiguration() {
        try {
            val carAudioMgr = carAudioManager ?: return
            
            Log.d(TAG, "=== 车载音频配置信息 ===")
            
            // 获取音频区域信息
            try {
                val audioZones = carAudioMgr.audioZoneIds
                Log.d(TAG, "可用音频区域: ${audioZones.joinToString()}")
                
                // 获取音频区域详细信息
                Log.d(TAG, "音频区域数量: ${audioZones.size}")
                
                for (zoneId in audioZones) {
                    Log.d(TAG, "音频区域ID: $zoneId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取音频区域信息失败: ${e.message}")
            }
            
            // 获取音量信息
            val maxVolume = carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            val currentVolume = carAudioMgr.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            
            Log.d(TAG, "音频区域: $PRIMARY_AUDIO_ZONE")
            Log.d(TAG, "音量组: $MEDIA_VOLUME_GROUP")
            Log.d(TAG, "最大音量: $maxVolume")
            Log.d(TAG, "当前音量: $currentVolume")
            
            // 获取输出设备信息
            try {
                val outputDevice = carAudioMgr.getOutputDeviceForUsage(
                    AudioManager.STREAM_MUSIC, 
                    PRIMARY_AUDIO_ZONE
                )
                Log.d(TAG, "输出设备: $outputDevice")
            } catch (e: Exception) {
                Log.w(TAG, "获取输出设备信息失败: ${e.message}")
            }
            
            Log.d(TAG, "=== 配置信息记录完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "记录音频配置失败: ${e.message}", e)
        }
    }
    
    /**
     * 记录标准音频配置信息（回退方案）
     * 用于调试和问题排查
     */
    private fun logFallbackAudioConfiguration() {
        try {
            val audioMgr = audioManager ?: return
            
            // 获取媒体音量信息
            val maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            Log.d(TAG, "标准音频配置 - 流类型: STREAM_MUSIC")
            Log.d(TAG, "音量范围: 0 - $maxVolume, 当前: $currentVolume")
            Log.d(TAG, "音频模式: ${audioMgr.mode}")
            Log.d(TAG, "是否静音: ${audioMgr.isStreamMute(AudioManager.STREAM_MUSIC)}")
            
        } catch (e: Exception) {
            Log.w(TAG, "记录标准音频配置失败: ${e.message}")
        }
    }
    
    /**
     * 添加音量变化监听器
     * 
     * @param listener VolumeChangeListener 音量变化监听器
     */
    fun addVolumeChangeListener(listener: VolumeChangeListener) {
        if (!volumeChangeListeners.contains(listener)) {
            volumeChangeListeners.add(listener)
            Log.d(TAG, "添加音量变化监听器，当前监听器数量: ${volumeChangeListeners.size}")
        }
    }
    
    /**
     * 移除音量变化监听器
     * 
     * @param listener VolumeChangeListener 要移除的音量变化监听器
     */
    fun removeVolumeChangeListener(listener: VolumeChangeListener) {
        if (volumeChangeListeners.remove(listener)) {
            Log.d(TAG, "移除音量变化监听器，当前监听器数量: ${volumeChangeListeners.size}")
        }
    }
    
    /**
     * 清除所有音量变化监听器
     */
    fun clearVolumeChangeListeners() {
        volumeChangeListeners.clear()
        Log.d(TAG, "清除所有音量变化监听器")
    }
    
    /**
     * 通知音量变化监听器
     * 
     * @param volumePercent Int 音量百分比
     * @param maxVolume Int 最大音量
     */
    private fun notifyVolumeChanged(volumePercent: Int, maxVolume: Int) {
        Log.d(TAG, "通知音量变化监听器: volumePercent=$volumePercent, maxVolume=$maxVolume, 监听器数量=${volumeChangeListeners.size}")
        
        try {
            val currentVolume = if (useCarAudio) {
                carAudioManager?.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP) ?: 0
            } else {
                audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            }
            
            volumeChangeListeners.forEach { listener ->
                try {
                    listener.onVolumeChanged(volumePercent, maxVolume, currentVolume, useCarAudio)
                    Log.d(TAG, "成功通知监听器: ${listener.javaClass.simpleName}")
                } catch (e: Exception) {
                    Log.e(TAG, "通知监听器失败: ${listener.javaClass.simpleName}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "通知音量变化监听器时发生错误", e)
        }
    }
    
    /**
     * 设置音量组音量
     * 优先使用CarAudioManager，不可用时回退到标准AudioManager
     * 
     * @param volumePercent Int 音量百分比 (0-100)
     * @param progressCallback ((Int) -> Unit)? 进度条更新回调，可选（已弃用，建议使用VolumeChangeListener）
     * @return Boolean 设置是否成功
     */
    fun setGroupVolume(volumePercent: Int, progressCallback: ((Int) -> Unit)? = null): Boolean {
        // 验证音量范围
        val validVolumePercent = volumePercent.coerceIn(0, 100)
        if (validVolumePercent != volumePercent) {
            Log.w(TAG, "音量百分比超出范围，已调整: $volumePercent -> $validVolumePercent")
        }
        
        val result = if (useCarAudio && isCarAudioAvailable) {
            setCarAudioVolume(validVolumePercent)
        } else {
            setFallbackAudioVolume(validVolumePercent)
        }
        
        // 如果设置成功，通知监听器和回调
        if (result) {
            // 获取当前最大音量用于回调
            val maxVolume = getMaxVolume()
            
            // 通知音量变化监听器
            notifyVolumeChanged(validVolumePercent, maxVolume)
            
            // 兼容旧的回调方式
            if (progressCallback != null) {
                try {
                    progressCallback(validVolumePercent)
                } catch (e: Exception) {
                    Log.w(TAG, "进度条回调执行失败: ${e.message}")
                }
            }
        }
        
        return result
    }
    
    /**
     * 使用车载音频管理器设置音量
     * @param volumePercent Int 音量百分比 (0-100)
     * @return Boolean 设置是否成功
     */
    private fun setCarAudioVolume(volumePercent: Int): Boolean {
        return try {
            val carAudioMgr = carAudioManager ?: run {
                Log.w(TAG, "车载音频管理器不可用")
                return false
            }
            
            // 获取音量组的最大音量
            val maxVolume = carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            if (maxVolume <= 0) {
                Log.e(TAG, "无效的最大音量值: $maxVolume")
                return false
            }
            
            // 计算目标音量
            val targetVolume = (maxVolume * volumePercent / 100).coerceIn(0, maxVolume)
            
            Log.d(TAG, "设置车载音量: $volumePercent% -> $targetVolume/$maxVolume")
            
            // 设置音量
            carAudioMgr.setGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP, targetVolume, VOLUME_FLAG_NO_UI)
            
            // 验证设置结果
            val actualVolume = carAudioMgr.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            val actualPercent = if (maxVolume == 0) 0 else (actualVolume * 100 / maxVolume)
            
            Log.d(TAG, "车载音量设置完成: 目标=$volumePercent%, 实际=$actualPercent%")
            
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "车载音量设置权限不足: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "车载音量设置失败: ${e.message}")
            false
        }
    }
    
    /**
     * 使用标准音频管理器设置音量（回退方案）
     * @param volumePercent Int 音量百分比 (0-100)
     * @return Boolean 设置是否成功
     */
    private fun setFallbackAudioVolume(volumePercent: Int): Boolean {
        return try {
            val audioMgr = audioManager ?: run {
                Log.w(TAG, "标准音频管理器不可用")
                return false
            }
            
            // 获取媒体流的最大音量
            val maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume <= 0) {
                Log.e(TAG, "无效的最大音量值: $maxVolume")
                return false
            }
            
            // 计算目标音量
            val targetVolume = (maxVolume * volumePercent / 100).coerceIn(0, maxVolume)
            
            Log.d(TAG, "设置标准音量: $volumePercent% -> $targetVolume/$maxVolume")
            
            // 设置音量
            audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            
            // 验证设置结果
            val actualVolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
            val actualPercent = if (maxVolume == 0) 0 else (actualVolume * 100 / maxVolume)
            
            Log.d(TAG, "标准音量设置完成: 目标=$volumePercent%, 实际=$actualPercent%")
            
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "标准音量设置权限不足: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "标准音量设置失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取当前音量百分比
     * 优先从CarAudioManager获取，不可用时回退到标准AudioManager
     * 
     * @return Int 当前音量百分比 (0-100)，失败时返回-1
     */
    fun getCurrentVolumePercent(): Int {
        return if (useCarAudio && isCarAudioAvailable) {
            getCarAudioVolumePercent()
        } else {
            getFallbackAudioVolumePercent()
        }
    }
    
    /**
     * 从车载音频管理器获取音量百分比
     * @return Int 当前音量百分比 (0-100)，失败时返回-1
     */
    private fun getCarAudioVolumePercent(): Int {
        return try {
            val carAudioMgr = carAudioManager ?: return -1
            
            val currentVolume = carAudioMgr.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            val maxVolume = carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            
            if (maxVolume <= 0) {
                Log.e(TAG, "无效的最大音量值: $maxVolume")
                return -1
            }
            
            val volumePercent = (currentVolume * 100 / maxVolume).coerceIn(0, 100)
            
            Log.d(TAG, "当前车载音量: $currentVolume/$maxVolume = $volumePercent%")
            
            volumePercent
        } catch (e: Exception) {
            Log.e(TAG, "获取车载音量失败: ${e.message}")
            -1
        }
    }
    
    /**
     * 从标准音频管理器获取音量百分比（回退方案）
     * @return Int 当前音量百分比 (0-100)，失败时返回-1
     */
    private fun getFallbackAudioVolumePercent(): Int {
        return try {
            val audioMgr = audioManager ?: return -1
            
            val currentVolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            if (maxVolume <= 0) {
                Log.e(TAG, "无效的最大音量值: $maxVolume")
                return -1
            }
            
            val volumePercent = (currentVolume * 100 / maxVolume).coerceIn(0, 100)
            
            Log.d(TAG, "当前标准音量: $currentVolume/$maxVolume = $volumePercent%")
            
            volumePercent
        } catch (e: Exception) {
            Log.e(TAG, "获取标准音量失败: ${e.message}")
            -1
        }
    }
    
    /**
     * 获取最大音量值
     * 优先从CarAudioManager获取，不可用时回退到标准AudioManager
     * 
     * @return Int 最大音量值，失败时返回-1
     */
    fun getMaxVolume(): Int {
        return if (useCarAudio && isCarAudioAvailable) {
            getCarAudioMaxVolume()
        } else {
            getFallbackAudioMaxVolume()
        }
    }
    
    /**
     * 从车载音频管理器获取最大音量值
     * @return Int 最大音量值，失败时返回-1
     */
    private fun getCarAudioMaxVolume(): Int {
        return try {
            val carAudioMgr = carAudioManager ?: return -1
            carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
        } catch (e: Exception) {
            Log.e(TAG, "获取车载最大音量失败: ${e.message}")
            -1
        }
    }
    
    /**
     * 从标准音频管理器获取最大音量值（回退方案）
     * @return Int 最大音量值，失败时返回-1
     */
    private fun getFallbackAudioMaxVolume(): Int {
        return try {
            val audioMgr = audioManager ?: return -1
            audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            Log.e(TAG, "获取标准最大音量失败: ${e.message}")
            -1
        }
    }
    
    /**
     * 检查音频控制是否可用（车载音频或标准音频）
     * 
     * @return Boolean 音频控制是否可用
     */
    fun isAvailable(): Boolean {
        // 如果使用车载音频模式，检查车载音频是否可用
        if (useCarAudio) {
            return isCarAudioAvailable && carAudioManager != null
        }
        // 如果使用标准音频模式，检查AudioManager是否可用
        return audioManager != null
    }
    
    /**
     * 获取音频区域信息
     * 用于调试和配置验证
     * 
     * @return String 音频区域信息
     */
    fun getAudioZoneInfo(): String {
        return if (useCarAudio) {
            getCarAudioZoneInfo()
        } else {
            getFallbackAudioZoneInfo()
        }
    }
    
    /**
     * 获取详细的音频区域配置信息
     * 包含所有可用音频区域和音量组的详细信息
     * 
     * @return Map<String, Any> 音频区域配置信息映射
     */
    fun getDetailedAudioZoneInfo(): Map<String, Any> {
        return if (useCarAudio && isCarAudioAvailable) {
            getDetailedCarAudioZoneInfo()
        } else {
            getDetailedFallbackAudioInfo()
        }
    }
    
    /**
     * 获取详细的车载音频区域配置信息
     * 
     * @return Map<String, Any> 车载音频区域配置信息
     */
    private fun getDetailedCarAudioZoneInfo(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            val carAudioMgr = carAudioManager ?: return mapOf("error" to "CarAudioManager不可用")
            
            // 获取音频区域信息
            try {
                val audioZones = carAudioMgr.audioZoneIds
                result["audioZoneIds"] = audioZones.toList()
                
                val zoneDetails = mutableListOf<Map<String, Any>>()
                
                for (zoneId in audioZones) {
                    val zoneInfo = mutableMapOf<String, Any>()
                    zoneInfo["id"] = zoneId
                    
                    // 获取该区域的音量组信息
                    try {
                        val volumeGroupCount = carAudioMgr.getVolumeGroupCount(zoneId)
                        zoneInfo["volumeGroupCount"] = volumeGroupCount
                        
                        val volumeGroups = mutableListOf<Map<String, Any>>()
                        for (groupId in 0 until volumeGroupCount) {
                            val groupInfo = mutableMapOf<String, Any>()
                            groupInfo["groupId"] = groupId
                            groupInfo["maxVolume"] = carAudioMgr.getGroupMaxVolume(zoneId, groupId)
                            groupInfo["currentVolume"] = carAudioMgr.getGroupVolume(zoneId, groupId)
                            volumeGroups.add(groupInfo)
                        }
                        zoneInfo["volumeGroups"] = volumeGroups
                    } catch (e: Exception) {
                        zoneInfo["volumeGroupError"] = e.message ?: "未知错误"
                    }
                    
                    zoneDetails.add(zoneInfo)
                }
                
                result["zones"] = zoneDetails
            } catch (e: Exception) {
                result["zoneError"] = e.message ?: "未知错误"
            }
            
            // 获取当前使用的音频区域和音量组信息
            result["currentZone"] = PRIMARY_AUDIO_ZONE
            result["currentVolumeGroup"] = MEDIA_VOLUME_GROUP
            
            try {
                result["currentMaxVolume"] = carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
                result["currentVolume"] = carAudioMgr.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            } catch (e: Exception) {
                result["currentVolumeError"] = e.message ?: "未知错误"
            }
            
        } catch (e: Exception) {
            result["error"] = e.message ?: "未知错误"
        }
        
        return result
    }
    
    /**
     * 获取详细的标准音频信息
     * 
     * @return Map<String, Any> 标准音频信息
     */
    private fun getDetailedFallbackAudioInfo(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            val audioMgr = audioManager ?: return mapOf("error" to "AudioManager不可用")
            
            result["audioMode"] = audioMgr.mode
            result["streamType"] = "STREAM_MUSIC"
            result["maxVolume"] = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            result["currentVolume"] = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
            result["isMuted"] = audioMgr.isStreamMute(AudioManager.STREAM_MUSIC)
            
            // 获取其他音频流信息
            val streamTypes = mapOf(
                "STREAM_VOICE_CALL" to AudioManager.STREAM_VOICE_CALL,
                "STREAM_SYSTEM" to AudioManager.STREAM_SYSTEM,
                "STREAM_RING" to AudioManager.STREAM_RING,
                "STREAM_MUSIC" to AudioManager.STREAM_MUSIC,
                "STREAM_ALARM" to AudioManager.STREAM_ALARM,
                "STREAM_NOTIFICATION" to AudioManager.STREAM_NOTIFICATION
            )
            
            val streamInfo = mutableMapOf<String, Map<String, Any>>()
            for ((name, streamType) in streamTypes) {
                try {
                    streamInfo[name] = mapOf(
                        "maxVolume" to audioMgr.getStreamMaxVolume(streamType),
                        "currentVolume" to audioMgr.getStreamVolume(streamType),
                        "isMuted" to audioMgr.isStreamMute(streamType)
                    )
                } catch (e: Exception) {
                    streamInfo[name] = mapOf("error" to (e.message ?: "未知错误"))
                }
            }
            result["allStreams"] = streamInfo
            
        } catch (e: Exception) {
            result["error"] = e.message ?: "未知错误"
        }
        
        return result
    }
    
    /**
     * 获取车载音频区域信息
     * 
     * @return String 车载音频区域信息
     */
    private fun getCarAudioZoneInfo(): String {
        if (!isCarAudioAvailable) {
            return "车载音频不可用"
        }
        
        return try {
            val carAudioMgr = carAudioManager ?: return "CarAudioManager为空"
            
            val maxVolume = carAudioMgr.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            val currentVolume = carAudioMgr.getGroupVolume(PRIMARY_AUDIO_ZONE, MEDIA_VOLUME_GROUP)
            val volumePercent = if (maxVolume == 0) 0 else (currentVolume * 100 / maxVolume)
            
            "车载音频区域: $PRIMARY_AUDIO_ZONE, 音量组: $MEDIA_VOLUME_GROUP, 音量: $currentVolume/$maxVolume ($volumePercent%)"
        } catch (e: Exception) {
            "获取车载音频区域信息失败: ${e.message}"
        }
    }
    
    /**
     * 获取标准音频区域信息
     * 
     * @return String 标准音频区域信息
     */
    private fun getFallbackAudioZoneInfo(): String {
        return try {
            val audioMgr = audioManager ?: return "AudioManager为空"
            
            val maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
            val volumePercent = if (maxVolume == 0) 0 else (currentVolume * 100 / maxVolume)
            val audioMode = audioMgr.mode
            val isMuted = audioMgr.isStreamMute(AudioManager.STREAM_MUSIC)
            
            "标准音频流: STREAM_MUSIC, 音量: $currentVolume/$maxVolume ($volumePercent%), 模式: $audioMode, 静音: $isMuted"
        } catch (e: Exception) {
            "获取标准音频区域信息失败: ${e.message}"
        }
    }
    
    /**
     * 释放音频资源
     * 断开Car连接并清理所有资源
     */
    fun release() {
        try {
            Log.d(TAG, "开始释放音频资源")
            
            // 清理音量变化监听器
            clearVolumeChangeListeners()
            
            // 断开Car连接
            try {
                car?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "断开Car连接时出现异常: ${e.message}")
            }
            car = null
            
            // 清理所有引用
            carAudioManager = null
            audioManager = null
            isCarAudioAvailable = false
            useCarAudio = false
            
            Log.d(TAG, "音频资源释放完成")
        } catch (e: Exception) {
            Log.e(TAG, "释放音频资源失败: ${e.message}")
        }
    }
    
    /**
     * 获取音频管理器状态信息
     * 用于调试和状态监控
     * 
     * @return String 状态信息
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("=== 音频管理器状态 ===")
            appendLine("控制模式: ${if (useCarAudio) "车载音频" else "标准音频"}")
            appendLine("总体可用状态: ${if (isAvailable()) "可用" else "不可用"}")
            
            appendLine("\n--- 车载音频状态 ---")
            appendLine("车载音频可用: ${if (isCarAudioAvailable) "是" else "否"}")
            appendLine("Car实例: ${if (car != null) "已连接" else "未连接"}")
            appendLine("CarAudioManager: ${if (carAudioManager != null) "已获取" else "未获取"}")
            
            appendLine("\n--- 标准音频状态 ---")
            appendLine("AudioManager: ${if (audioManager != null) "已获取" else "未获取"}")
            
            appendLine("\n--- 当前音频配置 ---")
            appendLine(getAudioZoneInfo())
            
            append("=========================")
        }
    }
}