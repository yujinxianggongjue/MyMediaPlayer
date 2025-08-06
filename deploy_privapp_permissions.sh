#!/bin/bash
# deploy_privapp_permissions.sh
# MTK 车载系统 privapp-permissions 自动部署脚本
# 用于解决 CAPTURE_AUDIO_OUTPUT 权限白名单问题

set -e  # 遇到错误立即退出

# 配置变量
APP_PACKAGE="com.example.mymediaplayer"
PERMISSION_FILE="privapp-permissions-${APP_PACKAGE}.xml"
TARGET_PATH="/system_ext/etc/permissions/${PERMISSION_FILE}"
TEMP_FILE="/tmp/${PERMISSION_FILE}"

# 颜色输出函数
print_info() {
    echo -e "\033[34m[INFO]\033[0m $1"
}

print_success() {
    echo -e "\033[32m[SUCCESS]\033[0m $1"
}

print_error() {
    echo -e "\033[31m[ERROR]\033[0m $1"
}

print_warning() {
    echo -e "\033[33m[WARNING]\033[0m $1"
}

# 检查 ADB 连接
check_adb_connection() {
    print_info "检查 ADB 设备连接..."
    if ! command -v adb &> /dev/null; then
        print_error "ADB 命令未找到，请确保 Android SDK 已正确安装"
        exit 1
    fi
    
    if ! adb devices | grep -q "device$"; then
        print_error "未检测到 ADB 设备连接"
        print_info "请确保："
        print_info "1. 设备已连接并开启 USB 调试"
        print_info "2. 已授权 ADB 调试"
        print_info "3. 运行 'adb devices' 确认设备状态"
        exit 1
    fi
    
    print_success "ADB 设备连接正常"
}

# 获取 root 权限
get_root_access() {
    print_info "获取 root 权限..."
    if ! adb root; then
        print_error "无法获取 root 权限"
        print_info "请确保设备已 root 或使用工程版本固件"
        exit 1
    fi
    
    sleep 2  # 等待 root 权限生效
    print_success "已获取 root 权限"
}

# 重新挂载系统分区
remount_system() {
    print_info "重新挂载系统分区为可写..."
    
    # 尝试使用 adb remount
    if adb remount; then
        print_success "系统分区重新挂载成功"
        return 0
    fi
    
    print_warning "adb remount 失败，尝试手动挂载..."
    
    # 手动重新挂载 system_ext 分区
    if adb shell "mount -o rw,remount /system_ext"; then
        print_success "手动挂载 /system_ext 成功"
        return 0
    fi
    
    print_error "无法重新挂载系统分区"
    print_info "可能的解决方案："
    print_info "1. 确保设备已解锁 bootloader"
    print_info "2. 使用工程版本固件"
    print_info "3. 通过 Magisk 等工具获取系统写入权限"
    exit 1
}

# 创建权限配置文件
create_permission_file() {
    print_info "创建权限配置文件..."
    
    cat > "${TEMP_FILE}" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<!-- 
    特权应用权限配置文件
    应用包名: com.example.mymediaplayer
    用途: 车载媒体播放器系统应用
    创建时间: $(date '+%Y-%m-%d %H:%M:%S')
-->
<permissions>
    <privapp-permissions package="com.example.mymediaplayer">
        <!-- 音频捕获权限 - 用于录制系统音频输出 -->
        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT"/>
        
        <!-- 基础音频权限 -->
        <permission name="android.permission.RECORD_AUDIO"/>
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS"/>
        
        <!-- 系统设置权限 -->
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
        <permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
        
        <!-- 存储权限 -->
        <permission name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <permission name="android.permission.READ_EXTERNAL_STORAGE"/>
        
        <!-- 媒体投影相关权限 -->
        <permission name="android.permission.MEDIA_CONTENT_CONTROL"/>
        <permission name="android.permission.BIND_MEDIA_ROUTE_SERVICE"/>
        
        <!-- 车载系统特定权限 -->
        <permission name="android.car.permission.CAR_AUDIO"/>
        <permission name="android.car.permission.CAR_CONTROL_AUDIO_VOLUME"/>
        <permission name="android.car.permission.CAR_MEDIA"/>
    </privapp-permissions>
</permissions>
EOF
    
    if [[ -f "${TEMP_FILE}" ]]; then
        print_success "权限配置文件创建成功: ${TEMP_FILE}"
    else
        print_error "权限配置文件创建失败"
        exit 1
    fi
}

# 验证 XML 格式
validate_xml() {
    print_info "验证 XML 文件格式..."
    
    if command -v xmllint &> /dev/null; then
        if xmllint --noout "${TEMP_FILE}" 2>/dev/null; then
            print_success "XML 格式验证通过"
        else
            print_error "XML 格式验证失败"
            exit 1
        fi
    else
        print_warning "xmllint 未安装，跳过 XML 格式验证"
    fi
}

# 部署权限配置文件
deploy_permission_file() {
    print_info "部署权限配置文件到设备..."
    
    # 确保目标目录存在
    adb shell "mkdir -p /system_ext/etc/permissions"
    
    # 推送文件到设备
    if adb push "${TEMP_FILE}" "${TARGET_PATH}"; then
        print_success "权限配置文件推送成功"
    else
        print_error "权限配置文件推送失败"
        exit 1
    fi
    
    # 设置正确的文件权限
    print_info "设置文件权限..."
    adb shell "chmod 644 ${TARGET_PATH}"
    adb shell "chown root:root ${TARGET_PATH}"
    
    print_success "文件权限设置完成"
}

# 验证部署结果
verify_deployment() {
    print_info "验证部署结果..."
    
    # 检查文件是否存在
    if adb shell "test -f ${TARGET_PATH}"; then
        print_success "权限配置文件部署成功"
        print_info "文件路径：${TARGET_PATH}"
        
        # 显示文件信息
        print_info "文件详细信息："
        adb shell "ls -la ${TARGET_PATH}"
        
        # 显示文件内容（前几行）
        print_info "文件内容预览："
        adb shell "head -10 ${TARGET_PATH}"
        
    else
        print_error "权限配置文件部署失败"
        exit 1
    fi
}

# 检查现有权限配置
check_existing_permissions() {
    print_info "检查现有的 privapp-permissions 配置..."
    
    # 查找所有 privapp-permissions 文件
    print_info "系统中的 privapp-permissions 文件："
    adb shell "find /system* -name '*privapp-permissions*' -type f 2>/dev/null" || true
    
    # 检查是否已存在相同包名的配置
    if adb shell "grep -r '${APP_PACKAGE}' /system*/etc/permissions/ 2>/dev/null"; then
        print_warning "发现现有的 ${APP_PACKAGE} 权限配置"
        print_info "新配置将覆盖或补充现有配置"
    fi
}

# 清理临时文件
cleanup() {
    if [[ -f "${TEMP_FILE}" ]]; then
        rm -f "${TEMP_FILE}"
        print_info "临时文件已清理"
    fi
}

# 显示后续步骤
show_next_steps() {
    print_success "privapp-permissions 配置部署完成！"
    echo
    print_info "后续步骤："
    print_info "1. 重启设备使配置生效："
    print_info "   adb reboot"
    echo
    print_info "2. 重启后验证应用权限："
    print_info "   adb shell dumpsys package ${APP_PACKAGE} | grep permission"
    echo
    print_info "3. 检查系统日志确认无错误："
    print_info "   adb logcat | grep -E '(Zygote|PermissionManager|${APP_PACKAGE})'"
    echo
    print_info "4. 如果仍有问题，请检查："
    print_info "   - 应用是否正确安装为系统应用"
    print_info "   - 应用是否使用正确的系统签名"
    print_info "   - 权限配置文件格式是否正确"
    echo
}

# 主函数
main() {
    echo "======================================"
    echo "MTK 车载系统 privapp-permissions 部署脚本"
    echo "用于解决 CAPTURE_AUDIO_OUTPUT 权限问题"
    echo "======================================"
    echo
    
    # 设置清理陷阱
    trap cleanup EXIT
    
    # 执行部署步骤
    check_adb_connection
    get_root_access
    remount_system
    check_existing_permissions
    create_permission_file
    validate_xml
    deploy_permission_file
    verify_deployment
    show_next_steps
    
    print_success "部署脚本执行完成！"
}

# 检查是否直接运行脚本
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi