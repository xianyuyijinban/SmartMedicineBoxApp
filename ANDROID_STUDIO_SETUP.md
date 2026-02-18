# Android Studio 配置和运行指南

## 📋 环境要求

- **Android Studio**: 2023.1.1 (Hedgehog) 或更新版本
- **JDK**: 17 或更高
- **操作系统**: Windows 10/11, macOS, 或 Linux
- **内存**: 建议 8GB+ (16GB更佳)
- **磁盘空间**: 至少 10GB 可用空间

---

## 🚀 快速开始

### 步骤1: 打开项目

1. 启动 **Android Studio**
2. 点击 **"Open"** (不要选 "Import Project")
3. 导航到项目目录：
   ```
   D:\STM32CubeMXProject\item\FOC controller\Project with XiaoJunWei\SmartMedicineBoxApp
   ```
4. 点击 **"OK"**

### 步骤2: 等待Gradle同步

首次打开项目时，Android Studio会自动进行 **Gradle同步**：
- 下载Gradle和依赖库
- 编译项目配置
- 生成构建文件

**⏰ 预计时间**: 5-15分钟（取决于网络速度）

**同步成功标志**: 底部状态栏显示 **"Sync finished"** 且无红色错误

### 步骤3: 配置设备

**方式A - 真机调试 (推荐):**

1. 用USB线连接Android手机
2. 手机开启开发者模式：
   - 设置 → 关于手机 → 连续点击"版本号"7次
   - 返回 → 系统 → 开发者选项 → 开启"USB调试"
3. 在Android Studio工具栏选择你的设备

**方式B - 模拟器:**

1. 点击工具栏设备下拉框
2. 选择 **"Device Manager"**
3. 点击 **"+"** → **"Create Virtual Device"**
4. 选择 **Pixel 6** → **Next**
5. 下载 **Android 13 (API 33)** → **Next** → **Finish**

### 步骤4: 运行APP

1. 点击绿色 **"Run"** 按钮 (▶️) 或按 `Shift + F10`
2. 等待编译完成（首次编译约2-5分钟）
3. APP会自动安装并启动

---

## ⚙️ 已完成的配置优化

我已为你配置了以下优化：

### 1. Android Studio性能优化
配置文件：`%USERPROFILE%\AppData\Roaming\Google\AndroidStudio2025.3.1\idea.properties`

- ✅ 内存分配优化（4GB堆内存）
- ✅ G1垃圾回收器
- ✅ Gradle守护进程
- ✅ 并行编译

### 2. Gradle全局配置
配置文件：`%USERPROFILE%\.gradle\gradle.properties`

- ✅ 构建缓存启用
- ✅ 按需配置
- ✅ 增量编译
- ✅ AndroidX支持

### 3. Maven国内镜像
配置文件：`%USERPROFILE%\.m2\settings.xml`

- ✅ 阿里云Maven镜像（加速依赖下载）
- ✅ Google仓库镜像
- ✅ Gradle插件镜像

### 4. 项目级配置
配置文件：`gradle.properties`

- ✅ Kotlin增量编译
- ✅ 非传递性R类
- ✅ 并行任务执行

---

## 🔧 常见问题解决

### Q1: Gradle同步失败，提示网络错误

**原因**: 无法访问Maven中央仓库

**解决**:
1. 检查网络连接
2. 我已配置阿里云镜像，应该可以加速
3. 如需代理，编辑 `%USERPROFILE%\.m2\settings.xml` 配置proxy

### Q2: 提示 "Minimum supported Gradle version is x.x.x"

**原因**: Gradle版本不匹配

**解决**:
```
File → Project Structure → Project
设置:
- Gradle Version: 8.0 或更高
- Android Gradle Plugin Version: 8.1.0
```

### Q3: 编译错误 "Could not find androidx.xxx"

**原因**: 依赖库下载失败

**解决**:
1. 点击 **"Sync Project with Gradle Files"** 按钮（大象图标）
2. 等待同步完成
3. 如仍失败，尝试 **File → Invalidate Caches → Invalidate and Restart**

### Q4: 找不到设备

**原因**: USB调试未开启或驱动问题

**解决**:
1. 确保手机开启USB调试
2. 安装手机对应的USB驱动
3. 重启ADB服务：
   ```cmd
   adb kill-server
   adb start-server
   ```

### Q5: APP安装失败 "INSTALL_FAILED_UPDATE_INCOMPATIBLE"

**原因**: 已存在签名不同的同名APP

**解决**:
1. 在手机设置中卸载旧版APP
2. 或使用以下命令强制安装：
   ```cmd
   adb install -r app-debug.apk
   ```

### Q6: 编译很慢

**原因**: 首次编译需要下载大量依赖

**解决**:
- 我已配置优化，后续编译会更快
- 确保Gradle守护进程运行
- 启用Build Cache（已配置）

---

## 📱 APP配置说明

### 首次运行配置

APP启动后，需要配置MQTT连接：

1. 点击右上角 **设置图标** (⚙️)
2. 输入MQTT服务器地址：
   ```
   tcp://192.168.1.100:1883
   ```
   （根据你的实际MQTT服务器地址修改）
3. 输入设备ID：
   ```
   medicine_box_001
   ```
4. 点击 **"连接"** 按钮
5. 连接成功后返回主界面查看数据

---

## 🔨 构建配置说明

### 构建类型

| 类型 | 用途 | 输出位置 |
|------|------|----------|
| Debug | 开发和调试 | `app/build/outputs/apk/debug/` |
| Release | 发布 | `app/build/outputs/apk/release/` |

### 生成发布版APK

1. **Build** → **Generate Signed Bundle / APK**
2. 选择 **APK** → **Next**
3. 创建新密钥或选择现有密钥
4. 选择 **release** → **Finish**
5. APK位置：`app/release/app-release.apk`

---

## 📊 项目结构

```
SmartMedicineBoxApp/
├── app/
│   ├── src/main/java/com/smartmedicine/    # Kotlin源代码
│   │   ├── ui/                              # Jetpack Compose UI
│   │   ├── mqtt/                            # MQTT通信
│   │   ├── data/db/                         # Room数据库
│   │   └── notification/                    # 系统通知
│   ├── src/main/res/                        # 资源文件
│   └── build.gradle                         # App模块配置
├── build.gradle                             # 项目级配置
├── settings.gradle                          # 项目设置
└── gradle.properties                        # Gradle配置
```

---

## 🎯 快捷键

| 快捷键 | 功能 |
|--------|------|
| `Shift + F10` | 运行APP |
| `Shift + F9` | 调试运行 |
| `Ctrl + F9` | 构建项目 |
| `Ctrl + Shift + A` | 查找操作 |
| `Ctrl + N` | 查找类 |
| `Ctrl + Shift + N` | 查找文件 |
| `Ctrl + B` | 跳转到定义 |
| `Ctrl + /` | 注释/取消注释 |

---

## 🆘 需要帮助？

- **Android Studio官方文档**: https://developer.android.com/studio
- **Gradle配置指南**: https://docs.gradle.org/
- **Jetpack Compose教程**: https://developer.android.com/jetpack/compose

---

## ✅ 配置检查清单

打开项目后，确认以下配置：

- [ ] Gradle同步成功完成
- [ ] 无红色错误提示
- [ ] 设备/模拟器已连接
- [ ] 能成功编译和运行APP
- [ ] MQTT服务器地址已配置

祝开发顺利！🎉
