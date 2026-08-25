#!/usr/bin/env python3
"""Host tests for Part B (bootstrap correctness):
codec-packages/scripts/plan-bootstrap.py + the closure/md5sums/alternatives
wiring inside codec-packages/scripts/assemble-bootstrap.sh.

Fixtures mirror the pinned upstream evidence (termux-packages @
1bbe66903526df2e8af51e704316bc68ede72603): the verbatim .alternatives files
of busybox / less / coreutils, and the dpkg admin-database format measured
against a live dpkg reference implementation.
"""

from __future__ import annotations

import hashlib
import importlib.util
import io
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import shutil
import subprocess
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSEMBLE = ROOT / "codec-packages" / "scripts" / "assemble-bootstrap.sh"
PLAN_PATH = ROOT / "codec-packages" / "scripts" / "plan-bootstrap.py"
PREFIX = "/data/data/com.codeci.ide/files/usr"

_spec = importlib.util.spec_from_file_location("plan_bootstrap", PLAN_PATH)
plan = importlib.util.module_from_spec(_spec)
sys.modules["plan_bootstrap"] = plan  # dataclasses resolve via sys.modules
_spec.loader.exec_module(plan)  # type: ignore[union-attr]

# --------------------------------------------------------------------------
# Verbatim upstream .alternatives evidence (fetched from the pinned ref).
# --------------------------------------------------------------------------

BUSYBOX_ALTERNATIVES = """Name: vi
Link: bin/vi
Alternative: libexec/busybox/vi
Priority: 10

Name: editor
Link: bin/editor
Alternative: libexec/busybox/vi
Priority: 10

Name: pager
Link: bin/pager
Alternative: libexec/busybox/less
Priority: 10

Name: nc
Link: bin/nc
Alternative: libexec/busybox/nc
Dependents:
  bin/ncat   ncat   libexec/busybox/nc
  bin/netcat netcat libexec/busybox/nc
Priority: 10

# vim: ft=raml
"""

LESS_ALTERNATIVES = """Name: pager
Link: bin/pager
Alternative: bin/less
Dependents:
  share/man/man1/pager.1.gz pager.1.gz share/man/man1/less.1.gz
Priority: 50 # default

# vim: ft=raml
"""

CAT_PAGER_ALTERNATIVES = """Name: pager
Link: bin/pager
Alternative: libexec/coreutils/cat
Dependents:
  share/man/man1/pager.1.gz pager.1.gz share/man/man1/cat.1.gz
Priority: 1

# vim: ft=raml
"""


class ParseDependencyNamesTest(unittest.TestCase):
    def test_version_constraints_and_alternatives(self) -> None:
        self.assertEqual(
            plan.parse_dependency_names("perl, clang, make, dpkg (= 1.22.6-5)"),
            ["perl", "clang", "make", "dpkg"],
        )
        self.assertEqual(
            plan.parse_dependency_names("libfoo | libbar, baz (>= 1.2), qux:any"),
            ["libfoo", "baz", "qux"],
        )
        self.assertEqual(plan.parse_dependency_names(""), [])


class SelectClosureTest(unittest.TestCase):
    def records(self):
        return {
            "a": plan.DebRecord("a", "/debs/a.deb", ["b", "c"]),
            "b": plan.DebRecord("b", "/debs/b.deb", ["c"]),
            "c": plan.DebRecord("c", "/debs/c.deb", []),
            "x": plan.DebRecord("x", "/debs/x.deb", []),
        }

    def test_transitive_closure_excludes_unreferenced_packages(self) -> None:
        self.assertEqual(
            sorted(plan.select_closure(self.records(), ["a"])),
            ["a", "b", "c"],
        )

    def test_cycle_terminates(self) -> None:
        rec = {
            "a": plan.DebRecord("a", "/debs/a.deb", ["b"]),
            "b": plan.DebRecord("b", "/debs/b.deb", ["a"]),
        }
        self.assertEqual(sorted(plan.select_closure(rec, ["a"])), ["a", "b"])

    def test_unresolved_dependency_fails_loudly(self) -> None:
        rec = {"a": plan.DebRecord("a", "/debs/a.deb", ["ghost"])}
        with self.assertRaises(plan.UnresolvedDependency) as ctx:
            plan.select_closure(rec, ["a"])
        self.assertIn("ghost", str(ctx.exception))

    def test_unbuilt_root_fails_loudly(self) -> None:
        with self.assertRaises(plan.UnresolvedDependency) as ctx:
            plan.select_closure({"a": plan.DebRecord("a", "/debs/a.deb", [])}, ["ghost"])
        self.assertIn("ghost", str(ctx.exception))


class ParseAlternativesTest(unittest.TestCase):
    def test_busybox_blocks(self) -> None:
        blocks = plan.parse_alternatives(BUSYBOX_ALTERNATIVES, "busybox")
        self.assertEqual([b.name for b in blocks], ["vi", "editor", "pager", "nc"])
        nc = blocks[3]
        self.assertEqual(nc.priority, 10)
        self.assertEqual([s.name for s in nc.slaves], ["ncat", "netcat"])
        self.assertEqual(nc.slaves[0].link, "bin/ncat")

    def test_less_block_priority_comment(self) -> None:
        blocks = plan.parse_alternatives(LESS_ALTERNATIVES, "less")
        self.assertEqual(len(blocks), 1)
        self.assertEqual(blocks[0].priority, 50)
        self.assertEqual(blocks[0].target, "bin/less")
        self.assertEqual(blocks[0].slaves[0].target, "share/man/man1/less.1.gz")


def pager_members() -> list:
    """Registration order for seeded packages sorted by name:
    busybox < coreutils < less."""
    return (
        plan.parse_alternatives(BUSYBOX_ALTERNATIVES, "busybox")[2],
        plan.parse_alternatives(CAT_PAGER_ALTERNATIVES, "coreutils")[0],
        plan.parse_alternatives(LESS_ALTERNATIVES, "less")[0],
    )


class PlanGroupTest(unittest.TestCase):
    EXPECTED_PAGER_ADMIN = (
        "auto\n"
        "/data/data/com.codeci.ide/files/usr/bin/pager\n"
        "pager.1.gz\n"
        "/data/data/com.codeci.ide/files/usr/share/man/man1/pager.1.gz\n"
        "\n"
        "/data/data/com.codeci.ide/files/usr/bin/less\n"
        "50\n"
        "/data/data/com.codeci.ide/files/usr/share/man/man1/less.1.gz\n"
        "/data/data/com.codeci.ide/files/usr/libexec/coreutils/cat\n"
        "1\n"
        "/data/data/com.codeci.ide/files/usr/share/man/man1/cat.1.gz\n"
        "/data/data/com.codeci.ide/files/usr/libexec/busybox/less\n"
        "10\n"
        # busybox/less declares no pager.1.gz slave; dpkg still requires an
        # (empty) placeholder line per group slave, then the terminator blank.
        "\n"
        "\n"
    )

    def test_pager_admin_parses_with_real_dpkg(self) -> None:
        """2026-08-25 incident: the seeded pager admin file shipped in
        userland-v2-dev was rejected by dpkg as "corrupt: unexpected end of
        file while trying to read master file" because slave-less records
        lacked placeholder lines. Prove the generated format parses with a
        real update-alternatives."""
        ua = shutil.which("update-alternatives")
        if ua is None:
            self.skipTest("update-alternatives not available on this host")
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "p"   # on-host stand-in for the device prefix
            fake.mkdir()
            _, admin = plan.plan_group("pager", list(pager_members()), str(fake))
            # dpkg only lists alternatives whose target exists; materialize them.
            for m in pager_members():
                target = fake / m.target
                target.parent.mkdir(parents=True, exist_ok=True)
                target.touch()
            admindir = fake / "var/lib/dpkg/alternatives"
            altdir = fake / "etc/alternatives"
            admindir.mkdir(parents=True); altdir.mkdir(parents=True)
            (admindir / "pager").write_text(admin)
            result = subprocess.run(
                [ua, "--admindir", str(admindir), "--altdir", str(altdir),
                 "--display", "pager"],
                text=True, capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn("corrupt", result.stderr)
            self.assertIn("pager - auto mode", result.stdout)
            self.assertIn(f"link best version is {fake}/bin/less", result.stdout)
            self.assertIn(f"{fake}/bin/less - priority 50", result.stdout)
            self.assertIn(f"{fake}/libexec/busybox/less - priority 10", result.stdout)

    def test_pager_group_winner_and_golden_admin(self) -> None:
        members = list(pager_members())
        ops, admin = plan.plan_group("pager", members, PREFIX)
        self.assertEqual(admin, self.EXPECTED_PAGER_ADMIN)
        op_map = dict(ops)
        self.assertEqual(op_map[f"{PREFIX}/bin/pager"],
                         f"{PREFIX}/etc/alternatives/pager")
        self.assertEqual(op_map[f"{PREFIX}/etc/alternatives/pager"],
                         f"{PREFIX}/bin/less")
        self.assertEqual(op_map[f"{PREFIX}/share/man/man1/pager.1.gz"],
                         f"{PREFIX}/etc/alternatives/pager.1.gz")
        self.assertEqual(op_map[f"{PREFIX}/etc/alternatives/pager.1.gz"],
                         f"{PREFIX}/share/man/man1/less.1.gz")

    def test_single_provider_admin_matches_dpkg_reference(self) -> None:
        editor = plan.parse_alternatives(BUSYBOX_ALTERNATIVES, "busybox")[1]
        ops, admin = plan.plan_group("editor", [editor], PREFIX)
        self.assertEqual(
            admin,
            "auto\n"
            "/data/data/com.codeci.ide/files/usr/bin/editor\n"
            "\n"
            "/data/data/com.codeci.ide/files/usr/libexec/busybox/vi\n"
            "10\n"
            "\n",
        )
        op_map = dict(ops)
        self.assertEqual(op_map[f"{PREFIX}/etc/alternatives/editor"],
                         f"{PREFIX}/libexec/busybox/vi")

    def test_tie_goes_to_most_recent_registration(self) -> None:
        bb = plan.parse_alternatives(BUSYBOX_ALTERNATIVES, "busybox")[0]  # vi 10
        other = replace(bb, package="sysvinit", target="bin/realvi")
        ops, _ = plan.plan_group("vi", [bb, other], PREFIX)
        self.assertEqual(dict(ops)[f"{PREFIX}/etc/alternatives/vi"],
                         f"{PREFIX}/bin/realvi")

    def test_duplicate_provider_refused(self) -> None:
        bb = plan.parse_alternatives(BUSYBOX_ALTERNATIVES, "busybox")[0]
        with self.assertRaises(ValueError):
            plan.plan_group("vi", [bb, bb], PREFIX)

    def test_paths_must_stay_inside_prefix(self) -> None:
        bb = plan.parse_alternatives(BUSYBOX_ALTERNATIVES, "busybox")[0]
        with self.assertRaises(ValueError):
            plan.plan_group("vi", [replace(bb, link="../usr/bin/vi")], PREFIX)
        with self.assertRaises(ValueError):
            plan.plan_group("vi", [replace(bb, target="/etc/passwd")], PREFIX)


class ApplyPlanTest(unittest.TestCase):
    def setUp(self) -> None:
        self.work = Path(tempfile.mkdtemp(prefix="apply-plan-"))
        self.addCleanup(shutil.rmtree, self.work, True)

    def test_creates_links_and_admin_db(self) -> None:
        ops = [
            (f"{PREFIX}/etc/alternatives/pager", f"{PREFIX}/bin/less"),
            (f"{PREFIX}/bin/pager", f"{PREFIX}/etc/alternatives/pager"),
        ]
        actions = plan.apply_plan(self.work, PREFIX, ops, {"pager": "auto\nx\n"})
        self.assertEqual(len(actions), 3)
        self.assertTrue((self.work / "bin/pager").is_symlink())
        self.assertEqual(os.readlink(self.work / "bin/pager"),
                         f"{PREFIX}/etc/alternatives/pager")
        self.assertEqual((self.work / "var/lib/dpkg/alternatives/pager").read_text(),
                         "auto\nx\n")

    def test_refuses_to_replace_real_file(self) -> None:
        (self.work / "bin").mkdir()
        (self.work / "bin/pager").write_text("real binary")
        ops = [(f"{PREFIX}/bin/pager", f"{PREFIX}/etc/alternatives/pager")]
        with self.assertRaises(ValueError):
            plan.apply_plan(self.work, PREFIX, ops, {})


# --------------------------------------------------------------------------
# End-to-end assemble tests over fixture .debs built on the host.
# --------------------------------------------------------------------------

def _add_dirs(tf: tarfile.TarFile, path: str) -> None:
    parts = path.rstrip("/").split("/")
    for i in range(1, len(parts)):
        name = "/".join(parts[:i])
        try:
            tf.getmember(name)
        except KeyError:
            info = tarfile.TarInfo(name)
            info.type = tarfile.DIRTYPE
            info.mode = 0o755
            tf.addfile(info)


def make_deb(dest_dir: Path, name: str, version: str, depends: str,
             files: dict) -> Path:
    """files: {"bin/less": (0o755, b"...")} or {"bin/x": ("L", "target")}"""
    data_buf = io.BytesIO()
    with tarfile.open(fileobj=data_buf, mode="w:gz") as tf:
        for rel, payload in files.items():
            full = f"./data/data/com.codeci.ide/files/usr/{rel}"
            _add_dirs(tf, full)
            info = tarfile.TarInfo(full)
            if isinstance(payload, tuple) and payload[0] == "L":
                info.type = tarfile.SYMTYPE
                info.linkname = payload[1]
                info.mode = 0o777
                tf.addfile(info)
            else:
                mode, content = payload
                info.size = len(content)
                info.mode = mode
                tf.addfile(info, io.BytesIO(content))
    control = (
        f"Package: {name}\n"
        f"Version: {version}\n"
        "Architecture: aarch64\n"
        "Maintainer: fixture\n"
        "Installed-Size: 1\n"
        "Description: fixture package\n"
        + (f"Depends: {depends}\n" if depends else "")
    )
    ctrl_buf = io.BytesIO()
    with tarfile.open(fileobj=ctrl_buf, mode="w:gz") as tf:
        info = tarfile.TarInfo("./control")
        payload = control.encode()
        info.size = len(payload)
        tf.addfile(info, io.BytesIO(payload))
    work = dest_dir / f"mk-{name}"
    work.mkdir(parents=True, exist_ok=True)
    (work / "debian-binary").write_text("2.0\n")
    (work / "control.tar.gz").write_bytes(ctrl_buf.getvalue())
    (work / "data.tar.gz").write_bytes(data_buf.getvalue())
    deb = dest_dir / f"{name}_{version}_aarch64.deb"
    subprocess.run(
        ["ar", "rc", str(deb), "debian-binary", "control.tar.gz", "data.tar.gz"],
        cwd=work, check=True, capture_output=True,
    )
    return deb


X = 0o755
FIXTURE_DEBS = [
    ("busybox", "1.36.1", "", {
        "bin/busybox": (X, b"bb"),
        "libexec/busybox/vi": (X, b"bb"),
        "libexec/busybox/less": (X, b"bb"),
        "libexec/busybox/nc": (X, b"bb"),
    }),
    ("bash", "5.2", "libfake", {"bin/bash": (X, b"bashbin")}),
    ("libfake", "1.0", "", {"lib/libfake.so": (0o644, b"\x7fELFfake")}),
    ("coreutils", "9.4", "libfake", {
        "libexec/coreutils/cat": (X, b"realcat"),
        "share/man/man1/cat.1.gz": (0o644, b"man-cat"),
    }),
    ("less", "608", "libfake", {
        "bin/less": (X, b"real-less"),
        "share/man/man1/less.1.gz": (0o644, b"man-less"),
    }),
    ("apt", "2.7", "dpkg, coreutils", {"bin/apt": (X, b"aptbin")}),
    ("dpkg", "1.22.6", "make", {"bin/dpkg": (X, b"dpkgbin")}),
    ("make", "4.4", "", {"bin/make": (X, b"makebin")}),
    # HTTPS metadata fetcher for the pkg frontend (Part B, 2026-08-23):
    # the curl CLI is a subpackage of the libcurl recipe, and upstream
    # auto-generates `Depends: libcurl (= <version>)` for it, so seeding
    # `curl` pulls libcurl and its TLS stack through the closure walk.
    ("curl", "8.21.0", "libcurl (= 8.21.0)", {"bin/curl": (X, b"curlbin")}),
    ("libcurl", "8.21.0", "libfake", {
        "lib/libcurl.so.4": (0o644, b"\x7fELFlibcurl"),
    }),
    # The official Termux repository keyring package may exist in the built
    # set only if something still depends on it; the CodeC apt override
    # removes that dependency, so it must never be seeded.
    ("termux-keyring", "3.13", "", {
        "share/termux-keyring/termux-autobuilds.gpg": (0o644, b"gpgkey"),
    }),
    # Not referenced by any seed dependency: must NOT be extracted/seeded.
    ("doxygen", "1.10", "", {"bin/doxygen": (X, b"build-tool")}),
    ("tor", "0.4", "", {"bin/tor": (X, b"build-dep")}),
]

CLOSURE_EXPECTED = {
    "apt", "bash", "busybox", "coreutils", "dpkg", "less", "libfake", "make",
    "curl", "libcurl",
}


@unittest.skipUnless(shutil.which("ar") and shutil.which("dpkg-deb"),
                     "requires ar and dpkg-deb on the host")
class AssembleBootstrapSeedTest(unittest.TestCase):

    def setUp(self) -> None:
        self.work = Path(tempfile.mkdtemp(prefix="assemble-seed-"))
        self.addCleanup(shutil.rmtree, self.work, True)
        self.tree = self.work / "tree"
        out = self.tree / "output"
        out.mkdir(parents=True)
        for package, alt_text in (
            ("busybox", BUSYBOX_ALTERNATIVES),
            ("less", LESS_ALTERNATIVES),
            ("coreutils", CAT_PAGER_ALTERNATIVES),
        ):
            pkg_dir = self.tree / "packages" / package
            pkg_dir.mkdir(parents=True)
            filename = (f"{package}.alternatives" if package != "coreutils"
                        else "cat.alternatives")
            (pkg_dir / filename).write_text(alt_text)
        for name, version, depends, files in FIXTURE_DEBS:
            make_deb(out, name, version, depends, files)
        self.dist = self.work / "dist"

    def run_assemble(self, extra_env: dict | None = None) -> subprocess.CompletedProcess:
        env = dict(os.environ)
        env["CODEC_BOOTSTRAP_NAME"] = "bootstrap-phase3"
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            ["bash", str(ASSEMBLE), str(self.tree), "aarch64", str(self.dist)],
            capture_output=True, text=True, env=env,
        )

    @staticmethod
    def member(tf: tarfile.TarFile, name: str) -> tarfile.TarInfo:
        return tf.getmember(name)

    def test_seeds_only_closure_with_md5sums_and_wired_alternatives(self) -> None:
        result = self.run_assemble()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        archive = self.dist / "bootstrap-phase3-aarch64.tar.gz"
        sidecar = Path(str(archive) + ".sha256")
        self.assertTrue(sidecar.exists())
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        self.assertEqual(sidecar.read_text().split()[0], digest)

        with tarfile.open(archive, "r:gz") as tf:
            names = set(tf.getnames())
            self.assertIn("./bin/bash", names)
            self.assertIn("./bin/busybox", names)
            self.assertIn("./bin/less", names)
            # The HTTPS metadata fetcher is seeded (Part B, 2026-08-23:
            # fresh devices had no curl/python3/wget and `pkg update`
            # failed its Release preflight).
            self.assertIn("./bin/curl", names)
            self.assertIn("./etc/apt/keyrings/codec-archive-keyring-v1.gpg", names)
            seeded_key = tf.extractfile(
                "./etc/apt/keyrings/codec-archive-keyring-v1.gpg"
            ).read()
            self.assertEqual(
                seeded_key,
                (ROOT / "codec-packages" / "keys" / "codec-archive-keyring-v1.gpg").read_bytes(),
            )
            self.assertNotIn("./bin/doxygen", names)
            self.assertNotIn("./bin/tor", names)
            # The official Termux repository keyring is never seeded.
            self.assertNotIn(
                "./share/termux-keyring/termux-autobuilds.gpg", names)
            for relink in ("./bin/doxygen", "./bin/tor"):
                self.assertFalse(any(n.endswith(relink[1:]) for n in names))

            status = tf.extractfile("./var/lib/dpkg/status").read().decode()
            seeded = set(
                line.split(": ", 1)[1]
                for line in status.splitlines()
                if line.startswith("Package: ")
            )
            self.assertEqual(seeded, CLOSURE_EXPECTED)
            self.assertNotIn("Package: termux-keyring", status)
            for pkg in CLOSURE_EXPECTED:
                self.assertIn(f"./var/lib/dpkg/info/{pkg}.list", names)
                self.assertIn(f"./var/lib/dpkg/info/{pkg}.md5sums", names)

            md5sums = tf.extractfile("./var/lib/dpkg/info/less.md5sums").read().decode()
            expected_md5 = hashlib.md5(b"real-less").hexdigest()
            self.assertIn(
                f"{expected_md5}  data/data/com.codeci.ide/files/usr/bin/less",
                md5sums,
            )
            # No absolute device paths inside the archive's md5sums (relative),
            # and no symlink entries hashed.
            for line in md5sums.splitlines():
                self.assertFalse(line.strip().endswith("pager.1.gz"))

            pager = self.member(tf, "./bin/pager")
            self.assertTrue(pager.issym())
            self.assertEqual(pager.linkname, "../etc/alternatives/pager")
            pager_alt = self.member(tf, "./etc/alternatives/pager")
            self.assertEqual(pager_alt.linkname, "../../bin/less")
            editor = self.member(tf, "./bin/editor")
            self.assertEqual(editor.linkname, "../etc/alternatives/editor")
            editor_alt = self.member(tf, "./etc/alternatives/editor")
            self.assertEqual(editor_alt.linkname, "../../libexec/busybox/vi")
            ncat = self.member(tf, "./bin/ncat")
            self.assertEqual(ncat.linkname, "../etc/alternatives/ncat")
            man_pager = self.member(tf, "./share/man/man1/pager.1.gz")
            self.assertEqual(man_pager.linkname, "../../../etc/alternatives/pager.1.gz")
            man_pager_alt = self.member(tf, "./etc/alternatives/pager.1.gz")
            self.assertEqual(man_pager_alt.linkname, "../../share/man/man1/less.1.gz")

            admin_pager = tf.extractfile(
                "./var/lib/dpkg/alternatives/pager").read().decode()
            self.assertEqual(admin_pager, PlanGroupTest.EXPECTED_PAGER_ADMIN)
            self.assertIn("./var/lib/dpkg/alternatives/editor", names)
            self.assertIn("./var/lib/dpkg/alternatives/vi", names)
            self.assertIn("./var/lib/dpkg/alternatives/nc", names)

        self.assertIn("closure seeds 10 of 13 built package(s)", result.stdout)

    def test_unresolvable_seed_root_fails(self) -> None:
        result = self.run_assemble(
            extra_env={"CODEC_BOOTSTRAP_SEED_PACKAGES": "busybox ghostpkg"})
        self.assertEqual(result.returncode, 1)
        self.assertIn("ghostpkg", result.stderr)

    def test_missing_build_dep_fails_loudly(self) -> None:
        # bash Depends libfake; remove libfake's deb and the closure walk
        # must refuse rather than seed a broken database.
        (self.tree / "output" / "libfake_1.0_aarch64.deb").unlink()
        result = self.run_assemble()
        self.assertEqual(result.returncode, 1)
        self.assertIn("libfake", result.stderr)


if __name__ == "__main__":
    unittest.main()
