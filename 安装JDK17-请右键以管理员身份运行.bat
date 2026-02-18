@echo off
chcp 65001 >nul
echo ========================================
echo    JDK 17 自动下载安装工具
echo ========================================
echo.
echo 请以管理员身份运行此脚本！
echo.
echo 安装步骤：
echo 1. 右键点击此文件
echo 2. 选择"以管理员身份运行"
echo 3. 等待下载和安装完成
echo.
echo ========================================
pause

powershell -ExecutionPolicy Bypass -File "%~dp0install-jdk17.ps1"
