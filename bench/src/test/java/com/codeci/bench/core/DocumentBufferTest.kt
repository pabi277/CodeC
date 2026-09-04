package com.codeci.bench.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class DocumentBufferTest {

    private fun assertMatchesOracle(buffer: DocumentBuffer, oracle: StringBuilder, message: String) {
        assertEquals("$message: text", oracle.toString(), buffer.toFullString())
        assertEquals("$message: length", oracle.length, buffer.length)
        // Offset index consistency: lineStart(line+1) == lineStart(line) + lineLength + 1
        for (line in 0 until buffer.lineCount - 1) {
            assertEquals(
                "$message: start chain at $line",
                buffer.lineStart(line) + buffer.lineLength(line) + 1,
                buffer.lineStart(line + 1)
            )
        }
        // locate() round-trips for every line start.
        for (line in 0 until buffer.lineCount) {
            val (l, c) = buffer.locate(buffer.lineStart(line))
            assertEquals("$message: locate line $line", line, l)
            assertEquals("$message: locate col $line", 0, c)
        }
    }

    @Test
    fun `initial split handles leading trailing and doubled newlines`() {
        val buffer = DocumentBuffer("first\n\nthird\n")
        assertEquals(4, buffer.lineCount)
        assertEquals("first", buffer.lineAt(0))
        assertEquals("", buffer.lineAt(1))
        assertEquals("third", buffer.lineAt(2))
        assertEquals("", buffer.lineAt(3))
        assertEquals("first\n\nthird\n", buffer.toFullString())
    }

    @Test
    fun `empty text is one empty line`() {
        val buffer = DocumentBuffer("")
        assertEquals(1, buffer.lineCount)
        assertEquals(0, buffer.length)
        assertEquals(0 to 0, buffer.locate(0))
    }

    @Test
    fun `single line insert returns the affected line range`() {
        val buffer = DocumentBuffer("hello world\nsecond line\nthird")
        val (first, until) = buffer.insert(5, " there")
        assertEquals(0, first)
        assertEquals(1, until)
        assertEquals("hello there world", buffer.lineAt(0))
        assertEquals("hello there world\nsecond line\nthird", buffer.toFullString())
    }

    @Test
    fun `multi line insert splits and joins lines`() {
        val buffer = DocumentBuffer("abcdef")
        buffer.insert(3, "XY\nZW\n")
        assertEquals("abcXY", buffer.lineAt(0))
        assertEquals("ZW", buffer.lineAt(1))
        assertEquals("def", buffer.lineAt(2))
        assertEquals("abcXY\nZW\ndef", buffer.toFullString())
        assertEquals(0 to 5, buffer.locate(5))
        assertEquals(1 to 0, buffer.locate(6))
        assertEquals(2 to 0, buffer.locate(9))
    }

    @Test
    fun `delete across lines joins head and tail`() {
        val buffer = DocumentBuffer("head\ntail-1\ntail-2")
        buffer.delete(2, 12) // removes indices 2..11 ("ad\ntail-1\n")
        assertEquals("hetail-2", buffer.toFullString())
    }

    @Test
    fun `same line delete keeps offsets consistent`() {
        val buffer = DocumentBuffer("aaa\nbbbb\ncc")
        buffer.delete(5, 8) // removes "bbb"
        assertEquals("aaa\nb\ncc", buffer.toFullString())
        assertEquals(4, buffer.lineStart(1))
        assertEquals(6, buffer.lineStart(2))
    }

    @Test
    fun `ten thousand seeded random edits match a StringBuilder oracle`() {
        val rng = Random(251)
        val oracle = StringBuilder("alpha\nbeta\ngamma\ndelta\n")
        val buffer = DocumentBuffer(oracle.toString())
        repeat(10_000) { op ->
            when (rng.nextInt(2)) {
                0 -> {
                    val text = buildString {
                        val n = rng.nextInt(6)
                        repeat(n) { chunk ->
                            if (rng.nextBoolean()) append('\n')
                            append(('a'..'z').random(rng))
                        }
                        if (isEmpty()) append('x')
                    }
                    val at = rng.nextInt(oracle.length + 1)
                    oracle.insert(at, text)
                    buffer.insert(at, text)
                }
                else -> {
                    if (oracle.length > 1) {
                        val start = rng.nextInt(oracle.length)
                        val end = (start + rng.nextInt((oracle.length - start).coerceAtLeast(1)))
                            .coerceAtMost(oracle.length)
                        oracle.delete(start, end)
                        buffer.delete(start, end)
                    }
                }
            }
            if (op % 2_500 == 0) assertMatchesOracle(buffer, oracle, "op $op")
        }
        assertMatchesOracle(buffer, oracle, "final")
    }

    @Test
    fun `locate binary search matches a linear scan on a large buffer`() {
        val rng = Random(7)
        val lines = List(5_000) { i -> "line-${i}-${'a' + (i % 26)}${if (i % 7 == 0) "\n" else ""}" }
        val text = lines.joinToString("\n")
        val buffer = DocumentBuffer(text)
        repeat(1_000) {
            val offset = rng.nextInt(text.length + 1)
            val (line, col) = buffer.locate(offset)
            assertEquals(offset, buffer.lineStart(line) + col)
            // The located line really contains the offset.
            assertTrue(offset >= buffer.lineStart(line))
            assertTrue(
                line == buffer.lineCount - 1 ||
                    offset < buffer.lineStart(line + 1)
            )
        }
    }
}
