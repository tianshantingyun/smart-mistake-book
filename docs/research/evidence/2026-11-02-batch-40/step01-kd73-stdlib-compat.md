# E-WS6-01 · KD-73 stdlib 兼容门复跑（批次 40）

| 字段 | 值 |
|---|---|
| evidence-id | E-WS6-01 |
| 对应台账 | `docs/known-defects.md` KD-73（open） |
| 对应计划 | `.zcode/plans/plan-2026-11-02-batch-40-5x.md` §1 WS6 item 35 |
| verdict | **静态半边 PASS；设备/R8 半边未执行 → KD-73 仍 open** |
| executed-at | 2026-11-02T07:12:32+0800 |
| head | `a3f367df`（`git rev-parse --short HEAD`） |
| worktree | 开工时 564 条（131 ` M` + 433 `??`）；本轮零产品代码改动 |
| 执行人 | WS6（构建治理） |
| 环境 | Windows / Git Bash；`python -I`；零 Gradle、零设备、零联网 |
| sanitized | 是（批次目录级 §4.4 扫描 0 命中，2026-11-02） |
| 原始命令 | 见 §1 |

## 1. 命令与输出

### 1.1 全解析图静态扫描

```text
$ python -I tools/ci/check_stdlib_compat.py
graph: 340 coordinates from 13 lockfiles (13 个路径，原文逐项列出，此处不展开)
NOTE: lockfiles are untracked working-tree artifacts; the graph follows them, not HEAD
      (runtime stdlib pin = 2.3.21, upstream requirement = 2.4.10)
artifacts: 329/340 coordinates scanned, 11 without cached artifact (not scanned)
unique kotlin/* refs: 23847 from 329 artifacts; buckets:
  {'non-stdlib': 2795, 'resolved': 1210, 'resolved-secondary': 19842}
unscannable class entries: 4 (lifecycle AARs without classes.jar; informational)
OK: 0 unresolved stdlib member references across 23847 unique kotlin/* refs
    (329 artifacts; 11 coordinates unscanned)
EXIT=0
```

> 摘录说明：中间 4 行 `WARN unscannable …` 与 11 行 `MISSING: …` 明细已略去；上面保留的每个数字与尾行判词均逐字来自本轮输出。完整可复跑命令见行首。

关键读数：**0 unresolved**、23,847 条唯一 `kotlin/*` 引用、329 个构件、11 个无缓存构件未扫描（6 BOM + `kotlin-stdlib-common` + 4 个 KMP 重定向节点）。metadata 腿仍显示 `io.github.dokar3:quickjs-kt-android:1.0.15` 是全图唯一声明 `kotlin-stdlib 2.4.10` 的坐标（与其 POM 事实一致）。

### 1.2 门脚本自测

```text
$ PYTHONPATH=tools python -I -m unittest discover -s tools/tests \
    -p "test_check_stdlib_compat.py" -t tools
..................
Ran 18 tests in 0.023s
OK
EXIT=0
```

## 2. 证据边界（必须与结论同读）

1. **解析图来源是盘上 13 个未跟踪锁**（脏树产物，非 HEAD 重解析）。脚本每次运行都会打印该 NOTE；本轮未跑 Gradle，图未刷新。锁与 Keeper/他线改动的关系见 `docs/research/2026-11-02-kd72-lock-window.md`。
2. **11 个坐标无缓存构件未扫描**；4 个 lifecycle AAR 无 `classes.jar` 计 WARN，不进判定。扫描覆盖不是全图字节级。
3. **设备/R8 冒烟半边未做**：本轮零设备、零 R8。KD-73 的 Reopen condition 要求"完整解析图扫描 unresolved=0 **且**设备/R8 冒烟无 stdlib 归因崩溃，或升级到 2.4.10 的构建/设备证据落盘"。本轮只交付前半边。
4. 批次 30 T7 自述的 10.3 万 class / 138,225 条 occurrence 口径与本轮 23,847 unique 引用口径**不可直接比较**；本轮不引用其数字作为证据，只确认其结论方向。

## 3. 回链

- 门脚本：`tools/ci/check_stdlib_compat.py`（git 跟踪）
- 自测：`tools/tests/test_check_stdlib_compat.py`（git 跟踪）
- 台账：`docs/known-defects.md` KD-73 的批次 40 Progress 块
- 解析图与锁窗口：`docs/research/2026-11-02-kd72-lock-window.md`
