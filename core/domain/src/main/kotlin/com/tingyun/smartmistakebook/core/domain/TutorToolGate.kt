package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorToolName

/**
 * 工具声明的**同一页口径**（`docs/tutor-surface-unification.md` §5.6）。
 *
 * 2026-09-21 裁定（ADR 0001 / D6/D7）之后，这里只剩下"声明什么"：智能体页所有模型调用
 * （Plan / Respond / 大厅）同一工具面（6 → 7 → 8：D-M M7 加入两枚咨询工具，4B A1 加入生图
 * 工具 `GENERATE_FIGURE`）。**"放行什么"不再有场景维度**——
 *
 * - 写不写由模型语义判定（系统提示词教会），代码零场景分叉（D6）：无题轮不再结构性拒写；
 * - MASTERY_READ 无场景分支（D7）：输出形态 / 聚合口径 / 24 解析上限 / 轮预算按旧裁定不变；
 * - 唯一剩下的逐次裁决是意图授权矩阵（core:model 的 `tutorToolAuthorization`：意图 × 置信度
 *   × 声明集）与 MASTERY_UPDATE 的代号白名单（本会话已披露集合，服务端结构性拒非法代号）——
 *   两者都取自模型自己这一轮的语义输出，不取自"这次调用来自哪个入口"。
 *
 * 放在 core:domain 而不是 core:data：声明集同时被数据层（工具环）与界面层（派发装配）
 * 读到，而 feature 模块看不到 core:data 的 internal。
 */

/**
 * 页面上的全量工具声明集（顺序稳定，便于提示词与测试逐项比对）。
 * 8 = spec §2 核心五 + D-M M7 咨询两枚 + 4B A1 生图一枚（`GENERATE_FIGURE`）。
 *
 * 声明全量不等于放行全量：生图工具的意图门在 `tutorToolAuthorization` 的
 * CURRENT_QUESTION_HELP 集合里（大厅/查库/闲聊轮没有可画的当前题）。
 */
val TUTOR_TOOL_DECLARATIONS: Set<TutorToolName> = linkedSetOf(
    TutorToolName.KNOWLEDGE_READ,
    TutorToolName.NOTEBOOK_READ,
    TutorToolName.MASTERY_READ,
    TutorToolName.MASTERY_UPDATE,
    TutorToolName.NOTEBOOK_WRITE,
    TutorToolName.ADVISORY_READ,
    TutorToolName.ADVISORY_WRITE,
    TutorToolName.GENERATE_FIGURE,
)

/**
 * 会**落库**的工具。它们的逐次准入不再是"本轮有没有绑定题"（2026-09-21 裁定 D6 废除
 * 那道场景门），而是：
 * - MASTERY_UPDATE：`terms[0]` 必须在本会话已披露的代号集合内（enum 白名单，服务端解析
 *   代号→id 后走统一本地门 MasteryWriteGate）；
 * - ADVISORY_WRITE：三 kind 限枚举 + 作用域校验（NODE 的代号同样走已披露白名单，
 *   PROBLEM/SUBJECT 由 runner 从会话/科目上下文解析，不接受模型给的 id）；
 * - NOTEBOOK_WRITE：意图授权矩阵仍要求学生明确命令（explicitActionRequest）。
 */
val TUTOR_WRITE_TOOLS: Set<TutorToolName> = setOf(
    TutorToolName.MASTERY_UPDATE,
    TutorToolName.NOTEBOOK_WRITE,
    TutorToolName.ADVISORY_WRITE,
)
