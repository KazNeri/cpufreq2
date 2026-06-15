**语言:**
[English](README.md) | [简体中文](README.zh-CN.md)

# cpufreq2

**Overview**

An Android CPU monitoring and benchmarking tool. No root or ADB required.

Reads CPU usage by computing the delta between idle time and total running time. When `/proc/stat` is unavailable, it falls back to `time_in_state` and idle state statistics. Real-time data can be displayed as a floating overlay anywhere on screen.

Additionally includes Dhrystone 2.1 integer benchmark with core-pinning support.

## Features

### CPU Monitoring
- Reads CPU usage without root or ADB by analyzing idle vs. total time deltas
- Falls back to `time_in_state` + idle state stats when `/proc/stat` is inaccessible
- Real-time floating overlay window (bar chart / text list / cluster view; tap to cycle)
- Long-press overlay to start / stop background recording
- History playback with line charts and frequency distribution heatmaps

### Dhrystone 2.1
- Classic integer-performance benchmark (Reinhold Weicker, 1988)
- **O0 / O1 / O2 / O3** compiler optimization levels — directly compare optimization impact
- **Processor core allocation** — pin to any core combination; each core runs independently with separate correctness validation
- Displays **DMIPS**, VAX MIPS, Dhrystones/sec, per-core DMIPS
- 18 numeric validation checks

## Known Issues

- Under full CPU load, idle time approaches zero, which may cause the usage monitor to report the CPU as idle.

---

> **Note:** The latest version has not been pushed to the repository yet, but the APK has been uploaded.

