package com.codeci.ide.ui.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.codeci.ide.ui.utils.AppLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bridge to the Termux app (https://termux.dev) via its RUN_COMMAND intent.
 *
 * Why Termux: Android 10+ denies execve() of downloaded binaries in an app's
 * own storage for apps targeting API 29+ (W^X policy). Termux sidesteps this
 * by targeting API 28, so binaries inside Termux run fine. When Android
 * blocks CodeC's bundled Clang, compiling/running through Termux's Clang is
 * the reliable fallback on real phones — and the only option on x86_64
 * emulators (Termux ships x86_64 packages too).
 *
 * One-time setup for the user (also shown in Settings -> Compiler Engine):
 *  1. Install Termux 0.109+ from F-Droid or GitHub Releases (the Play Store
 *     version is outdated and does not support this API).
 *  2. In Termux run:
 *         echo "allow-external-apps=true" >> ~/.termux/termux.properties
 *         termux-reload-settings
 *  3. Grant CodeC the "Run commands in Termux environment" permission:
 *     Android Settings -> Apps -> CodeC IDE -> Permissions -> Additional
 *     permissions.
 *
 * Communication protocol (from TermuxConstants.java, MIT):
 *  - We start com.termux.app.RunCommandService with action
 *    com.termux.RUN_COMMAND and a PendingIntent.
 *  - Termux runs the command in the background and replies through the
 *    pending intent with a Bundle under the "result" key containing
 *    "stdout", "stderr", "exitCode", "err", "errmsg".
 */
object TermuxCompiler {

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val PROGRAM_RELATIVE_PATH = ".codec/program"

    /** "com.termux.permission.RUN_COMMAND" — must be declared + user-granted. */
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    // RUN_COMMAND intent constants (exact values from TermuxConstants.java).
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    // Result extras (TermuxConstants.TERMUX_SERVICE).
    const val EXTRA_RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    private const val RUNNER_APP_SHELL = "app-shell"
    // "$PREFIX/" is expanded by Termux itself (see RUN_COMMAND docs).
    private const val BASH_PATH = "\$PREFIX/bin/bash"

    private val requestCounter = AtomicInteger(1000)

    data class TermuxResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int?,
        val internalErr: Int?,
        val internalErrMsg: String?,
        val timedOut: Boolean
    ) {
        /** Non-null when Termux itself failed before/while running the command. */
        val internalFailure: String?
            get() = if (internalErr != null && internalErr != -1) {
                internalErrMsg ?: "Termux internal error ($internalErr)"
            } else null
    }

    fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isRunCommandPermissionGranted(context: Context): Boolean = try {
        context.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun absoluteProgramPath(): String = "$TERMUX_HOME/$PROGRAM_RELATIVE_PATH"

    fun openTermux(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            AppLogger.e("Termux", "Could not open Termux", e)
        }
    }

    /**
     * Bash script that writes the C source to Termux home and compiles it with
     * Termux's Clang. When [sourceBase64] is set, the source is embedded in the
     * script (base64 — safe inside single quotes) which works on every Termux
     * version; otherwise the script reads the source from stdin. Pure function
     * for testability.
     */
    fun buildCompileScript(
        standard: String,
        warnings: Boolean,
        optimization: Int,
        sourceBase64: String? = null
    ): String {
        val std = standard.lowercase().removePrefix("c").let { "c$it" }
        val opt = optimization.coerceIn(0, 3)
        val warnFlag = if (warnings) " -Wall -Wextra" else ""
        return buildString {
            append("set -e; ")
            append("D=\"\$HOME/.codec\"; mkdir -p \"\$D\"; ")
            if (sourceBase64 != null) {
                append("printf '%s' '$sourceBase64' | base64 -d > \"\$D/source.c\"; ")
            } else {
                append("cat > \"\$D/source.c\"; ")
            }
            append("exec clang \"\$D/source.c\" -o \"\$D/program\" -std=$std$warnFlag -O$opt")
        }
    }

    /** Bash script that runs the program previously compiled by [buildCompileScript]. */
    fun buildRunScript(): String = "exec \"\$HOME/.codec/program\""

    fun buildCleanupScript(): String = "rm -f \"\$HOME/.codec/source.c\" \"\$HOME/.codec/program\""

    fun buildKillScript(): String = "pkill -f \"\$HOME/.codec/program\" 2>/dev/null || true"

    /**
     * Runs [commandPath] (use the `$PREFIX/` prefix for Termux paths) with
     * [arguments] inside Termux in the background and blocks until the result
     * comes back or [timeoutSeconds] elapses. [stdin] is piped to the command
     * when non-null. Must be called from a background thread.
     */
    fun runCommand(
        context: Context,
        commandPath: String = BASH_PATH,
        arguments: List<String>,
        workdir: String = TERMUX_HOME,
        stdin: String? = null,
        label: String = "CodeC command",
        timeoutSeconds: Long
    ): TermuxResult {
        val requestCode = requestCounter.incrementAndGet()
        val action = "com.codeci.ide.termux.result.$requestCode"
        val latch = CountDownLatch(1)
        // AtomicReference: the receiver (main thread) writes, the waiter
        // (IO thread) reads; the latch adds the happens-before edge.
        val received = AtomicReference<Intent?>(null)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                received.set(intent)
                latch.countDown()
            }
        }

        val filter = IntentFilter(action)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            AppLogger.e("Termux", "Could not register result receiver", e)
            return TermuxResult("", "", null, -5, "Internal receiver error", timedOut = false)
        }

        return try {
            val resultIntent = Intent(action)
            val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                })
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, resultIntent, pendingFlags)

            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_COMMAND_PATH, commandPath)
                putExtra(EXTRA_ARGUMENTS, arguments.toTypedArray())
                putExtra(EXTRA_WORKDIR, workdir)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_RUNNER, RUNNER_APP_SHELL)
                putExtra(EXTRA_COMMAND_LABEL, label)
                putExtra(EXTRA_COMMAND_DESCRIPTION, "Run by CodeC IDE")
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
                if (stdin != null) putExtra(EXTRA_STDIN, stdin)
            }
            context.startService(intent)

            val finished = latch.await(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                // Best-effort kill of a runaway compile/run in Termux.
                fireAndForget(context, listOf("-c", buildKillScript()), label = "CodeC kill")
                TermuxResult(
                    stdout = "",
                    stderr = "",
                    exitCode = null,
                    internalErr = -1,
                    internalErrMsg = "Timed out after ${timeoutSeconds}s",
                    timedOut = true
                )
            } else {
                val bundle: Bundle? = received.get()?.getBundleExtra(EXTRA_RESULT_BUNDLE)
                if (bundle == null) {
                    TermuxResult(
                        stdout = "",
                        stderr = "",
                        exitCode = null,
                        internalErr = -2,
                        internalErrMsg = "No result from Termux. Check that \"allow-external-apps=true\" is set in Termux (see Settings -> Compiler Engine).",
                        timedOut = false
                    )
                } else {
                    TermuxResult(
                        stdout = bundle.getString(RESULT_STDOUT) ?: "",
                        stderr = bundle.getString(RESULT_STDERR) ?: "",
                        exitCode = if (bundle.containsKey(RESULT_EXIT_CODE)) {
                            bundle.getInt(RESULT_EXIT_CODE)
                        } else {
                            null
                        },
                        internalErr = if (bundle.containsKey(RESULT_ERR)) {
                            bundle.getInt(RESULT_ERR)
                        } else {
                            null
                        },
                        internalErrMsg = bundle.getString(RESULT_ERRMSG),
                        timedOut = false
                    )
                }
            }
        } catch (e: SecurityException) {
            AppLogger.e(
                "Termux",
                "RUN_COMMAND denied — grant CodeC the 'Run commands in Termux environment' permission",
                e
            )
            TermuxResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                internalErr = -3,
                internalErrMsg = "Termux denied the request. Grant CodeC the \"Run commands in Termux environment\" permission (Android Settings -> Apps -> CodeC IDE -> Permissions).",
                timedOut = false
            )
        } catch (e: Exception) {
            AppLogger.e("Termux", "RUN_COMMAND failed", e)
            TermuxResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                internalErr = -4,
                internalErrMsg = "Could not start the Termux command: ${e.message}",
                timedOut = false
            )
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    private fun fireAndForget(context: Context, arguments: List<String>, label: String) {
        try {
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_COMMAND_PATH, BASH_PATH)
                putExtra(EXTRA_ARGUMENTS, arguments.toTypedArray())
                putExtra(EXTRA_WORKDIR, TERMUX_HOME)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_RUNNER, RUNNER_APP_SHELL)
                putExtra(EXTRA_COMMAND_LABEL, label)
            }
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}
