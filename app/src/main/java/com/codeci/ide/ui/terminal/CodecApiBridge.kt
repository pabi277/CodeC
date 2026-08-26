package com.codeci.ide.ui.terminal

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snapshot of the Android clipboard, kept android-free so it is host-testable. */
sealed interface ClipboardContent {
    /** No clip is present. */
    data object Empty : ClipboardContent

    /** A text clip (plain text, HTML or URI list coerced to text). */
    data class Text(val value: String) : ClipboardContent

    /** A clip with no text representation (e.g. an image). */
    data object NonText : ClipboardContent
}

/**
 * Handles [CodecApiProtocol] requests emitted by terminal programs:
 *
 *  1. [handle] is called by the UI layer with the raw OSC payload;
 *  2. the payload is parsed and both file paths are confinement-checked
 *     against `$PREFIX/tmp/codec-api`;
 *  3. the capability runs (clipboard via [ClipboardManager]);
 *  4. the outcome is written atomically into the response file.
 *
 * The app never executes anything from the payload; the only side effects
 * are a clipboard write (which Android surfaces with the standard system
 * clipboard notice) and writes inside the private API directory.
 */
object CodecApiBridge {

    /**
     * @return true when the payload was a recognized CodeCApi request
     * (regardless of whether the operation itself succeeded), false when it
     * should be silently ignored.
     */
    suspend fun handle(context: Context, payload: String, apiDir: File): Boolean =
        withContext(Dispatchers.IO) {
            val request = CodecApiProtocol.parse(payload) ?: return@withContext false
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val response = if (clipboardManager == null) {
                "${CodecApiProtocol.ERR_PREFIX}clipboard service unavailable"
            } else {
                execute(
                    request = request,
                    apiDir = apiDir,
                    readClipboard = { readClipboard(clipboardManager, context) },
                    writeClipboard = { text ->
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText("CodeC clipboard", text)
                        )
                    }
                )
            }
            deliver(request, apiDir, response)
            true
        }

    /**
     * Pure, host-testable core: validates paths, runs the capability through
     * the injected clipboard functions and returns the response body.
     *
     * Response conventions (see [PART_4_7_ANDROID_INTEGRATION.md]):
     *  - `clipboard.get`  → the raw clipboard text ("" when the clipboard is
     *    empty, since the CLI waits for file existence, not content);
     *  - `clipboard.set`/`clipboard.clear` → `OK`;
     *  - `clipboard.status` → human-readable two lines;
     *  - any failure → `ERR:<message>`.
     */
    fun execute(
        request: CodecApiProtocol.Request,
        apiDir: File,
        readClipboard: () -> ClipboardContent,
        writeClipboard: (String) -> Unit
    ): String {
        if (!CodecApiProtocol.isConfinedDirectChild(request.responseFile, apiDir)) {
            return "${CodecApiProtocol.ERR_PREFIX}response path escapes the CodeC API directory"
        }
        val requestFile = request.requestFile
        if (requestFile != null && !CodecApiProtocol.isConfinedDirectChild(requestFile, apiDir)) {
            return "${CodecApiProtocol.ERR_PREFIX}request path escapes the CodeC API directory"
        }

        return when (request.op) {
            CodecApiProtocol.Op.CLIPBOARD_GET -> when (val clip = readClipboard()) {
                is ClipboardContent.Empty -> ""
                is ClipboardContent.Text -> clip.value
                ClipboardContent.NonText ->
                    "${CodecApiProtocol.ERR_PREFIX}clipboard does not contain text"
            }

            CodecApiProtocol.Op.CLIPBOARD_SET -> {
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    if (file.length() > CodecApiProtocol.MAX_SET_BYTES.toLong()) {
                        "${CodecApiProtocol.ERR_PREFIX}clipboard content too large " +
                            "(max ${CodecApiProtocol.MAX_SET_BYTES} bytes)"
                    } else {
                        val text = try {
                            file.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            AppLogger.e("CodecApiBridge", "cannot read clipboard request", e)
                            return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                        }
                        writeClipboard(text)
                        "OK"
                    }
                }
            }

            CodecApiProtocol.Op.CLIPBOARD_CLEAR -> {
                writeClipboard("")
                "OK"
            }

            CodecApiProtocol.Op.CLIPBOARD_STATUS -> when (val clip = readClipboard()) {
                is ClipboardContent.Empty -> "clipboard: empty\nlength: 0"
                is ClipboardContent.Text -> "clipboard: text\nlength: ${clip.value.length}"
                ClipboardContent.NonText -> "clipboard: non-text\nlength: 0"
            }
        }
    }

    /** Atomically writes [response] into the validated response file. */
    private fun deliver(
        request: CodecApiProtocol.Request,
        apiDir: File,
        response: String
    ) {
        if (!CodecApiProtocol.isConfinedDirectChild(request.responseFile, apiDir)) {
            AppLogger.w(
                "CodecApiBridge",
                "refusing to write outside ${CodecApiProtocol.API_DIR_NAME}: ${request.responseFile}"
            )
            return
        }
        val target = File(request.responseFile)
        val partial = File(target.parentFile, "${target.name}.partial")
        try {
            partial.writeText(response)
            if (!partial.renameTo(target)) {
                // Some filesystems refuse rename over an existing file.
                target.writeText(response)
                partial.delete()
            }
        } catch (e: Exception) {
            AppLogger.e("CodecApiBridge", "cannot write clipboard response", e)
        }
    }

    private fun readClipboard(clipboardManager: ClipboardManager, context: Context): ClipboardContent {
        val clip = clipboardManager.primaryClip ?: return ClipboardContent.Empty
        if (clip.itemCount == 0) return ClipboardContent.Empty
        val description = clip.description
        val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
        if (!isText) return ClipboardContent.NonText
        val text = try {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } catch (e: Exception) {
            AppLogger.e("CodecApiBridge", "cannot coerce clipboard item", e)
            null
        }
        return if (text == null) ClipboardContent.Empty else ClipboardContent.Text(text)
    }
}
