package com.tingyun.smartmistakebook.core.data.pdf

/**
 * PDF `/AIGC` 增量注入的失败码（R1 `docs/research/2026-11-02-pdf-incremental-aigc.md` §5.2）。
 *
 * 语义：**fail-closed**。任何不在本枚举里的输入形态都不得"猜着写"，调用方必须把该次导出
 * 判失败（不发布、不写摘要、不返回半成品）。错误码是诊断面，不是 UI 文案；导出层映射到既有
 * `GENERATED_PDF_INVALID`，不新增用户可见枚举。
 *
 * 码表只收 R1 §5.2 中 WS1 八项会触达的条目；`INFO_MISSING_ALLOWED` 不是错误（走新建分支），
 * 因此不在枚举里。未实现的分支（如对象流里的 Info）仍保留拒绝码，保证"拒绝而不是跳过"。
 */
enum class PdfAigcErrorCode {
    // A：结构不可读
    NOT_PDF_HEADER,
    HEADER_TRUNCATED,
    HEADER_VERSION_INVALID,
    STARTXREF_MISSING,
    STARTXREF_NOT_INTEGER,
    STARTXREF_OUT_OF_RANGE,
    XREF_KEYWORD_MISSING,
    XREF_TABLE_MALFORMED,
    XREF_FREE_HEAD_MISSING,
    TRAILER_MISSING,
    TRAILER_MALFORMED,
    TRAILER_ROOT_MISSING,
    TRAILER_ROOT_NOT_INDIRECT,
    SIZE_MISSING,
    SIZE_INVALID,
    ID_MALFORMED,
    XREF_STREAM_MALFORMED,
    XREF_STREAM_W_INVALID,
    XREF_STREAM_INDEX_INVALID,
    XREF_STREAM_DATA_TRUNCATED,
    XREF_STREAM_UNSUPPORTED_FILTER,

    // B：合法但本注入主动不支持的 PDF 形态
    INFO_IN_OBJECT_STREAM,
    ENCRYPTED,
    LINEARIZED,
    HYBRID_XREF,

    // C：前置条件不满足
    INFO_NOT_INDIRECT,
    INFO_OBJECT_MISSING,
    INFO_NOT_DICTIONARY,
    INFO_OBJECT_TRUNCATED,
    AIGC_VALUE_NOT_STRING,
    AIGC_SCHEMA_INVALID,

    // D：资源/写出限制
    OBJECT_NUMBER_LIMIT_EXCEEDED,
    DECODED_STREAM_TOO_LARGE,
    INPUT_TOO_LARGE,
    OUTPUT_TOO_LARGE,
}

/**
 * 注入失败的 typed error。调用方按 [code] 分支，不要解析 message。
 *
 * 不携带 PDF 内容；[diagnostic] 只放非敏感结构信息（阶段、偏移、对象号）。
 */
class PdfAigcInjectionException(
    val code: PdfAigcErrorCode,
    message: String,
    val diagnostic: String? = null,
    cause: Throwable? = null,
) : Exception("[${code.name}] $message", cause)
