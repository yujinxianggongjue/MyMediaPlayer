#!/bin/bash

# =============================================================================
# Android系统应用自动安装脚本
# 适用于MTK车载系统
# =============================================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置变量
PACKAGE_NAME="com.example.mymediaplayer"
APP_NAME="MyMediaPlayer"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
PERMISSIONS_FILE="${PACKAGE_NAME}.xml"

# 系统路径配置
SYSTEM_APP_DIR="/system_ext/priv-app/${APP_NAME}"
PERMISSIONS_DIRS=(
    "/system_ext/etc/permissions"
    "/system_ext/etc/privapp-permissions"
    "/system/etc/permissions"
    "/vendor/etc/permissions"
)

# 函数：打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 函数：检查ADB连接
check_adb_connection() {
    print_info "检查ADB连接..."
    if ! adb devices | grep -q "device$"; then
        print_error "没有检测到ADB设备连接"
        print_info "请确保："
        print_info "1. 设备已连接并开启USB调试"
        print_info "2. 已授权ADB调试"
        exit 1
    fi
    print_success "ADB设备连接正常"
}

# 函数：检查APK文件
check_apk_file() {
    print_info "检查APK文件..."
    if [ ! -f "$APK_PATH" ]; then
        print_error "APK文件不存在: $APK_PATH"
        print_info "请先编译release版本: ./gradlew assembleRelease"
        exit 1
    fi
    print_success "APK文件存在: $APK_PATH"
}

# 函数：创建权限配置文件
create_permissions_file() {
    print_info "创建权限配置文件..."
    
    cat > "$PERMISSIONS_FILE" << 'EOF'
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
        
        <!-- 系统权限 -->
        <permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
        <permission name="android.permission.WAKE_LOCK"/>
        <permission name="android.permission.ACCESS_NETWORK_STATE"/>
        
        <!-- 车载系统特定权限 -->
        <permission name="android.car.permission.CAR_AUDIO"/>
        <permission name="android.car.permission.CAR_CONTROL_AUDIO_VOLUME"/>
    </privapp-permissions>
</permissions>
EOF
    
    print_success "权限配置文件已创建: $PERMISSIONS_FILE"
}

# 函数：获取root权限并重新挂载
setup_adb_root() {
    print_info "获取ADB root权限..."
    adb root
    sleep 2
    
    print_info "重新挂载系统分区为可写..."
    adb remount
    sleep 1
    
    print_success "系统分区挂载完成"
}

# 函数：卸载旧版本应用
uninstall_old_app() {
    print_info "检查并卸载旧版本应用..."
    
    # 检查应用是否已安装
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        print_warning "发现已安装的应用，正在卸载..."
        adb shell pm uninstall "$PACKAGE_NAME" || true
        
        # 清理系统目录中的残留文件
        adb shell rm -rf "$SYSTEM_APP_DIR" || true
        
        for permissions_dir in "${PERMISSIONS_DIRS[@]}"; do
            adb shell rm -f "${permissions_dir}/${PERMISSIONS_FILE}" || true
        done
        
        print_success "旧版本应用已卸载"
    else
        print_info "未发现已安装的应用"
    fi
}

# 函数：安装系统应用
install_system_app() {
    print_info "安装系统应用..."
    
    # 创建应用目录
    print_info "创建系统应用目录: $SYSTEM_APP_DIR"
    adb shell mkdir -p "$SYSTEM_APP_DIR"
    
    # 推送APK文件
    print_info "推送APK文件..."
    adb push "$APK_PATH" "${SYSTEM_APP_DIR}/${APP_NAME}.apk"
    
    # 设置APK文件权限
    adb shell chmod 644 "${SYSTEM_APP_DIR}/${APP_NAME}.apk"
    adb shell chown root:root "${SYSTEM_APP_DIR}/${APP_NAME}.apk"
    
    # 设置SELinux上下文
    adb shell chcon u:object_r:system_file:s0 "${SYSTEM_APP_DIR}/${APP_NAME}.apk" || true
    
    print_success "APK文件安装完成"
}

# 函数：安装权限配置文件
install_permissions() {
    print_info "安装权限配置文件..."
    
    for permissions_dir in "${PERMISSIONS_DIRS[@]}"; do
        print_info "推送权限文件到: $permissions_dir"
        
        # 创建目录（如果不存在）
        adb shell mkdir -p "$permissions_dir" || true
        
        # 推送权限文件
        if adb push "$PERMISSIONS_FILE" "${permissions_dir}/${PERMISSIONS_FILE}"; then
            # 设置文件权限
            adb shell chmod 644 "${permissions_dir}/${PERMISSIONS_FILE}"
            adb shell chown root:root "${permissions_dir}/${PERMISSIONS_FILE}"
            
            # 设置SELinux上下文
            adb shell chcon u:object_r:system_file:s0 "${permissions_dir}/${PERMISSIONS_FILE}" || true
            
            print_success "权限文件已安装到: $permissions_dir"
        else
            print_warning "无法安装权限文件到: $permissions_dir"
        fi
    done
}

# 函数：验证安装
verify_installation() {
    print_info "验证安装结果..."
    
    # 检查APK文件
    if adb shell ls "${SYSTEM_APP_DIR}/${APP_NAME}.apk" > /dev/null 2>&1; then
        print_success "APK文件安装成功"
    else
        print_error "APK文件安装失败"
        return 1
    fi
    
    # 检查权限文件
    local permissions_found=false
    for permissions_dir in "${PERMISSIONS_DIRS[@]}"; do
        if adb shell ls "${permissions_dir}/${PERMISSIONS_FILE}" > /dev/null 2>&1; then
            print_success "权限文件存在于: $permissions_dir"
            permissions_found=true
        fi
    done
    
    if [ "$permissions_found" = false ]; then
        print_error "权限文件安装失败"
        return 1
    fi
    
    print_success "安装验证通过"
}

# 函数：重启设备
reboot_device() {
    print_info "重启设备以应用更改..."
    print_warning "设备将在5秒后重启，按Ctrl+C取消"
    
    for i in {5..1}; do
        echo -n "$i "
        sleep 1
    done
    echo
    
    adb reboot
    print_success "设备重启命令已发送"
    print_info "请等待设备重启完成后测试应用功能"
}

# 函数：清理临时文件
cleanup() {
    print_info "清理临时文件..."
    rm -f "$PERMISSIONS_FILE"
    print_success "清理完成"
}

# 函数：显示安装后说明
show_post_install_info() {
    echo
    print_success "=== 安装完成 ==="
    print_info "应用已安装为系统特权应用"
    print_info "安装位置: $SYSTEM_APP_DIR"
    print_info "权限配置: 已安装到多个系统目录"
    echo
    print_info "下一步操作："
    print_info "1. 等待设备重启完成"
    print_info "2. 启动应用"
    print_info "3. 点击'系统应用状态检查'按钮验证权限"
    print_info "4. 测试音频捕获功能"
    echo
    print_warning "如果仍有权限问题，请检查:"
    print_warning "1. SELinux策略是否允许音频捕获"
    print_warning "2. 车载系统是否有额外的权限限制"
    print_warning "3. 查看系统日志: adb logcat | grep -i permission"
}

# 主函数
main() {
    echo
    print_info "=== Android系统应用自动安装脚本 ==="
    print_info "应用: $APP_NAME"
    print_info "包名: $PACKAGE_NAME"
    echo
    
    # 执行安装步骤
    check_adb_connection
    check_apk_file
    create_permissions_file
    setup_adb_root
    uninstall_old_app
    install_system_app
    install_permissions
    verify_installation
    
    # 清理临时文件
    cleanup
    
    # 显示完成信息
    show_post_install_info
    
    # 询问是否重启
    echo
    read -p "是否立即重启设备？(y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        reboot_device
    else
        print_info "请手动重启设备以应用更改"
    fi
}

# 错误处理
trap 'print_error "脚本执行失败，正在清理..."; cleanup; exit 1' ERR

# 运行主函数
main "$@"