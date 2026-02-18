# 自动下载和安装 JDK 17 脚本
# 以管理员身份运行 PowerShell 执行此脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   JDK 17 自动下载安装工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否以管理员身份运行
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")
if (-not $isAdmin) {
    Write-Host "⚠️ 警告: 建议以管理员身份运行此脚本" -ForegroundColor Yellow
    Write-Host ""
}

# JDK 17 下载链接 (Oracle OpenJDK)
$jdkUrl = "https://download.java.net/openjdk/jdk17/ri/openjdk-17+35_windows-x64_bin.zip"
$jdkZip = "$env:TEMP\openjdk-17.zip"
$installDir = "C:\Program Files\Java"
$jdkDir = "$installDir\jdk-17"

# 创建安装目录
if (!(Test-Path $installDir)) {
    New-Item -ItemType Directory -Path $installDir -Force | Out-Null
}

Write-Host "📥 正在下载 JDK 17..." -ForegroundColor Yellow
Write-Host "下载地址: $jdkUrl" -ForegroundColor Gray

try {
    # 下载 JDK
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing
    Write-Host "✅ 下载完成!" -ForegroundColor Green
} catch {
    Write-Host "❌ 下载失败，尝试备用链接..." -ForegroundColor Red
    # 备用链接: Adoptium (Eclipse Temurin)
    $jdkUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9.1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip"
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing
}

Write-Host ""
Write-Host "📦 正在解压 JDK 17..." -ForegroundColor Yellow

# 如果已存在，先删除
if (Test-Path $jdkDir) {
    Remove-Item -Path $jdkDir -Recurse -Force
}

# 解压
Expand-Archive -Path $jdkZip -DestinationPath $installDir -Force

# 重命名文件夹（如果下载的是 Adoptium）
$extractedDir = Get-ChildItem -Path $installDir -Directory | Where-Object { $_.Name -like "*jdk-17*" -or $_.Name -like "*temurin*" } | Select-Object -First 1
if ($extractedDir -and $extractedDir.Name -ne "jdk-17") {
    Rename-Item -Path $extractedDir.FullName -NewName "jdk-17" -Force
}

Write-Host "✅ 解压完成!" -ForegroundColor Green
Write-Host ""

# 设置环境变量
Write-Host "⚙️ 正在配置环境变量..." -ForegroundColor Yellow

# 设置 JAVA_HOME
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkDir, "Machine")

# 设置 PATH
$currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$newPath = "$jdkDir\bin;$currentPath"
[Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")

Write-Host "✅ 环境变量配置完成!" -ForegroundColor Green
Write-Host ""

# 清理临时文件
Remove-Item -Path $jdkZip -Force -ErrorAction SilentlyContinue

# 验证安装
Write-Host "🔍 验证 JDK 17 安装..." -ForegroundColor Yellow
$javaVersion = & "$jdkDir\bin\java.exe" -version 2>&1
Write-Host $javaVersion

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ JDK 17 安装完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "安装路径: $jdkDir" -ForegroundColor White
Write-Host "JAVA_HOME: $jdkDir" -ForegroundColor White
Write-Host ""
Write-Host "请重启 Android Studio 后重新同步项目" -ForegroundColor Yellow
Write-Host ""

Read-Host "按 Enter 退出"
