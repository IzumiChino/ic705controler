# IC-705 Controller v3.5.3 发布说明

## 📱 版本信息
- **版本号**: v3.5.3
- **发布日期**: 2026年4月6日
- **最低支持**: Android 8.0 (API 27)
- **目标版本**: Android 14 (API 36)

---

## ✨ 新功能

### 1. 地面站管理增强
**新增地面站列表功能**
- 主界面长按 GPS 信息栏可快速切换地面站
- 支持查看所有已保存的地面站
- 支持删除不需要的地面站
- 当前默认地面站显示勾选标记

**两种保存模式**
- **设为默认地面站**: 替换当前默认位置，立即用于卫星跟踪
- **添加到地面站库**: 保存到库中备用，方便后续快速切换

### 2. 卫星跟踪界面优化
**屏幕常亮功能**
- 卫星跟踪界面自动保持屏幕常亮
- 退出界面后自动恢复正常熄屏设置
- 避免跟踪过程中屏幕自动熄灭

---

## 🔧 修复与改进

### 1. 修复地面站保存崩溃问题
- 修复了保存同名地面站时应用闪退的问题
- 新增名称重复检测，提示用户更换名称
- 优化数据库结构，移除不必要的唯一约束

### 2. 界面主题调整
- 移除深色模式适配，统一使用浅色主题
- 简化主题配置，提升应用稳定性

---

## 📋 更新日志

### 数据库变更
- 数据库版本升级至 v11
- 优化地面站表结构

### 文件变更
```
新增:
- app/src/main/java/com/bh6aap/ic705Cter/ui/components/StationListDialog.kt

修改:
- app/src/main/java/com/bh6aap/ic705Cter/ui/theme/Theme.kt
- app/src/main/java/com/bh6aap/ic705Cter/SatelliteTrackingActivity.kt
- app/src/main/java/com/bh6aap/ic705Cter/data/database/DatabaseHelper.kt
- app/src/main/java/com/bh6aap/ic705Cter/ui/components/StationSettingsDialog.kt
- app/src/main/java/com/bh6aap/ic705Cter/MainActivity.kt
```

---

## 🚀 使用指南

### 快速切换地面站
1. 在主界面长按 GPS 信息栏
2. 在弹出的列表中选择要使用的地面站
3. 点击删除按钮可移除不需要的地面站

### 保存新地面站
1. 点击 GPS 栏左侧的设置按钮
2. 填写地面站信息（名称、QTH定位符、经纬度等）
3. 选择保存方式：
   - 设为默认：立即用于卫星跟踪
   - 添加到库：保存备用，后续可切换
4. 点击保存

### 卫星跟踪
1. 进入卫星跟踪界面后屏幕将保持常亮
2. 支持 VFO 避让功能（检测到手动调频时自动暂停跟踪）
3. 支持自定义模式和默认模式切换

---

## 📥 下载

**APK 下载**: [点击下载](https://github.com/zhangyulu8023/ic705controler/releases/tag/v3.5.3)

**源码**: `git clone https://github.com/zhangyulu8023/ic705controler.git`

---

## 🙏 致谢

感谢所有测试和反馈的用户！

如有问题或建议，请在 [Issues](https://github.com/zhangyulu8023/ic705controler/issues) 中提出。

---

**Full Changelog**: https://github.com/zhangyulu8023/ic705controler/compare/v3.5.2...v3.5.3
