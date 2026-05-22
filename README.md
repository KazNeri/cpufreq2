**语言:**
[English](README.md) | [简体中文](README.zh-CN.md)

# Cpufreq2

**Program Introduction**  
An Android CPU monitoring tool that does not require Root or ADB permissions. By implementing certain workarounds, it achieves CPU usage monitoring and visual display even without read access to `/proc/stat` and without ADB privileges.

**Features**
1. CPU usage reading: Calculates CPU usage by analyzing the difference between CPU idle time and total runtime. When `/proc/stat` is unavailable, it automatically falls back to reading `time_in_state` and idle state statistics.
2. Real-time floating window: Supports displaying CPU usage and frequency in real time via a floating window that can be positioned anywhere on the screen.

**Known Issues**  
Under high CPU load scenarios, idle time approaches zero, which may cause the tool to incorrectly interpret the CPU as idle.能被误判为空载。