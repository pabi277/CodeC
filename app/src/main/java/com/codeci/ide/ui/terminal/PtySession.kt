package com.codeci.ide.ui.terminal

import android.system.Os
import com.codeci.ide.ui.utils.AppLogger
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor

/**
 * An open PTY master plus the child process whose stdio is the slave.
 *
 * Prefer the JNI path (`openpty` + `fork`/`exec` + `TIOCSWINSZ`). The
 * posix_openpt fallback is used in tests and when the .so did not load.
 */
class PtySession private constructor(
    private val masterFd: Int,
    private val masterJavaFd: FileDescriptor?,
    val pid: Int,
    private val useNative: Boolean
) : Closeable {

    @Volatile
    var closed: Boolean = false
        private set

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        if (closed) return -1
        return if (useNative) {
            PtyNative.write(masterFd, bytes, offset, length)
        } else {
            try {
                Os.write(masterJavaFd, bytes, offset, length)
            } catch (e: Exception) {
                AppLogger.e("PtySession", "write failed", e)
                -1
            }
        }
    }

    fun write(text: String): Int {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return write(bytes, 0, bytes.size)
    }

    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int {
        if (closed) return -1
        return if (useNative) {
            PtyNative.read(masterFd, buffer, offset, length)
        } else {
            try {
                Os.read(masterJavaFd, buffer, offset, length)
            } catch (e: Exception) {
                AppLogger.e("PtySession", "read failed", e)
                -1
            }
        }
    }

    fun setWindowSize(rows: Int, cols: Int) {
        if (closed) return
        PtyNative.setWindowSize(masterFd, rows, cols, pid)
    }

    fun pollExit(): Int = PtyNative.waitPid(pid, hang = false)

    override fun close() {
        if (closed) return
        closed = true
        try {
            PtyNative.kill(pid, PtyNative.SIGHUP)
        } catch (_: Exception) {
        }
        try {
            PtyNative.FallbackProcesses.destroy(pid)
        } catch (_: Exception) {
        }
        try {
            if (useNative) {
                PtyNative.closeFd(masterFd)
            } else if (masterJavaFd != null) {
                Os.close(masterJavaFd)
            }
        } catch (_: Exception) {
        }
        try {
            PtyNative.kill(pid, PtyNative.SIGKILL)
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * Opens a PTY and execs [file] with [args] / [env] / [cwd].
         * [args] must include argv[0].
         */
        fun start(
            file: String,
            args: Array<String>,
            env: Array<String>,
            cwd: String?
        ): PtySession {
            val opened = PtyNative.openPty()
            val useNative = PtyNative.nativeAvailable && opened.slavePath == null
            val pid = PtyNative.spawn(
                masterFd = opened.masterFd,
                slaveFd = opened.slaveFd,
                file = file,
                args = args,
                envp = env,
                cwd = cwd
            )
            if (pid == 0 || (pid < 0 && useNative)) {
                PtyNative.closeFd(opened.masterFd)
                PtyNative.closeFd(opened.slaveFd)
                throw IllegalStateException("Failed to spawn $file")
            }
            // Parent no longer needs the slave (native spawn already closed it;
            // the fallback still holds it — close so only the child uses it).
            if (!useNative) {
                PtyNative.closeFd(opened.slaveFd)
            }
            val javaFd = if (useNative) null else try {
                val pfd = android.os.ParcelFileDescriptor.adoptFd(opened.masterFd)
                // Keep the wrapper alive for the session lifetime via the fd object.
                pfd.fileDescriptor
            } catch (_: Exception) {
                null
            }
            AppLogger.i(
                "PtySession",
                "started $file pid=$pid native=$useNative cwd=$cwd"
            )
            return PtySession(
                masterFd = opened.masterFd,
                masterJavaFd = javaFd,
                pid = pid,
                useNative = useNative
            )
        }

        fun startShell(prepared: PreparedShell): PtySession {
            val shell = if (prepared.shell.exists()) {
                prepared.shell.absolutePath
            } else {
                "/system/bin/sh"
            }
            val profile = File(ShellEnvironment.etcDir(prepared.prefix), "profile")
            val launch = if (profile.exists()) {
                ". ${TerminalHandoff.shellEscape(profile.absolutePath)}; " +
                    "exec ${TerminalHandoff.shellEscape(shell)} -i"
            } else {
                "exec ${TerminalHandoff.shellEscape(shell)} -i"
            }
            val cwd = prepared.cwd.takeIf { it.exists() }?.absolutePath
                ?: prepared.home.absolutePath
            return start(
                file = "/system/bin/sh",
                args = arrayOf("sh", "-c", launch),
                env = ShellEnvironment.envToArray(prepared.env),
                cwd = cwd
            )
        }
    }
}
