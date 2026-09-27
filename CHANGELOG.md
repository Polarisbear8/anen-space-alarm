# Changelog

本项目的主要变更记录。版本号遵循语义化版本。

## 1.0.2

相对 1.0.1 的主要变化：

- 自适应定位策略进一步优化：根据最近采样的 closing speed / ETA 动态收紧定位间隔，
  在到达提醒半径前保留足够的采样次数，降低高速接近时因低频采样跨过提醒圈、导致提醒延迟的风险。
- 跨越状态阈值仍可直接触发：CRITICAL 只是高频策略状态，不是触发前提。
  任意新鲜位置只要 `distance <= reminderRadius` 即直接触发（FAR → 半径内 → TRIGGERED 合法）。
- Native LocationManager 的 fresh-location 处理增强：记录 request 开始时间，
  只接受产生于 request 之后的 fix，超时或仅有旧 fix 时返回失败，不再把缓存 fix 当作 fresh。
- 后台 / 锁屏定位机制保持：Location FGS + GMS FusedLocationProvider + Native LocationManager fallback。
- 刷新按钮保持 fresh-only 语义：失败显示明确状态，不覆盖已有 fresh 时间戳，不使用 `lastKnownLocation()`。
- 新增定位服务 / 运行环境诊断（开发者模式）：Service 状态、Mode、请求间隔、最近回调、
  distance、closing speed、ETA、trigger 状态、availability。
- 提醒触发统一走 `ReminderTrigger`（原子抢占），Geofence 兜底与前台服务共用，防止重复触发。
- 显示名统一为 ANENG。
- 测试与可靠性验证增强：高速跨圈、锁屏回归、Native provider、refresh success / failure。

## 1.0.1

- 补齐剩余的双语文案：闹钟全屏页的范围提示、无名地点的兜底名称（未命名地点 / Unnamed place）
- 开发者模式「前台地图定位」改为显示实际设置的刷新档位（原为写死的过期值）
- 地图补充 OpenStreetMap 归属信息（此前 ⓘ 按钮内容为空）
- 「关于」页新增仓库链接，点击用系统浏览器打开

## 1.0.0

首个正式版。

- 高德分享直达：`ACTION_SEND` 接收，多级解析（URI / 裸坐标 / 短链 `p` 参数 / 重定向 / 静态 HTML / WebView 兜底），无需 API Key
- 三种提醒方式：仅通知、通知 + 震动、通知 + 震动 + 闹铃
- 一次性 / 循环两种触发方式（一次性提醒后自动删除）
- 地图主页：全部目标、触发范围圆、到目标直线与实时距离；启用橙色 / 停用灰色
- 目标信息逐页循环切换，按距离排序
- 创建前范围校验：已在目标范围内时拒绝创建（不写库、不注册围栏）
- 空间闹钟管理：随时编辑全部设置、启用/停用、删除
- 系统地理围栏触发（原子抢占，防重复提醒），失败自动回滚重试
- 手机重启后自动恢复生效中的提醒
- 中英双语（跟随系统 / 手动切换）
- 使用教程（首次启动显示，设置里可重温）
- 开发者模式（默认关闭）：App 系统状态、分享/解析/定位/围栏/闹钟/地图调试信息，一键复制
- 定位刷新档位：3 / 5 / 10 / 30 秒（默认 5 秒，仅前台地图）
