package com.codeci.ide

import com.codeci.ide.ui.projects.*
import org.junit.Assert.*
import org.junit.Test

class GitDiscardPolicyTest {
    @Test fun `only unstaged modifications and deletions of existing files qualify`() {
        for (xy in listOf(" M", " D", "MM", "MD")) {
            assertTrue(xy, GitDiscardPolicy.canDiscard(GitFileChange(xy[0], xy[1], "a.txt")))
        }
        for (xy in listOf("??", "!!", "A ", "AM", "AD", "M ", "D ", "RM", "CM", " T", "TM", "DD", "AU", "UD", "UA", "DU", "AA", "UU")) {
            assertFalse(xy, GitDiscardPolicy.canDiscard(GitFileChange(xy[0], xy[1], "a.txt")))
        }
        assertFalse(GitDiscardPolicy.canDiscard(GitFileChange(' ', 'M', "a.txt", "old.txt")))
    }
    @Test fun `stage toggle treats untracked as stage rather than unstage`() {
        assertFalse(GitFileChange('?', '?', "new.txt").isStaged)
        assertFalse(GitFileChange(' ', 'M', "a.txt").isStaged)
        assertTrue(GitFileChange('M', 'M', "a.txt").isStaged)
    }
    @Test fun `porcelain paths retain leading spaces arrow text and utf8 octal bytes`() {
        val names = GitStatusParser.parse(listOf(" M  leading.txt", " M a -> b.txt", " M \"\\346\\227\\245\\346\\234\\254.txt\"")).files
        assertEquals(listOf(" leading.txt", "a -> b.txt", "日本.txt"), names.map { it.path })
        assertTrue(names.all { it.oldPath == null })
    }
}
