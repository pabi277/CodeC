package com.codeci.ide.ui.terminal

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.io.FileDescriptor

/**
 * JNI bindings for the PTY shim (`libcodec-pty.so`) plus a posix_openpt
 * fallback used when the native library is missing (unit tests, exotic ABIs).
 *
 * Native methods stay package-private so tests can drive the fallback path
 * without loading a .so.
 */
object PtyNative {

    @Volatile
    var nativeAvailable: Boolean = false
        private set

    init {
        nativeAvailable = try {
            System.loadLibrary("codec-pty")
            true
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.w("PtyNative", "libcodec-pty.so not loaded (${e.message}); using posix fallback")
            false
        } catch (e: SecurityException) {
            AppLogger.w("PtyNative", "libcodec-pty.so blocked (${e.message}); using posix fallback")
            false
        }
    }

    external fun nativeOpenPty(): IntArray?
    external fun nativeCloseFd(fd: Int)
    external fun nativeSetWindowSize(fd: Int, rows: Int, cols: Int): Int
    external fun nativeSpawn(
        masterFd: Int,
        slaveFd: Int,
        file: String,
        args: Array<String>,
        envp: Array<String>,
        cwd: String?
    ): Int
    external fun nativeWaitPid(pid: Int, options: Int): Int
    external fun nativeKill(pid: Int, signal: Int): Int
    external fun nativeRead(fd: Int, buffer: ByteArray, off: Int, len: Int): Int
    external fun nativeWrite(fd: Int, buffer: ByteArray, off: Int, len: Int): Int
    external fun nativeLastErrno(): Int

    const val WNOHANG = 1
    const val SIGHUP = 1
    const val SIGINT = 2
    const val SIGKILL = 9
    const val SIGWINCH = 28

    data class OpenedPty(val masterFd: Int, val slaveFd: Int, val slavePath: String?)

    fun openPty(): OpenedPty {
        if (nativeAvailable) {
            val fds = nativeOpenPty()
            if (fds != null && fds.size >= 2 && fds[0] >= 0 && fds[1] >= 0) {
                return OpenedPty(fds[0], fds[1], slavePath = null)
            }
            AppLogger.w("PtyNative", "native openpty failed, trying posix fallback")
        }
        return openPtyPosix()
    }

    fun closeFd(fd: Int) {
        if (fd < 0) return
        if (nativeAvailable) {
            nativeCloseFd(fd)
        } else {
            try {
                ParcelFileDescriptor.adoptFd(fd).close()
            } catch (_: Exception) {
            }
        }
    }

    fun setWindowSize(fd: Int, rows: Int, cols: Int, pid: Int = -1) {
        if (fd < 0 || rows <= 0 || cols <= 0) return
        if (nativeAvailable) {
            nativeSetWindowSize(fd, rows, cols)
            if (pid > 0) nativeKill(pid, SIGWINCH)
        }
        // Fallback: TIOCSWINSZ needs a struct pointer; skip if no JNI.
    }

    fun spawn(
        masterFd: Int,
        slaveFd: Int,
        file: String,
        args: Array<String>,
        envp: Array<String>,
        cwd: String?
    ): Int {
        if (nativeAvailable) {
            return nativeSpawn(masterFd, slaveFd, file, args, envp, cwd)
        }
        return spawnPosix(slaveFd, file, args, envp, cwd)
    }

    fun waitPid(pid: Int, hang: Boolean): Int {
        if (pid <= 0) return -1
        if (nativeAvailable) {
            return nativeWaitPid(pid, if (hang) 0 else WNOHANG)
        }
        return -1
    }

    fun kill(pid: Int, signal: Int = SIGHUP) {
        if (pid <= 0) return
        if (nativeAvailable) {
            nativeKill(pid, signal)
            return
        }
        try {
            android.os.Process.sendSignal(pid, signal)
        } catch (_: Exception) {
        }
    }

    fun read(fd: Int, buffer: ByteArray, off: Int = 0, len: Int = buffer.size): Int {
        if (nativeAvailable) {
            return nativeRead(fd, buffer, off, len)
        }
        return try {
            Os.read(intToFd(fd), buffer, off, len)
        } catch (e: Exception) {
            AppLogger.e("PtyNative", "posix read failed", e)
            -1
        }
    }

    fun write(fd: Int, buffer: ByteArray, off: Int = 0, len: Int = buffer.size): Int {
        if (nativeAvailable) {
            return nativeWrite(fd, buffer, off, len)
        }
        return try {
            Os.write(intToFd(fd), buffer, off, len)
        } catch (e: Exception) {
            AppLogger.e("PtyNative", "posix write failed", e)
            -1
        }
    }

    internal fun openPtyPosix(): OpenedPty {
        val master = Os.posix_openpt(
            OsConstants.O_RDWR or OsConstants.O_NOCTTY or OsConstants.O_CLOEXEC
        )
        Os.grantpt(master)
        Os.unlockpt(master)
        val name = Os.ptsname(master)
        val slave = Os.open(name, OsConstants.O_RDWR or OsConstants.O_NOCTTY, 0)
        return OpenedPty(fdToInt(master), fdToInt(slave), name)
    }

    /**
     * Attach the child's stdio to [slaveFd] via a `/system/bin/sh -c exec … <>pts`
     * redirect. Not a controlling tty (no job-control), but `isatty()` is true
     * and the VT emulator still works. Used only when JNI is unavailable.
     */
    internal fun spawnPosix(
        slaveFd: Int,
        file: String,
        args: Array<String>,
        envp: Array<String>,
        cwd: String?
    ): Int {
        val slavePath = slavePathOf(slaveFd) ?: return -1
        val command = buildString {
            append("exec ")
            append(TerminalHandoff.shellEscape(file))
            for (arg in args.drop(1)) {
                append(' ')
                append(TerminalHandoff.shellEscape(arg))
            }
            append(" <>")
            append(TerminalHandoff.shellEscape(slavePath))
            append(" >&0 2>&0")
        }
        val builder = ProcessBuilder("/system/bin/sh", "-c", command)
        if (!cwd.isNullOrEmpty()) {
            builder.directory(File(cwd))
        }
        builder.redirectErrorStream(true)
        val env = builder.environment()
        env.clear()
        for (entry in envp) {
            val eq = entry.indexOf('=')
            if (eq > 0) env[entry.substring(0, eq)] = entry.substring(eq + 1)
        }
        val process = builder.start()
        FallbackProcesses.register(process)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.pid().toInt()
        } else {
            -process.hashCode()
        }
    }

    internal object FallbackProcesses {
        private val live = java.util.concurrent.ConcurrentHashMap<Int, Process>()
        fun register(process: Process) {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.pid().toInt()
            } else {
                -process.hashCode()
            }
            live[key] = process
        }
        fun destroy(pid: Int) {
            live.remove(pid)?.destroy()
        }
    }

    private fun slavePathOf(slaveFd: Int): String? {
        return try {
            val link = File("/proc/self/fd/$slaveFd")
            if (link.exists()) link.canonicalPath else null
        } catch (_: Exception) {
            null
        }
    }

    internal fun fdToInt(fd: FileDescriptor): Int {
        val adopted = ParcelFileDescriptor.dup(fd)
        return adopted.detachFd()
    }

    internal fun intToFd(fd: Int): FileDescriptor {
        val pfd = ParcelFileDescriptor.adoptFd(fd)
        val javaFd = pfd.fileDescriptor
        // Detach so adoptFd doesn't close the real fd when pfd is GC'd.
        // We leak the ParcelFileDescriptor wrapper on purpose: the int fd
        // is owned by PtySession.
        @Suppress("UNUSED_VARIABLE")
        val leaked = pfd
        return javaFd
    }
}
