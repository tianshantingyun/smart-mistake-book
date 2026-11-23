package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorInteractiveSceneTest {
    private fun scene(controls: String = "", form: String = ""): String = buildString {
        append("""{"schemaVersion":1,"sceneId":"s","revision":1,"viewBox":{"x":0,"y":0,"width":360,"height":240}""")
        if (controls.isNotEmpty()) append(""","controls":$controls""")
        if (form.isNotEmpty()) append(""","form":$form""")
        append('}')
    }

    @Test
    fun parsesEveryControlKindAndAForm() {
        val spec = parseTutorInteractiveSpec(scene(
            controls = """
                [
                  {"id":"a","kind":"range","label":"a 的值","value":1,"min":-5,"max":5,"step":0.5,"semantics":"斜率"},
                  {"id":"show","kind":"toggle","label":"显示辅助线","value":true},
                  {"id":"fn","kind":"select","label":"函数","value":"sin",
                   "options":[{"value":"sin","label":"正弦"},{"value":"cos","label":"余弦"}]},
                  {"id":"reset","kind":"button","label":"重置","enabled":false}
                ]
            """.trimIndent(),
            form = """
                {"formId":"form-1","title":"检查一下","rationaleLabel":"理由",
                 "items":[{"id":"q1","kind":"radioGroup","prompt":"当 a>0 时图像怎么变？",
                           "options":[{"value":"up","label":"上移"},{"value":"down","label":"下移"}]}]}
            """.trimIndent(),
        ))

        assertEquals(4, spec.controls.size)
        assertEquals(TutorControlKind.RANGE, spec.controls[0].kind)
        assertEquals(-5.0, spec.controls[0].min)
        assertEquals(0.5, spec.controls[0].step)
        assertEquals(true, spec.controls[1].booleanValue)
        assertEquals("sin", spec.controls[2].stringValue)
        assertEquals(listOf("sin", "cos"), spec.controls[2].options.map { it.value })
        assertEquals(false, spec.controls[3].enabled)
        val form = requireNotNull(spec.form)
        assertEquals("form-1", form.formId)
        assertEquals("检查一下", form.title)
        assertEquals(1, form.items.size)
        assertEquals(2, form.items.single().options.size)
    }

    @Test
    fun aStaticSceneHasAnEmptyInteractiveSpec() {
        val spec = parseTutorInteractiveSpec(scene())
        assertTrue(spec.isEmpty)
        assertNull(spec.form)
    }

    @Test
    fun rejectsUnknownKeysAndInvalidShapes() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(controls = """[{"id":"a","kind":"button","label":"A","extra":1}]"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                controls = """[{"id":"a","kind":"range","label":"a","value":1,"min":5,"max":1}]""",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                controls = """[{"id":"s","kind":"select","label":"s","value":"x",
                    "options":[{"value":"a","label":"A"}]}]""",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                controls = """[{"id":"a","kind":"button","label":"A"},
                    {"id":"a","kind":"button","label":"B"}]""",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                form = """{"formId":"f","items":[{"id":"q","kind":"checkbox","prompt":"q",
                    "options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]}]}""",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                controls = """[{"id":"transport","kind":"transport","label":"播放"}]""",
            ))
        }
    }

    @Test
    fun aNullFormIsTreatedAsAbsentLikeTheCompiler() {
        val spec = parseTutorInteractiveSpec(scene(form = "null"))
        assertNull(spec.form)
        assertTrue(spec.controls.isEmpty())
    }

    @Test
    fun rangeNumbersStayInsideTheFloatSafeBoundAndLabelsStayNonBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                controls = """[{"id":"r","kind":"range","label":"r","value":0,
                    "min":-1e300,"max":1e300}]""",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                controls = """[{"id":"r","kind":"button","label":"   "}]""",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(
                form = """{"formId":"f","items":[{"id":"q.1","kind":"radioGroup","prompt":"p",
                    "options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]}]}""",
            ))
        }
    }

    @Test
    fun enforcesTheSameBudgetsAsTheCompiler() {
        val tooManyControls = (1..9).joinToString(",", prefix = "[", postfix = "]") {
            """{"id":"c$it","kind":"button","label":"B"}"""
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(controls = tooManyControls))
        }
        val tooManyItems = (1..5).joinToString(",", prefix = "[", postfix = "]") {
            """{"id":"q$it","kind":"radioGroup","prompt":"p",
                "options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]}"""
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTutorInteractiveSpec(scene(form = """{"formId":"f","items":$tooManyItems}"""))
        }
    }

    @Test
    fun controlEventCarriesTheHostAssignedSequenceAndIdentity() {
        val event = visualControlEventJson(
            programId = "visual-program-1",
            sceneId = "scene-1",
            sceneRevision = 3,
            sequence = 7,
            kind = "change",
            controlId = "a",
            value = buildJsonObject { put("value", JsonPrimitive(1.5)) },
        )

        assertTrue(event, event.contains("\"programId\":\"visual-program-1\""))
        assertTrue(event, event.contains("\"sceneRevision\":3"))
        assertTrue(event, event.contains("\"sequence\":7"))
        assertTrue(event, event.contains("\"controlId\":\"a\""))
        assertTrue(event, event.contains("\"value\":{\"value\":1.5}"))
    }
}
