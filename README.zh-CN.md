**语言:**
[English](README.md) | [简体中文](README.zh-CN.md)

# cpufreq2

**简介**

一款无需 Root 或 ADB 权限的 Android CPU 监控与基准测试工具。

通过分析 CPU 空闲时间与总运行时间的差值计算占用率。当 `/proc/stat` 不可用时，自动降级至 `time_in_state` 与空闲状态统计。实时数据可通过悬浮窗在屏幕任意位置显示。

此外还包含 CPU 压力测试、GPU/CPU 基准测试、微架构探针，以及支持核心绑定的 Dhrystone 2.1 整数性能基准。

## 功能

### CPU 监控
- 无需 Root 或 ADB，通过空闲时间与总时间的差值计算 CPU 占用率
- `/proc/stat` 不可用时自动降级至 `time_in_state` + 空闲状态统计
- 实时悬浮窗（柱状图 / 文本列表 / 集群视图，点击切换）
- 电池电量 / 电流悬浮窗
- 长按悬浮窗快速开始 / 停止后台录制
- 历史数据回放（折线图与频率分布热力图）

### 压力测试
- 多核 CPU 压力测试，覆盖 14 种运算：int8 / int16 / int32 / int64 / f16 / f32 / f64 加减乘
- 支持核心绑定、NEON 加速、O0 / O3 编译优化对比
- 显示 ops / Hz 效率

### 基准测试
- **GPU OpenCL**：全局带宽、SP / HP / DP 浮点计算、INT / INT24 / CHAR / SHORT 整数计算、传输带宽、内核延迟
- **CPU 原生（pthreads）**：与 GPU kernel 相同的计算模式，直接对比 CPU 与 GPU
- **CPU OpenCL**：在 CPU 设备上运行 OpenCL kernel
- **微架构探针**：ILP（指令级并行度）、DIV（除法延迟）、FMA（浮点吞吐）、MEMBW（读写带宽 1 KB–64 MB）、LAT（指针追踪延迟 4 KB–64 MB）、BRANCH（有序 vs 随机分支误预测惩罚）、AES（软件 vs 硬件加速）、MULTI（1..N 核扩展性）

### Dhrystone 2.1
- 经典整数性能基准（Reinhold Weicker, 1988）
- **O0 / O1 / O2 / O3** 四种编译优化级别，直观对比编译器优化效果
- **处理器核心分配** — 可绑定任意核心组合，各核心独立运行并独立验证
- 显示 **DMIPS**、VAX MIPS、Dhrystones/秒、每核 DMIPS
- 18 项数值正确性验证

## 已知问题

- 在 CPU 满载场景下，因空闲时间趋近于零，可能被误判为空载。

---

> **注意：** 最新版本尚未推送至仓库，但 APK 安装包已上传。
