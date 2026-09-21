package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 52.2 — loading surfaces keep their own shape instead of going blank. */
class SkeletonCoverageTest {
    private val fileTree = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    ).readText()
    private val packages = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt"
    ).readText()
    private val git = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/GitControlView.kt"
    ).readText()

    @Test fun `file tree has a distinct loading branch`() {
        assertTrue(fileTree.contains("treeLoading && tree.isEmpty()"))
        assertTrue(fileTree.contains("SkeletonFileTreeRow()"))
    }

    @Test fun `package list has a stable six-row loading branch`() {
        assertTrue(packages.contains("if (!packageListReady)"))
        assertTrue(packages.contains("count = PackageCatalog.ALL_PACKAGES.size"))
        assertTrue(packages.contains("SkeletonPackageRow()"))
    }

    @Test fun `source control uses a git-shaped loading branch`() {
        assertTrue(git.contains("state.loading || state.busy"))
        assertTrue(git.contains("repeat(4) { SkeletonGitRow() }"))
    }
}
