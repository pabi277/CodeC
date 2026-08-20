package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.utils.AppLogger
import java.io.Closeable
import java.io.File

/**
 * An open PTY master plus the child process whose stdio is the slave.
 * Requires `libcodec-pty.so` (JNI openpty / fork / exec / TIOCSWINSZ).
 */
class PtySession private constructor(
    private val masterFd: Int,
    val pid: Int
) : Closeable {

    @Volatile
    var closed: Boolean = false
        private set

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        if (closed) return -1
        return PtyNative.write(masterFd, bytes, offset, length)
    }

    fun write(text: String): Int {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return write(bytes, 0, bytes.size)
    }

    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int {
        if (closed) return -1
        return PtyNative.read(masterFd, buffer, offset, length)
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
            PtyNative.closeFd(masterFd)
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
            val pid = try {
                PtyNative.spawn(
                    masterFd = opened.masterFd,
                    slaveFd = opened.slaveFd,
                    file = file,
                    args = args,
                    envp = env,
                    cwd = cwd
                )
            } catch (e: Exception) {
                PtyNative.closeFd(opened.masterFd)
                PtyNative.closeFd(opened.slaveFd)
                throw e
            }
            if (pid <= 0) {
                PtyNative.closeFd(opened.masterFd)
                PtyNative.closeFd(opened.slaveFd)
                throw IllegalStateException("Failed to spawn $file")
            }
            AppLogger.i("PtySession", "started $file pid=$pid cwd=$cwd")
            return PtySession(masterFd = opened.masterFd, pid = pid)
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
