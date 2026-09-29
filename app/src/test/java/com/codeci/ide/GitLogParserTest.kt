package com.codeci.ide

import com.codeci.ide.ui.projects.GitLogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 73.3 — pure tests for [GitLogParser] (no process, no Android), in
 * the same spirit as the existing porcelain-status parser tests: the field
 * separator is the unit separator control character (`%x1f`), never a comma
 * or pipe, specifically so nothing a real commit subject can contain is
 * mistaken for a field boundary.
 */
class GitLogParserTest {

    private val sep = "\u001F"

    @Test fun `parses one line per commit, newest first, untouched`() {
        val lines = listOf(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa${sep}aaaaaaa${sep}Ann${sep}2026-09-29T10:00:00+05:30${sep}Second",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb${sep}bbbbbbb${sep}Bo${sep}2026-09-28T09:00:00+05:30${sep}First"
        )
        val commits = GitLogParser.parse(lines)
        assertEquals(2, commits.size)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", commits[0].sha)
        assertEquals("aaaaaaa", commits[0].shortSha)
        assertEquals("Ann", commits[0].author)
        assertEquals("2026-09-29T10:00:00+05:30", commits[0].date)
        assertEquals("Second", commits[0].subject)
        assertEquals("First", commits[1].subject)
    }

    @Test fun `a commit subject containing a comma or pipe is not split`() {
        val lines = listOf(
            "cccccccccccccccccccccccccccccccccccccccc${sep}ccccccc${sep}Cy${sep}2026-09-29T00:00:00Z${sep}Fix a, b | c and d"
        )
        val commits = GitLogParser.parse(lines)
        assertEquals(1, commits.size)
        assertEquals("Fix a, b | c and d", commits[0].subject)
    }

    @Test fun `blank and malformed lines are dropped, not thrown`() {
        val lines = listOf(
            "",
            "not-enough-fields${sep}only-two",
            "dddddddddddddddddddddddddddddddddddddddd${sep}ddddddd${sep}Dee${sep}2026-09-27T00:00:00Z${sep}Ok"
        )
        val commits = GitLogParser.parse(lines)
        assertEquals(1, commits.size)
        assertEquals("Ok", commits[0].subject)
    }

    @Test fun `a subject can itself contain the separator character's neighbours safely`() {
        // Regression guard: limit = 5 on the split means a subject with
        // stray unit-separator-adjacent bytes (never expected from real git,
        // but defensive) still keeps the whole remainder as one subject.
        val lines = listOf(
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee${sep}eeeeeee${sep}Eve${sep}2026-09-26T00:00:00Z${sep}a${sep}b"
        )
        val commits = GitLogParser.parse(lines)
        assertEquals(1, commits.size)
        assertTrue(commits[0].subject.contains(sep))
        assertEquals("a$sep" + "b", commits[0].subject)
    }
}
