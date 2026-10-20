# 对照基准件（F1=A+C 入仓副本）

本目录是《教材覆盖对照表》（FINAL-COVERAGE-TABLE.md）所用两份**对照基准件**。入仓时为两份源文件的
**原样字节副本**；2026-10-20 已按 W-7 官方名修正 5 处章名（见「已知待修 / 本轮修订」），其余字节未改。
两份源文件位于仓库 `kb_tools/`，该目录被 `.gitignore:45`（`kb_tools/`）整目录忽略、未入版本控制；
入仓按 F1=A+C 要求复制，供对照结论溯源与重跑比对。

## 文件与指纹

| 文件 | 来源 | 源文件修改时间（版本日期） | 大小 | sha256 | 内容 |
|---|---|---|---|---|---|
| `standard_chapter_names.json` | `kb_tools/standard_chapter_names.json` | 2026-09-06 01:27 +0800 | 3211 B | `8491642f11b6b6c52a9d3b37b741ef4b50176987c40b624867d1d98636650774`（2026-10-20 修订后；入仓原样副本为 `3d091d53bdd0dad65d1c7d747118e0379bb5d1eb5d6c0f3c23cb799cb31d4d45`） | 四科 21 册 / 90 章官方章名（物理 6 册 + 化学 5 + 生物 5 + 数学 5） |
| `chapter_structure_full.json` | `kb_tools/chapter_structure_full.json` | 2026-09-06 01:32 +0800 | 24015 B | `c4ee7c292570ddc4dea1dc819e4a966071949a3c9e115ea523ee64b0ac29e097`（2026-10-20 修订后；入仓原样副本为 `dca648fe3f3572cb6f016050303b15d255f3fbd3880968718fff6584efd51089`） | 四科 22 键 / 91 章 / 471 节（含「物理其余」127 节兜底键） |

- 入仓日期：2026-10-14；复制方式：`shutil.copyfile` 字节原样，复制后逐文件 sha256 与字节比较通过。
- 结构统计：`standard_chapter_names.json` 共 21 册 / 90 章；
  `chapter_structure_full.json` 共 22 键 / 91 章 / 471 节。

## 用途与口径

- 两者均为**对照基准清单**（用于与官方教材目录逐字比对），**不是构建期数据源**：
  `standard_chapter_names.json` 全仓无构建代码引用（W-7 已核）；`chapter_structure_full.json`
  亦不参与知识包生成（知识包为 `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json`）。
- 目录口径：只读目录（章/节名），不读教材正文、不留存页图与原文。

## 已知待修 / 本轮修订

- **2026-10-20 已修（本线，按 W-7 官方名逐字）**：`standard_chapter_names.json` 与
  `chapter_structure_full.json` 的 5 处章名——
  - 生物学 2 处：选必1 第 1 章「内环境与稳态」→「人体的内环境与稳态」；
    选必2 第 4 章「生态环境的保护」→「人与环境」；
  - 物理 3 处：必修三第 4 章「电能能量守恒定律与可持续发展」→「电能 能量守恒定律」；
    选必三第 2 章「气体固体和液体」→「气体、固体和液体」；选必三第 5 章「原子核和核反应」→「原子核」。
  - 修订依据：`PHYSICS.md`（2019 人教版官方目录逐字）与 `BIOLOGY.md`（`第1章 人体的内环境与稳态`、`第4章 人与环境`）。
- `chapter_structure_full.json`：数学节表与官方目录 5 处不符（选必三第七章缺 7.3、第八章仅 2 节且节名不同、
  必修二多出「10.4 统计与概率的应用」等），若将来按它做 1:1 对齐会继承这些错误
  （明细见同目录 `MATH.md`「附带核出」）——**本章名修订未触碰节表**。
- 两份文件的 `kb_tools/` 源文件**本轮未改**（该目录被 `.gitignore` 忽略、属另线），
  因此副本 sha256 已与源文件不同；如上游修订，请重新复制并同步本 README 的 sha256 与日期。

## 重跑

```bash
python build/f1-toc-refresh/copy_reference.py   # 重新复制并校验字节一致
```
