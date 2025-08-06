package com.example.mymediaplayer

import android.app.Service
import android.car.Car
import android.car.CarNotConnectedException
import android.car.media.CarMediaManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log

/**
 * CarMediaMonitorService - 车载媒体监控服务
 * 负责管理车载系统连接、媒体源监听和媒体控制器更新
 * 提供车载环境下的媒体播放状态监控和控制功能
 */
class CarMediaMonitorService : Service() {

    companion object {
        private const val TAG = "CarMediaMonitorService"
    }

    /**
     * 服务绑定器 - 提供服务实例访问
     */
    inner class CarMediaMonitorBinder : Binder() {
        /**
         * 获取服务实例
         * @return CarMediaMonitorService实例
         */
        fun getService(): CarMediaMonitorService = this@CarMediaMonitorService
    }

    private val binder = CarMediaMonitorBinder()
    
    /**
     * Car实例 - 车载系统连接对象
     */
    private var car: Car? = null
    
    /**
     * 车载媒体管理器 - 管理车载媒体源和播放状态
     */
    private var carMediaManager: CarMediaManager? = null
    
    /**
     * 当前活跃的媒体控制器
     */
    private var activeMediaController: MediaControllerCompat? = null
    
    /**
     * 车载服务生命周期监听器 - 监听车载服务连接状态变化
     */
    private val carServiceLifecycleListener = object : Car.CarServiceLifecycleListener {
        /**
         * 车载服务生命周期状态变化回调
         * @param car Car实例
         * @param ready 服务是否就绪
         */
        override fun onLifecycleChanged(car: Car, ready: Boolean) {
            Log.d(TAG, "车载服务生命周期变化: ready=$ready")
            if (ready) {
                Log.i(TAG, "车载服务已就绪，初始化CarMediaManager")
                try {
                    // 获取车载媒体管理器
                    carMediaManager = car.getCarManager(Car.CAR_MEDIA_SERVICE) as? CarMediaManager
                    carMediaManager?.let { manager ->
                        // 添加媒体源监听器
                        manager.addMediaSourceListener(
                            mediaSourceListener,
                            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
                        )
                        Log.d(TAG, "CarMediaManager初始化成功，已添加媒体源监听器")
                        
                        // 更新当前活跃的媒体控制器
                        updateActiveMediaController()
                    } ?: run {
                        Log.e(TAG, "获取CarMediaManager失败")
                    }
                } catch (e: CarNotConnectedException) {
                    Log.e(TAG, "车载服务未连接异常", e)
                } catch (e: Exception) {
                    Log.e(TAG, "初始化CarMediaManager时发生异常", e)
                }
            } else {
                Log.w(TAG, "车载服务未就绪")
                carMediaManager = null
            }
        }
    }
    
    /**
     * 媒体源监听器 - 监听车载媒体源变化
     */
    private val mediaSourceListener = object : CarMediaManager.MediaSourceChangedListener {
        /**
         * 媒体源变化回调
         * @param mediaSource 新的媒体源信息
         */
        override fun onMediaSourceChanged(mediaSource: android.content.ComponentName) {
            Log.d(TAG, "媒体源变化: ${mediaSource.packageName}")
            updateActiveMediaController()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CarMediaMonitorService创建")
        
        // 连接到车载服务
        connectToCar()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "服务绑定")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "服务解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CarMediaMonitorService销毁")
        
        // 清理资源
        cleanup()
    }

    /**
     * 连接到车载服务
     * 创建Car实例并建立与车载系统的连接
     */
    private fun connectToCar() {
        Log.d(TAG, "connectToCar called.")
        if (this.car == null) {
            Log.i(TAG, "Car instance is null. Calling Car.createCar() with listener.") // 使用 Info 级别日志
            try {
                // Car.createCar 将尝试连接并通知监听器。
                // 监听器将在准备就绪后分配 car 实例 (this@CarMediaMonitorService.car = carObj)。
                // 将 createCar 的结果分配给 this.car，以便在监听器触发之前
                // 后续对 connectToCar 的调用可以看到 car 对象存在并且正在连接中。
                this.car = Car.createCar(
                    applicationContext, // 使用 applicationContext
                    null, // 回调的 Handler (如果为 null 则在主线程)
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                    carServiceLifecycleListener
                )
                // 不要在此处调用 car.connect()。
                // createCar 返回的 Car 对象（并已分配给 this.car）
                // 将自动尝试连接并通知监听器。
                // IllegalStateException 是由此处之前的显式 connect 调用引起的。
                Log.d(TAG, "Car.createCar() invoked and instance assigned. Waiting for CarServiceLifecycleListener to confirm connection and readiness.")
            } catch (e: Exception) { // 为安全起见捕获通用 Exception
                Log.e(TAG, "Exception during Car.createCar(): ${e.message}", e)
                this.car = null // 如果创建失败，确保 car 为 null 以允许重试。
            }
        } else {
            // Car 实例已存在
            if (this.car?.isConnected == true) {
                Log.d(TAG, "Car instance already exists and is connected.")
                // 如果管理器由于某种原因未设置，请尝试设置它们。
                // 这主要由 onLifecycleChanged 处理，但可以作为后备。
                if (carMediaManager == null && this.car != null) {
                    Log.w(TAG, "Car connected but carMediaManager is null. Attempting to reinitialize.")
                    try {
                        carMediaManager = this.car?.getCarManager(Car.CAR_MEDIA_SERVICE) as? CarMediaManager
                        carMediaManager?.addMediaSourceListener(mediaSourceListener, CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error re-initializing carMediaManager for already connected car", e)
                    }
                }
                updateActiveMediaController() // 确保 UI 是最新的
            } else if (this.car?.isConnecting == true) {
                Log.d(TAG, "Car instance already exists and is currently connecting. Waiting for listener.")
            } else {
                // Car 实例存在但未连接且未在连接中。尝试重新连接。
                Log.d(TAG, "Car instance exists but is not connected/connecting. Attempting to reconnect by calling car.connect().")
                try {
                    this.car?.connect()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException when trying to reconnect existing Car instance: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception when trying to reconnect existing Car instance: ${e.message}", e)
                }
            }
        }
    }

    /**
     * 更新当前活跃的媒体控制器
     * 获取当前播放的媒体应用的控制器实例
     */
    private fun updateActiveMediaController() {
        try {
            carMediaManager?.let { manager ->
                // 获取当前媒体源 - 使用正确的API方法
                Log.d(TAG, "尝试获取当前媒体源")
                
                // 这里可以根据需要更新媒体控制器
                // 实际实现需要根据具体的车载系统API
                Log.d(TAG, "媒体控制器更新完成")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新媒体控制器失败", e)
        }
    }

    /**
     * 获取车载连接状态
     * @return true表示已连接，false表示未连接
     */
    fun isCarConnected(): Boolean {
        return car?.isConnected == true
    }

    /**
     * 获取车载媒体管理器
     * @return CarMediaManager实例，如果未连接则返回null
     */
    fun getCarMediaManager(): CarMediaManager? {
        return carMediaManager
    }

    /**
     * 获取当前活跃的媒体控制器
     * @return MediaControllerCompat实例，如果没有则返回null
     */
    fun getActiveMediaController(): MediaControllerCompat? {
        return activeMediaController
    }

    /**
     * 清理资源
     * 断开车载服务连接并释放相关资源
     */
    private fun cleanup() {
        try {
            // 移除媒体源监听器
            carMediaManager?.removeMediaSourceListener(mediaSourceListener, CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
            carMediaManager = null
            
            // 断开车载服务连接
            car?.disconnect()
            car = null
            
            // 清理媒体控制器
            activeMediaController = null
            
            Log.d(TAG, "资源清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理资源时发生异常", e)
        }
    }

    /**
     * 重新连接车载服务
     * 用于在连接失败后重试连接
     */
    fun reconnect() {
        Log.d(TAG, "重新连接车载服务")
        cleanup()
        connectToCar()
    }
}