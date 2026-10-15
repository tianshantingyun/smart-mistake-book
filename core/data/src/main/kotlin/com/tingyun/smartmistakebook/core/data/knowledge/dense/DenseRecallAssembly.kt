package com.tingyun.smartmistakebook.core.data.knowledge.dense

import android.content.Context
import android.util.Log
import com.tingyun.smartmistakebook.core.database.DenseRecallCandidate
import com.tingyun.smartmistakebook.core.database.DenseRecallReranker
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 端侧稠密腿的实现：查询文本 → 句向量 → 与资产里**同科**全部向量做整数点积 → 节点取 max
 * → 与词面分按 spec §2.4 融合 → 只重排词面召回集（成员不变）。
 *
 * 三个依赖都是"取用时才加载"的函数：任何一步失败（资产缺失/哈希不符/模型缺失/推理抛错）
 * 都收敛成 `null` = 不可用，调用方（`RoomKnowledgeBaseStore`）已有纯词面结果 ⇒ **绝不崩、
 * 绝不返回空**。加载顺序也是刻意的：**先模型后资产**——模型缺失（最可能的失败）时
 * 不必把 17MB 资产拉进内存。
 */
internal class OnDeviceDenseRecallReranker(
    private val tokenizer: () -> DenseTokenizer,
    private val asset: () -> DenseVectorAsset,
    private val encoder: () -> DenseQueryEncoder,
) : DenseRecallReranker {

    override suspend fun order(
        subject: String,
        queryText: String,
        candidates: List<DenseRecallCandidate>,
    ): List<DenseRecallCandidate>? = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext null
        try {
            val vector = encoder().encode(queryText)
            val vectors = asset()
            val subjectKey = subject.lowercase(Locale.ROOT)
            val denseScores = vectors.nodeScores(vector, subjectKey).mapValues { it.value.toDouble() }
            val lexicalScores = candidates.associate { it.knowledgeNodeId to it.lexicalScore.toDouble() }
            val orderedIds = DenseFusion.order(
                orderDomain = candidates.map(DenseRecallCandidate::knowledgeNodeId),
                denseScores = denseScores,
                lexicalScores = lexicalScores,
            )
            val byId = candidates.associateBy(DenseRecallCandidate::knowledgeNodeId)
            val reordered = orderedIds.map { byId.getValue(it) }
            Log.i(
                TAG,
                "dense rerank on subject=$subject candidates=${candidates.size} " +
                    "denseNodeScores=${denseScores.size}",
            )
            reordered
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Log.w(TAG, "dense leg unavailable for subject=$subject: $failure")
            null
        }
    }

    private companion object {
        const val TAG = "DenseRecall"
    }
}

/**
 * 端侧稠密腿的**装配点与总开关**（core:data 唯一一处生产装配；store 侧只认端口）。
 *
 * `ENABLED = false` 是一行回退：装配返回 `null` ⇒ 检索路径回到纯词面（Stage-1/2 行为）。
 */
object DenseRecallAssembly {
    /** 总开关。关掉 = 检索退回纯词面（不改任何其它代码）。 */
    const val ENABLED = true

    /**
     * 模型件路径（assets 内）：LiteRT `.tflite`（**随包分发**，体积与 sha256 见同目录 README
     * 与 `tools/dense_build/README.md` §7）。换件必须重跑真机对拍门（≥0.999）——缺件或哈希
     * 不符时这条腿是**静默回退**（返回纯词面）而不是报错，没有门就没人知道腿掉了。
     */
    const val MODEL_ASSET_PATH = "dense/bge-small-zh-v1.5-int8.tflite"

    /** 随包资产（classpath 资源，与 `.vec` 同目录）。 */
    const val VECTOR_ASSET_PATH = "knowledge/dense/bge-small-zh-int8.vec"
    const val VOCAB_ASSET_PATH = "knowledge/dense/bge-small-zh-v1.5-vocab.txt"

    /**
     * 资产身份（sha256），权威值在 `knowledge/dense/bge-small-zh-int8.vec.json` 旁车里
     * （`DenseAssetProvenanceTest` 钉住"这里的常量 == 旁车 == 实际文件"三处一致）。
     * 不匹配 ⇒ 判不可用：宁可没有稠密腿，也不用一份来路不明的向量表排序。
     *
     * 2026-10-16 随批次11（v15：成品包 v14 → v15——原子点 3,359 → 3,210；批次11 超配收口）+
     * 金标 v15 expectedSlug 迁移（5 条改指、0 退役）后的 **dense 重打 + 连锁重钉**：
     * 43,388 → **43,265 行**（3,210 原子节点 + 40,055 别名），sha `7a70c01f…` → 本值。
     * 量源 = 新 `.vec` 的实测 sha256（`sha256sum core/data/src/main/resources/knowledge/dense/
     * bge-small-zh-int8.vec` 独立复算 6b65271f…）与旁车 `…vec.json` 记录逐位一致；
     * 旁车 `packSha256`（d741c92d…）亦等于现行成品包实测 sha256（三方同源，`DenseAssetProvenanceTest` 复核）。
     * 上一代：批次10（v14）的 43,388 行（3,359 + 40,029）/ `7a70c01f…`；
     * 更旧：批次 8（v13）的 43,718 行（3,741 + 39,977）/ `a489a0a9…`；
     * 批次 7 的 41,157 行 / `a68d6ed5…`；批次 5 的 41,126 行 / `28a14ec1…`；
     * 再旧：批次 3 的 41,117 行 / `8dbc0d54…`。
     */
    const val VECTOR_ASSET_SHA256 = "6b65271f472013247a34484ca1d5ab352bd839cfc75407e7e9d4de6603f798c4"

    /** 生产装配：不可用（未启用）时返回 `null`。 */
    fun reranker(context: Context): DenseRecallReranker? {
        if (!ENABLED) return null
        val runtime = DenseRuntime(context.applicationContext)
        return OnDeviceDenseRecallReranker(
            tokenizer = runtime::tokenizer,
            asset = runtime::vectorAsset,
            encoder = runtime::encoder,
        )
    }

    /** 三件重物各自"首次用到才加载、之后复用"。 */
    private class DenseRuntime(private val context: Context) {
        private val tokenizerHolder = lazy { loadTokenizer() }
        private val assetHolder = lazy { loadVectorAsset() }
        private val encoderHolder = lazy { loadEncoder() }

        fun tokenizer(): DenseTokenizer = tokenizerHolder.value

        fun vectorAsset(): DenseVectorAsset = assetHolder.value

        fun encoder(): DenseQueryEncoder = encoderHolder.value

        private fun loadTokenizer(): DenseTokenizer =
            DenseTokenizer(DenseVocab.fromLines(readResourceText(VOCAB_ASSET_PATH).split('\n')))

        private fun loadVectorAsset(): DenseVectorAsset {
            val bytes = readResourceBytes(VECTOR_ASSET_PATH)
            return DenseVectorAsset.read(bytes, expectedSha256 = VECTOR_ASSET_SHA256)
        }

        /**
         * 生产装配点：线程数**显式**交给编码器（口径来源仍是
         * [LiteRtDenseQueryEncoder.DEFAULT_THREADS]，这里只是把它摆在装配点上让"端侧跑几线程"
         * 一眼可见、A/B 时只动这一处形参）。Stage-6 前这里不传 ⇒ 恒 2 线程。
         */
        private fun loadEncoder(): DenseQueryEncoder = LiteRtDenseQueryEncoder.openFromAssets(
            context = context,
            assetPath = MODEL_ASSET_PATH,
            tokenizer = tokenizer(),
            threads = LiteRtDenseQueryEncoder.DEFAULT_THREADS,
        )

        private fun readResourceBytes(path: String): ByteArray {
            val stream = DenseRecallAssembly::class.java.classLoader?.getResourceAsStream(path)
                ?: error("bundled dense asset is missing: $path")
            return stream.use { it.readBytes() }
        }

        private fun readResourceText(path: String): String =
            readResourceBytes(path).decodeToString()
    }
}
