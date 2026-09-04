# CodeC Phase 24.7 — "Open with CodeC" Intent Filter

**Status:** ✅ **IMPLEMENTED & DEVICE-PASSED** (2026-09-04) · **Cost:** `[client-only]` · **Effort:** XS
· **Depends on:** Phase 8 (Import File / Import ZIP flows already exist)
· **Target files:** `AndroidManifest.xml`, `MainActivity.kt`

---

## 1. Design

Declare intent filters so CodeC appears in the Android share sheet for
common source-code and ZIP files. When the user shares a file to CodeC,
it opens the existing Import flow.

### Manifest additions

```xml
<!-- In MainActivity's <activity> block -->

<!-- "Open with" for text/source files -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/*" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/x-python" />
</intent-filter>

<!-- "Share" / "Open with" for ZIP files (Import ZIP flow) -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/zip" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/zip" />
</intent-filter>
```

### `MainActivity.onCreate` intent handling

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...existing setup...
    handleIncomingIntent(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingIntent(intent)
}

private fun handleIncomingIntent(intent: Intent) {
    val uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    } ?: return

    val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
    when {
        mimeType == "application/zip" -> importZipFromUri(uri)
        mimeType.startsWith("text/")  -> importFileFromUri(uri)
        else                          -> importFileFromUri(uri)  // try as text
    }
}
```

`importZipFromUri` and `importFileFromUri` reuse the existing Phase 8 import
flows (`ProjectTransfer` / file copy into project).

---

## 2. Implementation steps

1. Add intent filters to `AndroidManifest.xml`.
2. Add `handleIncomingIntent` to `MainActivity`.
3. Wire to Phase 8 import flows.
4. Test: share a `.py` file from Files → "Open with CodeC" → verify it opens
   in the editor.

---

## 3. Exit condition

```text
1. In Files/Downloads, long-press a .c file → Share → "Open with CodeC".
   EXPECT: CodeC opens and imports the file.
2. Share a .zip → "Open with CodeC".
   EXPECT: Import ZIP flow starts.
3. Share a .py file from another app → "Open with CodeC".
   EXPECT: file opens in the editor.
PASS = steps 1–3 behave as described.
```
