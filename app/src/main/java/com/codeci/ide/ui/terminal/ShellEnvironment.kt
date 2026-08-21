package com.codeci.ide.ui.terminal

import android.content.Context
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.FileManager
import java.io.File

/**
 * Phase-1 Mini-Termux userland that lives under the app-private prefix
 * (`/data/data/com.codeci.ide/files/usr`).
 *
 * Writes the `cc` frontend (wired to the embedded TCC), a `pkg` placeholder
 * and a `bash` shim so the terminal can `exec bash` before a real bootstrap
 * lands. After Phase 2 extract, [resolveShell] prefers ELF bash/busybox.
 * Script bodies are pure strings so they are unit-tested.
 */
object ShellEnvironment {
    const val BOOTSTRAP_VERSION = "14"
    const val PREFIX_NAME = "usr"
    const val HOME_NAME = "home"
    const val PACKAGE_REPOSITORY_URL = "https://pabi277.github.io/CodeC/dev"
    const val PACKAGE_REPOSITORY_SUITE = "stable"
    const val PACKAGE_REPOSITORY_COMPONENT = "main"

    fun prefixDir(filesDir: File): File = File(filesDir, PREFIX_NAME)
    fun homeDir(filesDir: File): File = File(filesDir, HOME_NAME)

    fun binDir(prefix: File): File = File(prefix, "bin")
    fun etcDir(prefix: File): File = File(prefix, "etc")
    fun tmpDir(prefix: File): File = File(prefix, "tmp")

    fun ccScript(): String = """
        #!/system/bin/sh
        # CodeC cc — frontend for the embedded TCC compiler (Phase 1).
        if [ ! -x "${'$'}{TCC_BIN:-}" ]; then
          echo "cc: built-in TCC is not available on this device." >&2
          echo "cc: reinstall the app, or pick another Compiler Engine in Settings." >&2
          exit 127
        fi
        if [ ! -d "${'$'}{TCC_BUNDLE:-}" ]; then
          echo "cc: TCC support files missing. Open the editor and tap RUN once to extract them." >&2
          exit 127
        fi
        CWD=${'$'}(pwd)
        converted=""
        outfile=""
        out_next=0
        for arg in "${'$'}@"; do
          if [ "${'$'}out_next" = 1 ]; then
            case "${'$'}arg" in
              /*) ;;
              *) arg="${'$'}CWD/${'$'}arg" ;;
            esac
            outfile="${'$'}arg"
            out_next=0
            continue
          fi
          case "${'$'}arg" in
            -o) out_next=1; continue ;;
            -*) ;;
            *)
              case "${'$'}arg" in
                /*) ;;
                *)
                  if [ -e "${'$'}CWD/${'$'}arg" ]; then
                    arg="${'$'}CWD/${'$'}arg"
                  elif [ -n "${'$'}CODEC_PROJECTS" ] && [ -e "${'$'}CODEC_PROJECTS/${'$'}arg" ]; then
                    arg="${'$'}CODEC_PROJECTS/${'$'}arg"
                  else
                    arg="${'$'}CWD/${'$'}arg"
                  fi
                  if [ ! -e "${'$'}arg" ]; then
                    echo "cc: not found: ${'$'}arg" >&2
                    echo "cc: save the file in the Editor, then run: cc main.c -o a.out" >&2
                    exit 1
                  fi
                  ;;
              esac
              ;;
          esac
          converted="${'$'}converted ${'$'}arg"
        done
        if [ -z "${'$'}outfile" ]; then
          outfile="${'$'}CWD/a.out"
        fi
        cd "${'$'}TCC_BUNDLE" || exit 1
        extra="-nostdlib -static"
        [ -n "${'$'}CC_STD" ] && extra="${'$'}extra -std=${'$'}CC_STD"
        [ -n "${'$'}CC_WARN" ] && extra="${'$'}extra ${'$'}CC_WARN"
        [ -n "${'$'}CC_OPT" ] && extra="${'$'}extra ${'$'}CC_OPT"
        extra="${'$'}extra -I include-tcc -I include -B . -L ."
        [ ! -f codec_stdio.o ] && [ -f codec_stdio.c ] && "${'$'}TCC_BIN" -c -I include-tcc -I include -o codec_stdio.o codec_stdio.c
        # Same order as EmbeddedCompiler.buildCompileCommand: archives then -o last.
        # Do not exec: we must chmod +x the ELF afterwards or ./a.out is denied.
        # shellcheck disable=SC2086
        "${'$'}TCC_BIN" ${'$'}extra crt1.o crti.o codec_stdio.o ${'$'}converted libtcc1.a libc.a libtcc1.a libc.a crtn.o -o "${'$'}outfile"
        status=${'$'}?
        if [ "${'$'}status" -eq 0 ] && [ -n "${'$'}outfile" ] && [ -f "${'$'}outfile" ]; then
          chmod 755 "${'$'}outfile" 2>/dev/null
        fi
        exit ${'$'}status
    """.trimIndent() + "\n"

    fun pkgScript(): String = """
        #!/system/bin/sh
        # CodeC pkg — guarded frontend for the CodeC-only apt/dpkg backend.
        # This script never reads an ambient Termux sources.list.
        set -u

        PREFIX="${'$'}{PREFIX:-$(cd "${'$'}{0%/*}/.." 2>/dev/null && pwd)}"
        REPOSITORY="${PACKAGE_REPOSITORY_URL}"
        SUITE="${PACKAGE_REPOSITORY_SUITE}"
        COMPONENT="${PACKAGE_REPOSITORY_COMPONENT}"
        STATE="${'$'}PREFIX/var/lib/codec-pkg"
        SOURCES="${'$'}STATE/sources.list"
        CACHE="${'$'}PREFIX/var/cache/apt/archives"
        LOCK="${'$'}STATE/lock"
        MIN_FREE_KB=32768

        error() {
          echo "pkg: ${'$'}*" >&2
          exit 1
        }

        require_backend() {
          [ -x "${'$'}PREFIX/bin/apt-get" ] || error "package manager is not present in this userland. Install a Phase 3 CodeC bootstrap; never add an official Termux repository."
          [ -x "${'$'}PREFIX/bin/dpkg" ] || error "dpkg is not present in this CodeC userland; refusing to use an external package manager."
          # dpkg executes maintainer scripts (reviewed alternatives postinst/
          # prerm only); the kernel can only run shebang scripts under ${'$'}PREFIX
          # with the termux-exec LD_PRELOAD library. Export it defensively so
          # even a session started before the bootstrap update stays correct.
          for _codec_preload in "${'$'}PREFIX/lib/libtermux-exec-ld-preload.so" "${'$'}PREFIX/lib/libtermux-exec.so"; do
            if [ -f "${'$'}_codec_preload" ]; then export LD_PRELOAD="${'$'}_codec_preload"; break; fi
          done
          unset _codec_preload
          mkdir -p "${'$'}STATE" "${'$'}CACHE/partial" || error "cannot create package state under ${'$'}PREFIX"
          # Only this file is supplied to apt. sourceparts=- prevents a stale
          # sources.list.d from mixing another repository into the transaction.
          printf '%s\n' "deb [trusted=yes] ${'$'}REPOSITORY ${'$'}SUITE ${'$'}COMPONENT" > "${'$'}SOURCES"
        }

        acquire_lock() {
          i=0
          while ! mkdir "${'$'}LOCK" 2>/dev/null; do
            i=${'$'}((i + 1))
            [ "${'$'}i" -lt 60 ] || error "another package operation is still running (lock: ${'$'}LOCK)"
            sleep 1
          done
          printf '%s\n' "${'$'}${'$'}" > "${'$'}LOCK/pid"
          trap 'rm -rf "${'$'}LOCK"' EXIT HUP INT TERM
        }

        free_kb() {
          df -Pk "${'$'}PREFIX" 2>/dev/null | awk 'NR == 2 { print ${'$'}4 }'
        }

        apt_get() {
          # Dir::Etc::sourceparts=- is intentional: only the CodeC source above
          # is permitted. APT still keeps its lists/cache under this prefix.
          "${'$'}PREFIX/bin/apt-get" \
            -o "Dir::Etc::sourcelist=${'$'}SOURCES" \
            -o "Dir::Etc::sourceparts=-" \
            -o "Dir::State=${'$'}PREFIX/var/lib/apt" \
            -o "Dir::State::status=${'$'}PREFIX/var/lib/dpkg/status" \
            -o "Dir::Cache=${'$'}PREFIX/var/cache/apt" \
            -o "Dir::Log=${'$'}PREFIX/var/log/apt" \
            -o "Acquire::Retries=3" \
            -o "Acquire::https::Verify-Peer=true" \
            "${'$'}@"
        }

        apt_cache() {
          "${'$'}PREFIX/bin/apt-cache" \
            -o "Dir::Etc::sourcelist=${'$'}SOURCES" \
            -o "Dir::Etc::sourceparts=-" \
            -o "Dir::State=${'$'}PREFIX/var/lib/apt" \
            -o "Dir::State::status=${'$'}PREFIX/var/lib/dpkg/status" \
            -o "Dir::Cache=${'$'}PREFIX/var/cache/apt" \
            "${'$'}@"
        }

        verify_release_checksum() {
          # The development channel is HTTPS plus a separately published SHA-256
          # sidecar. A signed Release key will be added before production
          # promotion; never silently downgrade this check to HTTP or trusted
          # official Termux metadata.
          downloader=""
          if [ -x "${'$'}PREFIX/bin/wget" ]; then
            downloader="${'$'}PREFIX/bin/wget"
          elif [ -x "${'$'}PREFIX/bin/busybox" ]; then
            downloader="${'$'}PREFIX/bin/busybox wget"
          else
            error "cannot verify repository integrity: CodeC wget is not installed"
          fi
          release="${'$'}STATE/Release.partial"
          checksum="${'$'}STATE/Release.sha256.partial"
          rm -f "${'$'}release" "${'$'}checksum"
          # shellcheck disable=SC2086
          ${'$'}downloader -q -O "${'$'}release" "${'$'}REPOSITORY/dists/${'$'}SUITE/Release" || error "offline or unable to download CodeC Release metadata"
          # shellcheck disable=SC2086
          ${'$'}downloader -q -O "${'$'}checksum" "${'$'}REPOSITORY/dists/${'$'}SUITE/Release.sha256" || error "CodeC Release checksum is unavailable; refusing the repository"
          expected="${'$'}(awk 'NF { print ${'$'}1; exit }' "${'$'}checksum")"
          actual="${'$'}(sha256sum "${'$'}release" 2>/dev/null | awk '{ print ${'$'}1 }')"
          if [ -z "${'$'}actual" ] || [ "${'$'}actual" != "${'$'}expected" ]; then
            rm -f "${'$'}release" "${'$'}checksum"
            error "repository Release SHA-256 mismatch; no package was installed"
          fi
          mv "${'$'}release" "${'$'}STATE/Release"
          mv "${'$'}checksum" "${'$'}STATE/Release.sha256"
        }

        friendly_apt() {
          output="${'$'}("${'$'}@" 2>&1)"
          status="${'$'}?"
          if [ "${'$'}status" -ne 0 ]; then
            echo "${'$'}output" >&2
            case "${'$'}output" in
              *"Could not resolve"*|*"Temporary failure"*|*"Network is unreachable"*|*"Connection timed out"*)
                echo "pkg: offline or repository unreachable; installed packages remain usable." >&2 ;;
              *"Hash Sum mismatch"*|*"checksum"*|*"NO_PUBKEY"*|*"not signed"*)
                echo "pkg: repository integrity check failed; no package was installed." >&2 ;;
              *"No space left"*|*"not enough free space"*)
                echo "pkg: insufficient disk space; free space under ${'$'}PREFIX and retry." >&2 ;;
              *) echo "pkg: apt failed; check the command above and retry." >&2 ;;
            esac
          else
            printf '%s\n' "${'$'}output"
          fi
          return "${'$'}status"
        }

        validate_control_scripts() {
          package_name="${'$'}1"
          control_dir="${'$'}2"
          scripts=""
          for script in preinst postinst prerm postrm; do
            [ -e "${'$'}control_dir/${'$'}script" ] || continue
            scripts="${'$'}scripts ${'$'}script"
          done
          [ -z "${'$'}scripts" ] && return 0
          # The only reviewed maintainer-script policy in the first channel is
          # Termux's generated coreutils cat.alternatives postinst/prerm. It
          # only calls the CodeC update-alternatives binary.
          case "${'$'}package_name" in
            coreutils) alt_name=pager; alt_link=bin/pager; alt_target=libexec/coreutils/cat; priority=1; slave_link=share/man/man1/pager.1.gz; slave_name=pager.1.gz; slave_target=share/man/man1/cat.1.gz ;;
            less) alt_name=pager; alt_link=bin/pager; alt_target=bin/less; priority=50; slave_link=share/man/man1/pager.1.gz; slave_name=pager.1.gz; slave_target=share/man/man1/less.1.gz ;;
            nano) alt_name=editor; alt_link=bin/editor; alt_target=bin/nano; priority=50; slave_link=share/man/man1/editor.1.gz; slave_name=editor.1.gz; slave_target=share/man/man1/nano.1.gz ;;
            *) error "maintainer scripts are forbidden for ${'$'}package_name: ${'$'}scripts" ;;
          esac
          [ ! -e "${'$'}control_dir/preinst" ] && [ ! -e "${'$'}control_dir/postrm" ] || error "${'$'}package_name has an unapproved maintainer script"
          install_spec="--install \"${'$'}PREFIX/${'$'}alt_link\" \"${'$'}alt_name\" \"${'$'}PREFIX/${'$'}alt_target\" ${'$'}priority"
          slave_spec="--slave \"${'$'}PREFIX/${'$'}slave_link\" \"${'$'}slave_name\" \"${'$'}PREFIX/${'$'}slave_target\""
          remove_spec="--remove \"${'$'}alt_name\" \"${'$'}PREFIX/${'$'}alt_target\""
          for script in postinst prerm; do
            file="${'$'}control_dir/${'$'}script"
            [ -e "${'$'}file" ] || continue
            grep -q 'Automatically added by termux_step_create_alternatives' "${'$'}file" || error "${'$'}package_name ${'$'}script is not the reviewed alternatives script"
            if [ "${'$'}script" = postinst ]; then
              grep -F -q -- "${'$'}install_spec" "${'$'}file" || error "unexpected ${'$'}package_name install alternative"
              grep -F -q -- "${'$'}slave_spec" "${'$'}file" || error "unexpected ${'$'}package_name slave alternative"
            else
              grep -F -q -- "${'$'}remove_spec" "${'$'}file" || error "unexpected ${'$'}package_name removal alternative"
            fi
            grep -E -q '(\$\(|`|com\.termux|/system/)' "${'$'}file" 2>/dev/null && error "unsafe command in coreutils ${'$'}script"
            grep -E -v '^[[:space:]]*(if \[|#|fi|then|${'$'})' "${'$'}file" 2>/dev/null | grep -E -q '(;|&&|\|\||(^|[^[:alnum:]_])(rm|curl|wget|chmod|chown|ln|cp|mv|dd|eval|exec|source)([^[:alnum:]_]|${'$'}))' && error "unsafe command in coreutils ${'$'}script"
            while IFS= read -r line; do
              line="${'$'}{line#"${'$'}{line%%[![:space:]]*}"}"
              case "${'$'}line" in
                ""|\#*|fi|then) ;;
                "if [ \"\${'$'}1\" = 'configure' ]"*|"if [ \"\${'$'}1\" = 'remove' ]"*|"if [ -x \"/data/data/com.codeci.ide/files/usr/bin/update-alternatives\" ]"*) ;;
                update-alternatives*|--install*|--slave*|--remove*)
                  case "${'$'}line" in
                    *com.termux*|*"/system/"*|*".."*) error "unsafe path in coreutils ${'$'}script" ;;
                  esac
                  ;;
                *) error "unapproved line in coreutils ${'$'}script: ${'$'}line" ;;
              esac
            done < "${'$'}file"
          done
        }

        preflight_deb() {
          deb="${'$'}1"
          [ -f "${'$'}deb" ] || error "downloaded package is missing: ${'$'}deb"
          arch="${'$'}(${'$'}PREFIX/bin/dpkg-deb -f "${'$'}deb" Architecture 2>/dev/null)" || error "cannot read package metadata: ${'$'}deb"
          device_arch="${'$'}(${'$'}PREFIX/bin/dpkg --print-architecture 2>/dev/null)" || error "cannot determine CodeC architecture"
          case "${'$'}arch" in
            all|"${'$'}device_arch") ;;
            *) error "package ${'$'}deb is for architecture ${'$'}arch, device is ${'$'}device_arch" ;;
          esac
          if grep -a -q 'com.termux' "${'$'}deb" 2>/dev/null; then
            error "package ${'$'}deb contains the official com.termux identity"
          fi
          control="${'$'}STATE/control-${'$'}${'$'}"
          rm -rf "${'$'}control"
          mkdir -p "${'$'}control" || error "cannot create package preflight directory"
          "${'$'}PREFIX/bin/dpkg-deb" --control "${'$'}deb" "${'$'}control" >/dev/null 2>&1 || error "invalid .deb control archive: ${'$'}deb"
          package_name="${'$'}(${'$'}PREFIX/bin/dpkg-deb -f "${'$'}deb" Package 2>/dev/null)" || error "cannot read package name: ${'$'}deb"
          validate_control_scripts "${'$'}package_name" "${'$'}control"
          members="${'$'}STATE/members-${'$'}${'$'}"
          "${'$'}PREFIX/bin/dpkg-deb" --contents "${'$'}deb" > "${'$'}members" 2>/dev/null || error "invalid .deb data archive: ${'$'}deb"
          while IFS= read -r line; do
            member="${'$'}{line##* ./}"
            member="${'$'}{member%% -> *}"
            case "${'$'}member" in
              ""|data|data/|data/data|data/data/|data/data/com.codeci.ide|data/data/com.codeci.ide/|data/data/com.codeci.ide/files|data/data/com.codeci.ide/files/|data/data/com.codeci.ide/files/usr|data/data/com.codeci.ide/files/usr/*) ;;
              *) error "package ${'$'}deb contains a path outside the CodeC prefix: ${'$'}member" ;;
            esac
            case "${'$'}member" in
              /*|*"/../"*|../*|*"/..") error "unsafe package path: ${'$'}member" ;;
            esac
            if printf '%s\n' "${'$'}line" | grep -q ' -> '; then
              target="${'$'}{line#* -> }"
              case "${'$'}target" in
                /*|*".."*) error "unsafe symlink target in ${'$'}deb: ${'$'}target" ;;
              esac
            fi
          done < "${'$'}members"
        }

        preflight_cache() {
          found=0
          for deb in "${'$'}CACHE"/*.deb; do
            [ -f "${'$'}deb" ] || continue
            found=1
            preflight_deb "${'$'}deb"
          done
          [ "${'$'}found" -eq 1 ] || error "apt downloaded no packages; run pkg update and retry"
        }

        install_specs() {
          [ "${'$'}#" -gt 0 ] || error "usage: pkg install <name> [name ...]"
          before="${'$'}(free_kb)"
          if [ -n "${'$'}before" ] && [ "${'$'}before" -lt "${'$'}MIN_FREE_KB" ]; then
            error "insufficient disk space under ${'$'}PREFIX (${'$'}before KB free; need at least ${'$'}MIN_FREE_KB KB)"
          fi
          marker="${'$'}STATE/transaction.pending"
          printf '%s\n' "${'$'}*" > "${'$'}marker"
          verify_release_checksum
          friendly_apt apt_get --download-only --yes --no-install-recommends install "${'$'}@" || return "${'$'}?"
          preflight_cache
          # The package set was validated before dpkg is allowed to run. The
          # repository policy rejects maintainer scripts, so no untrusted code
          # is executed as part of this first milestone.
          friendly_apt apt_get --yes --no-install-recommends install "${'$'}@" || return "${'$'}?"
          rm -f "${'$'}marker"
          echo "pkg: installed ${'$'}*"
        }

        upgrade_packages() {
          before="${'$'}(free_kb)"
          if [ -n "${'$'}before" ] && [ "${'$'}before" -lt "${'$'}MIN_FREE_KB" ]; then
            error "insufficient disk space under ${'$'}PREFIX (${'$'}before KB free; need at least ${'$'}MIN_FREE_KB KB)"
          fi
          marker="${'$'}STATE/transaction.pending"
          printf '%s\n' upgrade > "${'$'}marker"
          verify_release_checksum
          friendly_apt apt_get --download-only --yes --no-install-recommends upgrade || return "${'$'}?"
          preflight_cache
          friendly_apt apt_get --yes --no-install-recommends upgrade || return "${'$'}?"
          rm -f "${'$'}marker"
          echo "pkg: upgraded CodeC packages"
        }

        repair() {
          [ -e "${'$'}STATE/transaction.pending" ] || { echo "pkg: no interrupted transaction"; return 0; }
          echo "pkg: repairing the recorded transaction: ${'$'}(cat "${'$'}STATE/transaction.pending")"
          friendly_apt apt_get --yes --no-install-recommends -f install
          rm -f "${'$'}STATE/transaction.pending"
        }

        command="${'$'}{1:-help}"
        shift || true
        if [ "${'$'}command" = help ] || [ "${'$'}command" = -h ] || [ "${'$'}command" = --help ]; then
          cat <<'HELP'
CodeC packages (CodeC repository only)
  pkg update                 refresh CodeC package indexes
  pkg search <name>          search the cached CodeC catalog
  pkg install <name> ...     download, verify, and install packages
  pkg upgrade                upgrade installed CodeC packages
  pkg uninstall <name> ...   remove packages from this userland
  pkg repair                 recover an interrupted transaction

The Phase 3 development channel requires a CodeC apt/dpkg bootstrap. It never
uses official com.termux packages or repositories.
HELP
          exit 0
        fi
        require_backend
        case "${'$'}command" in
          update)
            acquire_lock
            verify_release_checksum
            friendly_apt apt_get update
            ;;
          search)
            [ "${'$'}#" -gt 0 ] || error "usage: pkg search <name>"
            apt_cache search "${'$'}@"
            ;;
          install|i)
            acquire_lock
            install_specs "${'$'}@"
            ;;
          upgrade)
            acquire_lock
            upgrade_packages
            ;;
          uninstall|remove|rm)
            [ "${'$'}#" -gt 0 ] || error "usage: pkg uninstall <name>"
            for name in "${'$'}@"; do
              case "${'$'}name" in
                bash|busybox|apt|dpkg|codec-pkg) error "refusing to remove CodeC base package ${'$'}name" ;;
              esac
            done
            acquire_lock
            friendly_apt apt_get --yes remove "${'$'}@"
            ;;
          repair)
            acquire_lock
            repair
            ;;
          help|-h|--help)
            cat <<'HELP'
CodeC packages (CodeC repository only)
  pkg update                 refresh CodeC package indexes
  pkg search <name>          search the cached CodeC catalog
  pkg install <name> ...     download, verify, and install packages
  pkg upgrade                upgrade installed CodeC packages
  pkg uninstall <name> ...   remove packages from this userland
  pkg repair                 recover an interrupted transaction

The Phase 3 development channel requires a CodeC apt/dpkg bootstrap. It never
uses official com.termux packages or repositories.
HELP
            ;;
          *) error "unknown command '${'$'}command' (try: pkg help)" ;;
        esac
    """.trimIndent() + "\n"

    fun bashShim(): String = """
        #!/system/bin/sh
        # Phase 1 stand-in: Android's /system/bin/sh until the bootstrap
        # ships a real bash (Phase 2). Keeps "exec bash" working today.
        exec /system/bin/sh "${'$'}@"
    """.trimIndent() + "\n"

    fun profileScript(prefix: File, home: File, projects: File): String = """
        # CodeC login profile (Phase 1)
        export PREFIX='${prefix.absolutePath}'
        export HOME='${home.absolutePath}'
        # Prefer the path the app exported (current editor folder).
        if [ -z "${'$'}CODEC_PROJECTS" ]; then
          export CODEC_PROJECTS='${projects.absolutePath}'
        fi
        export TMPDIR="${'$'}PREFIX/tmp"
        export PATH="${'$'}PREFIX/bin:/system/bin:/system/xbin:${'$'}PATH"
        # termux-exec (Phase 3 bootstrap): lets the kernel execute shebang
        # scripts under the CodeC prefix. Only export when the library is
        # present; a dangling LD_PRELOAD would break every process.
        for _codec_preload in "${'$'}PREFIX/lib/libtermux-exec-ld-preload.so" "${'$'}PREFIX/lib/libtermux-exec.so"; do
          if [ -f "${'$'}_codec_preload" ]; then export LD_PRELOAD="${'$'}_codec_preload"; break; fi
        done
        unset _codec_preload
        export TERM="${'$'}{TERM:-xterm-256color}"
        export COLORTERM="${'$'}{COLORTERM:-truecolor}"
        export LANG="${'$'}{LANG:-C.UTF-8}"
        export PS1='codec ${'$'} '
        mkdir -p "${'$'}HOME" "${'$'}TMPDIR" "${'$'}CODEC_PROJECTS" 2>/dev/null
        pj() { cd "${'$'}CODEC_PROJECTS" || return; }
        cd "${'$'}CODEC_PROJECTS" 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || true
        if [ -z "${'$'}CODEC_MOTD_SHOWN" ]; then
          export CODEC_MOTD_SHOWN=1
          echo "CodeC terminal"
          /system/bin/ls
          echo
          if ! /system/bin/ls *.c >/dev/null 2>&1; then
            echo "(no .c files here — Editor: Save, then: ls)"
            echo
          fi
        fi
    """.trimIndent() + "\n"

    fun normalizeStandard(standard: String): String =
        "c" + standard.lowercase().removePrefix("c")

    fun warningFlags(warnings: Boolean): String =
        if (warnings) "-Wall -Wextra" else ""

    fun optimizationFlag(level: Int): String = "-O${level.coerceIn(0, 3)}"

    fun buildEnv(
        filesDir: File,
        nativeLibDir: File,
        tccBinary: File?,
        tccBundle: File,
        standard: String,
        warnings: Boolean,
        optimization: Int,
        extraPath: List<File> = emptyList(),
        projectsDir: File? = null
    ): Map<String, String> {
        val prefix = prefixDir(filesDir)
        val home = homeDir(filesDir)
        val projects = projectsDir ?: File(filesDir, "CodeC/projects")
        val pathParts = buildList {
            add(binDir(prefix).absolutePath)
            add(nativeLibDir.absolutePath)
            extraPath.forEach { add(it.absolutePath) }
            add("/system/bin")
            add("/system/xbin")
            add("/vendor/bin")
        }
        return buildMap {
            put("PREFIX", prefix.absolutePath)
            put("HOME", home.absolutePath)
            put("TMPDIR", tmpDir(prefix).absolutePath)
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("LANG", "C.UTF-8")
            put("LC_ALL", "C.UTF-8")
            put("PATH", pathParts.joinToString(":"))
            // termux-exec (Phase 3 bootstrap): intercepts exec() so the kernel
            // can run shebang scripts under $PREFIX (dpkg maintainer scripts,
            // user scripts). Only set when the library exists — a dangling
            // LD_PRELOAD would break every process.
            termuxExecPreload(prefix)?.let { put("LD_PRELOAD", it.absolutePath) }
            put("CC_STD", normalizeStandard(standard))
            put("CC_WARN", warningFlags(warnings))
            put("CC_OPT", optimizationFlag(optimization))
            put("ENV", File(etcDir(prefix), "profile").absolutePath)
            put("PS1", "codec $ ")
            put("USER", "codec")
            put("CODEC_PROJECTS", projects.absolutePath)
            if (tccBinary != null) put("TCC_BIN", tccBinary.absolutePath)
            put("TCC_BUNDLE", tccBundle.absolutePath)
        }
    }

    fun isElf(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            file.inputStream().use { input ->
                val mag = ByteArray(4)
                if (input.read(mag) != 4) return false
                mag[0] == 0x7f.toByte() && mag[1] == 'E'.code.toByte() &&
                    mag[2] == 'L'.code.toByte() && mag[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Result of a launch probe. [missingLibrary] is the shared library the
     * dynamic loader reported as absent (e.g. `libandroid-support.so`), when
     * the failure output contains one.
     */
    data class LaunchDiagnostic(val ok: Boolean, val missingLibrary: String? = null)

    private val MISSING_LIBRARY =
        Regex("""library\s*["']([^"']+)["']\s*not found""")

    /** Extract the missing shared-library name from loader diagnostics. */
    fun missingLibraryFromOutput(output: String): String? =
        MISSING_LIBRARY.find(output)?.groupValues?.get(1)

    /**
     * True only when a native shell can actually start. ELF magic alone is not
     * enough: a downloaded Bash may have an absent `libandroid-support.so` or
     * another missing shared library, in which case Android's PTY would die
     * immediately with "library ... not found".
     */
    fun hasRealUserland(prefix: File): Boolean {
        val bin = binDir(prefix)
        val bash = File(bin, "bash")
        val busybox = File(bin, "busybox")
        return canLaunch(bash, prefix, bash = true) || canLaunch(busybox, prefix, bash = false)
    }

    /** Try a minimal non-interactive command so the dynamic loader is tested. */
    fun canLaunch(file: File, prefix: File, bash: Boolean): Boolean =
        launchDiagnostic(file, prefix, bash).ok

    /**
     * Launch probe with diagnostics. Runs the shell with a minimal command so
     * the dynamic loader resolves every shared library; on failure the output
     * is scanned for the missing library name.
     */
    fun launchDiagnostic(file: File, prefix: File, bash: Boolean): LaunchDiagnostic {
        if (!isElf(file) || !file.canExecute()) return LaunchDiagnostic(false)
        return try {
            val command = if (bash) {
                arrayOf(file.absolutePath, "-c", "exit 0")
            } else {
                arrayOf(file.absolutePath, "sh", "-c", "exit 0")
            }
            val process = ProcessBuilder(*command)
                .directory(prefix)
                .apply {
                    environment()["PREFIX"] = prefix.absolutePath
                    environment()["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
                    environment()["PATH"] = File(prefix, "bin").absolutePath + ":/system/bin"
                }
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                LaunchDiagnostic(true)
            } else {
                // Output is bounded (linker diagnostics are a few lines);
                // reading after waitFor is safe.
                val output = process.inputStream.bufferedReader().readText()
                LaunchDiagnostic(false, missingLibraryFromOutput(output))
            }
        } catch (_: Exception) {
            LaunchDiagnostic(false)
        }
    }

    /**
     * The termux-exec LD_PRELOAD library that lets the kernel execute shebang
     * scripts under the CodeC prefix (dpkg maintainer scripts, user scripts).
     * `null` when the userland does not carry it (Phase 2 userland).
     */
    fun termuxExecPreload(prefix: File): File? {
        val lib = File(prefix, "lib")
        val primary = File(lib, "libtermux-exec-ld-preload.so")
        if (primary.isFile) return primary
        val compat = File(lib, "libtermux-exec.so")
        return if (compat.isFile) compat else null
    }

    /**
     * Preferred shell: runnable CodeC ELF Bash, then runnable BusyBox ash,
     * then the Phase-1 shim, then `/system/bin/sh`.
     */
    fun resolveShell(prefix: File): File {
        val bash = File(binDir(prefix), "bash")
        val busybox = File(binDir(prefix), "busybox")
        if (canLaunch(bash, prefix, bash = true)) return bash
        if (canLaunch(busybox, prefix, bash = false)) return busybox
        val candidates = listOf(
            bash,
            File("/system/bin/bash"),
            File("/system/bin/sh")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
            ?: File("/system/bin/sh")
    }

    fun envToArray(env: Map<String, String>): Array<String> =
        env.entries
            .sortedBy { it.key }
            .map { "${it.key}=${it.value}" }
            .toTypedArray()
}

data class PreparedShell(
    val prefix: File,
    val home: File,
    val shell: File,
    val cwd: File,
    val env: Map<String, String>
)

class ShellBootstrap(private val context: Context) {

    fun prefixDir(): File = ShellEnvironment.prefixDir(context.filesDir)
    fun homeDir(): File = ShellEnvironment.homeDir(context.filesDir)

    /**
     * Creates `$PREFIX/{bin,etc,tmp}` and `$HOME`, extracts TCC if needed,
     * and (re)writes the Phase-1 scripts when the bootstrap version changes.
     */
    fun prepare(settings: CompilerSettings = CompilerSettings("c11", warnings = true, optimization = 0)): PreparedShell {
        val prefix = prefixDir()
        val home = homeDir()
        val bin = ShellEnvironment.binDir(prefix)
        val etc = ShellEnvironment.etcDir(prefix)
        val tmp = ShellEnvironment.tmpDir(prefix)
        bin.mkdirs()
        etc.mkdirs()
        tmp.mkdirs()
        home.mkdirs()

        val extracted = EmbeddedCompiler.ensureExtracted(context)
        if (!extracted) {
            AppLogger.w("ShellBootstrap", "TCC bundle not extracted; cc will report unavailable")
        }

        val projects = try {
            FileManager(context).getProjectDir()
        } catch (_: Exception) {
            File(context.filesDir, "CodeC/projects")
        }
        if (!projects.exists()) projects.mkdirs()

        val marker = File(prefix, ".bootstrap-v${ShellEnvironment.BOOTSTRAP_VERSION}")
        writeExecutable(File(bin, "cc"), ShellEnvironment.ccScript())
        writeExecutable(File(bin, "pkg"), ShellEnvironment.pkgScript())
        val bash = File(bin, "bash")
        if (!ShellEnvironment.isElf(bash)) {
            writeExecutable(bash, ShellEnvironment.bashShim())
        }
        File(etc, "profile").writeText(
            ShellEnvironment.profileScript(prefix, home, projects)
        )
        File(home, ".profile").writeText(". ${TerminalHandoff.shellEscape(File(etc, "profile").absolutePath)}\n")
        marker.writeText(ShellEnvironment.BOOTSTRAP_VERSION)

        val tcc = EmbeddedCompiler.tccBinary(context)
        val bundle = EmbeddedCompiler.bundleDir(context)
        val nativeLib = File(context.applicationInfo.nativeLibraryDir)

        val env = ShellEnvironment.buildEnv(
            filesDir = context.filesDir,
            nativeLibDir = nativeLib,
            tccBinary = tcc,
            tccBundle = bundle,
            standard = settings.cStandard,
            warnings = settings.warnings,
            optimization = settings.optimization,
            projectsDir = projects
        )
        return PreparedShell(
            prefix = prefix,
            home = home,
            shell = ShellEnvironment.resolveShell(prefix),
            cwd = projects,
            env = env
        )
    }

    private fun writeExecutable(file: File, body: String) {
        file.parentFile?.mkdirs()
        file.writeText(body)
        file.setExecutable(true, false)
    }
}
