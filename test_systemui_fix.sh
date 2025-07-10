#!/bin/bash

# SystemUI 崩溃修复验证脚本
# 用于测试 MediaProjection 兼容性修复是否成功

echo "=== SystemUI MediaProjection 修复验证脚本 ==="
echo

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查 ADB 连接
echo -e "${BLUE}1. 检查 ADB 连接...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 设备未连接或 ADB 不可用${NC}"
    echo "请确保设备已连接并启用 USB 调试"
    exit 1
fi
echo -e "${GREEN}✅ ADB 连接正常${NC}"
echo

# 检查应用是否安装
echo -e "${BLUE}2. 检查应用安装状态...${NC}"
if ! adb shell pm list packages | grep -q "com.example.mymediaplayer"; then
    echo -e "${RED}❌ 应用未安装${NC}"
    echo "请先运行 install_system_app.sh 安装应用"
    exit 1
fi
echo -e "${GREEN}✅ 应用已安装${NC}"
echo

# 检查应用是否为系统应用
echo -e "${BLUE}3. 检查系统应用状态...${NC}"
app_path=$(adb shell pm path com.example.mymediaplayer | cut -d: -f2 | tr -d '\r')
if [[ $app_path == *"/system"* ]] || [[ $app_path == *"/system_ext"* ]]; then
    echo -e "${GREEN}✅ 应用已安装为系统应用: $app_path${NC}"
else
    echo -e "${YELLOW}⚠️  应用未安装为系统应用: $app_path${NC}"
    echo "建议重新运行 install_system_app.sh"
fi
echo

# 检查权限配置
echo -e "${BLUE}4. 检查权限配置...${NC}"
permissions=$(adb shell dumpsys package com.example.mymediaplayer | grep "android.permission.CAPTURE_AUDIO_OUTPUT")
if [[ -n "$permissions" ]]; then
    echo -e "${GREEN}✅ CAPTURE_AUDIO_OUTPUT 权限已配置${NC}"
else
    echo -e "${RED}❌ CAPTURE_AUDIO_OUTPUT 权限未配置${NC}"
    echo "请检查权限配置文件是否正确安装"
fi
echo

# 检查 SystemUI 状态
echo -e "${BLUE}5. 检查 SystemUI 进程状态...${NC}"
systemui_pid=$(adb shell ps | grep com.android.systemui | awk '{print $2}' | head -1)
if [[ -n "$systemui_pid" ]]; then
    echo -e "${GREEN}✅ SystemUI 进程运行正常 (PID: $systemui_pid)${NC}"
else
    echo -e "${RED}❌ SystemUI 进程未找到${NC}"
fi
echo

# 启动应用
echo -e "${BLUE}6. 启动应用进行测试...${NC}"
adb shell am start -n com.example.mymediaplayer/.MainActivity
sleep 3
echo -e "${GREEN}✅ 应用已启动${NC}"
echo

# 监控日志
echo -e "${BLUE}7. 开始监控应用和 SystemUI 日志...${NC}"
echo -e "${YELLOW}请在设备上点击 'AUDIO PLAYBACK CAPTURE' 按钮${NC}"
echo -e "${YELLOW}监控将持续 30 秒，按 Ctrl+C 提前结束${NC}"
echo

# 创建日志文件
log_file="systemui_test_$(date +%Y%m%d_%H%M%S).log"

# 后台监控 SystemUI 崩溃
(
    timeout 30 adb logcat | grep -E "(FATAL EXCEPTION|AndroidRuntime|com.android.systemui|MyMediaPlayer)" > "$log_file" 2>&1
) &
monitor_pid=$!

# 等待监控完成
wait $monitor_pid

echo
echo -e "${BLUE}8. 分析测试结果...${NC}"

# 检查是否有 SystemUI 崩溃
if grep -q "com.android.systemui.*FATAL EXCEPTION" "$log_file"; then
    echo -e "${RED}❌ 检测到 SystemUI 崩溃${NC}"
    echo "崩溃详情："
    grep -A 10 "com.android.systemui.*FATAL EXCEPTION" "$log_file"
    echo
    echo -e "${YELLOW}建议操作：${NC}"
    echo "1. 检查 androidx.lifecycle 版本是否正确降级"
    echo "2. 验证 CompatMediaProjectionManager 是否正确集成"
    echo "3. 查看完整日志文件: $log_file"
else
    echo -e "${GREEN}✅ 未检测到 SystemUI 崩溃${NC}"
fi

# 检查应用是否正常运行
if grep -q "MyMediaPlayer.*音频捕获" "$log_file"; then
    echo -e "${GREEN}✅ 应用音频捕获功能正常${NC}"
else
    echo -e "${YELLOW}⚠️  未检测到音频捕获活动${NC}"
fi

# 检查是否使用了降级方案
if grep -q "直接音频捕获" "$log_file"; then
    echo -e "${YELLOW}ℹ️  应用使用了降级方案（直接音频捕获）${NC}"
fi

echo
echo -e "${BLUE}9. 测试总结${NC}"
echo "日志文件已保存: $log_file"
echo

# 提供下一步建议
if ! grep -q "FATAL EXCEPTION" "$log_file"; then
    echo -e "${GREEN}🎉 测试通过！SystemUI 崩溃问题已修复${NC}"
    echo
    echo -e "${BLUE}后续建议：${NC}"
    echo "1. 进行更多功能测试确保稳定性"
    echo "2. 在不同场景下测试音频捕获功能"
    echo "3. 监控长期运行稳定性"
else
    echo -e "${RED}❌ 测试失败，仍存在崩溃问题${NC}"
    echo
    echo -e "${BLUE}故障排除步骤：${NC}"
    echo "1. 重新编译应用: ./gradlew clean assembleDebug"
    echo "2. 重新安装应用: ./install_system_app.sh"
    echo "3. 重启设备后再次测试"
    echo "4. 查看详细解决方案: SYSTEMUI_CRASH_COMPLETE_SOLUTION.md"
fi

echo
echo "=== 测试完成 ==="