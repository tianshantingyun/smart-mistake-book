package com.tingyun.smartmistakebook.core.export

import androidx.core.content.FileProvider

/**
 * Word 交付的 FileProvider（与 [MistakePdfFileProvider] 分开）：两条链各有自己的根目录与
 * 授权面，任一格式的 URI 判据写错都只影响自己那一条，不会把另一条也打开。
 */
class MistakeDocxFileProvider : FileProvider()
