package com.codeci.ide

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.SmartTyping
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 26.2 — Smart typing host tests (pure, no Android).
 */
class SmartTypingTest {

    @Test
    fun `typeOver moves caret over matching closer instead of inserting`() {
        val old = TextFieldValue("()", TextRange(1))
        // old caret between '(' and ')', next char is ')'
        val incoming = ")"
        val res = SmartTyping.handleTypeOver(old, incoming, SmartTyping.Config(), LanguageType.C)
        assertEquals(TextRange(2), res?.selection)
        assertEquals("()", res?.text)
    }

    @Test
    fun `typeOver returns null when next char is not matcher`() {
        val old = TextFieldValue("ab", TextRange(1))
        val res = SmartTyping.handleTypeOver(old, ")", SmartTyping.Config(), LanguageType.C)
        assertEquals(null, res)
    }

    @Test
    fun `wrapSelection surrounds selection with pair`() {
        val old = TextFieldValue("hello world", TextRange(6, 11)) // select "world"
        val res = SmartTyping.handleWrapSelection(old, "(", SmartTyping.Config())
        assertEquals("hello (world)", res?.text)
        // selection should be inside parens: start 7, end 12 ("world" kept selected)
        assertEquals(TextRange(7, 12), res?.selection)
    }

    @Test
    fun `wrapSelection respects config toggle off`() {
        val old = TextFieldValue("hello world", TextRange(6, 11))
        val res = SmartTyping.handleWrapSelection(old, "(", SmartTyping.Config(wrapSelection = false))
        assertEquals(null, res)
    }

    @Test
    fun `empty pair backspace deletes both sides`() {
        val old = TextFieldValue("()", TextRange(1))
        val res = SmartTyping.handleEmptyPairBackspace(old, SmartTyping.Config(), LanguageType.C)
        assertEquals("", res?.text)
        assertEquals(TextRange(0), res?.selection)
    }

    @Test
    fun `deletePrevWord deletes word before caret leaving dot`() {
        val value = TextFieldValue("foo.bar", TextRange(7))
        val res = SmartTyping.deletePrevWord(value)
        assertEquals("foo.", res.text)
        assertEquals(4, res.selection.start)
    }

    @Test
    fun `deletePrevWord with whitespace before deletes whitespace run`() {
        val value = TextFieldValue("foo  bar", TextRange(8))
        // caret after "bar", should delete "bar"
        val res = SmartTyping.deletePrevWord(value)
        assertEquals("foo  ", res.text)
    }

    @Test
    fun `autoIndent after open brace indents next line`() {
        // Simulate old "{" + caret after it, then user presses Enter -> newValue has \n after '{'
        val old = TextFieldValue("{", TextRange(1))
        val newValue = TextFieldValue("{\n", TextRange(2))
        val res = SmartTyping.handleAutoIndent(old, newValue, LanguageType.C, tabSize = 4, config = SmartTyping.Config())
        // Should have added indent after newline (but old line indent is empty, so extra = 4 spaces)
        assertEquals("{\n    ", res?.text)
    }

    @Test
    fun `autoIndent copies previous line indent`() {
        // Simulate pressing Enter after a line that starts with 4 spaces.
        val old = TextFieldValue("    x = 1;", TextRange(10))
        val newValue = TextFieldValue("    x = 1;\n", TextRange(11))
        val res = SmartTyping.handleAutoIndent(old, newValue, LanguageType.C, tabSize = 4, config = SmartTyping.Config())
        // Should indent the new line with the same 4 spaces.
        assertEquals("    x = 1;\n    ", res?.text)
        assertEquals(15, res?.selection?.start)
    }

    @Test
    fun `transform dispatches typeOver for closer at boundary`() {
        val old = TextFieldValue("()", TextRange(1))
        val newValue = TextFieldValue("())", TextRange(2)) // naive insert of ')'
        val lang = LanguageType.C
        val out = SmartTyping.transform(old, newValue, lang, tabSize = 4, config = SmartTyping.Config())
        // typeOver should move caret to 2 without inserting extra )
        assertEquals("()", out.text)
        assertEquals(2, out.selection.start)
    }

    @Test
    fun `a typing surface closes brackets, the key strip does not (device round 2026-09-07)`() {
        // Owner report: "want auto brackets close if i give one bracelet, ",
        // ',{ etc". CodeC Keys commits through the VM, so sora's own
        // SymbolPairMatch never sees the keystroke — these pure rules are the
        // ONLY source of pairing on the IME-free keyboard, and they used to be
        // suppressed there — every keyboard commit passed the flag that is now
        // named `suppressAutoPair` (it was `isStrip` when the strip was the
        // only non-IME surface).
        val cfg = SmartTyping.Config()
        val old = TextFieldValue("int x = ", TextRange(8))
        val pairs = listOf("(" to "()", "{" to "{}", "[" to "[]", "\"" to "\"\"", "'" to "''")
        for ((ch, paired) in pairs) {
            val naive = TextFieldValue(old.text + ch, TextRange(9))
            val smart = SmartTyping.transform(old, naive, LanguageType.C, 4, cfg)
            assertEquals("int x = " + paired, smart.text)
            assertEquals(9, smart.selection.start) // caret INSIDE the pair
            // The editor key strip keeps its suppression: it has an explicit
            // `()` cap, so its swipe-up single bracket must stay single.
            val single = SmartTyping.transform(
                old, naive, LanguageType.C, 4, cfg, suppressAutoPair = true
            )
            assertEquals("int x = " + ch, single.text)
        }
    }

    @Test
    fun `enter inside a fresh pair indents and splits the closer onto its own line`() {
        // The owner's exact ask: "{ and enter it auto enter a tab in the next
        // line and the close bracket in the next line" (26.2 rule 4 — it only
        // became reachable on CodeC Keys once the pair exists).
        val cfg = SmartTyping.Config()
        var v = TextFieldValue("int main()\n", TextRange(11))
        fun type(ch: String, lang: LanguageType = LanguageType.C) {
            val s = v.selection.start
            val naive = TextFieldValue(
                v.text.substring(0, s) + ch + v.text.substring(v.selection.end),
                TextRange(s + ch.length)
            )
            v = SmartTyping.transform(v, naive, lang, 4, cfg)
        }
        type("{")
        assertEquals("int main()\n{}", v.text)
        assertEquals(12, v.selection.start)
        type("\n")
        assertEquals("int main()\n{\n    \n}", v.text)
        assertEquals(17, v.selection.start) // caret on the indented line
        type("r"); type("e"); type("t")
        assertEquals("int main()\n{\n    ret\n}", v.text)
        assertEquals(20, v.selection.start)

        // An EXISTING pair (typed with any keyboard) splits the same way.
        v = TextFieldValue("if (x) {\n}", TextRange(8))
        type("\n")
        assertEquals("if (x) {\n    \n}", v.text)
        assertEquals(13, v.selection.start)

        // Python's `:` earns the level too (26.2 rule 4).
        v = TextFieldValue("def f()", TextRange(7))
        type(":", LanguageType.PYTHON)
        type("\n", LanguageType.PYTHON)
        assertEquals("def f():\n    ", v.text)
        assertEquals(13, v.selection.start)
    }

    @Test
    fun `transform leaves string aware content untouched when disabled`() {
        val old = TextFieldValue("\"hello\"", TextRange(1))
        val newValue = TextFieldValue("\"hhello\"", TextRange(2))
        val out = SmartTyping.transform(old, newValue, LanguageType.C, config = SmartTyping.Config(stringAware = false))
        // no smart rule for 'h', so should return newValue unchanged
        assertEquals(newValue.text, out.text)
    }

    // -------------------------------------------------------------------------
    // Phase 75.1 — the Python block header: ONE rule, BOTH Enter routes
    // -------------------------------------------------------------------------

    @Test
    fun `every python block keyword opens a level`() {
        for (line in listOf(
            "def f():",
            "for item in items:",
            "while not done:",
            "if x > 3:",
            "elif y:",
            "else:",
            "try:",
            "except ValueError as e:",
            "finally:",
            "with open(p) as f:",
            "class A(B):",
            "async def go():",
            "match command:",
            "case 1 | 2:",
            "    if __name__ == \"__main__\":",
            "    for i in range(10):"
        )) {
            assertEquals("not a block header? -> $line", true, SmartTyping.opensPythonBlock(line))
        }
    }

    @Test
    fun `ordinary colons comments and strings never invent an indent`() {
        for (line in listOf(
            "# note:",
            "    # TODO: later",
            "x:",
            "d[\"key\"]:",
            "label:",
            "format:",
            "elsex:",
            "if x: print(1)",
            "s = \"a: b\"",
            "s = \"\"\"sql:",
            "d = dict(",
            "x = {",
            "return total",
            "",
            "   ",
            "}"
        )) {
            assertEquals("a block header? -> $line", false, SmartTyping.opensPythonBlock(line))
        }
    }

    @Test
    fun `a trailing comment does not hide the block colon`() {
        assertEquals(true, SmartTyping.opensPythonBlock("for i in items:  # walk the list"))
        assertEquals(true, SmartTyping.opensPythonBlock("def f():  # noqa"))
        assertEquals(false, SmartTyping.opensPythonBlock("x = 1  # note: keep"))
    }

    @Test
    fun `autoIndent indents a for loop at column zero - the owner's case A`() {
        val old = TextFieldValue("for i in items:", TextRange(15))
        val bare = TextFieldValue("for i in items:\n", TextRange(16))
        val res = SmartTyping.handleAutoIndent(old, bare, LanguageType.PYTHON, 4, SmartTyping.Config())
        assertEquals("for i in items:\n    ", res?.text)
        assertEquals(20, res?.selection?.start)
    }

    @Test
    fun `autoIndent adds a level at depth too - the half that was missing`() {
        // `def` sits at column 0 and reached the VM rule; a `for` one level
        // down copies an indent, which used to take it off this route entirely.
        val prev = "    for i in items:"
        val old = TextFieldValue(prev, TextRange(prev.length))
        val bare = TextFieldValue("$prev\n", TextRange(prev.length + 1))
        val res = SmartTyping.handleAutoIndent(old, bare, LanguageType.PYTHON, 4, SmartTyping.Config())
        assertEquals("$prev\n        ", res?.text)
        assertEquals(prev.length + 1 + 8, res?.selection?.start)
    }

    @Test
    fun `autoIndent copies the indent of a non block line but adds no level`() {
        val prev = "    # note:"
        val old = TextFieldValue(prev, TextRange(prev.length))
        val bare = TextFieldValue("$prev\n", TextRange(prev.length + 1))
        val res = SmartTyping.handleAutoIndent(old, bare, LanguageType.PYTHON, 4, SmartTyping.Config())
        // The indent ride is ordinary editor behaviour (sora does the same on
        // the IME route); what must NOT appear is a second level.
        assertEquals("$prev\n    ", res?.text)
    }

    @Test
    fun `a newline sora already indented is left alone - no second level`() {
        // The IME route after the fix: sora copied the four spaces AND the
        // language's delta, so the change is not a bare newline and the VM rule
        // must keep its hands off it.
        val old = TextFieldValue("    for i in items:", TextRange(19))
        val withIndent = TextFieldValue("    for i in items:\n        ", TextRange(28))
        val out = SmartTyping.transform(old, withIndent, LanguageType.PYTHON, 4, SmartTyping.Config())
        assertEquals(withIndent.text, out.text)
        assertEquals(28, out.selection.start)
    }

    // -------------------------------------------------------------------------
    // Phase 75.1 — Backspace inside indentation: one space per press
    // -------------------------------------------------------------------------

    @Test
    fun `an IME that wipes the whole indentation run is corrected to one space`() {
        // The auto-indented empty body line under a `for`; the caret sits after
        // the four spaces and the surface removed all of them in one press.
        val old = TextFieldValue("for i in items:\n    ", TextRange(20))
        val wiped = TextFieldValue("for i in items:\n", TextRange(16))
        val res = SmartTyping.handleIndentBackspace(old, wiped)
        assertEquals("for i in items:\n   ", res?.text)
        assertEquals(TextRange(19), res?.selection)
    }

    @Test
    fun `four presses return to the previous level one space at a time`() {
        var v = TextFieldValue("    x = 1", TextRange(4))
        repeat(4) {
            val caret = v.selection.start
            // What the surface asks for each time: the run before the caret, gone.
            val ask = TextFieldValue(v.text.substring(caret), TextRange(0))
            v = SmartTyping.transform(v, ask, LanguageType.PYTHON, 4, SmartTyping.Config())
        }
        assertEquals("x = 1", v.text)
        assertEquals(0, v.selection.start)
    }

    @Test
    fun `a tab is one press too`() {
        val old = TextFieldValue("\t\t\tbody", TextRange(3))
        val res = SmartTyping.handleIndentBackspace(old, TextFieldValue("body", TextRange(0)))
        assertEquals("\t\tbody", res?.text)
        assertEquals(TextRange(2), res?.selection)
    }

    @Test
    fun `ordinary deletions keep their meaning`() {
        // a) code, not indentation — the whole tail of the line may go.
        assertEquals(
            null,
            SmartTyping.handleIndentBackspace(
                TextFieldValue("x = compute(1)", TextRange(14)),
                TextFieldValue("x = ", TextRange(4))
            )
        )
        // b) a selection is a deletion of the selection, not a Backspace press.
        assertEquals(
            null,
            SmartTyping.handleIndentBackspace(
                TextFieldValue("    x", TextRange(0, 4)),
                TextFieldValue("x", TextRange(0))
            )
        )
        // c) a deletion that swallows the newline (a line join) is untouched:
        //    sora is stopped from offering it at the host, and this guard never
        //    rewrites a join into a single space.
        assertEquals(
            null,
            SmartTyping.handleIndentBackspace(
                TextFieldValue("a = 1\n    b = 2", TextRange(10)),
                TextFieldValue("a = 1b = 2", TextRange(5))
            )
        )
        // d) one space is already the contract — nothing to correct.
        assertEquals(
            null,
            SmartTyping.handleIndentBackspace(
                TextFieldValue("  x", TextRange(2)),
                TextFieldValue(" x", TextRange(1))
            )
        )
    }

    @Test
    fun `trailing spaces after code are deleted as the surface asks`() {
        val old = TextFieldValue("x = 1   ", TextRange(8))
        val res = SmartTyping.handleIndentBackspace(old, TextFieldValue("x = 1", TextRange(5)))
        assertEquals(null, res)
    }

    @Test
    fun `the word delete cap opts out of the guard and still removes the run`() {
        val old = TextFieldValue("    x = 1", TextRange(4))
        val ask = TextFieldValue("x = 1", TextRange(0))
        val out = SmartTyping.transform(
            old, ask, LanguageType.PYTHON, 4, SmartTyping.Config(),
            indentBackspaceGuard = false
        )
        assertEquals("x = 1", out.text)
        assertEquals(0, out.selection.start)
    }

    @Test
    fun `empty pair backspace survives the new guard`() {
        // The ⌫ inside `(|)` still deletes BOTH sides — rule 3 is older than
        // Phase 75.1 and must not be eaten by it.
        val old = TextFieldValue("()", TextRange(1))
        val out = SmartTyping.transform(old, TextFieldValue("(", TextRange(0)), LanguageType.C, 4, SmartTyping.Config())
        assertEquals("", out.text)
        assertEquals(0, out.selection.start)
    }

    // ---- Phase 75.2 device round: keyword moment + Tab unit -----------------

    @Test
    fun `typedBlockKeyword matches the exact python block keywords and nothing else`() {
        for (kw in listOf(
            "def", "class", "for", "while", "if", "elif", "else",
            "try", "except", "finally", "with", "async", "match", "case", "DEF"
        )) {
            assertEquals("expected true for $kw", true, SmartTyping.typedBlockKeyword(kw))
        }
        for (other in listOf("", "de", "defm", "deft", "ifmain", "fori", "print", "import", "return")) {
            assertEquals("expected false for $other", false, SmartTyping.typedBlockKeyword(other))
        }
    }

    @Test
    fun `tab in leading indentation advances to the next tab stop in spaces`() {
        // Column 0 -> 4 spaces.
        val col0 = SmartTyping.transform(
            TextFieldValue("x = 1", TextRange(0)),
            TextFieldValue("\tx = 1", TextRange(1)),
            LanguageType.PYTHON, 4, SmartTyping.Config()
        )
        assertEquals("    x = 1", col0.text)
        assertEquals(4, col0.selection.start)

        // Column 2 (messy 2-space indent) -> 2 spaces so the line lands on
        // column 4 and lines up with a normal level.
        val col2 = SmartTyping.transform(
            TextFieldValue("  x = 1", TextRange(2)),
            TextFieldValue("  \tx = 1", TextRange(3)),
            LanguageType.PYTHON, 4, SmartTyping.Config()
        )
        assertEquals("    x = 1", col2.text)
        assertEquals(4, col2.selection.start)

        // Column 4 -> another full level (lands on column 8).
        val col4 = SmartTyping.transform(
            TextFieldValue("    x = 1", TextRange(4)),
            TextFieldValue("    \tx = 1", TextRange(5)),
            LanguageType.PYTHON, 4, SmartTyping.Config()
        )
        assertEquals("        x = 1", col4.text)
        assertEquals(8, col4.selection.start)

        // Mid-line after code -> one full level of spaces.
        val mid = SmartTyping.transform(
            TextFieldValue("x =", TextRange(3)),
            TextFieldValue("x =\t", TextRange(4)),
            LanguageType.PYTHON, 2, SmartTyping.Config()
        )
        assertEquals("x =  ", mid.text)
        assertEquals(5, mid.selection.start)
    }

    // ---- Phase 75.3 device round 2: `{` after `int main()` ------------------

    @Test
    fun `typing open brace inside empty parens steps outside to form function body`() {
        // Owner report: "in c coding I tried to write int main() then curly
        // brackets it sent the brackets inside the first brackets like ({})".
        val old = TextFieldValue("int main()", TextRange(9)) // int main(|)
        // Step 1: single `{` insert at index 9.
        val step1 = SmartTyping.transform(
            old,
            TextFieldValue("int main({)", TextRange(10)),
            LanguageType.C, 4, SmartTyping.Config()
        )
        assertEquals("int main(){}", step1.text)
        assertEquals(11, step1.selection.start) // int main(){|}

        // Step 2: if Sora's SymbolPairMatch follows up with `}` inside `({|})`
        // before the next frame's replay, keep `int main(){|}`.
        val step2 = SmartTyping.transform(
            step1,
            TextFieldValue("int main({})", TextRange(11)),
            LanguageType.C, 4, SmartTyping.Config()
        )
        assertEquals("int main(){}", step2.text)
        assertEquals(11, step2.selection.start)

        // Batch `{}` insert inside empty `(|)` also steps outside `)`.
        val batch = SmartTyping.transform(
            old,
            TextFieldValue("int main({})", TextRange(10)),
            LanguageType.C, 4, SmartTyping.Config()
        )
        assertEquals("int main(){}", batch.text)
        assertEquals(11, batch.selection.start)
    }
}
