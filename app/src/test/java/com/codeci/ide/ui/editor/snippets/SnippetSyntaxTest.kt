package com.codeci.ide.ui.editor.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 30.1 — the two pure halves of the snippet pipeline:
 *  - [SnippetJson]: the hand-rolled strict reader (no `org.json` on host, the
 *    codebase law) including its "malformed → null, never throw" contract;
 *  - [SnippetSyntax]: VS Code snippet syntax → plain insert text + the caret
 *    park (plan rule S3).
 *
 * Every case below is a real shape found in the vendored MIT packs
 * (`assets/snippets/`), so a resolver bug is a device bug.
 */
class SnippetSyntaxTest {

    // ---- the JSON reader -------------------------------------------------

    @Test
    fun `json reader keeps object order and resolves escapes`() {
        val node = SnippetJson.parse(
            """{"a":1,"b":["x","y"],"c":{"d":true},"e":null,"f":"A\n\t\"q\""}"""
        ) as? JsonValue.Obj
        assertTrue("object parsed", node != null)
        assertEquals(
            listOf("a", "b", "c", "e", "f"),
            node!!.entries.keys.toList()
        )
        assertEquals("A\n\t\"q\"", SnippetJson.asString(node["f"]))
        assertEquals(listOf("x", "y"), SnippetJson.asStringList(node["b"]))
        // A scalar reads as a one-element list (VS Code allows both forms).
        assertEquals(listOf("A\n\t\"q\""), SnippetJson.asStringList(node["f"]))
        assertEquals(emptyList<String>(), SnippetJson.asStringList(node["e"]))
    }

    @Test
    fun `json reader decodes unicode escapes and literals`() {
        // A NORMAL string, not a raw one on purpose: Kotlin resolves unicode
        // escapes at compile time even inside raw strings, which would hide
        // the escape sequence from the reader under test.
        val node = SnippetJson.parse("{\"s\":\"\\u0041é中\"}") as? JsonValue.Obj
        assertEquals("Aé中", SnippetJson.asString(node?.get("s")))
    }

    @Test
    fun `malformed json yields null instead of throwing`() {
        assertNull(SnippetJson.parse(""))
        assertNull(SnippetJson.parse("   "))
        assertNull(SnippetJson.parse("{"))
        assertNull(SnippetJson.parse("""{"a":1,}"""))
        assertNull(SnippetJson.parse("""{"a":1"""))
        assertNull(SnippetJson.parse("""{} trailing"""))
        assertNull(SnippetJson.parse("""{"a":"unterminated}"""))
        assertNull(SnippetJson.parse("""{"a":tru}"""))
    }

    // ---- the snippet-body resolver ---------------------------------------

    @Test
    fun `placeholder resolves and the caret parks on it`() {
        // c.json `printf`: body ["printf(\"${1:%s}\\n\"$2);$0"]
        val resolved = SnippetSyntax.resolve("printf(\"\${1:%s}\\n\"\$2);\$0")
        assertEquals("printf(\"%s\\n\");", resolved.text)
        assertEquals(8, resolved.caretOffset)
    }

    @Test
    fun `mirrors repeat the placeholder text instead of vanishing`() {
        // Dropping mirrors would emit "for (int i = 0;  < n; ++)" — broken C.
        val resolved = SnippetSyntax.resolve("for (int \${1:i} = 0; \$1 < \${2:n}; \$1++) {\n\t\$0\n}")
        assertEquals("for (int i = 0; i < n; i++) {\n    \n}", resolved.text)
        assertEquals(9, resolved.caretOffset)
    }

    @Test
    fun `a mirror that appears before its placeholder still resolves`() {
        val resolved = SnippetSyntax.resolve("\$1 = \${1:value};")
        assertEquals("value = value;", resolved.text)
        assertEquals(0, resolved.caretOffset)
    }

    @Test
    fun `choices keep the first alternative`() {
        // css.json `ai`: "align-items: ${1|flex-start,…|};"
        val resolved = SnippetSyntax.resolve("align-items: \${1|flex-start,center,stretch|};")
        assertEquals("align-items: flex-start;", resolved.text)
        assertEquals(13, resolved.caretOffset)
    }

    @Test
    fun `nested placeholders flatten and keep their own stops`() {
        // php.json `priarg`: "${1:private} ${2:${3:Type} \$${4:var}${5: = ${6:null}}}"
        val resolved = SnippetSyntax.resolve("\${1:private} \${2:\${3:Type} \\\$\${4:var}\${5: = \${6:null}}}")
        assertEquals("private Type \$var = null", resolved.text)
        assertEquals(0, resolved.caretOffset)
        // The lowest-index stop wins; index 3 sits inside the flattened text.
        val nested = SnippetSyntax.resolve("\${2:\${3:Type} x}")
        assertEquals("Type x", nested.text)
        assertEquals(0, nested.caretOffset)
    }

    @Test
    fun `escapes unescape only snippet syntax, never source text`() {
        assertEquals("\$this->x = 5;", SnippetSyntax.resolve("\\\$this->x = 5;").text)
        assertEquals("a } b", SnippetSyntax.resolve("a \\} b").text)
        assertEquals("a\\b", SnippetSyntax.resolve("a\\\\b").text)
        // A C string escape is SOURCE, not snippet syntax: it must survive.
        assertEquals("printf(\"\\n\");", SnippetSyntax.resolve("printf(\"\\n\");").text)
    }

    @Test
    fun `tabs become four spaces (the editor indents with spaces)`() {
        assertEquals("if (a) {\n    b();\n}", SnippetSyntax.resolve("if (a) {\n\tb();\n}").text)
    }

    @Test
    fun `no stops means no caret park (null = end of insert)`() {
        assertNull(SnippetSyntax.resolve("int a = 1;").caretOffset)
        // A trailing $0 is the end of the insert, which is also "null".
        val trailing = SnippetSyntax.resolve("abc\$0")
        assertEquals("abc", trailing.text)
        assertNull(trailing.caretOffset)
    }

    @Test
    fun `a lone dollar-zero parks the caret where it sat`() {
        // c.json `Comment block`: "/*$0 */"
        val resolved = SnippetSyntax.resolve("/*\$0 */")
        assertEquals("/* */", resolved.text)
        assertEquals(2, resolved.caretOffset)
    }

    @Test
    fun `filename variables resolve from the open file`() {
        val resolved = SnippetSyntax.resolve(
            "describe('\${TM_FILENAME_BASE}', () => {\n\t\${0}\n})",
            "src/app.test.js"
        )
        assertTrue("resolved: ${resolved.text}", resolved.text.startsWith("describe('app.test', () => {"))
        assertEquals("main.c", SnippetSyntax.resolve("\${TM_FILENAME}", "proj/main.c").text)
        assertEquals("main", SnippetSyntax.resolve("\${TM_FILENAME_BASE}", "proj/main.c").text)
        assertEquals("proj", SnippetSyntax.resolve("\${TM_DIRECTORY}", "proj/main.c").text)
        // Unknown context (no clipboard/workspace/random on a phone editor)
        // vanishes instead of printing "${RANDOM}" into the buffer.
        assertEquals("x", SnippetSyntax.resolve("\${RANDOM}x").text)
        assertEquals("x", SnippetSyntax.resolve("x\$CLIPBOARD").text)
    }

    @Test
    fun `index transformations apply case ops`() {
        // react.json `us`: set${1/(.*)/${1:/capitalize}/}
        val resolved = SnippetSyntax.resolve(
            "const [\${1:state}, set\${1/(.*)/\${1:/capitalize}/}] = useState(\${2:initValue})\$0"
        )
        assertEquals("const [state, setState] = useState(initValue)", resolved.text)
        assertEquals(7, resolved.caretOffset)
    }

    @Test
    fun `variable transformations build the C++ include guard`() {
        // cpp.json `#guard`, verbatim. A RAW string on purpose: the regex keeps
        // its own `\/` `\\` escapes, and unescaping them would break the
        // `[\/\\]` character class the pack relies on.
        val body = "#ifndef INCLUDE" +
            "\${TM_DIRECTORY/.*[\\/\\\\](.*)/_\${1:/upcase}/}" +
            "\${TM_FILENAME_BASE/(.*)/_\${1:/upcase}/}" +
            "\${TM_FILENAME/.*\\.(.*)/_\${1:/upcase}/}_"
        assertEquals("#ifndef INCLUDE_INC_THING_H_", SnippetSyntax.resolve(body, "/src/inc/thing.h").text)
        // No directory in the path: that segment contributes nothing.
        assertEquals("#ifndef INCLUDE_THING_H_", SnippetSyntax.resolve(body, "thing.h").text)
    }

    @Test
    fun `a plain variable transformation upcases its match`() {
        assertEquals(
            "_THING",
            SnippetSyntax.resolve("\${TM_FILENAME_BASE/(.*)/_\${1:/upcase}/}", "a/thing.h").text
        )
    }

    // A transformation lives INSIDE the braces — `${var/regex/format/options}`
    // — and runs against the variable's resolved text. The cases below drive it
    // through TM_FILENAME_BASE (a known quantity) and through ${RANDOM}, which
    // always resolves to "" on a phone editor and so exercises the empty forms.

    @Test
    fun `an unmatchable or exotic transformation degrades to empty`() {
        assertEquals("[]", SnippetSyntax.resolve("[\${TM_FILENAME_BASE/zzz/}]", "a.c").text)
        // A regex that does not compile must not take the editor down.
        assertEquals("[]", SnippetSyntax.resolve("[\${TM_FILENAME_BASE/([unclosed/}]", "a.c").text)
    }

    @Test
    fun `conditional replacement forms are honoured`() {
        assertEquals("[set]", SnippetSyntax.resolve("[\${TM_FILENAME_BASE/(.*)/\${1:+set}/}]", "a.c").text)
        assertEquals("[]", SnippetSyntax.resolve("[\${RANDOM/(.*)/\${1:+set}/}]").text)
        assertEquals("[fallback]", SnippetSyntax.resolve("[\${RANDOM/(.*)/\${1:-fallback}/}]").text)
        assertEquals("[yes]", SnippetSyntax.resolve("[\${TM_FILENAME_BASE/(.*)/\${1:?yes:no}/}]", "a.c").text)
        assertEquals("[no]", SnippetSyntax.resolve("[\${RANDOM/(.*)/\${1:?yes:no}/}]").text)
    }

    @Test
    fun `a global transformation replaces every match`() {
        assertEquals("X-X", SnippetSyntax.resolve("\${TM_FILENAME_BASE/a/X/g}", "a-a.c").text)
        // Without "g" only the first match is replaced.
        assertEquals("X-a", SnippetSyntax.resolve("\${TM_FILENAME_BASE/a/X/}", "a-a.c").text)
    }

    @Test
    fun `array bodies join with newlines`() {
        val resolved = SnippetSyntax.resolveLines(listOf("int main(void)", "{", "\t\$0", "}"))
        assertEquals("int main(void)\n{\n    \n}", resolved.text)
    }

    @Test
    fun `a pathological placeholder cannot loop forever`() {
        // Self-reference through a mirror: resolution must terminate.
        val resolved = SnippetSyntax.resolve("\${1:\$1x}")
        assertTrue("runaway expansion: ${resolved.text}", resolved.text.length < 50)
    }
}
