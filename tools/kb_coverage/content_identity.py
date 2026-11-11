# -*- coding: utf-8 -*-
"""内容身份（content_sha）的唯一实现：全内容、规范化换行/Unicode 后取 sha256。

## 它消灭的失败

`office_extract._fp` 曾只哈希 CJK 字符，于是数学公式、数字、拉丁字母变化
（`$x=1$` → `$x=2$`、`10 N` → `20 N`）得到同一指纹，判定账本会把“内容已改”
误当成“同一块已处理”。同一仓库里 ledger 与 extractor 若各写一份哈希，
还会出现“账本说失效、块池说没失效”的双口径。

本模块是**唯一**的内容哈希实现：`canonical_text` 统一 CRLF/CR → LF 与
Unicode NFC；`content_sha` 对规范化后的**全部字符**取 sha256。

## 口径

- `canon_version = "text-nfc-lf-v1"`：换行与 Unicode 规范化后、UTF-8 编码。
- 不做 trim、不丢弃任何字符：空白/标点/公式/数字的变化都必须改变哈希。
- 这是**内容身份**，不是去重指纹；排版差异是否要复用旧结果，由调用方显式决定。
"""

from __future__ import annotations

import hashlib
import unicodedata
from pathlib import Path

CANON_VERSION = "text-nfc-lf-v1"


def canonical_text(text: str) -> str:
    """规范化文本但不丢内容：CRLF/CR → LF，Unicode → NFC。"""
    if not isinstance(text, str):
        raise TypeError("content identity 只接受 str")
    return unicodedata.normalize("NFC", text.replace("\r\n", "\n").replace("\r", "\n"))


def sha256_text(text: str) -> str:
    """任意字符串的 sha256（UTF-8），不做规范化。"""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def content_sha(text: str) -> str:
    """内容身份：规范化后全部字符的 sha256。"""
    return sha256_text(canonical_text(text))


def sha256_file(path: Path | str) -> str:
    """文件原始字节的 sha256（不读成文本、不做规范化）。"""
    digest = hashlib.sha256()
    with Path(path).open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_text_content_sha(path: Path | str) -> str:
    """文本文件的内容身份：UTF-8 解码后按 `content_sha` 计算。"""
    return content_sha(Path(path).read_text(encoding="utf-8"))
