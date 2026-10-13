# 对照基准件（F1=A+C 入仓副本）

本目录是《教材覆盖对照表》（FINAL-COVERAGE-TABLE.md）所用两份**对照基准件**的**原样字节副本**。
两份源文件位于仓库 `kb_tools/`，该目录被 `.gitignore:45`（`kb_tools/`）整目录忽略、未入版本控制；
本轮按 F1=A+C 要求复制入仓，供对照结论溯源与重跑比对。

## 文件与指纹

| 文件 | 来源 | 源文件修改时间（版本日期） | 大小 | sha256 | 内容 |
|---|---|---|---|---|---|
| `standard_chapter_names.json` | `kb_tools/standard_chapter_names.json` | 2026-09-06 01:27 +0800 | 3211 B | `3d091d53bdd0dad65d1c7d747118e0379bb5d1eb5d6c0f3c23cb799cb31d4d45` | 四科 21 册 / 90 章官方章名（物理 6 册 + 化学 5 + 生物 5 + 数学 5） |
| `chapter_structure_full.json` | `kb_tools/chapter_structure_full.json` | 2026-09-06 01:32 +0800 | 24015 B | `dca648fe3f3572cb6f016050303b15d255f3fbd3880968718fff6584efd51089` | 四科 22 键 / 91 章 / 471 节（含「物理其余」127 节兜底键） |

- 入仓日期：2026-10-14；复制方式：`shutil.copyfile` 字节原样，复制后逐文件 sha256 与字节比较通过。
- 结构统计：`standard_chapter_names.json` 共 21 册 / 90 章；
  `chapter_structure_full.json` 共 22 键 / 91 章 / 471 节。

## 用途与口径

- 两者均为**对照基准清单**（用于与官方教材目录逐字比对），**不是构建期数据源**：
  `standard_chapter_names.json` 全仓无构建代码引用（W-7 已核）；`chapter_structure_full.json`
  亦不参与知识包生成（知识包为 `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json`）。
- 目录口径：只读目录（章/节名），不读教材正文、不留存页图与原文。

## 已知待修（沿用 W-7 结论，本轮未改）

- `standard_chapter_names.json`：生物学 2 处与官方目录不符——选必1 第 1 章应为「人体的内环境与稳态」
  （现为「内环境与稳态」）、选必2 第 4 章现为旧版名「生态环境的保护」（官方为「人与环境」）。
- `chapter_structure_full.json`：数学节表与官方目录 5 处不符（选必三第七章缺 7.3、第八章仅 2 节且节名不同、
  必修二多出「10.4 统计与概率的应用」等），若将来按它做 1:1 对齐会继承这些错误
  （明细见同目录 `MATH.md`「附带核出」）。
- 两份文件**未被本轮修改**；如上游修订，请重新复制并更新本 README 的 sha256 与日期。

## 重跑

```bash
python build/f1-toc-refresh/copy_reference.py   # 重新复制并校验字节一致
```
