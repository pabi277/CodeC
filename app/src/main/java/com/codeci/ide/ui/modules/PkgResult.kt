package com.codeci.ide.ui.modules

import java.io.File

/**
 * Phase 71.1 — how the last package operation ENDED, as `pkg` itself reports it.
 *
 * Before this, the Packages row learned only "is the binary on disk yet?", so a
 * failed install (offline, no space, signature) left a disabled INSTALLING
 * button that polled forever. The generated `pkg` script
 * (`ShellEnvironment.pkgScript`) now leaves one line on the way out —
 *
 *     <epoch-seconds> <command> <exit-status> <package names…>
 *
 * — in `$PREFIX/var/lib/codec-pkg/last-result`. The command the user sees in the
 * Terminal is unchanged; the app only reads the file. Pure Kotlin: the parsing
 * and the verdict are host-tested, the screen owns the polling.
 */
data class PkgResult(
    val epochSec: Long,
    val command: String,
    val exit: Int,
    val targets: List<String>,
) {
    val isInstall: Boolean get() = command == "install" || command == "i"

    companion object {
        /** Path of the result file under the userland prefix. */
        const val RELATIVE_PATH = "var/lib/codec-pkg/last-result"

        fun file(prefix: File): File = File(prefix, RELATIVE_PATH)

        /**
         * The package names a card's install command hands to `pkg`, or empty
         * when the command is anything else — a chain (`… && pip install …`), a
         * pipe, another tool. Empty means "`pkg` cannot speak for this command":
         * the row keeps its disk-only behaviour instead of concluding early from
         * the first half of a chain.
         */
        fun installTargets(command: String): List<String> {
            val tokens = command.trim().split(Regex("\\s+"))
            if (tokens.size < 3 || tokens[0] != "pkg" || (tokens[1] != "install" && tokens[1] != "i")) {
                return emptyList()
            }
            if (tokens.any { it == "&&" || it == "||" || it == ";" || it == "|" || it.endsWith(";") }) {
                return emptyList()
            }
            return tokens.drop(2).filter { !it.startsWith("-") }
        }

        /** `null` for anything that is not exactly the documented line. */
        fun parse(line: String?): PkgResult? {
            val parts = line?.trim()?.split(' ')?.filter { it.isNotEmpty() } ?: return null
            if (parts.size < 3) return null
            val epoch = parts[0].toLongOrNull() ?: return null
            val exit = parts[2].toIntOrNull() ?: return null
            return PkgResult(epoch, parts[1], exit, parts.drop(3))
        }

        /** Reads the file; a missing/unreadable/garbled file is simply "no result". */
        fun read(prefix: File): PkgResult? =
            runCatching { parse(file(prefix).takeIf { it.isFile }?.readText()) }.getOrNull()
    }
}

/** What the row should conclude from disk + result. */
enum class InstallOutcome {
    /** Nothing conclusive yet — the install is still running (or not started). */
    WAITING,

    /** The package is on disk. */
    INSTALLED,

    /** `pkg install` ended with a non-zero status for this package. */
    FAILED,

    /** `pkg install` ended cleanly but nothing was installed (declined / already newest). */
    ENDED_WITHOUT_INSTALL,
}

object InstallOutcomes {

    /**
     * @param packageNames what `pkg install` was asked for ([PkgResult.installTargets]);
     *   empty = `pkg` cannot speak for this command, so the answer stays WAITING.
     * @param startedAtSec when the user's own install was issued (epoch seconds);
     *   a result older than this belongs to an earlier command and is ignored.
     * @param installedOnDisk read AFTER [result]: a `0` result then implies the
     *   package manager finished before the disk was looked at, so "not on disk"
     *   is the truth and not a race.
     */
    fun decide(
        packageNames: List<String>,
        startedAtSec: Long,
        installedOnDisk: Boolean,
        result: PkgResult?,
    ): InstallOutcome {
        if (installedOnDisk) return InstallOutcome.INSTALLED
        val mine = result != null &&
            result.isInstall &&
            result.epochSec >= startedAtSec &&
            packageNames.isNotEmpty() &&
            result.targets.containsAll(packageNames)
        if (!mine) return InstallOutcome.WAITING
        return if (result!!.exit != 0) InstallOutcome.FAILED else InstallOutcome.ENDED_WITHOUT_INSTALL
    }
}
