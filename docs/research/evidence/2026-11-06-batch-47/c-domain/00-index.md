# C 域证据索引（批次 47 修复环，2026-11-06）

> 目标：批次 47 复核发现 16 条 C 域行级复核 `witness_source` 内嵌的
> 「fresh rerun 2026-11-06: 94 passed, 0 failed, EXIT=0」声明无归档工件支撑（不可复算）。
> 本目录把该复跑落到可复算工件上。批次 44 原始证据
> （`docs/research/evidence/2026-11-05-batch-44/c-domain/`）不变、仍被台账引用。

## 1. 复跑命令与 exit

```bash
node tools/visual_compiler/run_tests.mjs
```

- 实跑：2026-11-06 批次 47 修复环，仓库根目录，Node v26.4.0，exit=0。
- stdout verbatim（100 字节，sha256=d7c3eeefe046016418f1381da538064fe26ae1e7dee48da9e7f3beb74d0e65cb）：
  `[timing] upper-bound scene (256 nodes / 4096 commands): 2.0 ms on Node v26.4.0` /
  空行 / `94 passed, 0 failed`。

## 2. 文件

| 文件 | 内容 |
|---|---|
| `c-node-bench-b47.cmd.log` | 复跑记录（TAG/CMD/SOURCES/RESULT/OUT_SHA256/RAW_OUTPUT/EXIT=0）；16 条 C 域行 witness_source 指向本文件 |
