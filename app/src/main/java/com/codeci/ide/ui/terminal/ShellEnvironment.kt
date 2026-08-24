package com.codeci.ide.ui.terminal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
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
    const val BOOTSTRAP_VERSION = "20"
    const val PREFIX_NAME = "usr"
    const val HOME_NAME = "home"
    const val PACKAGE_REPOSITORY_URL = "https://pabi277.github.io/CodeC/dev"
    const val PACKAGE_REPOSITORY_SUITE = "stable"
    const val PACKAGE_REPOSITORY_COMPONENT = "main"
    const val PACKAGE_REPOSITORY_KEYRING = "codec-archive-keyring-v1.gpg"

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
        # Maintainer scripts are generated at build time with the canonical
        # /data/data/ prefix; on a running device Android exposes the same
        # location as /data/user/0/ (a symlink alias). Build the expected
        # alternative specs against the canonical form so the byte check
        # matches the generated postinst/prerm.
        case "${'$'}PREFIX" in
          /data/user/0/*) CANON_PREFIX="/data/data/${'$'}{PREFIX#/data/user/0/}" ;;
          *) CANON_PREFIX="${'$'}PREFIX" ;;
        esac
        REPOSITORY="${PACKAGE_REPOSITORY_URL}"
        SUITE="${PACKAGE_REPOSITORY_SUITE}"
        COMPONENT="${PACKAGE_REPOSITORY_COMPONENT}"
        STATE="${'$'}PREFIX/var/lib/codec-pkg"
        SOURCES="${'$'}STATE/sources.list"
        CACHE="${'$'}PREFIX/var/cache/apt/archives"
        LOCK="${'$'}STATE/lock"
        KEYRING="${'$'}PREFIX/etc/apt/keyrings/${PACKAGE_REPOSITORY_KEYRING}"
        MIN_FREE_KB=32768

        error() {
          echo "pkg: ${'$'}*" >&2
          exit 1
        }

        require_backend() {
          [ -x "${'$'}PREFIX/bin/apt-get" ] || error "package manager is not present in this userland. Install a Phase 3 CodeC bootstrap; never add an official Termux repository."
          [ -x "${'$'}PREFIX/bin/dpkg" ] || error "dpkg is not present in this CodeC userland; refusing to use an external package manager."
          [ -x "${'$'}PREFIX/bin/gpgv" ] || error "gpgv is missing from this CodeC userland; signed repository verification is unavailable."
          [ -s "${'$'}KEYRING" ] || error "CodeC repository keyring is missing; update CodeC or reinstall the signed userland."
          # dpkg executes maintainer scripts (reviewed alternatives postinst/
          # prerm only); the kernel can only run shebang scripts under ${'$'}PREFIX
          # with the termux-exec LD_PRELOAD library. Export it defensively so
          # even a session started before the bootstrap update stays correct.
          for _codec_preload in "${'$'}PREFIX/lib/libtermux-exec-ld-preload.so" "${'$'}PREFIX/lib/libtermux-exec.so"; do
            if [ -f "${'$'}_codec_preload" ]; then export LD_PRELOAD="${'$'}_codec_preload"; break; fi
          done
          unset _codec_preload
          mkdir -p "${'$'}STATE" "${'$'}CACHE/partial" || error "cannot create package state under ${'$'}PREFIX"
          # The published userland-v2-dev bootstrap predates the dpkg-perl
          # recipe fix and seeds "Depends: perl, clang, make" into the dpkg
          # status DB. clang is a build tool, absent from the runtime repo, so
          # apt treats dpkg-perl as broken and refuses every install. Heal the
          # stale entry in place so a re-extracted stale bootstrap self-repairs
          # on first pkg use (idempotent: no-op once clang is already gone).
          status_db="${'$'}PREFIX/var/lib/dpkg/status"
          if [ -f "${'$'}status_db" ]; then
            sed -i '/^Package: dpkg-perl${'$'}/,/^${'$'}/ s/ clang,//' "${'$'}status_db"
          fi
          # dpkg runs every maintainer script through `sh`. Termux normally
          # gets bin/sh from termux-tools, which CodeC intentionally drops
          # (its termux-am Android wrapper chain is unwanted). Provide the
          # missing bin/sh here so dpkg can run the reviewed postinst/prerm.
          [ -e "${'$'}PREFIX/bin/sh" ] || ln -s bash "${'$'}PREFIX/bin/sh" 2>/dev/null || true
          # apt writes its EIPP plan/log under Dir::Log during the install
          # (non-download-only) phase; the dir must exist or apt aborts.
          mkdir -p "${'$'}PREFIX/var/log/apt" 2>/dev/null || true
          # The official apt recipe's RM_AFTER_INSTALL drops etc/apt/apt.conf.d
          # and etc/apt/preferences.d, and the Phase 3 bootstrap does not
          # recreate them. apt still tries to read both and warns
          # "DirectoryExists"; create them so apt stays quiet.
          mkdir -p "${'$'}PREFIX/etc/apt/apt.conf.d" "${'$'}PREFIX/etc/apt/preferences.d" 2>/dev/null || true
          # Only this file is supplied to apt. sourceparts=- prevents a stale
          # sources.list.d from mixing another repository into the transaction.
          printf '%s\n' "deb [signed-by=${'$'}CANON_PREFIX/etc/apt/keyrings/${PACKAGE_REPOSITORY_KEYRING}] ${'$'}REPOSITORY ${'$'}SUITE ${'$'}COMPONENT" > "${'$'}SOURCES"
        }

        reclaim_stale_lock() {
          # mkdir is the per-lock-instance claim: only one waiter may inspect
          # and move this lock. The claim directory moves with a stale lock, so
          # another waiter cannot accidentally reclaim a newly-created lock.
          mkdir "${'$'}LOCK/reclaiming" 2>/dev/null || return 1
          owner_pid="${'$'}(cat "${'$'}LOCK/pid" 2>/dev/null || true)"
          case "${'$'}owner_pid" in
            ''|*[!0-9]*)
              rmdir "${'$'}LOCK/reclaiming" 2>/dev/null || true
              return 1
              ;;
          esac
          if kill -0 "${'$'}owner_pid" 2>/dev/null; then
            rmdir "${'$'}LOCK/reclaiming" 2>/dev/null || true
            return 1
          fi
          stale_lock="${'$'}LOCK.stale.${'$'}${'$'}"
          if mv "${'$'}LOCK" "${'$'}stale_lock" 2>/dev/null; then
            rm -rf "${'$'}stale_lock"
            echo "pkg: recovered stale package-operation lock (dead pid ${'$'}owner_pid)" >&2
            return 0
          fi
          rmdir "${'$'}LOCK/reclaiming" 2>/dev/null || true
          return 1
        }

        acquire_lock() {
          i=0
          while ! mkdir "${'$'}LOCK" 2>/dev/null; do
            if reclaim_stale_lock; then
              continue
            fi
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

        format_kb() {
          _kb="${'$'}1"
          case "${'$'}_kb" in
            ''|*[!0-9]*) _kb=0 ;;
          esac
          if [ "${'$'}_kb" -ge 1024 ]; then
            _mb_int=${'$'}(( _kb / 1024 ))
            _mb_dec=${'$'}(( (_kb * 10 / 1024) % 10 ))
            printf '%d.%d MB' "${'$'}_mb_int" "${'$'}_mb_dec"
          else
            printf '%d KB' "${'$'}_kb"
          fi
        }

        confirm_transaction() {
          _action_desc="${'$'}1"
          if [ "${'$'}YES_FLAG" -eq 1 ]; then
            return 0
          fi
          printf 'Do you want to continue? [Y/n] '
          if [ -t 0 ]; then
            read -r _resp || _resp="n"
          else
            if ! read -r _resp; then
              echo "" >&2
              error "standard input is not a terminal and -y was not specified"
            fi
          fi
          case "${'$'}_resp" in
            ""|[yY]|[yY][eE][sS])
              return 0
              ;;
            *)
              echo "pkg: ${'$'}{_action_desc} aborted by user."
              return 1
              ;;
          esac
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

        verify_release_signature() {
          # Fetch signed metadata independently and verify it before apt is
          # allowed to read any repository index. apt repeats the same check
          # through signed-by=; this preflight provides an early, clear failure
          # and prevents an unsigned downgrade from reaching package handling.
          fetch_metadata() {
            dest="${'$'}1"
            url="${'$'}2"
            if [ -x "${'$'}PREFIX/bin/curl" ]; then
              "${'$'}PREFIX/bin/curl" -fsSL --max-time 120 -o "${'$'}dest" "${'$'}url"
            elif [ -x "${'$'}PREFIX/bin/python3" ]; then
              "${'$'}PREFIX/bin/python3" -c 'import sys, urllib.request; urllib.request.urlretrieve(sys.argv[1], sys.argv[2])' "${'$'}url" "${'$'}dest"
            elif [ -x "${'$'}PREFIX/bin/wget" ]; then
              "${'$'}PREFIX/bin/wget" -q -O "${'$'}dest" "${'$'}url"
            else
              return 1
            fi
          }
          inrelease="${'$'}STATE/InRelease.partial"
          release="${'$'}STATE/Release.partial"
          verify_log="${'$'}STATE/gpgv.log"
          rm -f "${'$'}inrelease" "${'$'}release" "${'$'}verify_log"
          fetch_metadata "${'$'}inrelease" "${'$'}REPOSITORY/dists/${'$'}SUITE/InRelease" || error "offline or signed CodeC InRelease metadata is unavailable (HTTPS required)"
          if ! "${'$'}PREFIX/bin/gpgv" --keyring "${'$'}KEYRING" --output "${'$'}release" "${'$'}inrelease" >"${'$'}verify_log" 2>&1; then
            cat "${'$'}verify_log" >&2 2>/dev/null || true
            rm -f "${'$'}inrelease" "${'$'}release"
            error "CodeC InRelease signature verification failed; no package was installed"
          fi
          grep -qx 'Origin: CodeC' "${'$'}release" || error "signed repository Origin is not CodeC"
          grep -qx "Suite: ${'$'}SUITE" "${'$'}release" || error "signed repository suite is not ${'$'}SUITE"
          mv "${'$'}inrelease" "${'$'}STATE/InRelease"
          mv "${'$'}release" "${'$'}STATE/Release"
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

        spec_in_file() {
          # Exact byte substring check. The userland grep (BusyBox 1.38)
          # fails to match some -F patterns that are verifiably present in
          # the file (e.g. the alternatives spec strings), so the reviewed-
          # spec checks do not rely on grep at all. This must not depend on
          # python3 either: the fresh-device Phase 3 closure ships no
          # python3 (verified 2026-08-23), so a python-based check would
          # break `pkg install` preflight even with curl seeded. Command
          # substitution strips only trailing newlines, so an exact byte
          # substring mid-file still matches; the specs are single lines.
          codec_spec_body="${'$'}(cat "${'$'}1" 2>/dev/null)" || return 1
          case "${'$'}codec_spec_body" in
            *"${'$'}2"*) return 0 ;;
            *) return 1 ;;
          esac
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
          install_spec="--install \"${'$'}CANON_PREFIX/${'$'}alt_link\" \"${'$'}alt_name\" \"${'$'}CANON_PREFIX/${'$'}alt_target\" ${'$'}priority"
          slave_spec="--slave \"${'$'}CANON_PREFIX/${'$'}slave_link\" \"${'$'}slave_name\" \"${'$'}CANON_PREFIX/${'$'}slave_target\""
          remove_spec="--remove \"${'$'}alt_name\" \"${'$'}CANON_PREFIX/${'$'}alt_target\""
          for script in postinst prerm; do
            file="${'$'}control_dir/${'$'}script"
            [ -e "${'$'}file" ] || continue
            grep -q 'Automatically added by termux_step_create_alternatives' "${'$'}file" || error "${'$'}package_name ${'$'}script is not the reviewed alternatives script"
            if [ "${'$'}script" = postinst ]; then
              spec_in_file "${'$'}file" "${'$'}install_spec" || error "unexpected ${'$'}package_name install alternative"
              spec_in_file "${'$'}file" "${'$'}slave_spec" || error "unexpected ${'$'}package_name slave alternative"
            else
              spec_in_file "${'$'}file" "${'$'}remove_spec" || error "unexpected ${'$'}package_name removal alternative"
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
                /*) error "unsafe absolute symlink target in ${'$'}deb: ${'$'}target" ;;
              esac
              # Relative targets may climb with ../ as long as they stay
              # inside the CodeC prefix (Termux license symlinks, e.g.
              # share/licenses/nano -> ../../LICENSES/GPL-3.0.txt). Resolve
              # the climb against the link's directory and reject escapes.
              resolved_dir="${'$'}{member%/*}"
              remaining="${'$'}target"
              while :; do
                case "${'$'}remaining" in
                  ../*) remaining="${'$'}{remaining#../}"; resolved_dir="${'$'}{resolved_dir%/*}" ;;
                  ./*) remaining="${'$'}{remaining#./}" ;;
                  *) break ;;
                esac
              done
              case "${'$'}resolved_dir" in
                data/data/com.codeci.ide/files/usr|data/data/com.codeci.ide/files/usr/*) ;;
                *) error "unsafe symlink target in ${'$'}deb: ${'$'}target" ;;
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

        display_cache_summary() {
          _operation="${'$'}1"
          _pkg_count=0
          _total_dl_kb=0
          _total_inst_kb=0
          _pkg_list=""
          for _deb in "${'$'}CACHE"/*.deb; do
            [ -f "${'$'}_deb" ] || continue
            _p_name="${'$'}("${'$'}PREFIX/bin/dpkg-deb" -f "${'$'}_deb" Package 2>/dev/null || true)"
            [ -n "${'$'}_p_name" ] || continue
            _p_ver="${'$'}("${'$'}PREFIX/bin/dpkg-deb" -f "${'$'}_deb" Version 2>/dev/null || echo "unknown")"
            _p_inst="${'$'}("${'$'}PREFIX/bin/dpkg-deb" -f "${'$'}_deb" Installed-Size 2>/dev/null || echo 0)"
            case "${'$'}_p_inst" in
              ''|*[!0-9]*) _p_inst=0 ;;
            esac
            _deb_bytes="${'$'}(wc -c < "${'$'}_deb" 2>/dev/null || echo 0)"
            case "${'$'}_deb_bytes" in
              ''|*[!0-9]*) _deb_bytes=0 ;;
            esac
            _deb_kb=${'$'}(( (_deb_bytes + 1023) / 1024 ))
            _total_dl_kb=${'$'}(( _total_dl_kb + _deb_kb ))
            _total_inst_kb=${'$'}(( _total_inst_kb + _p_inst ))
            _pkg_count=${'$'}(( _pkg_count + 1 ))
            _fmt_dl="${'$'}(format_kb "${'$'}_deb_kb")"
            _fmt_inst="${'$'}(format_kb "${'$'}_p_inst")"
            _pkg_list="${'$'}{_pkg_list}    • ${'$'}{_p_name} ${'$'}{_p_ver} (download: ${'$'}{_fmt_dl}, installed: ~${'$'}{_fmt_inst})\n"
          done

          _fmt_total_dl="${'$'}(format_kb "${'$'}_total_dl_kb")"
          _fmt_total_inst="${'$'}(format_kb "${'$'}_total_inst_kb")"

          printf '\nCodeC Package Manager — Transaction Summary:\n'
          printf '  Operation:        %s\n' "${'$'}_operation"
          printf '  Packages (%d):\n' "${'$'}_pkg_count"
          printf '%b' "${'$'}_pkg_list"
          printf '  Preflight:        PASSED (signed repo, verified ABI, prefix-confined, script allowlist)\n'
          printf '  Total download:   %s\n' "${'$'}_fmt_total_dl"
          printf '  Space change:     ~%s\n\n' "${'$'}_fmt_total_inst"
        }

        install_specs() {
          [ "${'$'}#" -gt 0 ] || error "usage: pkg install [-y] <name> [name ...]"
          before="${'$'}(free_kb)"
          if [ -n "${'$'}before" ] && [ "${'$'}before" -lt "${'$'}MIN_FREE_KB" ]; then
            error "insufficient disk space under ${'$'}PREFIX (${'$'}before KB free; need at least ${'$'}MIN_FREE_KB KB)"
          fi
          marker="${'$'}STATE/transaction.pending"
          printf '%s\n' "${'$'}*" > "${'$'}marker"
          verify_release_signature
          rm -f "${'$'}CACHE"/*.deb
          friendly_apt apt_get --download-only --yes --no-install-recommends install "${'$'}@" || { rm -f "${'$'}marker" "${'$'}CACHE"/*.deb; return "${'$'}?"; }
          preflight_cache
          display_cache_summary "Install"
          if ! confirm_transaction "installation"; then
            rm -f "${'$'}marker" "${'$'}CACHE"/*.deb
            return 0
          fi
          # The package set was validated before dpkg is allowed to run. The
          # repository policy rejects maintainer scripts, so no untrusted code
          # is executed as part of this first milestone.
          friendly_apt apt_get --yes --no-install-recommends install "${'$'}@" || { rm -f "${'$'}marker"; return "${'$'}?"; }
          rm -f "${'$'}marker" "${'$'}CACHE"/*.deb
          echo "pkg: installed ${'$'}*"
        }

        upgrade_packages() {
          before="${'$'}(free_kb)"
          if [ -n "${'$'}before" ] && [ "${'$'}before" -lt "${'$'}MIN_FREE_KB" ]; then
            error "insufficient disk space under ${'$'}PREFIX (${'$'}before KB free; need at least ${'$'}MIN_FREE_KB KB)"
          fi
          marker="${'$'}STATE/transaction.pending"
          printf '%s\n' upgrade > "${'$'}marker"
          verify_release_signature
          rm -f "${'$'}CACHE"/*.deb
          friendly_apt apt_get --download-only --yes --no-install-recommends upgrade || { rm -f "${'$'}marker" "${'$'}CACHE"/*.deb; return "${'$'}?"; }
          found=0
          for _deb in "${'$'}CACHE"/*.deb; do
            if [ -f "${'$'}_deb" ]; then found=1; break; fi
          done
          if [ "${'$'}found" -eq 0 ]; then
            rm -f "${'$'}marker"
            echo "pkg: all packages are up to date."
            return 0
          fi
          preflight_cache
          display_cache_summary "Upgrade"
          if ! confirm_transaction "upgrade"; then
            rm -f "${'$'}marker" "${'$'}CACHE"/*.deb
            return 0
          fi
          friendly_apt apt_get --yes --no-install-recommends upgrade || { rm -f "${'$'}marker"; return "${'$'}?"; }
          rm -f "${'$'}marker" "${'$'}CACHE"/*.deb
          echo "pkg: upgraded CodeC packages"
        }

        repair() {
          [ -e "${'$'}STATE/transaction.pending" ] || { echo "pkg: no interrupted transaction"; return 0; }
          echo "pkg: repairing the recorded transaction: ${'$'}(cat "${'$'}STATE/transaction.pending")"
          friendly_apt apt_get --yes --no-install-recommends -f install
          rm -f "${'$'}STATE/transaction.pending"
        }

        YES_FLAG=0
        VERBOSE_FLAG=0
        COMMAND=""
        TARGETS=""

        while [ "${'$'}#" -gt 0 ]; do
          case "${'$'}1" in
            -y|--yes|--assume-yes)
              YES_FLAG=1
              shift
              ;;
            -v|--verbose)
              VERBOSE_FLAG=1
              shift
              ;;
            -h|--help)
              if [ -z "${'$'}COMMAND" ]; then
                COMMAND="help"
              fi
              shift
              ;;
            -*)
              error "unknown option '${'$'}1' (try: pkg help)"
              ;;
            *)
              if [ -z "${'$'}COMMAND" ]; then
                COMMAND="${'$'}1"
              else
                TARGETS="${'$'}{TARGETS}${'$'}{TARGETS:+ }${'$'}1"
              fi
              shift
              ;;
          esac
        done

        COMMAND="${'$'}{COMMAND:-help}"

        if [ "${'$'}COMMAND" = help ] || [ "${'$'}COMMAND" = -h ] || [ "${'$'}COMMAND" = --help ]; then
          cat <<'HELP'
CodeC packages (CodeC repository only)
  pkg update                 refresh CodeC package indexes
  pkg search <name>          search the cached CodeC catalog
  pkg install [-y] <name>... download, verify, and install packages
  pkg upgrade [-y]           upgrade installed CodeC packages
  pkg uninstall [-y] <name>  remove packages from this userland
  pkg repair                 recover an interrupted transaction

Flags:
  -y, --yes                  automatic yes to confirmation prompts
  -h, --help                 show this help message

The Phase 3 development channel requires a CodeC apt/dpkg bootstrap. It never
uses official com.termux packages or repositories.
HELP
          exit 0
        fi
        require_backend
        set -- ${'$'}TARGETS
        case "${'$'}COMMAND" in
          update)
            acquire_lock
            verify_release_signature
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
            [ "${'$'}#" -gt 0 ] || error "usage: pkg uninstall [-y] <name> [name ...]"
            for name in "${'$'}@"; do
              case "${'$'}name" in
                bash|busybox|apt|dpkg|codec-pkg) error "refusing to remove CodeC base package ${'$'}name" ;;
              esac
            done
            acquire_lock
            printf '\nCodeC Package Manager — Transaction Summary:\n'
            printf '  Operation:        Uninstall\n'
            printf '  Packages to remove: %s\n\n' "${'$'}*"
            if ! confirm_transaction "uninstallation"; then
              return 0
            fi
            friendly_apt apt_get --yes remove "${'$'}@"
            echo "pkg: uninstalled ${'$'}*"
            ;;
          repair)
            acquire_lock
            repair
            ;;
          *) error "unknown command '${'$'}COMMAND' (try: pkg help)" ;;
        esac
    """.trimIndent() + "\n"

    fun bashShim(): String = """
        #!/system/bin/sh
        # Phase 1 stand-in: Android's /system/bin/sh until the bootstrap
        # ships a real bash (Phase 2). Keeps "exec bash" working today.
        exec /system/bin/sh "${'$'}@"
    """.trimIndent() + "\n"

    fun setupStorageScript(): String = """
        #!/system/bin/sh
        # CodeC codec-setup-storage — set up symlinks to Android shared storage in ~/storage.
        # Analogous to termux-setup-storage (Phase 4.1).
        set -u

        PREFIX="${'$'}{PREFIX:-$(cd "${'$'}{0%/*}/.." 2>/dev/null && pwd)}"
        HOME="${'$'}{HOME:-$(cd "${'$'}PREFIX/../home" 2>/dev/null && pwd)}"
        STORAGE_DIR="${'$'}HOME/storage"

        echo "Setting up shared storage symlinks in ${'$'}STORAGE_DIR..."

        mkdir -p "${'$'}STORAGE_DIR" || {
          echo "codec-setup-storage: cannot create ${'$'}STORAGE_DIR" >&2
          exit 1
        }

        # Standard Android external storage root
        SHARED_ROOT="/storage/emulated/0"
        [ -d "${'$'}SHARED_ROOT" ] || SHARED_ROOT="${'$'}{EXTERNAL_STORAGE:-/sdcard}"

        setup_link() {
          target="${'$'}1"
          link_name="${'$'}2"
          link_path="${'$'}STORAGE_DIR/${'$'}link_name"
          rm -f "${'$'}link_path" 2>/dev/null
          ln -s "${'$'}target" "${'$'}link_path" 2>/dev/null || true
          if [ -L "${'$'}link_path" ] || [ -e "${'$'}link_path" ]; then
            echo "  ~/storage/${'$'}link_name -> ${'$'}target"
          fi
        }

        setup_link "${'$'}SHARED_ROOT" "shared"
        setup_link "${'$'}SHARED_ROOT/Download" "downloads"
        setup_link "${'$'}SHARED_ROOT/Documents" "documents"
        setup_link "${'$'}SHARED_ROOT/DCIM" "dcim"
        setup_link "${'$'}SHARED_ROOT/Pictures" "pictures"
        setup_link "${'$'}SHARED_ROOT/Music" "music"
        setup_link "${'$'}SHARED_ROOT/Movies" "movies"

        # Look for secondary external storage mounts (e.g. /storage/XXXX-XXXX)
        ext_idx=1
        for dev in /storage/*; do
          [ -d "${'$'}dev" ] || continue
          case "${'$'}{dev##*/}" in
            emulated|self|knox-emulated) continue ;;
            *)
              setup_link "${'$'}dev" "external-${'$'}ext_idx"
              ext_idx=${'$'}((ext_idx + 1))
              ;;
          esac
        done

        # Check read/write access to shared root
        probe_file="${'$'}SHARED_ROOT/Download/.codec_storage_probe_${'$'}${'$'}"
        if touch "${'$'}probe_file" 2>/dev/null; then
          rm -f "${'$'}probe_file" 2>/dev/null || true
          echo "Storage setup complete — write access OK."
        else
          # Signal terminal emulator via OSC 1337 in-band escape sequence to pop up permission screen
          printf '\033]1337;CodeCRequestStorage\007'
          echo "Storage setup complete."
          echo "Requesting All Files Access permission from CodeC..."
          echo "Please grant 'Allow access to manage all files' in the Android prompt."
        fi
    """.trimIndent() + "\n"

    data class StorageLink(val name: String, val target: File)
    data class StorageSetupResult(
        val success: Boolean,
        val storageDir: File,
        val createdLinks: List<StorageLink>,
        val errorMessage: String? = null
    )

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun setupStorageDirectory(
        homeDir: File,
        externalStorageDir: File = Environment.getExternalStorageDirectory(),
        storageRoot: File = File("/storage")
    ): StorageSetupResult {
        val storageDir = File(homeDir, "storage")
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            return StorageSetupResult(
                success = false,
                storageDir = storageDir,
                createdLinks = emptyList(),
                errorMessage = "Cannot create directory ${storageDir.absolutePath}"
            )
        }

        val targetPairs = mutableListOf(
            "shared" to externalStorageDir,
            "downloads" to File(externalStorageDir, "Download"),
            "documents" to File(externalStorageDir, "Documents"),
            "dcim" to File(externalStorageDir, "DCIM"),
            "pictures" to File(externalStorageDir, "Pictures"),
            "music" to File(externalStorageDir, "Music"),
            "movies" to File(externalStorageDir, "Movies")
        )

        if (storageRoot.isDirectory) {
            var extIndex = 1
            storageRoot.listFiles()?.filter { it.isDirectory }?.forEach { dev ->
                if (dev.name !in setOf("emulated", "self", "knox-emulated")) {
                    targetPairs.add("external-$extIndex" to dev)
                    extIndex++
                }
            }
        }

        val links = mutableListOf<StorageLink>()
        for ((name, target) in targetPairs) {
            val linkFile = File(storageDir, name)
            if (createOrUpdateSymlink(target, linkFile)) {
                links.add(StorageLink(name, target))
            }
        }

        return StorageSetupResult(
            success = true,
            storageDir = storageDir,
            createdLinks = links
        )
    }

    fun createOrUpdateSymlink(target: File, link: File): Boolean {
        try {
            link.delete()
        } catch (_: Exception) {
        }
        // Try android.system.Os.symlink on Android
        try {
            val osClass = Class.forName("android.system.Os")
            val symlinkMethod = osClass.getMethod("symlink", String::class.java, String::class.java)
            symlinkMethod.invoke(null, target.absolutePath, link.absolutePath)
            if (link.exists()) return true
        } catch (_: Throwable) {
        }
        // Try java.nio.file.Files via reflection (safe on host JVM and Android 26+)
        try {
            val filesClass = Class.forName("java.nio.file.Files")
            val pathsClass = Class.forName("java.nio.file.Paths")
            val getPathMethod = pathsClass.getMethod("get", String::class.java, Array<String>::class.java)
            val linkPath = getPathMethod.invoke(null, link.absolutePath, emptyArray<String>())
            val targetPath = getPathMethod.invoke(null, target.absolutePath, emptyArray<String>())
            val fileAttrArray = java.lang.reflect.Array.newInstance(Class.forName("java.nio.file.attribute.FileAttribute"), 0)
            val createSymlinkMethod = filesClass.getMethod(
                "createSymbolicLink",
                Class.forName("java.nio.file.Path"),
                Class.forName("java.nio.file.Path"),
                fileAttrArray.javaClass
            )
            createSymlinkMethod.invoke(null, linkPath, targetPath, fileAttrArray)
            if (link.exists()) return true
        } catch (_: Throwable) {
        }
        // Fallback: ln -s process
        return try {
            val process = ProcessBuilder("ln", "-s", target.absolutePath, link.absolutePath)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0 && link.exists()
        } catch (_: Exception) {
            false
        }
    }

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
        installRepositoryKey(prefix)

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
        writeExecutable(File(bin, "codec-setup-storage"), ShellEnvironment.setupStorageScript())
        writeExecutable(File(bin, "termux-setup-storage"), ShellEnvironment.setupStorageScript())
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

    private fun installRepositoryKey(prefix: File) {
        val keyDir = File(prefix, "etc/apt/keyrings")
        check(keyDir.mkdirs() || keyDir.isDirectory) { "cannot create CodeC apt keyring directory" }
        val target = File(keyDir, ShellEnvironment.PACKAGE_REPOSITORY_KEYRING)
        val publicKey = context.assets.open(ShellEnvironment.PACKAGE_REPOSITORY_KEYRING).use {
            it.readBytes()
        }
        check(publicKey.isNotEmpty()) { "CodeC repository public key asset is empty" }
        if (target.isFile && target.readBytes().contentEquals(publicKey)) return
        val partial = File(keyDir, ".${ShellEnvironment.PACKAGE_REPOSITORY_KEYRING}.partial")
        partial.writeBytes(publicKey)
        partial.setReadable(true, false)
        partial.setWritable(true, true)
        check(partial.renameTo(target)) { "cannot install CodeC repository public key" }
    }

    private fun writeExecutable(file: File, body: String) {
        file.parentFile?.mkdirs()
        file.writeText(body)
        file.setExecutable(true, false)
    }
}
