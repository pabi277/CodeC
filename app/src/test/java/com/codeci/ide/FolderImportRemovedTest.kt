package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 46.1 — the pin that keeps "Open Folder" dead (PART_46_1 exit 6).
 *
 * The owner's row: *"The project have a feature open a folder (phase 43,
 * incomplete) i want to remove it completely."* A deletion is only complete
 * when nothing can reach the code and nothing in the product still refers to
 * it — so this test scans the REAL `app/src/main` tree (the same role
 * [SettingsAuditTest] plays for Settings) and fails the build the moment any
 * of the deleted identifiers comes back, e.g. a future "let's re-add
 * open-folder" landing silently.
 *
 * Deliberate boundary (what is NOT banned here): `OpenDocument` (the single
 * *document* picker behind Import ZIP / Import file), `CreateDocument`
 * (export), and `ACTION_VIEW`/`ACTION_SEND` ("Open with CodeC") are different
 * features that stay. Only the *tree* walk is banned.
 */
class FolderImportRemovedTest {

    /** The eight identifiers the deletion must make unreachable (PART_46_1). */
    private val bannedIdentifiers = listOf(
        "OpenDocumentTree",
        "copyDocumentTree",
        "copyDocumentChildren",
        "importFolder",
        "onOpenFolder",
        "folderImportLauncher",
        "hub_sheet_folder",
        "hub_sheet_folder_subtitle"
    )

    private fun sources(): List<File> = RepoFiles.mainKotlinSources()

    private fun allMainText(): String =
        sources().joinToString("\n") { it.readText() }

    @Test
    fun `no folder-import identifier survives in main sources`() {
        val text = allMainText()
        for (id in bannedIdentifiers) {
            assertFalse(
                "Phase 46.1 regression: '$id' is back in app/src/main — the folder " +
                    "import was removed completely and must not return silently " +
                    "(see docs/chat-phase46/PART_46_1_REMOVE_OPEN_FOLDER.md)",
                text.contains(id)
            )
        }
    }

    @Test
    fun `strings xml has neither hub folder key`() {
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        assertFalse(strings.contains("hub_sheet_folder"))
    }

    @Test
    fun `the single-document SAF features the phase keeps are still present`() {
        // The honest boundary: the deletion is of the TREE walk only. If these
        // disappear the deletion went too far and ate a live feature.
        val text = allMainText()
        assertTrue(text.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(text.contains("copySingleDocument"))
    }
}
