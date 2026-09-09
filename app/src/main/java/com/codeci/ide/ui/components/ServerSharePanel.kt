package com.codeci.ide.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.ui.services.QrModules
import com.codeci.ide.ui.services.ServerEndpoints
import com.codeci.ide.ui.services.ServerEntry

private const val QR_DARK = 0xFF000000.toInt()
private const val QR_LIGHT = 0xFFFFFFFF.toInt()

/**
 * Phase 37.1 — the share row of a running server, in the owner's words: "use a
 * device as a server and run our files at localhost".
 *
 * It shows the TWO addresses the spec asks for and never more than that:
 * `http://127.0.0.1:<port>` for the phone's own preview (unchanged behaviour),
 * and `http://<lan-ip>:<port>` for other devices, with a copy button and a QR
 * code so a second device can join by scanning. The LAN row is honest about
 * why it may be empty — no Wi-Fi address, or a server that bound to loopback —
 * because a dead URL on a phone is worse than no URL.
 *
 * The panel is stateless about the server: it renders a [ServerEndpoints] value
 * and reports taps, so the Output Panel and the Web Preview can both show it
 * from the same source (the [com.codeci.ide.ui.services.ServerHost] registry).
 */
@Composable
fun ServerSharePanel(
    endpoints: ServerEndpoints?,
    lanShared: Boolean,
    onToggleLan: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    servers: List<ServerEntry> = emptyList(),
    onOpenUrl: (String) -> Unit = {},
    onStopAll: () -> Unit = {},
    dense: Boolean = false,
    /**
     * False renders the LAN row read-only: a surface that does not own the
     * server must not pretend its switch can rebind it (the Web Preview shows
     * the switch for its own static server only).
     */
    showSwitch: Boolean = true
) {
    val context = LocalContext.current
    var showQr by remember(endpoints?.port, endpoints?.lanShared) { mutableStateOf(false) }
    val urls = endpoints ?: return
    val notice = urls.notice()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1F24))
            .padding(horizontal = 12.dp, vertical = if (dense) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (dense) 1.dp else 4.dp)
    ) {
        AddressRow(
            label = "On this phone",
            url = urls.loopbackUrl,
            accent = Color(0xFF8A8A8A),
            onCopy = { context.copyToClipboard("CodeC URL", urls.loopbackUrl) },
            onOpen = { onOpenUrl(urls.loopbackUrl) }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Other devices",
                color = if (urls.hasLan()) Color(0xFF55FF55) else Color(0xFF8A8A8A),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(96.dp)
            )
            Text(
                text = urls.lanUrl ?: "not shared",
                style = monoStyle(),
                color = if (urls.hasLan()) Color(0xFFD6FFD6) else Color(0xFF777777),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (urls.lanUrl != null) {
                            Modifier.clickable {
                                context.copyToClipboard("CodeC URL", urls.lanUrl)
                            }
                        } else {
                            Modifier
                        }
                    )
            )
            if (urls.lanUrl != null) {
                IconButton(
                    onClick = { context.copyToClipboard("CodeC URL", urls.lanUrl) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy LAN URL",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { showQr = !showQr },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = "Show QR code",
                        tint = if (showQr) Color(0xFF66B2FF) else Color(0xFF55FF55),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (showSwitch) {
                Switch(checked = lanShared, onCheckedChange = onToggleLan)
            }
        }
        notice?.let {
            Text(
                text = it,
                color = Color(0xFFFFB347),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showQr && urls.lanUrl != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCodeImage(text = urls.lanUrl, size = if (dense) 108.dp else 148.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = com.codeci.ide.ui.services.QrCode.hint(urls.lanUrl),
                    color = Color(0xFF9AA0A6),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 4
                )
            }
        }
        if (servers.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = servers.joinToString("   ") { it.describe() },
                    color = Color(0xFF8A8A8A),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onStopAll, modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "STOP ALL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF5555)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressRow(
    label: String,
    url: String,
    accent: Color,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = url,
            style = monoStyle(),
            color = Color(0xFFD0D0D0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { onCopy() }
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy URL",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Visibility,
                contentDescription = "Open URL",
                tint = Color(0xFF55FF55),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** The QR itself, drawn from the module grid ZXing produced (no image library). */
@Composable
private fun QrCodeImage(text: String, size: Dp) {
    val modules = remember(text) { com.codeci.ide.ui.services.QrCode.encode(text) }
    val bitmap = remember(modules) { modules?.toBitmap() }
    if (bitmap == null) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "QR unavailable",
                color = Color(0xFF8A8A8A),
                style = MaterialTheme.typography.labelSmall
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(6.dp))
                .padding(6.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR code for the LAN URL",
                modifier = Modifier.size(size - 12.dp)
            )
        }
    }
}

private fun monoStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp
)

/** Modules → ARGB pixels. `Bitmap.createBitmap` takes the array row-major as-is. */
private fun QrModules.toBitmap(): Bitmap? {
    if (size <= 0) return null
    val pixels = IntArray(size * size) { index -> if (dark[index]) QR_DARK else QR_LIGHT }
    return runCatching { Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888) }.getOrNull()
}

/** Clipboard write with the toast the rest of the app uses (Phase 11 pattern). */
private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copied $text", Toast.LENGTH_SHORT).show()
}
