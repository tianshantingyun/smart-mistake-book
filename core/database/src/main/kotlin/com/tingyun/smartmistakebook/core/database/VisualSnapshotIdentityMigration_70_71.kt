package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v70→71（批次 32 / S1）：把**渲染器版本与字体清单版本并入快照的唯一键**。
 *
 * **它消灭的具体失败**（S1）：v70 的唯一键只有 `(program_id, state_sha256,
 * draw_commands_sha256)`，同理派生 `snapshotId` 也只有这三段。painter 或字体实现升级后，
 * 同一 program + state + draw 的再提交按旧键**静默复用旧行**；恢复侧读到
 * `rendererVersion != CURRENT` 判 `RENDERER_VERSION_MISMATCH` → 只读，而**没有任何写路径能
 * 补出一行新版本快照** ⇒ 一次版本 bump 把历史图不可逆降级为只读。
 *
 * 三处必须同批同源（派生键 = 唯一键 = 查重键）：
 * 1. 唯一索引：本文件（DROP 旧三列键 + CREATE 新五列键）；
 * 2. `TutorVisualIdentity.deriveSnapshotId`：五参、全部长度前缀；
 * 3. `TutorVisualDao.findSnapshotByContent`：五列 WHERE。
 * 只改其中一两处时：派生新 id 会被旧键吞掉（S2），或查到旧版本行而被静默复用（S4）。
 *
 * **不需要 `ALTER TABLE`**：`renderer_version` / `font_manifest_version` 两列在 v70 已存在
 * （`TutorVisualEntities.kt`），本迁移只碰索引。
 *
 * **必须先 DROP 再 CREATE**：`CREATE UNIQUE INDEX IF NOT EXISTS` 遇到**同名**索引会静默
 * 保留旧定义（SQLite 官方：becomes a no-op），迁移"看似成功"而写入侧仍按三列去重。这里用
 * 新名 + 先 DROP 旧名两道保险。
 *
 * **新键是旧键的严格超集** ⇒ 对存量行建唯一索引不可能失败（同三列 + 新两列的行在旧键下已被
 * 去重，五列更不可能冲突）。这是"迁移不会因数据失败"的结构性保证；若将来改成非超集，必须
 * 先在测试里证明。
 *
 * **生成与回填（不在本文件/本轨）**：这里只改代码——**不生成 `71.json`、不写任何
 * `identityHash` 常量**。`71.json` 与 `identityHash` 只能由带 KSP 的构建产出
 * （`docs/research/2026-10-29-schema-generation-flow.md` §3：`./gradlew :core:database:kspDebugKotlin`
 * 会同时写 `StudyDatabase_Impl.kt` 与 `schemas/…/71.json`，两处必须同值）；随后由**回填轨**
 * 把生成值写进 `SmbkSelectionPlan.SCHEMA_VERSION/IDENTITY_HASH` 与相关测试字面量。
 * 在回填完成前，`KernelWave0SchemaContractTest`（导出最高版本 == 71）与本模块
 * `TutorVisualSchemaContractTest` 的 v71 用例**必然红**：这是"提了版本但没导出"的诚实信号，
 * 不得用估算/占位哈希或放宽断言把门变绿。
 *
 * **不建触发器 backstop（本批裁定，见下）**：R2 研究（`docs/research/2026-10-29-schema-generation-flow.md`
 * §2.4/C7）确认"只有 LIVE 且同 program 的快照可被 link/pin"这条不变量目前由 DAO 的
 * `INSERT … SELECT … WHERE status='LIVE' AND program_id=…` 与指针 CAS 的 `EXISTS` 在 SQL 层
 * 保证，触发器只能加一层针对"绕过 DAO 的直写"的纵深防御。本批不加，理由是三条具体的
 * 冲突/代价，不是"以后再说"：
 * 1. **会改掉既有异常契约**：`TutorVisualStructureGatesInstrumentedTest` 的 4 条负向断言钉的是
 *    `ImmutablePayloadConflictException`（`linkMessageVisual` / `pinRevisionVisual` 回读为空
 *    的分流）；`BEFORE INSERT … RAISE(ABORT)` 会在插入时直接抛 `SQLiteConstraintException`，
 *    必须连带改写这 4 条断言与 DAO 的异常映射，属于契约变更而非本次索引迁移；
 * 2. **会删掉一条有分辨力的用例**：淘汰 SQL 的 `NOT EXISTS` 需要一条"指向 STAGING 快照的
 *    link"来证明谓词生效（`TutorVisualStructureGatesInstrumentedTest.rawMessageLink` 的
 *    KDoc 明写"引用可能在快照降级后仍存在"）。INSERT 触发器让这种行无法被构造，那条断言
 *    只能删——本批纪律不允许为了新机制删既有断言；
 * 3. **fresh install 不会自动有触发器**：Room 不校验触发器（`TableInfo` 只比列/FK/索引），
 *    生成的 `onCreate` 是空的，只在迁移里建会得到"升级库有、全新安装没有"的不一致
 *    defensive 面（R2 §2.4/C7）。补齐需要 `RoomDatabase.Callback` 或首次使用前的
 *    `ensure…` 路径并配 M8 用例，是独立的一批。
 * ⇒ 登记为待裁定项；本批保持 DAO 结构门为唯一执行面。
 */
internal val VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71 =
    object : Migration(70, 71) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(DROP_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71)
            connection.execSQL(VISUAL_SNAPSHOT_IDENTITY_INDEX_DDL_70_71)
        }
    }

/**
 * v70 的唯一索引名（Room 默认名 = 三列全拼）。**必须按名 DROP**：同名
 * `CREATE … IF NOT EXISTS` 不会改列（SQLite no-op）。
 */
internal val DROP_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71: String =
    "DROP INDEX IF EXISTS `index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256`"

/**
 * v71 的内容 + 版本唯一键。列序与 `TutorVisualSnapshotEntity` 的 `@Index` 声明逐位一致
 * （Room 在真机升级时逐列比较索引键列/唯一性；顺序不同就是 `Migration didn't properly handle`）。
 */
internal val VISUAL_SNAPSHOT_IDENTITY_INDEX_DDL_70_71: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tutor_visual_snapshot_content_render_identity` " +
        "ON `tutor_visual_snapshot` " +
        "(`program_id`, `state_sha256`, `draw_commands_sha256`, `renderer_version`, " +
        "`font_manifest_version`)"
