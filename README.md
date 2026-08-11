<p align="center">
  <img src="doc/logo.svg" alt="HeartRate" width="120" height="120" />
</p>

<h1 align="center">HeartRate · 心率监测</h1>

<p align="center">
  Android 心率监测应用：通过 BLE 连接心率带，实时监测心率、训练区间与卡路里消耗。
</p>

## 功能特性

- **BLE 心率带连接**：通过低功耗蓝牙（BLE）连接心率带，实时接收心率数据
- **实时心率显示**：大号数字 + 心电图动画背景，心率变化一目了然
- **心率区间（Z1-Z5）**：按最大心率百分比划分五个训练区间，实时高亮当前区间，并统计各区间累计时长
- **训练记录**：开始 / 暂停 / 继续 / 结束训练，记录心率曲线与区间分布
- **训练回顾**：查看历史训练的消耗热量、平均 / 最高 / 最低心率、心率变化趋势图与区间分布图，支持截图分享
- **心率悬浮窗**：在其他应用上方显示实时心率，支持系统悬浮窗权限
- **个性化配置**：设置性别、出生年月、身高体重，自动计算最大心率或手动指定

## 技术栈

- **Kotlin** + **Jetpack Compose**（Material 3）
- **Hilt** 依赖注入
- **Room** 本地数据库
- **DataStore** 偏好存储
- **BLE** 蓝牙低功耗
- **Coroutines / Flow** 异步与响应式编程

## 构建

需要 JDK 21 与 Android SDK 36。

```bash
./gradlew assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 贡献

欢迎提交 Issue 与 Pull Request。

## 许可证

本项目仅供学习参考。
