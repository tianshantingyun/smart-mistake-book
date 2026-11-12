# 工作树临时产物归档（2026-11-12）

> 目的：主工作树不再留未跟踪尾巴；临时/审计产物已移至仓外归档，路径与清单见下。

## 归档位置

`D:/smbk-workspace-archive-2026-11-12/`

- 来源：主工作树 `D:/smart mistake book` 的全部未跟踪条目（`git status` 的 369 条，实际 3,579 个文件）。
- 时间：2026-11-12。
- 方式：按原相对路径整体移动（同盘 rename），不删除任何内容。

## 归档内容分类

| 类别 | 例子 | 说明 |
|---|---|---|
| 审计/实验工作区 | `tmp/`（1.2 GB，61 个子目录：kd77-api23-smoke、kd78-classes、r8-parity-check、regression-2026-11-01 等） | 历史审计证据与一次性实验 |
| 审计截图 | `.agent_audit_crops*`、`crops/`（25 MB）、`output/`（9.5 MB） | 页面/框裁剪图 |
| 临时脚本/导出 | `tmp_judge_8/`、`_tmp_*`、`buildagent-*.txt`、`tmp_slice*.txt`、`%TEMP%/` 残留 | 一次性脚本与中间导出 |
| KB 临时件 | `knowledge-production/_tmp_*`、`tools/kb_coverage/*.tmp.txt` 等 | KB 线中间产物 |
| 其他 | `x/`、`out/`、`growth/`、`audit_tmp`、`dynamic_gui_architecture_plan.md`（未跟踪副本） | 空目录/散件 |

## 例外

- `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul`：Windows 保留名设备文件，
  无法移动/删除为普通文件；已从工作树移除该条目（其内容是构建重定向残留，无价值）。

## 结果

- 执行后 `git status --porcelain` = **0**（tracked 与 untracked 均干净）。
- 归档内容不参与构建；干净 clone 已在没有这些文件的情况下通过 release 构建。
- 需要某份历史证据时从上述归档路径按原相对路径取回。
