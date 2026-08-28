# CodeC Phase 8 — Projects & File Tree (Keystone Architecture)

**Status:** Implementation started 2026-08-28 · device acceptance pending · **Cost:** `[client-only]` · **Depends on:** Phase 7 (Multi-Terminal)
**Keystone Role:** Phase 8 is the foundational keystone for Phase 9 (Editor), Phase 11 (Output Panel & Run), Phase 12 (Multi-language), and Phase 13 (GitHub).

---

## 1. Context & Motivation

In current versions of CodeC, `FileManagerScreen.kt` is a flat file list in a single directory without hierarchical folder support. Projects cannot have subdirectories (e.g. `src/`, `include/`, `assets/`), cannot easily be imported/exported from device storage, and the terminal's working directory (`cwd`) is not synchronized with the active project.

Phase 8 creates a real hierarchical Project Workspace model:
1. **Hierarchical File Tree:** Expandable/collapsible directories, breadcrumbs, nested file creation, rename, move, and delete.
2. **Project Model & Run Configuration (`.codec/project.json`):** Standard metadata defining build commands, run commands, and compiler flags per project.
3. **SAF Import / Export:** Native Android Storage Access Framework (SAF) document tree picker for importing existing codebases and exporting projects as ZIP archives.
4. **Terminal & Editor Synchronization:** Selecting a project synchronizes the terminal session's working directory (`cd <project_dir>`) and editor workspace.

---

## 2. Architectural Design (Decision D1)

### 2.1 Project Workspace Structure
Projects reside in the app-private executable storage (`$HOME/projects/` or `filesDir/CodeC/projects/<projectName>/`):
```text
my_c_project/
├── .codec/
│   └── project.json
├── include/
│   └── utils.h
├── src/
│   ├── main.c
│   └── utils.c
├── Makefile
└── README.md
```

### 2.2 Project Configuration Schema (`project.json`)
```json
{
  "version": 1,
  "name": "my_c_project",
  "type": "c",
  "entry": "src/main.c",
  "build": "cc -I include src/main.c src/utils.c -o bin/app",
  "run": "./bin/app",
  "clean": "rm -rf bin/app"
}
```

### 2.3 Hierarchical Tree Data Model
```kotlin
sealed class FileNode(val file: File, val depth: Int) {
    data class DirectoryNode(
        val dir: File,
        val depth: Int,
        val isExpanded: Boolean = false,
        val children: List<FileNode> = emptyList()
    ) : FileNode(dir, depth)

    data class FileLeaf(
        val fileItem: File,
        val depth: Int,
        val extension: String,
        val sizeBytes: Long
    ) : FileNode(fileItem, depth)
}
```

### 2.4 SAF Import & Export Architecture
- **Import Folder (`ACTION_OPEN_DOCUMENT_TREE`):** Recursively reads DocumentFile tree and copies files into `$HOME/projects/<imported_name>`.
- **Export ZIP (`ACTION_CREATE_DOCUMENT`):** Compresses project folder into a `.zip` stream and writes directly to user's chosen location (e.g. `Downloads/`).
- **Import Single File (`ACTION_OPEN_DOCUMENT`):** Imports `.c`, `.h`, `.txt`, `.py`, etc. into current folder.

### 2.5 Terminal CWD & Editor Integration
- When opening a project, `TerminalViewModel` / `TerminalSessionManager` receives `setProjectCwd(projectDir)`.
- If an active terminal exists, it dispatches `cd "$PROJECT_DIR"` into the shell.
- Editor displays breadcrumb path (e.g., `my_c_project > src > main.c`) and remembers active open files.

---

## 3. Implementation Steps

1. **Step 1:** Create `ProjectManager.kt` and `FileTreeRepository.kt` in `app/src/main/java/com/codeci/ide/ui/projects/`.
2. **Step 2:** Refactor `FileManagerScreen.kt` to render a tree view with directory expand/collapse, icons by extension, and contextual options (New File, New Folder, Rename, Delete, Export ZIP).
3. **Step 3:** Implement SAF file & folder picker launcher contracts in `MainActivity.kt` and `FileManagerScreen.kt`.
4. **Step 4:** Implement `.codec/project.json` parser, generator, and default config builder.
5. **Step 5:** Wire project selection with `TerminalViewModel` (`cd`) and `EditorScreen` (breadcrumbs).
6. **Step 6:** Write unit tests for recursive tree parsing, SAF copy streaming, and ZIP pack/unpack.

---

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following checklist on a real device:

```sh
# Setup & Folder Tree Test
# 1. Open Files tab -> Tap "+ New Project" -> Name "calculator".
# 2. Inside "calculator", create subfolder "src" and "include".
# 3. Create "include/calc.h" and "src/calc.c", and "src/main.c".
# 4. Open "src/main.c" in Editor -> verify breadcrumb shows "calculator > src > main.c".
# 5. Open Terminal -> run `pwd` -> verify output is ".../calculator".
# 6. Run: cc -I include src/main.c src/calc.c -o a.out && ./a.out -> verify output.
# 7. In Files tab, tap "Export as ZIP" -> select Downloads in SAF -> verify calculator.zip created.
# 8. In Files tab, tap "Import ZIP" -> import calculator.zip as "calculator_copy" -> verify file tree identical.
# PASS
```

---

## 5. Non-Goals & Invariants

- **Invariants:** Projects must reside on app-private storage (`filesDir`) so compiled binaries retain executable (`chmod +x`) permissions without `noexec` blocks.
- **Not in Phase 8:** Editor split output panel (Phase 11), Git remote sync (Phase 13).
