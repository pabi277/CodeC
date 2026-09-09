package com.codeci.ide

import com.codeci.ide.ui.editor.DecorationDirtyPolicy
import com.codeci.ide.ui.editor.FindDecorationKey
import com.codeci.ide.ui.editor.FindOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationDirtyPolicyTest {

    @Test
    fun `find is not dirty when the query state is unchanged`() {
        val key = FindDecorationKey(true, "needle", FindOptions())
        assertFalse(DecorationDirtyPolicy.findNeedsRefresh(key, key))
    }

    @Test
    fun `query visibility and options each invalidate find highlights`() {
        val key = FindDecorationKey(true, "needle", FindOptions())
        assertTrue(DecorationDirtyPolicy.findNeedsRefresh(key, key.copy(query = "other")))
        assertTrue(DecorationDirtyPolicy.findNeedsRefresh(key, key.copy(visible = false)))
        assertTrue(DecorationDirtyPolicy.findNeedsRefresh(key, key.copy(options = FindOptions(matchCase = true))))
    }
}
