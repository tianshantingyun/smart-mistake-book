package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.PaperAnswerKeyChain
import com.tingyun.smartmistakebook.core.domain.PaperAnswerKeyChainStore
import com.tingyun.smartmistakebook.core.domain.PaperAnswerSheet
import com.tingyun.smartmistakebook.core.domain.PaperAnswerSheetRecordReceipt
import com.tingyun.smartmistakebook.core.domain.PaperAnswerSheetStore
import java.util.concurrent.ConcurrentHashMap

/**
 * 键链/答卷内存仓储（T5/5B-G8 零 schema 先行的契约参照实现）。
 *
 * **它消灭的失败**：域层两个存储接口（[PaperAnswerKeyChainStore] / [PaperAnswerSheetStore]）
 * 零实现——回收契约不可执行、测试无处落、导出侧真实接线（他线热区 `core/export` 目录，登记
 * 遗留）没有可对齐的参照行为。内存实现把契约钉住：键链按 (exportId, 指纹) 读写、同
 * sheetKey 重传只记一条且首条权威。
 *
 * 真实持久化（键链随导出记录落库、答卷/证据幂等落 review 通道）不在本类：待导出侧接线
 * 批次以 Room 表或导出记录扩展落地时，本类转为该批的测试替身或删除，不改契约。
 */
class InMemoryPaperAnswerKeyChainStore : PaperAnswerKeyChainStore {

    private val chains = ConcurrentHashMap<Pair<String, String>, PaperAnswerKeyChain>()

    override suspend fun saveChain(chain: PaperAnswerKeyChain) {
        chains[chain.exportId to chain.paperFingerprint] = chain
    }

    override suspend fun readChain(
        exportId: String,
        paperFingerprint: String,
    ): PaperAnswerKeyChain? = chains[exportId to paperFingerprint]
}

class InMemoryPaperAnswerSheetStore : PaperAnswerSheetStore {

    private val sheets = ConcurrentHashMap<String, PaperAnswerSheet>()

    override suspend fun recordAnswerSheet(
        sheet: PaperAnswerSheet,
    ): PaperAnswerSheetRecordReceipt {
        val sheetKey = sheet.identity.sheetKey
        val existing = sheets.putIfAbsent(sheetKey, sheet)
        return PaperAnswerSheetRecordReceipt(
            sheetKey = sheetKey,
            created = existing == null,
        )
    }

    override suspend fun readAnswerSheet(sheetKey: String): PaperAnswerSheet? = sheets[sheetKey]
}
