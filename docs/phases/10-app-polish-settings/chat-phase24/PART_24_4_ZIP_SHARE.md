# CodeC Phase 24.4 — Project ZIP Share (Export + Share Intent)

**Status:** ✅ **IMPLEMENTED & DEVICE-PASSED** (2026-09-04) · **Cost:** `[client-only]` · **Effort:** XS
· **Depends on:** Phase 8 (`ProjectTransfer.exportZip` already exists)
· **Target files:** `ui/screens/FileManagerScreen.kt` or `ui/screens/ProjectsHub.kt`
  (overflow menu), `ui/projects/ProjectTransfer.kt`

---

## 1. Design

`ProjectTransfer.exportZip()` already creates a ZIP of the project. Today it
saves the ZIP via Android SAF (CreateDocument picker). Add a **Share** option
that, instead of saving to disk, fires an `ACTION_SEND` intent with the ZIP's
`FileProvider` URI — letting the user send the project to Telegram, Gmail,
Google Drive, etc. in one tap.

### Share flow

```
Projects card → ⋮ → "Share as ZIP"
    └─ ProjectTransfer.exportZipToCache(projectRoot)  (new: write to app cache dir)
    └─ FileProvider.getUriForFile(context, "${packageName}.fileprovider", zipFile)
    └─ startActivity(Intent(ACTION_SEND).apply {
           type = "application/zip"
           putExtra(Intent.EXTRA_STREAM, uri)
           addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
       })
```

The ZIP is written to `context.cacheDir` (not permanent; Android cleans it up).
No SAF picker is shown — the share sheet opens directly.

### `FileProvider` entry

Check if `FileProvider` already has a `cache-path` entry (Phase 18 camera
uses `files-path`). If not, add:
```xml
<cache-path name="shared_zips" path="." />
```
in `file_paths.xml` (the existing provider paths file).

---

## 2. Implementation steps

1. Add `exportZipToCache(root: File, context: Context): File` to
   `ProjectTransfer` (writes to `context.cacheDir/shares/<name>.zip`).
2. Wire **"Share as ZIP"** in the Projects card `⋮` overflow and the
   file manager project overflow.
3. Add `cache-path` to `file_paths.xml` if needed.
4. Write a host unit test — `exportZipToCache` produces a ZIP with the
   expected files (mock the output dir).

---

## 3. Exit condition

```text
1. In Projects, tap ⋮ on any project → "Share as ZIP".
   EXPECT: Android share sheet opens with the project ZIP.
2. Share to Files/Drive: EXPECT: the ZIP is received and extractable.
3. No SAF picker appears (this is "Share", not "Export to disk").
PASS = steps 1–3 behave as described.
```
