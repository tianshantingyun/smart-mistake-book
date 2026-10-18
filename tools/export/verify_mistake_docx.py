#!/usr/bin/env python3
"""6B-1 交付校验：Word（.docx = OOXML/OPC 包）产物的结构与内容检查。

两种用法：

1) 校验一个真实产物（设备/门禁上跑）：
     python tools/export/verify_mistake_docx.py <path/to/mistake.docx> [--expect-json texts.json]

2) 设计自检（JVM 金样不可达时的最小闭环）：
     python tools/export/verify_mistake_docx.py --selfcheck

   `--selfcheck` 按 `DocxMistakeRenderer.kt` 的**同构模板**在 Python 里拼一个样本包，再用本脚本
   的同一套校验器 + python-docx（若已安装）打开它。它证明"手写 XML 的命名空间/关系/内容类型
   形状能被标准解析器与 python-docx 接受"，**不证明 Kotlin 渲染器的字节**——后者由
   `DocxMistakeRendererTest`（JVM 金样）与设备走查负责。

安全口径：解析任何 XML 前先拒绝 `<!DOCTYPE`/`<!ENTITY`（不启用外部实体），
与仓库「解析不可信 XML」的约束一致。
"""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PACKAGE_RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"

CONTENT_TYPES_PART = "[Content_Types].xml"
PACKAGE_RELS_PART = "_rels/.rels"
DOCUMENT_PART = "word/document.xml"
DOCUMENT_RELS_PART = "word/_rels/document.xml.rels"
MEDIA_PREFIX = "word/media/"

OFFICE_DOCUMENT_RELATIONSHIP = (
    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
)
IMAGE_RELATIONSHIP = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
DOCUMENT_CONTENT_TYPE = (
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
)


def _parse_xml(data: bytes, source: str) -> ET.Element:
    head = data.upper()
    if b"<!DOCTYPE" in head or b"<!ENTITY" in head:
        raise ValueError(f"{source}: refusing XML with DOCTYPE/ENTITY")
    return ET.fromstring(data)


def _fail(problems: list[str], message: str) -> None:
    problems.append(message)


def validate(path: Path, expected_texts: list[str] | None = None) -> tuple[list[str], list[str]]:
    problems: list[str] = []
    texts: list[str] = []
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        media = {name for name in names if name.startswith(MEDIA_PREFIX)}
        allowed = {CONTENT_TYPES_PART, PACKAGE_RELS_PART, DOCUMENT_PART, DOCUMENT_RELS_PART}
        unexpected = {name for name in names if name not in allowed and name not in media}
        if unexpected:
            _fail(problems, f"unexpected parts: {sorted(unexpected)}")
        for required in (CONTENT_TYPES_PART, PACKAGE_RELS_PART, DOCUMENT_PART):
            if required not in names:
                _fail(problems, f"missing required part: {required}")
        if DOCUMENT_RELS_PART in names and not media:
            _fail(problems, "document relationships exist without any media part")

        if CONTENT_TYPES_PART in names:
            content_types = _parse_xml(archive.read(CONTENT_TYPES_PART), CONTENT_TYPES_PART)
            if content_types.tag != f"{{{CONTENT_TYPES_NS}}}Types":
                _fail(problems, "content types root element is not Types")
            children = list(content_types)
            defaults = [
                element.get("Extension")
                for element in children
                if element.tag == f"{{{CONTENT_TYPES_NS}}}Default"
            ]
            overrides = {
                element.get("PartName"): element.get("ContentType")
                for element in children
                if element.tag == f"{{{CONTENT_TYPES_NS}}}Override"
            }
            if "rels" not in defaults or "xml" not in defaults:
                _fail(problems, f"content types defaults incomplete: {sorted(defaults)}")
            if overrides.get(f"/{DOCUMENT_PART}") != DOCUMENT_CONTENT_TYPE:
                _fail(problems, f"document override missing/incorrect: {overrides}")
            has_png = any(name.endswith(".png") for name in media)
            if has_png and "png" not in defaults:
                _fail(problems, "media has png parts but content types has no png default")
            if "png" in defaults and not has_png:
                _fail(problems, "content types declares png but no png media part")
            # OPC 架构是 sequence：Default 必须在 Override 之前。
            first_override = next(
                (index for index, element in enumerate(children)
                 if element.tag == f"{{{CONTENT_TYPES_NS}}}Override"),
                None,
            )
            last_default = next(
                (index for index, element in reversed(list(enumerate(children)))
                 if element.tag == f"{{{CONTENT_TYPES_NS}}}Default"),
                None,
            )
            if (
                first_override is not None
                and last_default is not None
                and first_override < last_default
            ):
                _fail(problems, "Override appears before Default (OPC requires Default first)")

        if PACKAGE_RELS_PART in names:
            rels = _parse_xml(archive.read(PACKAGE_RELS_PART), PACKAGE_RELS_PART)
            targets = {
                element.get("Type"): element.get("Target")
                for element in rels.findall(f"{{{PACKAGE_RELS_NS}}}Relationship")
            }
            if targets.get(OFFICE_DOCUMENT_RELATIONSHIP) != DOCUMENT_PART:
                _fail(problems, f"office document relationship is wrong: {targets}")

        if DOCUMENT_RELS_PART in names:
            rels = _parse_xml(archive.read(DOCUMENT_RELS_PART), DOCUMENT_RELS_PART)
            relationships = rels.findall(f"{{{PACKAGE_RELS_NS}}}Relationship")
            targets = {element.get("Target") for element in relationships}
            if any(element.get("Type") != IMAGE_RELATIONSHIP for element in relationships):
                _fail(problems, "document relationships must target images only")
            if {name[len("word/"):] for name in media} != targets:
                _fail(problems, f"media parts and relationships do not match: {sorted(targets)}")

        if DOCUMENT_PART in names:
            document = _parse_xml(archive.read(DOCUMENT_PART), DOCUMENT_PART)
            if document.tag != f"{{{W_NS}}}document":
                _fail(problems, "document root element is not w:document")
            body = document.find(f"{{{W_NS}}}body")
            if body is None:
                _fail(problems, "w:body is missing")
            elif body[-1].tag != f"{{{W_NS}}}sectPr":
                _fail(problems, "w:sectPr must be the last body element")
            texts = [
                node.text or ""
                for node in document.iter(f"{{{W_NS}}}t")
            ]

    if expected_texts is not None and texts != expected_texts:
        _fail(problems, f"texts differ:\n  expected: {expected_texts}\n  actual:   {texts}")
    return problems, texts


def selfcheck_package(target: Path) -> None:
    """按渲染器的同构模板拼一个样本包（设计检查夹具，不是 Kotlin 产物）。"""
    declaration = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'

    def paragraph(text: str, bold: bool = False, size: int = 24) -> str:
        properties = ""
        if bold:
            properties += "<w:b/>"
        if size:
            properties += f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>'
        run_properties = f"<w:rPr>{properties}</w:rPr>" if properties else ""
        return (
            f'<w:p><w:r>{run_properties}<w:t xml:space="preserve">{text}</w:t></w:r></w:p>'
        )

    png = bytes.fromhex("89504e470d0a1a0a") + b"\x00" * 16  # 仅作部件字节，尺寸断言不在此脚本
    drawing = (
        "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
        '<wp:extent cx="6341110" cy="3170555"/><wp:docPr id="1" name="image1.png"/>'
        '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        '<pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="image1.png"/><pic:cNvPicPr/></pic:nvPicPr>'
        '<pic:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="6341110" cy="3170555"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
        "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    )
    document = (
        declaration
        + '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
        'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
        'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
        'xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>'
        + paragraph("函数单调区间", bold=True, size=40)
        + paragraph("MATH · 第 3 版", size=20)
        + paragraph("题面标题：题面", size=20)
        + paragraph("干净题面", bold=True, size=30)
        + drawing
        + paragraph("已知 f(x)=x²，求单调区间。")
        + paragraph("A. 甲")
        + paragraph("答案", bold=True, size=30)
        + paragraph("1. A. 甲")
        + paragraph("我的备注", bold=True, size=30)
        + paragraph("这题考单调性")
        + '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
        '<w:pgMar w:top="960" w:right="960" w:bottom="960" w:left="960" '
        'w:header="851" w:footer="992" w:gutter="0"/></w:sectPr>'
        "</w:body></w:document>"
    )
    content_types = (
        declaration
        + f'<Types xmlns="{CONTENT_TYPES_NS}">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Default Extension="png" ContentType="image/png"/>'
        f'<Override PartName="/{DOCUMENT_PART}" ContentType="{DOCUMENT_CONTENT_TYPE}"/></Types>'
    )
    package_rels = (
        declaration
        + f'<Relationships xmlns="{PACKAGE_RELS_NS}">'
        f'<Relationship Id="rId1" Type="{OFFICE_DOCUMENT_RELATIONSHIP}" Target="{DOCUMENT_PART}"/>'
        "</Relationships>"
    )
    document_rels = (
        declaration
        + f'<Relationships xmlns="{PACKAGE_RELS_NS}">'
        f'<Relationship Id="rId1" Type="{IMAGE_RELATIONSHIP}" Target="media/image1.png"/>'
        "</Relationships>"
    )
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in (
            (CONTENT_TYPES_PART, content_types.encode("utf-8")),
            (PACKAGE_RELS_PART, package_rels.encode("utf-8")),
            (DOCUMENT_RELS_PART, document_rels.encode("utf-8")),
            (DOCUMENT_PART, document.encode("utf-8")),
            ("word/media/image1.png", png),
        ):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            archive.writestr(info, data)


def main() -> int:
    parser = argparse.ArgumentParser(description="校验 6B-1 Word 交付产物（OOXML 最小集）")
    parser.add_argument("path", nargs="?", help="要校验的 .docx")
    parser.add_argument("--expect-json", help="逐条期望文本的 JSON 数组文件")
    parser.add_argument("--selfcheck", action="store_true", help="拼样本包并校验（设计自检）")
    args = parser.parse_args()

    failures = 0
    if args.selfcheck:
        with tempfile.TemporaryDirectory() as directory:
            sample = Path(directory) / "selfcheck.docx"
            selfcheck_package(sample)
            problems, texts = validate(sample)
            print(f"[selfcheck] parts/texts parsed: {len(texts)} text nodes")
            for problem in problems:
                print(f"[selfcheck][FAIL] {problem}")
                failures += 1
            if not problems:
                print("[selfcheck][OK] OOXML structure accepted by stdlib zipfile+ElementTree")
            try:
                import docx  # python-docx

                opened = docx.Document(str(sample))
                paragraphs = [paragraph.text for paragraph in opened.paragraphs]
                print(f"[selfcheck][python-docx] opened: {len(paragraphs)} paragraphs")
                if paragraphs and paragraphs[0] == "函数单调区间":
                    print("[selfcheck][OK] python-docx reads the expected first paragraph")
                else:
                    print(f"[selfcheck][FAIL] unexpected paragraphs: {paragraphs}")
                    failures += 1
            except ImportError:
                print("[selfcheck][python-docx] not installed; skipped")
            except Exception as failure:  # noqa: BLE001 - 自检要把真实原因打出来
                print(f"[selfcheck][FAIL] python-docx could not open the package: {failure!r}")
                failures += 1

            # 负向自检：缺 word/document.xml 的包必须被拒（校验器不是"永远通过"）。
            broken = Path(directory) / "broken.docx"
            with zipfile.ZipFile(sample) as source, zipfile.ZipFile(broken, "w") as target:
                for info in source.infolist():
                    if info.filename == DOCUMENT_PART:
                        continue
                    target.writestr(info, source.read(info.filename))
            broken_problems, _ = validate(broken)
            if broken_problems:
                print("[selfcheck][OK] a package without word/document.xml is rejected")
            else:
                print("[selfcheck][FAIL] validator accepted a package without word/document.xml")
                failures += 1
        return 1 if failures else 0

    if not args.path:
        parser.error("need a .docx path or --selfcheck")
    expected = None
    if args.expect_json:
        expected = json.loads(Path(args.expect_json).read_text(encoding="utf-8"))
    problems, texts = validate(Path(args.path), expected)
    for problem in problems:
        print(f"[FAIL] {problem}")
    if not problems:
        print(f"[OK] {args.path}: {len(texts)} text nodes, structure and content verified")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
