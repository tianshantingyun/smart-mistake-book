package com.tingyun.smartmistakebook.core.model

/**
 * 工具面文案的**单一来源**（ADR 0001 施工注记）：提示词里的用途描述（长版）与原生
 * function schema 的 description（短版）此前各抄一份、各自演化——MASTERY_UPDATE 的
 * 长版还写着 "terms=[知识点id]（须是当前题真实绑定的知识点）"，与"原始 id 永不进
 * prompt/工具参数"（D5）直接矛盾，审计断链 P2 之一就是它。
 *
 * 现在两处文案都从这里出：改一个工具的契约措辞，只改这一份。
 *
 * A-06（批次 35）：[figureMethods] 参数让 `GENERATE_FIGURE` 的描述按**真实能力集合**派生
 * （`method=JS` 时指名独立 program 字段、删去"本地未配置可用的图像模型时也不会生成"的
 * 旧句）。默认（生产）只含 IMAGE_MODEL，返回值与 A-06 之前**逐字节相同**；生产消费点
 * （`OpenAiModelProtocol.kt` / `OpenAiModelTaskAdapters.kt`）本批不传参 → 广告文本零改动，
 * 接通时由与 A-03 schema 相同的 figureMethods 集合喂两处（描述与 method 枚举同源，消灭
 * "schema 广告了 JS、描述还说只有图像模型"的漂移）。非 GENERATE_FIGURE 工具不读该参数。
 */
fun TutorToolName.purposeDescription(
    figureMethods: Set<FigureGenerationMethod> = setOf(FigureGenerationMethod.IMAGE_MODEL),
): String = when (this) {
    TutorToolName.APP_CONTEXT -> "读取应用实际上下文、科目清单、已选择题目和备份引用。parameters.subject 可从已返回的 subjects 中选择。"
    TutorToolName.SELECT_QUESTION -> "选择错题本实际返回的题目，parameters.entryId 必填，返回所选题目的完整上下文。"
    TutorToolName.READ_FIGURE -> "读取当前上下文已披露配图，parameters.assetId 必填。返回像素证据与 acceptance.status；status≠PASS（空白/内容过少/贴边裁切/对比度低）时必须如实说明问题，不得声称图片清晰；自己生成的图应先修正参数重新生成。"
    TutorToolName.RUN_JAVASCRIPT -> "执行通用 JavaScript 函数体 script，可读 input.state，调用 agent.callTool(name, parameters) 或 agent.callAction(name, parameters)。宿主同步挂起调用、执行后重放并返回实际结果，随后继续执行。最终 return JSON。stateJson 可选，只承载 JSON 初始状态。禁止递归 RUN_JAVASCRIPT。"
    TutorToolName.KNOWLEDGE_READ ->
        "读取这道题相关知识点讲解材料（返回**代号**+名称+边界（≤80字）+绑定材料《标题》与" +
            "正文节选（≤120字，公式/句界安全截断）；无材料节点显式标注「（该节点无材料）」；" +
            "代号本会话有效，可直接作为 MASTERY_UPDATE 的 terms）"
    TutorToolName.KNOWLEDGE_CATALOG_READ ->
        "检索已审知识目录（terms 必填），返回命中的精确身份 nodeId@taxonomyVersion、名称、" +
            "章节路径与目录摘要；改知识点关联（RECLASSIFY_PROBLEM）只能使用这里真实返回过的值。"
    TutorToolName.NOTEBOOK_READ ->
        "读取错题本条目，terms 可空表示全部。parameters 可含 subject、offset（非负）、limit（1..30）、sectionId、masteryId、sort、createdFrom、createdTo。subject 从实际返回的科目中选择，翻页继续读取实际条目。"
    TutorToolName.MASTERY_READ ->
        "读取学生对相关知识的掌握情况（限当前科目；只查掌握情况，不含错题条目本身——" +
            "要找题用 NOTEBOOK_READ）。terms 留空＝返回本科目全部有学习证据的" +
            "知识点，按最弱优先，每行含名称、粒度、保守掌握度、区间（下界~上界）、证据量、状态、" +
            "最近证据与最近独立错误的时间档位、绑定错题数；terms 填知识点关键词＝聚焦解析到的知识点，" +
            "并额外给出结构化历史聚合（独立答对次数及跨几个题目族/学习日、独立错误次数、讲题与测验证据的" +
            "接受与被拒条数）与记忆行（稳定度天数/上次作答时间；仅在知识点已产生作答证据时给出）。" +
            "结果超出字符预算会被截断并注明；确实需要一次拿更多时，把 " +
            "extendedResult 置 true（本地决定实际上限，且每轮只放一次）。"
    TutorToolName.NOTEBOOK_WRITE -> "保存当前已识别题目到错题本，自动执行并返回实际保存结果"
    TutorToolName.MASTERY_UPDATE ->
        "提交一条学习证据：terms=[**代号**]（本会话已披露知识点的代号 K1..Kn，只能来自知识点" +
            "代号表或 KNOWLEDGE_READ 的返回——绝不编造代号、绝不用原始 id）、" +
            "direction∈{POSITIVE,NEGATIVE}（学生这次是掌握还是卡住）、" +
            "understanding∈{STRUGGLING,UNCERTAIN,CONFIDENT,MASTERED}（你对学生理解程度的判断）、" +
            "confidence∈[0,1]（你判断的置信度，低于 0.7 本地拒写）。" +
            "写不写由你按语义判定：学生对已披露知识点给出理解性陈述或可观察行为时写；" +
            "纯寒暄不写。rationale 必须用引号逐字引用学生原话或可观察行为" +
            "（引文锚底线：POSITIVE≥1 条、MASTERED≥2 条，本地逐条比对，编造引文不算证据）。" +
            "判断必须基于学生在本对话中表现出的可观察行为，不得凭学生口头声称或你的整体印象；" +
            "学生说\"我懂了\"不算掌握证据，只能当作待验证的线索。"
    TutorToolName.ADVISORY_READ ->
        "读取该学生的教学咨询记录（本会话科目的典型误区/有效讲法/难度判断；只读文本记录，" +
            "不含错题条目与掌握数值）。作用域三选一：scope=NODE 时 terms[0] 填本会话已披露的" +
            "知识点代号（K1..Kn，绝不编造代号、绝不用原始 id）；scope=PROBLEM 读当前这道题的记录；" +
            "scope=SUBJECT 读本科目记录；省略 scope 时 terms 空＝本科目、terms 非空＝按知识点代号。" +
            "只返回最近若干条（本地有上限，超出预算会截断并注明）。"
    TutorToolName.ADVISORY_WRITE ->
        "写入一条教学咨询（该生典型误区/有效讲法/这道题的难度判断），供以后讲题参考。" +
            "只写持久共识：值得跨会话保留的典型误区或确实有效的讲法；一次性闲聊、" +
            "对学生状态的临时印象、重复已有记录都不写；同一目标同一 kind 会**更新**已有记录，" +
            "不会堆积。scope 必填且三选一：NODE（terms[0]＝本会话已披露知识点代号，须真实存在）、" +
            "PROBLEM（当前这道题）、SUBJECT（本科目）；kind∈{TEACHING_FOCUS,MISCONCEPTION," +
            "DIFFICULTY_TIER}，DIFFICULTY_TIER 的 payload 只能是 EASY/MEDIUM/HARD 且只能用" +
            "PROBLEM 作用域；payload 是一条简短共识（上限 " +
            "${TutorToolCall.MAX_ADVISORY_PAYLOAD_CHARS} 字符）。"
    TutorToolName.GENERATE_FIGURE -> generateFigureDescription(figureMethods)
    TutorToolName.READ_RESULT ->
        "分页回读被结果预算挡下的本地工具结果：parameters.resultId 必填，offset/limit 可选" +
            "（单次最多 ${TutorToolCall.MAX_RESULT_CHUNK_CHARS} 字符）。先看结果里的 resultId 与 totalChars，" +
            "再按 nextOffset 继续；不要编造 resultId。"
    TutorToolName.REVIEW_PLAN_READ ->
        "读取今天的真实复习计划、容量、每日复习设置和考试日历（不写库；没有计划时如实说明）。"
}

/**
 * `GENERATE_FIGURE` 长描述按能力集合派生（A-06）。默认（只 IMAGE_MODEL）返回与 A-06 之前
 * 逐字节相同的现文本；能力集合含 JS 时把"本地未配置可用的图像模型时也不会生成"替换为
 * 按方法表述：program 只进独立字段（工具环契约层对 JS 调用仍要求 kind/description，
 * 见 `TutorToolLoop` 的 GENERATE_FIGURE 校验）、未接通的方法不得输出其载荷。
 */
private fun generateFigureDescription(figureMethods: Set<FigureGenerationMethod>): String {
    val head =
        "按当前任务生成有用的配图，可依据学生描述绘制图形、数轴和步骤示意，保持已提供题面事实。" +
            "kind 二选一：REDRAW_PROBLEM＝把当前题的题面图去手写重绘成干净题面" +
            "（源图由本地自动取自当前题的规范资产，你不得也不能提供图片）；" +
            "GENERATE_PROCESS＝按 description 生成一张解析过程图。" +
            "description 用平实中文写清这张图要表达什么（上限 " +
            "${TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS} 字符），terms 必须留空。" +
            "仅当图能实质降低当前题当前小问的理解负担时才申请；" +
            "没有当前题（如大厅轮次）时不会生成；" +
            "生成结果只回图的 id 与状态：**生成成功时**，图会保存到本地并作为本轮" +
            "回复下方的一张可展开配图（学生点开可见），不要让学生去找别处的图，也不要声称自己" +
            "看到了图的内容。"
    val tail = if (FigureGenerationMethod.JS in figureMethods) {
        "生成过程图可依据当前任务描述；重绘已有题面图必须有已授权真实源图。本机未接通的方法不得输出其载荷。" +
            "method=JS 时在 program 字段给出完整结构化程序（不要把程序写进 description）；" +
            "返回不可用时不要反复申请。"
    } else {
        "生成过程图可依据当前任务描述；重绘已有题面图必须有已授权真实源图。本地未配置可用的图像模型时不会生成" +
            "（返回不可用，不要反复申请）。"
    }
    return head + tail
}

/** 原生 function schema 用的短描述（与 [purposeDescription] 同一来源，措辞收紧）。 */
fun TutorToolName.nativePurposeDescription(
    figureMethods: Set<FigureGenerationMethod> = setOf(FigureGenerationMethod.IMAGE_MODEL),
): String = when (this) {
    TutorToolName.APP_CONTEXT -> "读取应用实际上下文、科目清单、已选择题目和备份引用。parameters.subject 可从已返回的 subjects 中选择。"
    TutorToolName.SELECT_QUESTION -> "选择错题本实际返回的题目，parameters.entryId 必填，返回所选题目的完整上下文。"
    TutorToolName.READ_FIGURE -> "读取当前上下文已披露配图，parameters.assetId 必填。返回像素证据与 acceptance.status；status≠PASS（空白/内容过少/贴边裁切/对比度低）时必须如实说明问题，不得声称图片清晰；自己生成的图应先修正参数重新生成。"
    TutorToolName.RUN_JAVASCRIPT -> "执行通用 JavaScript 函数体 script，可读 input.state，调用 agent.callTool(name, parameters) 或 agent.callAction(name, parameters)。宿主同步挂起调用、执行后重放并返回实际结果，随后继续执行。最终 return JSON。stateJson 可选，只承载 JSON 初始状态。禁止递归 RUN_JAVASCRIPT。"
    TutorToolName.KNOWLEDGE_READ ->
        "读取当前题相关知识点讲解材料（返回代号+名称+边界+材料《标题》与正文节选≤120字；无材料显式标注）"
    TutorToolName.KNOWLEDGE_CATALOG_READ ->
        "检索已审知识目录（terms 必填），返回 nodeId@taxonomyVersion 等精确身份（供改知识点关联）"
    TutorToolName.NOTEBOOK_READ -> purposeDescription(figureMethods)
    TutorToolName.MASTERY_READ ->
        "读取学生对相关知识的掌握情况（terms 留空＝本科目全部清单，填关键词＝聚焦并附历史聚合；不含错题条目）"
    TutorToolName.MASTERY_UPDATE ->
        "提交一条学习证据（terms[0]＝本会话已披露知识点的代号；模型判 direction/understanding/confidence，" +
            "权重与门控本地定）"
    TutorToolName.NOTEBOOK_WRITE -> "保存当前已识别题目到错题本，自动执行并返回实际保存结果"
    TutorToolName.ADVISORY_READ ->
        "读取该学生的教学咨询记录（本会话科目的典型误区/有效讲法/难度判断；scope＝NODE 配代号、" +
            "PROBLEM 读当前题、SUBJECT 读本科目；只回最近若干条）"
    TutorToolName.ADVISORY_WRITE ->
        "写入一条教学咨询（只写跨会话的持久共识，不写一次性闲聊；scope/kinds 本地限枚举，" +
            "同一目标同一 kind 更新已有记录、不堆积）"
    TutorToolName.GENERATE_FIGURE -> generateFigureNativeDescription(figureMethods)
    TutorToolName.READ_RESULT ->
        "分页回读大工具结果：resultId 必填，offset/limit 可选（单次 ≤${TutorToolCall.MAX_RESULT_CHUNK_CHARS} 字符）"
    TutorToolName.REVIEW_PLAN_READ -> "读取今天的复习计划、容量、排程设置和考试日历"
}

/** `GENERATE_FIGURE` 短描述按能力集合派生（A-06）；默认与 A-06 之前逐字节相同。 */
private fun generateFigureNativeDescription(figureMethods: Set<FigureGenerationMethod>): String {
    val head =
        "按当前任务描述生成配图（kind=REDRAW_PROBLEM 重绘干净题面，源图本地自动取、不接受模型供图；" +
            "kind=GENERATE_PROCESS 按 description 文生图；terms 留空，" +
            "description≤${TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS} 字符；"
    val tail = "结果只回 id+状态，生成成功时作为本轮回复下方的可展开配图展示）"
    return if (FigureGenerationMethod.JS in figureMethods) {
        head + "method=JS 时在 program 字段给出完整结构化程序（不写进 description），" +
            "只使用宿主声明为可用的方法，未接通的方法不得输出其载荷；" + tail
    } else {
        head + tail
    }
}
