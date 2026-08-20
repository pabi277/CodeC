package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.utils.AppLogger

/**
 * JNI bindings for the PTY shim (`libcodec-pty.so`).
 *
 * openpty + fork/exec + TIOCSWINSZ live in `app/src/main/cpp/pty.c`.
 * When the .so is missing the session surfaces a clear error; Phase 1
 * does not try to emulate a PTY in Java (those Os helpers are absent
 * from some compile-sdk stubs).
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
            AppLogger.w("PtyNative", "libcodec-pty.so not loaded (${e.message})")
            false
        } catch (e: SecurityException) {
            AppLogger.w("PtyNative", "libcodec-pty.so blocked (${e.message})")
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

    data class OpenedPty(val masterFd: Int, val slaveFd: Int)

    fun requireNative() {
        if (!nativeAvailable) {
            throw IllegalStateException(
                "PTY native library (libcodec-pty.so) is not loaded. " +
                    "Rebuild the APK with the NDK so openpty/fork/exec can run."
            )
        }
    }

    fun openPty(): OpenedPty {
        requireNative()
        val fds = nativeOpenPty()
        if (fds == null || fds.size < 2 || fds[0] < 0 || fds[1] < 0) {
            throw IllegalStateException("openpty failed (errno=${nativeLastErrno()})")
        }
        return OpenedPty(fds[0], fds[1])
    }

    fun closeFd(fd: Int) {
        if (fd < 0 || !nativeAvailable) return
        nativeCloseFd(fd)
    }

    fun setWindowSize(fd: Int, rows: Int, cols: Int, pid: Int = -1) {
        if (!nativeAvailable || fd < 0 || rows <= 0 || cols <= 0) return
        nativeSetWindowSize(fd, rows, cols)
        if (pid > 0) nativeKill(pid, SIGWINCH)
    }

    fun spawn(
        masterFd: Int,
        slaveFd: Int,
        file: String,
        args: Array<String>,
        envp: Array<String>,
        cwd: String?
    ): Int {
        requireNative()
        return nativeSpawn(masterFd, slaveFd, file, args, envp, cwd)
    }

    fun waitPid(pid: Int, hang: Boolean): Int {
        if (!nativeAvailable || pid <= 0) return -1
        return nativeWaitPid(pid, if (hang) 0 else WNOHANG)
    }

    fun kill(pid: Int, signal: Int = SIGHUP) {
        if (!nativeAvailable || pid <= 0) return
        nativeKill(pid, signal)
    }

    fun read(fd: Int, buffer: ByteArray, off: Int = 0, len: Int = buffer.size): Int {
        if (!nativeAvailable) return -1
        return nativeRead(fd, buffer, off, len)
    }

    fun write(fd: Int, buffer: ByteArray, off: Int = 0, len: Int = buffer.size): Int {
        if (!nativeAvailable) return -1
        return nativeWrite(fd, buffer, off, len)
    }
}
