package com.codeci.ide

import com.codeci.ide.ui.modules.PackageCatalog
import com.codeci.ide.ui.modules.PackageItem
import com.codeci.ide.ui.modules.PackagePins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 71.1 — owner: "Add package pin 📌 option". */
class PackagePinsTest {

    private val known = setOf("git", "nano", "vim", "clang")

    private fun item(id: String) = PackageItem(
        id = id, name = id, binary = id,
        category = com.codeci.ide.ui.modules.PackageCategory.UTILS,
        description = "",
    )

    @Test
    fun `parse keeps order, drops unknown and duplicate ids`() {
        assertEquals(
            listOf("vim", "git"),
            PackagePins.parse(" vim, gone ,git,vim,, ", known),
        )
    }

    @Test
    fun `parse of nothing or garbage is nothing pinned`() {
        assertEquals(emptyList<String>(), PackagePins.parse(null, known))
        assertEquals(emptyList<String>(), PackagePins.parse("", known))
        assertEquals(emptyList<String>(), PackagePins.parse(",,;;", known))
    }

    @Test
    fun `serialize then parse round-trips`() {
        val pins = listOf("nano", "git")
        assertEquals(pins, PackagePins.parse(PackagePins.serialize(pins), known))
    }

    @Test
    fun `pinning goes to the front and unpinning removes`() {
        var pins = emptyList<String>()
        pins = PackagePins.toggle(pins, "git")
        pins = PackagePins.toggle(pins, "nano")
        assertEquals(listOf("nano", "git"), pins)
        assertTrue(PackagePins.isPinned(pins, "git"))
        pins = PackagePins.toggle(pins, "git")
        assertEquals(listOf("nano"), pins)
        assertFalse(PackagePins.isPinned(pins, "git"))
    }

    @Test
    fun `the group is capped and the oldest pin falls off`() {
        var pins = emptyList<String>()
        for (n in 1..PackagePins.MAX_PINS + 3) pins = PackagePins.toggle(pins, "p$n")
        assertEquals(PackagePins.MAX_PINS, pins.size)
        assertEquals("p${PackagePins.MAX_PINS + 3}", pins.first())
        assertFalse("p1" in pins)
    }

    @Test
    fun `arrange puts pinned cards first in pin order and leaves the rest in catalog order`() {
        val items = listOf("clang", "git", "nano", "vim").map(::item)
        val (pinned, rest) = PackagePins.arrange(items, listOf("vim", "clang"))
        assertEquals(listOf("vim", "clang"), pinned.map { it.id })
        assertEquals(listOf("git", "nano"), rest.map { it.id })
    }

    @Test
    fun `arrange skips a pin whose card is filtered out of the list`() {
        val items = listOf("git", "nano").map(::item)
        val (pinned, rest) = PackagePins.arrange(items, listOf("vim", "nano"))
        assertEquals(listOf("nano"), pinned.map { it.id })
        assertEquals(listOf("git"), rest.map { it.id })
    }

    @Test
    fun `every real catalog id survives a parse`() {
        val ids = PackageCatalog.ALL_PACKAGES.map { it.id }
        assertEquals(ids.size, ids.toSet().size) // pins are keyed on id: ids must be unique
        assertEquals(
            ids.take(PackagePins.MAX_PINS),
            PackagePins.parse(ids.joinToString(","), ids.toSet()),
        )
    }
}
