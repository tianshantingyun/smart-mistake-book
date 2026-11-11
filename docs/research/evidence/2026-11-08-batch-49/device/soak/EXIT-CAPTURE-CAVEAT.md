# 2h soak 退出码证据缺口（批次 49 修复环，2026-11-08）

## 结论（先读）

- **2026-11-08 批次 49 的历史 `soak.cmd.log` 里的 `EXIT=0` 不是真实退出码。**
  旧版 `f-soak.sh` 的汇总 heredoc 无条件写入字面量 `EXIT=0`（修复前 `f-soak.sh:56`），
  与运行是否崩溃无关。批次 44 的同脚本日志在 `CRASHED=1`、2504s FATAL、未完成 2h 的
  情况下同样写着 `EXIT=0`（`docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log`），
  直接证明该 marker 不反映失败。
- 因此 F-055/F-057/F-063 的 `cmd_exit` **不能**作为“命令退出 0”的证明；历史日志保持原字节
  不改（改日志等于伪造现场），本文件登记缺口。
- 批次 49 的真实证据是 `soak-samples.csv`（117 轮、pid 28350、crash_lines 19→19、
  dropbox 2→2、monkey_rc 全 0）与 `soak-summary.txt` 的统计；完成性以 CSV 逐轮数据判。
- 修复后的 `f-soak.sh` 由运行结果计算 `OUTCOME`/`EXIT`：未达时长、crash_lines 增量、
  dropbox 增量、monkey 非零退出任一命中即写 `EXIT=1` 并以 1 退出；全部满足才写
  `EXIT=0` 并以 0 退出。自测见 `docs/research/evidence/2026-11-08-batch-49/repair/`。

## 对验收行的影响

| 行 | 处理 |
|---|---|
| F-055 | 证据列 `cmd:` 改为 `file:`（仅留存命令日志出处，不再声明退出码）；`soak_raw`/`device_text` 仍从 CSV 复算完成性 |
| F-057 | 回退为 FAIL（判据要求超限路径命中；本批 117 轮未命中，不能把“未命中”记成通过） |
| F-063 | 同上，`cmd:` 改 `file:`；回归结论由 CSV（同 pid、无重启）与批次44 crash log 支撑 |

## 复跑

```bash
bash docs/research/evidence/2026-11-08-batch-49/device/soak/f-soak.sh emulator-5590 7200 <outdir>
# 汇总最后两行为 OUTCOME=... 与 EXIT=<真实状态>
```
