package com.tingyun.smartmistakebook.core.database

/**
 * 一次草稿提交的结局（4C·B5 题级去重可见化）：[commit] 是原来的回执，
 * [reusedExistingProblem] 是「这次提交有没有复用既有的题行」。
 *
 * 题级去重本来就发生在提交事务里（`ProblemDraftTransactionDao.resolveCommittedProblemIds`
 * 按 `problem.canonical_fingerprint` 或「同科 + 同题面」找既有题行），但此前回执里没有
 * 任何一个地方写着"这一次是复用"——[CommitProblemDraftResult.created] 只回答"这次调用做了
 * 写入、还是幂等重放"，于是录入界面只能把复用和新建都显示成「已入库」。
 *
 * [reusedExistingProblem] 的判据只有一处（DAO 里那次查询），口径是
 * **「这次提交没有写出新的 problem 行」**：
 *
 * - 命中既有题行 → `true`：绑定到旧行、旧修订、旧练习单元，没有新的题行；
 * - 同一笔提交的幂等重放（回执已存在，这次一行也没写）→ `true`：题行确实早就在库里，
 *   这次同样没有新建；
 * - 真的新建了一行题 → `false`。
 *
 * 它和 [CommitProblemDraftResult.created] 回答的是两个不同的问题，不能互相替代：
 * `created = true` 的提交既可能是新建，也可能是命中了既有题行（后者不回执复用就会误报成新建）。
 */
data class ProblemDraftCommitOutcome(
    val commit: CommitProblemDraftResult,
    val reusedExistingProblem: Boolean,
)
