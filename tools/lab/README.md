# tools/lab — FHN 探针（数值依据的可复现出处）

五个纯 Python（仅标准库，Python 3.x）探针，复现「心跳即庇护」迁移计划
（[docs/archive/heartbeat-shelter-migration-plan-2026-11-16.md](../../docs/archive/heartbeat-shelter-migration-plan-2026-11-16.md)）与札记 11 §2 的实测数据。
它们模拟的是模组核心同一个四维系统：FHN 快慢变量（`a = α − k·s`，M = 1）
+ 钟声的 α 核脉冲（area 12 / τ 5），RK4 积分。

| 脚本 | 测量内容 | 支撑的数据表 |
|---|---|---|
| `fhn_probe.py` | Hopf 点、静息→振荡起点、自然心跳带（s 扫描）与 I 扫描的周期/峰值 | §2.1 自然心跳带；§2.4 溯源表 |
| `fhn_duty.py` | 健康节拍的占空比 `duty(v ≥ 1.8)` | §2.3（`dutyMax` 的出处） |
| `fhn_pacemaker.py` | 静息格上钟声能否起搏：8–90 s 节奏的牵入比与起搏周期 | §2.2 敲钟起搏表 |
| `fhn_pacemaker_stats.py` | 各敲钟节奏下的峰值、占空比、v 范围、末态（去极化阻滞判据） | §2.2 / §2.3；Step 3 复核 12 s/20 s 牵入行的 CV/duty |
| `fhn_slow.py` | 慢系统（s 恢复 / 自激）+ c0 漏失 + 救赎窗 | §2.4（τ_c0 / 扰动场）；札记 11 附录 C 三幕弧 |

运行：`python tools/lab/fhn_probe.py`（以此类推）。每个脚本约几秒到一分钟。

纪律：这些脚本是 `Heartbeat.scala` 参数（`nominalPeriod` / `periodMin` /
`periodMax` / `dutyMax`）的实测出处；改动方程参数（`MandateParam` /
α 核）后必须重跑并同步更新札记 11 §2 的实测表。
