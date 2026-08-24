#!/usr/bin/env python3
"""Tests for CodeC Phase 4 Part 4.2: pkg package-install confirmation UX."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SHELL_ENV_KT = REPO_ROOT / "app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt"


def get_pkg_script() -> str:
    """Extract and unescape the pkg script from ShellEnvironment.kt."""
    content = SHELL_ENV_KT.read_text(encoding="utf-8")
    match = re.search(
        r'fun pkgScript\(\): String = """\n(.*?)\n\s*"""\.trimIndent\(\)',
        content,
        re.DOTALL,
    )
    if not match:
        raise RuntimeError("Could not find pkgScript in ShellEnvironment.kt")
    script = match.group(1)
    # Simulate Kotlin trimIndent
    lines = script.split("\n")
    min_indent = min(len(line) - len(line.lstrip()) for line in lines if line.strip())
    script = "\n".join(line[min_indent:] if len(line) >= min_indent else line for line in lines)
    # Replace Kotlin template variables
    script = script.replace("${PACKAGE_REPOSITORY_URL}", "https://pabi277.github.io/CodeC/dev")
    script = script.replace("${PACKAGE_REPOSITORY_SUITE}", "stable")
    script = script.replace("${PACKAGE_REPOSITORY_COMPONENT}", "main")
    script = script.replace("${PACKAGE_REPOSITORY_KEYRING}", "codec-archive-keyring-v1.gpg")
    script = script.replace("${SIGNING_SUBKEY_FINGERPRINT}", "328500868CE9B0F74B62CEFC1D7D52F6F8135015")
    script = script.replace("${SIGNING_PRIMARY_FINGERPRINT}", "3185B4D219C5EF30B263F5E50A458891ED0FB8D3")
    script = script.replace("${'$'}", "$")
    return script


class PkgConfirmationTest(unittest.TestCase):
    """Test suite verifying pkg confirmation prompts, flags, and transaction summaries."""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.prefix = self.root / "usr"
        self.bin = self.prefix / "bin"
        self.bin.mkdir(parents=True)
        self.keyrings = self.prefix / "etc/apt/keyrings"
        self.keyrings.mkdir(parents=True)
        (self.keyrings / "codec-archive-keyring-v1.gpg").write_text("test-key")

        self.pkg_script_path = self.bin / "pkg"
        self.pkg_script_path.write_text(get_pkg_script())
        self.pkg_script_path.chmod(0o755)

        # Mock standard backend tools
        self._create_mock_tool("apt-get")
        self._create_mock_tool("apt-cache")
        self._create_mock_tool("dpkg")
        self._create_mock_tool("dpkg-deb")
        self._create_mock_tool("gpgv", """#!/bin/sh
out=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--output" ]; then out="$2"; shift 2; else shift; fi
done
if [ -n "$out" ]; then
  printf "Origin: CodeC\\nSuite: stable\\n" > "$out"
fi
exit 0
""")
        self._create_mock_tool("curl", """#!/bin/sh
dest=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "-o" ]; then dest="$2"; shift 2; else shift; fi
done
if [ -n "$dest" ]; then
  printf "Origin: CodeC\\nSuite: stable\\n" > "$dest"
fi
exit 0
""")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _create_mock_tool(self, name: str, body: str = "#!/bin/sh\nexit 0\n") -> Path:
        tool = self.bin / name
        tool.write_text(body)
        tool.chmod(0o755)
        return tool

    def _run_pkg(self, *args: str, input_str: str | None = None, check: bool = False) -> subprocess.CompletedProcess:
        env = os.environ.copy()
        env["PREFIX"] = str(self.prefix)
        env["PATH"] = f"{self.bin}:{env.get('PATH', '')}"
        return subprocess.run(
            ["/bin/sh", str(self.pkg_script_path), *args],
            input=input_str,
            text=True,
            capture_output=True,
            env=env,
            check=check,
        )

    def test_pkg_help_displays_flags_and_is_offline(self) -> None:
        res = self._run_pkg("help")
        self.assertEqual(res.returncode, 0)
        self.assertIn("pkg install [-y] <name>...", res.stdout)
        self.assertIn("pkg upgrade [-y]", res.stdout)
        self.assertIn("pkg uninstall [-y] <name>", res.stdout)
        self.assertIn("-y, --yes", res.stdout)
        self.assertIn("-h, --help", res.stdout)

        # Short flag -h and long flag --help
        res_h = self._run_pkg("-h")
        self.assertEqual(res_h.returncode, 0)
        self.assertIn("CodeC packages", res_h.stdout)

        res_help = self._run_pkg("--help")
        self.assertEqual(res_help.returncode, 0)
        self.assertIn("CodeC packages", res_help.stdout)

    def test_pkg_install_with_yes_flag_skips_prompt(self) -> None:
        # Mock apt-get to create a fake .deb in cache during download
        apt_get_script = f"""#!/bin/sh
case "$*" in
  *--download-only*)
    mkdir -p "{self.prefix}/var/cache/apt/archives"
    deb="{self.prefix}/var/cache/apt/archives/nano_9.2_aarch64.deb"
    printf "fakedeb" > "$deb"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
"""
        self._create_mock_tool("apt-get", apt_get_script)

        # Mock dpkg-deb to return package metadata
        dpkg_deb_script = """#!/bin/sh
case "$*" in
  *"-f "*Package*) echo "nano"; exit 0 ;;
  *"-f "*Version*) echo "9.2"; exit 0 ;;
  *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
  *"-f "*Installed-Size*) echo "840"; exit 0 ;;
  *"--control "*) exit 0 ;;
  *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/nano"; exit 0 ;;
  *) exit 0 ;;
esac
"""
        self._create_mock_tool("dpkg-deb", dpkg_deb_script)

        # Mock dpkg --print-architecture
        self._create_mock_tool("dpkg", """#!/bin/sh
if [ "$1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
exit 0
""")

        # Mock gpgv to write valid InRelease/Release
        gpgv_script = f"""#!/bin/sh
out=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--output" ]; then out="$2"; shift 2; else shift; fi
done
if [ -n "$out" ]; then
  printf "Origin: CodeC\\nSuite: stable\\n" > "$out"
fi
exit 0
"""
        self._create_mock_tool("gpgv", gpgv_script)

        # 1) Test with -y flag after command
        res = self._run_pkg("install", "-y", "nano")
        self.assertEqual(res.returncode, 0, f"pkg failed: {res.stderr}")
        self.assertIn("Transaction Summary", res.stdout)
        self.assertIn("nano 9.2", res.stdout)
        self.assertIn("Preflight:        PASSED", res.stdout)
        self.assertIn("pkg: installed nano", res.stdout)

        # 2) Test with -y flag before command (pkg -y install nano)
        res_pre = self._run_pkg("-y", "install", "nano")
        self.assertEqual(res_pre.returncode, 0, f"pkg failed: {res_pre.stderr}")
        self.assertIn("pkg: installed nano", res_pre.stdout)

    def test_pkg_install_prompt_accept_y(self) -> None:
        # Mock apt-get to create a fake .deb in cache during download
        apt_get_script = f"""#!/bin/sh
case "$*" in
  *--download-only*)
    mkdir -p "{self.prefix}/var/cache/apt/archives"
    deb="{self.prefix}/var/cache/apt/archives/nano_9.2_aarch64.deb"
    printf "fakedeb-bytes" > "$deb"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
"""
        self._create_mock_tool("apt-get", apt_get_script)

        dpkg_deb_script = """#!/bin/sh
case "$*" in
  *"-f "*Package*) echo "nano"; exit 0 ;;
  *"-f "*Version*) echo "9.2"; exit 0 ;;
  *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
  *"-f "*Installed-Size*) echo "840"; exit 0 ;;
  *"--control "*) exit 0 ;;
  *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/nano"; exit 0 ;;
  *) exit 0 ;;
esac
"""
        self._create_mock_tool("dpkg-deb", dpkg_deb_script)
        self._create_mock_tool("dpkg", """#!/bin/sh
if [ "$1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
exit 0
""")

        gpgv_script = """#!/bin/sh
out=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--output" ]; then out="$2"; shift 2; else shift; fi
done
if [ -n "$out" ]; then
  printf "Origin: CodeC\\nSuite: stable\\n" > "$out"
fi
exit 0
"""
        self._create_mock_tool("gpgv", gpgv_script)

        # Interactive simulation: user inputs 'y'
        res = self._run_pkg("install", "nano", input_str="y\n")
        self.assertEqual(res.returncode, 0, f"pkg failed: {res.stderr}")
        self.assertIn("Do you want to continue? [Y/n]", res.stdout)
        self.assertIn("pkg: installed nano", res.stdout)

        # Interactive simulation: user presses Enter (default Yes)
        res_enter = self._run_pkg("install", "nano", input_str="\n")
        self.assertEqual(res_enter.returncode, 0, f"pkg failed: {res_enter.stderr}")
        self.assertIn("pkg: installed nano", res_enter.stdout)

    def test_pkg_install_prompt_abort_n(self) -> None:
        apt_get_script = f"""#!/bin/sh
case "$*" in
  *--download-only*)
    mkdir -p "{self.prefix}/var/cache/apt/archives"
    deb="{self.prefix}/var/cache/apt/archives/nano_9.2_aarch64.deb"
    printf "fakedeb-bytes" > "$deb"
    exit 0
    ;;
  *)
    echo "ERROR: dpkg install mutation step should NOT have been called" >&2
    exit 99
    ;;
esac
"""
        self._create_mock_tool("apt-get", apt_get_script)

        dpkg_deb_script = """#!/bin/sh
case "$*" in
  *"-f "*Package*) echo "nano"; exit 0 ;;
  *"-f "*Version*) echo "9.2"; exit 0 ;;
  *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
  *"-f "*Installed-Size*) echo "840"; exit 0 ;;
  *"--control "*) exit 0 ;;
  *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/nano"; exit 0 ;;
  *) exit 0 ;;
esac
"""
        self._create_mock_tool("dpkg-deb", dpkg_deb_script)
        self._create_mock_tool("dpkg", """#!/bin/sh
if [ "$1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
exit 0
""")

        gpgv_script = """#!/bin/sh
out=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--output" ]; then out="$2"; shift 2; else shift; fi
done
if [ -n "$out" ]; then
  printf "Origin: CodeC\\nSuite: stable\\n" > "$out"
fi
exit 0
"""
        self._create_mock_tool("gpgv", gpgv_script)

        # User inputs 'n'
        res = self._run_pkg("install", "nano", input_str="n\n")
        self.assertEqual(res.returncode, 0)
        self.assertIn("pkg: installation aborted by user.", res.stdout)
        self.assertNotIn("pkg: installed nano", res.stdout)
        self.assertNotIn("ERROR", res.stderr)

        # Ensure no pending transaction marker remains
        marker = self.prefix / "var/lib/codec-pkg/transaction.pending"
        self.assertFalse(marker.exists())

    def test_pkg_uninstall_confirmation(self) -> None:
        self._create_mock_tool("dpkg", "#!/bin/sh\nexit 0\n")
        self._create_mock_tool("apt-get", """#!/bin/sh
case "$*" in
  *remove*) echo "removing package"; exit 0 ;;
  *) exit 0 ;;
esac
""")

        # User enters 'n' -> aborts
        res_abort = self._run_pkg("uninstall", "nano", input_str="n\n")
        self.assertEqual(res_abort.returncode, 0)
        self.assertIn("pkg: uninstallation aborted by user.", res_abort.stdout)
        self.assertNotIn("pkg: uninstalled nano", res_abort.stdout)

        # User enters 'y' -> removes
        res_yes = self._run_pkg("uninstall", "nano", input_str="y\n")
        self.assertEqual(res_yes.returncode, 0)
        self.assertIn("pkg: uninstalled nano", res_yes.stdout)

        # With -y flag -> removes directly without prompt
        res_flag = self._run_pkg("uninstall", "-y", "nano")
        self.assertEqual(res_flag.returncode, 0)
        self.assertIn("pkg: uninstalled nano", res_flag.stdout)

    def test_pkg_install_multiple_packages_and_mb_format(self) -> None:
        apt_get_script = f"""#!/bin/sh
case "$*" in
  *--download-only*)
    mkdir -p "{self.prefix}/var/cache/apt/archives"
    # Create two deb files: one small, one large (> 1MB)
    deb1="{self.prefix}/var/cache/apt/archives/nano_9.2_aarch64.deb"
    deb2="{self.prefix}/var/cache/apt/archives/libmagic_5.45_aarch64.deb"
    printf "%s" "nano-bytes" > "$deb1"
    # 1.5 MB dummy
    dd if=/dev/zero of="$deb2" bs=1024 count=1500 2>/dev/null
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
"""
        self._create_mock_tool("apt-get", apt_get_script)

        dpkg_deb_script = """#!/bin/sh
deb="$2"
case "$deb" in
  *nano*)
    case "$*" in
      *"-f "*Package*) echo "nano"; exit 0 ;;
      *"-f "*Version*) echo "9.2"; exit 0 ;;
      *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
      *"-f "*Installed-Size*) echo "840"; exit 0 ;;
      *"--control "*) exit 0 ;;
      *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/nano"; exit 0 ;;
    esac
    ;;
  *libmagic*)
    case "$*" in
      *"-f "*Package*) echo "libmagic"; exit 0 ;;
      *"-f "*Version*) echo "5.45"; exit 0 ;;
      *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
      *"-f "*Installed-Size*) echo "2048"; exit 0 ;;
      *"--control "*) exit 0 ;;
      *"--contents "*) echo "data/data/com.codeci.ide/files/usr/lib/libmagic.so"; exit 0 ;;
    esac
    ;;
esac
exit 0
"""
        self._create_mock_tool("dpkg-deb", dpkg_deb_script)
        self._create_mock_tool("dpkg", """#!/bin/sh
if [ "$1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
exit 0
""")

        res = self._run_pkg("install", "-y", "nano", "libmagic")
        self.assertEqual(res.returncode, 0, f"pkg failed: {res.stderr}")
        self.assertIn("Packages (2):", res.stdout)
        self.assertIn("nano 9.2", res.stdout)
        self.assertIn("libmagic 5.45", res.stdout)
        self.assertIn("MB", res.stdout)
        self.assertIn("pkg: installed nano libmagic", res.stdout)

    def test_pkg_install_non_interactive_without_yes_flag_aborts(self) -> None:
        apt_get_script = f"""#!/bin/sh
case "$*" in
  *--download-only*)
    mkdir -p "{self.prefix}/var/cache/apt/archives"
    deb="{self.prefix}/var/cache/apt/archives/nano_9.2_aarch64.deb"
    printf "fakedeb" > "$deb"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
"""
        self._create_mock_tool("apt-get", apt_get_script)
        self._create_mock_tool("dpkg-deb", """#!/bin/sh
case "$*" in
  *"-f "*Package*) echo "nano"; exit 0 ;;
  *"-f "*Version*) echo "9.2"; exit 0 ;;
  *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
  *"-f "*Installed-Size*) echo "840"; exit 0 ;;
  *"--control "*) exit 0 ;;
  *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/nano"; exit 0 ;;
  *) exit 0 ;;
esac
""")
        self._create_mock_tool("dpkg", """#!/bin/sh
if [ "$1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
exit 0
""")

        # When stdin is empty (EOF immediately), should abort with error
        res = self._run_pkg("install", "nano", input_str="")
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("standard input is not a terminal and -y was not specified", res.stderr)

    def test_pkg_upgrade_flow(self) -> None:
        # 1) When all packages are up to date
        self._create_mock_tool("apt-get", """#!/bin/sh
case "$*" in
  *--download-only*)
    # No debs downloaded
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
""")
        res_uptodate = self._run_pkg("upgrade")
        self.assertEqual(res_uptodate.returncode, 0)
        self.assertIn("pkg: all packages are up to date.", res_uptodate.stdout)

        # 2) When packages are available to upgrade
        apt_get_script = f"""#!/bin/sh
case "$*" in
  *--download-only*)
    mkdir -p "{self.prefix}/var/cache/apt/archives"
    deb="{self.prefix}/var/cache/apt/archives/sed_4.9_aarch64.deb"
    printf "fakedeb" > "$deb"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
"""
        self._create_mock_tool("apt-get", apt_get_script)
        self._create_mock_tool("dpkg-deb", """#!/bin/sh
case "$*" in
  *"-f "*Package*) echo "sed"; exit 0 ;;
  *"-f "*Version*) echo "4.9"; exit 0 ;;
  *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
  *"-f "*Installed-Size*) echo "420"; exit 0 ;;
  *"--control "*) exit 0 ;;
  *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/sed"; exit 0 ;;
  *) exit 0 ;;
esac
""")
        self._create_mock_tool("dpkg", """#!/bin/sh
if [ "$1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
exit 0
""")

        # Upgrade with -y flag
        res_up_y = self._run_pkg("upgrade", "-y")
        self.assertEqual(res_up_y.returncode, 0)
        self.assertIn("Transaction Summary", res_up_y.stdout)
        self.assertIn("Operation:        Upgrade", res_up_y.stdout)
        self.assertIn("sed 4.9", res_up_y.stdout)
        self.assertIn("pkg: upgraded CodeC packages", res_up_y.stdout)

        # Upgrade with user aborting 'n'
        res_up_n = self._run_pkg("upgrade", input_str="n\n")
        self.assertEqual(res_up_n.returncode, 0)
        self.assertIn("pkg: upgrade aborted by user.", res_up_n.stdout)
        self.assertNotIn("pkg: upgraded CodeC packages", res_up_n.stdout)

    def test_pkg_unknown_command_and_options(self) -> None:
        res_cmd = self._run_pkg("foobar")
        self.assertNotEqual(res_cmd.returncode, 0)
        self.assertIn("unknown command 'foobar'", res_cmd.stderr)

        res_opt = self._run_pkg("--invalid-flag")
        self.assertNotEqual(res_opt.returncode, 0)
        self.assertIn("unknown option '--invalid-flag'", res_opt.stderr)

    def test_pkg_status_trust_indicator_command(self) -> None:
        # Test pkg status
        res_status = self._run_pkg("status")
        self.assertEqual(res_status.returncode, 0, f"pkg status failed: {res_status.stderr}")
        self.assertIn("CodeC Package Repository & Trust Status:", res_status.stdout)
        self.assertIn("https://pabi277.github.io/CodeC/dev", res_status.stdout)
        self.assertIn("stable/main", res_status.stdout)
        self.assertIn("328500868CE9B0F74B62CEFC1D7D52F6F8135015", res_status.stdout)
        self.assertIn("3185B4D219C5EF30B263F5E50A458891ED0FB8D3", res_status.stdout)
        self.assertIn("Installed & Active", res_status.stdout)

        # Test aliases pkg trust and pkg channel
        res_trust = self._run_pkg("trust")
        self.assertEqual(res_trust.returncode, 0)
        self.assertIn("Trust Model", res_trust.stdout)

        res_channel = self._run_pkg("channel")
        self.assertEqual(res_channel.returncode, 0)
        self.assertIn("Channel:", res_channel.stdout)

    def test_pkg_friendly_hint_on_unindexed_package(self) -> None:
        # Mock apt-get failing with Unable to locate package
        self._create_mock_tool("apt-get", """#!/bin/sh
echo "E: Unable to locate package foobar" >&2
exit 100
""")
        res = self._run_pkg("install", "-y", "foobar")
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("package not found; run 'pkg update' first to refresh the package catalog.", res.stderr)


if __name__ == "__main__":
    unittest.main()
