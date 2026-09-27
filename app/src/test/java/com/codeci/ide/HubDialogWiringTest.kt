package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 66.1 — the Projects hub's dialogs say it in their own window, the demo
 * is seeded once, and the hub's remaining raw words are gone (source scan).
 *
 * Owner answers (2026-09-27): *"Keep the current cards"*, *"Yes — stay
 * deleted"*, *"(a) demo + (b) dialogs + (c) wording — all in one part"*,
 * *"Keep today: hub file tree"*. What these pins hold:
 *
 * - (b) the New Project, Rename Project and Import ZIP dialogs each read the
 *   pure verdict (`ProjectNameCheck.check`), draw the field's own error state,
 *   receive the operation's failure through `onFailed` (the ViewModel hands the
 *   same message to the snackbar AND the caller), take focus on open, and
 *   submit from the keyboard's Done;
 * - (b) the search field takes focus and offers the Search action;
 * - (b) the wizard's type rows are radio buttons to accessibility services;
 * - (c) the tree header prints the kind's word, not the raw config id, and the
 *   hub's hardcoded sentences are string resources;
 * - (a) `DemoProjects.ensure` is still called from the one list read (the
 *   seed path exists), and the once-per-install law is behavioural
 *   (`DemoProjectSeedTest`), not a comment.
 *
 * Nothing here pins a look: the cards, the ＋, the sheet and the chips are
 * untouched by this part (owner: keep the current cards).
 */
class HubDialogWiringTest {

    private val screenPath = "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    private val viewModelPath = "app/src/main/java/com/codeci/ide/ui/viewmodels/FileManagerViewModel.kt"

    private val screen = RepoFiles.mainSource(screenPath).readText()
    private val screenCode = RepoFiles.codeOnly(screen)
    private val viewModel = RepoFiles.mainSource(viewModelPath).readText()
    private val viewModelCode = RepoFiles.codeOnly(viewModel)

    /** The three dialogs, by the state that opens each. */
    private fun dialog(opener: String, next: String): String =
        screenCode.substringAfter(opener).substringBefore(next)

    private val createDialog get() = dialog("if (showCreateProject) {", "if (showCreateItem) {")
    private val zipDialog get() = dialog("if (showZipNameDialog) {", "if (showCloneDialog) {")
    private val renameDialog get() = dialog("renameProjectTarget?.let { project ->", "private fun projectNameSupportingText(")

    @Test
    fun `the three name dialogs read the pure verdict and draw the field's error state`() {
        for ((name, body) in mapOf("New Project" to createDialog, "Import ZIP" to zipDialog, "Rename" to renameDialog)) {
            assertTrue("$name must ask ProjectNameCheck", body.contains("ProjectNameCheck.check("))
            assertTrue("$name must draw isError from the verdict", body.contains("isError = verdict.showsError"))
            assertTrue("$name must say why under the field", body.contains("supportingText ="))
            assertTrue("$name must submit only a usable name", body.contains("enabled = verdict.ok"))
        }
    }

    @Test
    fun `the rename dialog exempts the project's own name and the zip dialog allows a taken one`() {
        assertTrue(renameDialog.contains("current = project.name"))
        assertTrue(zipDialog.contains("allowTaken = true"))
        assertTrue("the ZIP dialog says what the import will be called", zipDialog.contains("ProjectNameCheck.importedNameFor("))
        assertTrue(zipDialog.contains("R.string.project_name_will_import_as"))
    }

    @Test
    fun `a failure lands inside the open dialog, not only behind it`() {
        for ((name, body) in mapOf("New Project" to createDialog, "Import ZIP" to zipDialog, "Rename" to renameDialog)) {
            assertTrue("$name must pass onFailed to the ViewModel", body.contains("onFailed = {"))
            assertTrue("$name must render the error in its own text column", body.contains("MaterialTheme.colorScheme.error"))
        }
        // The ViewModel hands the same message to both surfaces.
        for (fn in listOf("fun createProject(", "fun renameProject(", "fun importZip(")) {
            val header = viewModelCode.substringAfter(fn).substringBefore(") {")
            assertTrue("$fn must accept onFailed", header.contains("onFailed: (String) -> Unit"))
        }
        assertTrue(viewModelCode.contains("onFailure: (String) -> Unit"))
        val runOperation = viewModelCode.substringAfter("private fun <T> runOperation(")
        assertTrue(runOperation.contains("_userMessage.value = message"))
        assertTrue(runOperation.contains("onFailure(message)"))
    }

    @Test
    fun `the name fields take focus and submit from the keyboard`() {
        for ((name, body) in mapOf("New Project" to createDialog, "Import ZIP" to zipDialog, "Rename" to renameDialog)) {
            assertTrue("$name must attach a FocusRequester", body.contains(".focusRequester(nameFocus)"))
            assertTrue("$name must request focus from inside the dialog", body.contains("nameFocus.requestFocus()"))
            assertTrue("$name must offer Done", body.contains("ImeAction.Done"))
            assertTrue("$name must submit on Done", body.contains("KeyboardActions(onDone ="))
        }
        // The request is made beside the field (a FocusRequester with no node
        // attached throws): every requestFocus sits after its focusRequester.
        for (body in listOf(createDialog, zipDialog, renameDialog)) {
            assertTrue(body.indexOf(".focusRequester(nameFocus)") < body.indexOf("nameFocus.requestFocus()"))
        }
    }

    @Test
    fun `the search field takes focus and offers the Search action`() {
        val search = screenCode.substringAfter("if (searchOpen) {").substringBefore("} else {")
        assertTrue(search.contains(".focusRequester(searchFocus)"))
        assertTrue(search.contains("ImeAction.Search"))
        assertTrue(search.contains("KeyboardActions(onSearch ="))
        assertTrue(search.contains("searchFocus.requestFocus()"))
        assertTrue(search.indexOf(".focusRequester(searchFocus)") < search.indexOf("searchFocus.requestFocus()"))
    }

    @Test
    fun `the wizard's type rows are radio buttons, in one group, with the glyphs kept`() {
        assertTrue(createDialog.contains("selectableGroup()"))
        assertTrue(createDialog.contains("role = Role.RadioButton"))
        assertTrue("the rows still select on tap", createDialog.contains("onClick = { selectedType = option.id }"))
        // The look is the owner's: the ●/○ glyphs stay (string literals are
        // blanked by codeOnly, so read the raw source here).
        val rawCreate = screen.substringAfter("if (showCreateProject) {").substringBefore("if (showCreateItem) {")
        assertTrue(rawCreate.contains("if (selected) \"●\" else \"○\""))
        assertFalse("no M3 RadioButton widget was added", createDialog.contains("RadioButton("))
    }

    @Test
    fun `the tree header names the kind, never the raw config id`() {
        val title = screenCode.substringAfter("title = {").substringBefore("navigationIcon = {")
        assertFalse("the raw config type must not be printed", title.contains("activeProject!!.config.type"))
        assertTrue(title.contains("hubKindLabel(activeProject!!, hubEntries)"))
        val helper = screenCode.substringAfter("private fun hubKindLabel(")
        assertTrue("the card's own word first", helper.contains("ProjectsHub.kindLabel(it.kind)"))
        assertTrue("then the wizard's label for the declared type", helper.contains("ProjectTypes.optionFor(project.config.type)?.label"))
    }

    @Test
    fun `the hub's sentences are resources now`() {
        val raw = screen
        for (gone in listOf(
            "\"Delete project \${project.name} and all its files?\"",
            "\"Delete \${node.relativePath}? This cannot be undone.\"",
            "\"In \${newItemParent}\"",
            "\"No entry configured\"",
            "\"Start with C, Python, or a web page",
            "joinToString(\"  >  \")",
        )) {
            assertFalse("still hardcoded: $gone", raw.contains(gone))
        }
        for (resource in listOf(
            "R.string.delete_project_confirm", "R.string.delete_item_confirm", "R.string.new_item_in",
            "R.string.project_entry_none", "R.string.no_projects_hint", "R.string.hub_no_search_match",
            "R.string.project_name_taken", "R.string.project_name_invalid_hint",
        )) {
            assertTrue("missing $resource", screenCode.contains(resource))
        }
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        for (name in listOf(
            "delete_project_confirm", "delete_item_confirm", "new_item_in", "project_entry_none",
            "hub_no_search_match", "project_name_invalid_hint", "project_name_will_import_as",
            "project_name_unchanged", "import_file_needs_project", "import_failed", "zip_import_failed",
            "export_done", "export_failed", "backup_failed", "backup_imported", "backup_import_failed",
            "delete_project_failed", "unknown_error",
        )) {
            assertTrue("strings.xml lacks $name", strings.contains("<string name=\"$name\">"))
        }
        // The three dead strings found on 2026-09-27 (zero readers) are gone.
        for (dead in listOf("hub_rename_project", "clone_from_github", "clone_fetch_branches")) {
            assertFalse("dead string $dead is back", strings.contains("name=\"$dead\""))
        }
        // …and the ViewModel's hub-flow messages read resources too. (The
        // "Export all projects" summary — a composed diagnostic with rename and
        // skip lists — is a Settings flow and stays as it was; out of 66.1.)
        for (gone in listOf("\"Project is no longer available\"", "\"Open a project before importing a file\"",
            "\"Import failed: ", "\"ZIP import failed: ", "\"Exported \${project.name}", "\"Export failed: ",
            "\"Backup failed: ", "\"Imported backup (", "\"Backup import failed: ", "\"Could not delete project\"",
            "\"unknown error\"")) {
            assertFalse("ViewModel still hardcodes $gone", viewModel.contains(gone))
        }
    }

    @Test
    fun `a search miss and a filter miss are told apart`() {
        val noMatch = screenCode.substringAfter("if (visible.isEmpty()) {").substringBefore("return@Column")
        assertTrue(noMatch.contains("R.string.hub_no_search_match"))
        assertTrue(noMatch.contains("R.string.hub_no_match"))
        assertTrue(noMatch.contains("searchQuery.isNotBlank()"))
    }

    @Test
    fun `destructive confirms wear the error role, and the badge amber is the palette's`() {
        assertEquals(
            "both Delete confirms (file/folder, project)",
            2,
            Regex("""colors = destructiveTextButtonColors\(\)""").findAll(screenCode).count()
        )
        assertTrue(screenCode.contains("ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)"))
        assertTrue(screenCode.contains("HubBadgeYellow = Color(CodecPalette.WARNING)"))
        assertFalse(screenCode.contains("0xFFE6B33C"))
    }

    @Test
    fun `the demo seed path still exists, once, from the list read`() {
        val manager = RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects/ProjectManager.kt").readText()
        )
        assertEquals(1, Regex("""DemoProjects\.ensure\(""").findAll(manager).count())
        val demo = RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects/DemoProjects.kt").readText()
        )
        val ensure = demo.substringAfter("fun ensure(").substringBefore("private fun writeProject(")
        assertTrue("the marker gates the seed", ensure.contains("if (marker.exists()) return null"))
        assertTrue("the marker is written after a complete seed", ensure.indexOf("writeProject(project)") < ensure.indexOf("marker.writeText(MARKER_TEXT)\n            project"))
    }

    @Test
    fun `the after-Create landing is unchanged - the hub's own tree`() {
        // Owner: "Keep today: hub file tree". The wizard's success path still
        // hands the project to onProjectSelected and nothing else (no
        // onProjectFileSelected — that would open the editor).
        val success = createDialog.substringAfter("onCreated = { project ->").substringBefore("}")
        assertTrue(success.contains("onProjectSelected(project)"))
        assertFalse(success.contains("onProjectFileSelected("))
        val vm = viewModelCode.substringAfter("fun createProject(").substringBefore("fun renameProject(")
        assertTrue("the ViewModel still opens the created project's tree", vm.contains("_activeProject.value = project"))
    }
}
