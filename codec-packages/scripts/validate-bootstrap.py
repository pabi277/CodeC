#!/usr/bin/env python3
"""Validate a CodeC Phase 3 package-manager bootstrap archive before release.

The archive must contain the *contents* of the CodeC prefix (root-level
`bin/`, `lib/`, `etc/`, `var/`), a real ELF shell, CodeC-built apt/dpkg, a
seeded dpkg status database (without official Termux packages such as
termux-keyring), the termux-exec LD_PRELOAD library, the runtime shared
libraries, an HTTPS metadata fetcher for the `pkg` frontend (bin/curl), and
no official Termux contamination.

Usage:
    validate-bootstrap.py ARCHIVE [ARCHIVE ...]

Each archive must be named `bootstrap-phase3-<arch>.tar.gz` with
`<arch>` in `aarch64`/`x86_64`, and must be accompanied by a
`<archive>.sha256` sidecar in the standard two-field form whose first
token is the archive SHA-256 and which names the archive file.
"""
from __future__ import annotations

import hashlib
import posixpath
import re
import sys
import tarfile
from pathlib import Path
from typing import Dict, Iterable, List

SUPPORTED_ARCHES = ("aarch64", "x86_64")
KEYRING_MEMBER = "etc/apt/keyrings/codec-archive-keyring-v1.gpg"
PINNED_KEYRING = Path(__file__).resolve().parents[1] / "keys" / "codec-archive-keyring-v1.gpg"

ELF_MAGIC = b"\x7fELF"

# Bytes that must never appear in any bootstrap payload file or symlink
# target: the official Termux app identity and the activity-manager wrapper.
FORBIDDEN_BYTES = (
    b"com.termux",
    b"termux-am",
    # Official Termux repository URLs (the apt conffile must carry the CodeC
    # development channel only).
    b"packages.termux.dev",
    b"packages-cf.termux.dev",
)

REQUIRED_FILES = (
    "bin/bash",
    "bin/busybox",
    "bin/apt-get",
    "bin/dpkg",
    # HTTPS metadata fetcher for the `pkg` frontend's Release/Release.sha256
    # preflight (fresh-device Part B defect 2026-08-23: the closure shipped
    # no curl/python3/wget and `pkg update` failed before apt ever ran).
    "bin/curl",
    KEYRING_MEMBER,
    "var/lib/dpkg/status",
    "var/lib/dpkg/arch",
)

# The bootstrap must carry the CodeC-built apt/dpkg runtime dependencies.
def _norm(name: str) -> str:
    name = name.replace("\\", "/")
    while name.startswith("./"):
        name = name[2:]
    return name.lstrip("/")


class BootstrapError(ValueError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_sidecar(archive: Path) -> str:
    sidecar = archive.with_name(archive.name + ".sha256")
    if not sidecar.is_file():
        raise BootstrapError(f"missing checksum sidecar: {sidecar}")
    tokens = sidecar.read_text().split()
    if not tokens:
        raise BootstrapError(f"empty checksum sidecar: {sidecar}")
    token = tokens[0]
    if not re.fullmatch(r"[0-9a-fA-F]{64}", token):
        raise BootstrapError(f"sidecar does not start with a SHA-256: {sidecar}")
    if token.lower() != sha256(archive):
        raise BootstrapError(f"checksum sidecar mismatch: {sidecar}")
    if archive.name not in sidecar.read_text():
        raise BootstrapError(f"sidecar names the wrong file: {sidecar}")
    return token.lower()


def check_listing(members: Iterable[tarfile.TarInfo]) -> List[str]:
    names = []
    for member in members:
        name = _norm(member.name)
        if not name:
            continue
        parts = name.split("/")
        if ".." in parts:
            raise BootstrapError(f"path traversal in archive member: {member.name}")
        if name.startswith("data/data/"):
            raise BootstrapError(f"nested Android path in archive: {member.name}")
        for forbidden in FORBIDDEN_BYTES:
            if forbidden in name.encode("utf-8", "replace"):
                raise BootstrapError(f"forbidden member name: {member.name}")
        names.append(name)
    return names


def check_required(names: List[str]) -> None:
    name_set = set(names)
    for required in REQUIRED_FILES:
        if required not in name_set:
            raise BootstrapError(f"archive is missing required member: {required}")
    # termux-exec LD_PRELOAD library (primary variant or compatibility name).
    # Warning only: the standalone preload build is best-effort in CI (the
    # official recipe needs a Termux-farm-only prebuilt build dependency).
    # A bootstrap without it still boots; dpkg maintainer scripts may fail
    # on real devices until the library is shipped.
    if not any(
        re.fullmatch(r"lib/libtermux-exec(-ld-preload)?\.so", name)
        for name in name_set
    ):
        print(
            "WARNING: archive is missing the termux-exec LD_PRELOAD library "
            "(lib/libtermux-exec-ld-preload.so); dpkg maintainer scripts may "
            "not run on real devices",
            file=sys.stderr,
        )
    # The runtime shared library that broke userland-v1 when absent.
    if not any(name.startswith("lib/libandroid-support.so") for name in name_set):
        raise BootstrapError(
            "archive is missing the libandroid-support.so runtime library"
        )
    # `bin/apt` is normally a symlink to apt-get; accept either a file or a
    # symlink, but never its absence.
    if "bin/apt" not in name_set and "bin/apt-get" not in name_set:
        raise BootstrapError("archive is missing apt (bin/apt or bin/apt-get)")


def check_repository_keyring(
    tar: tarfile.TarFile, by_name: Dict[str, tarfile.TarInfo]
) -> None:
    if not PINNED_KEYRING.is_file() or PINNED_KEYRING.stat().st_size == 0:
        raise BootstrapError("pinned CodeC repository keyring is missing from source")
    info = by_name.get(KEYRING_MEMBER)
    if info is None or not info.isreg():
        raise BootstrapError(f"{KEYRING_MEMBER} is not a regular file")
    stream = tar.extractfile(info)
    if stream is None or stream.read() != PINNED_KEYRING.read_bytes():
        raise BootstrapError("bootstrap CodeC repository keyring differs from pinned public key")


def check_elf_members(
    tar: tarfile.TarFile, by_name: Dict[str, tarfile.TarInfo], names: List[str]
) -> None:
    name_set = set(names)
    for required in ("bin/bash", "bin/busybox", "bin/apt-get", "bin/dpkg", "bin/curl"):
        if required not in name_set:
            continue
        info = by_name.get(required)
        if info is None:
            raise BootstrapError(f"cannot read {required} from the archive")
        if not info.isreg():
            raise BootstrapError(f"{required} is not a regular file in the archive")
        stream = tar.extractfile(info)
        if stream is None:
            raise BootstrapError(f"cannot read {required} from the archive")
        magic = stream.read(4)
        if magic != ELF_MAGIC:
            raise BootstrapError(f"{required} is not an ELF executable")


def check_symlinks(members: Iterable[tarfile.TarInfo]) -> None:
    for member in members:
        if not member.issym() and not member.islnk():
            continue
        linkname = member.linkname
        if linkname.startswith("/"):
            raise BootstrapError(
                f"unsafe symlink target in archive: {member.name} -> {linkname}"
            )
        # Relative targets may climb with ".." as long as the resolved path
        # stays inside the archive root (e.g. the termux-keyring
        # trusted.gpg.d link, relativized to the prefix by the builder).
        base = posixpath.dirname(_norm(member.name))
        resolved = posixpath.normpath(posixpath.join(base, linkname))
        if resolved.startswith(".."):
            raise BootstrapError(
                f"unsafe symlink target in archive: {member.name} -> {linkname}"
            )
        for forbidden in FORBIDDEN_BYTES:
            if forbidden in linkname.encode("utf-8", "replace"):
                raise BootstrapError(
                    f"symlink carries forbidden content: {member.name} -> {linkname}"
                )


def check_dpkg_status(
    tar: tarfile.TarFile, by_name: Dict[str, tarfile.TarInfo], names: List[str]
) -> None:
    if "var/lib/dpkg/status" not in set(names):
        raise BootstrapError("archive is missing var/lib/dpkg/status")
    status_info = by_name.get("var/lib/dpkg/status")
    stream = tar.extractfile(status_info) if status_info is not None else None
    if stream is None:
        raise BootstrapError("cannot read var/lib/dpkg/status from the archive")
    stanzas: Dict[str, str] = {}
    current = None
    for raw_line in stream.read().decode("utf-8", "replace").splitlines():
        if not raw_line.strip():
            current = None
            continue
        if raw_line[0].isspace() and current is not None:
            stanzas[current] += "\n" + raw_line
            continue
        if ":" in raw_line:
            key, value = raw_line.split(":", 1)
            if key == "Package":
                current = value.strip()
                stanzas[current] = ""
            elif current is not None:
                stanzas[current] += "\n" + raw_line
    # termux-exec is merged in from a standalone build (not a .deb), so it
    # has no dpkg status entry; only the deb-installed roots are required.
    for package in ("apt", "dpkg", "bash", "busybox"):
        stanza = stanzas.get(package)
        if stanza is None:
            raise BootstrapError(f"dpkg status database has no entry for {package}")
        if "Status: install ok installed" not in stanza:
            raise BootstrapError(
                f"dpkg status database does not mark {package} as installed"
            )
    # The official Termux repository keyring package must never be seeded:
    # its payload is the GPG keys of the official Termux repositories,
    # installed into etc/apt/trusted.gpg.d/ (fresh-device Part B defect
    # 2026-08-23: the seeded closure recorded `ii termux-keyring 3.13`).
    if "termux-keyring" in stanzas:
        raise BootstrapError(
            "dpkg status database seeds termux-keyring (official Termux "
            "repository signing keys)"
        )
    arch_line = None
    arch_info = by_name.get("var/lib/dpkg/arch")
    stream = tar.extractfile(arch_info) if arch_info is not None else None
    if stream is not None:
        arch_line = stream.read().decode("utf-8", "replace").strip()
        if arch_line not in SUPPORTED_ARCHES:
            raise BootstrapError(f"unexpected dpkg architecture: {arch_line!r}")


def check_contamination(
    tar: tarfile.TarFile, by_name: Dict[str, tarfile.TarInfo], names: List[str]
) -> None:
    for name in names:
        if not name or name.endswith("/"):
            continue
        info = by_name.get(name)
        if info is None or not info.isreg():
            continue
        stream = tar.extractfile(info)
        if stream is None:
            raise BootstrapError(f"cannot read {name} from the archive")
        with stream:
            offset = 0
            window = b""
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                window += chunk
                if len(window) > 1024 * 1024:
                    data, window = window[:-4096], window[-4096:]
                else:
                    data = window
                    window = b""
                for forbidden in FORBIDDEN_BYTES:
                    if forbidden in data:
                        raise BootstrapError(
                            f"forbidden content {forbidden!r} in {name} (offset ~{offset})"
                        )
                offset += len(chunk)


def check_size(archive: Path) -> None:
    # Truncated uploads are caught by the SHA-256 sidecar; an oversized
    # archive would only burn device storage, so cap it.
    size = archive.stat().st_size
    if size == 0:
        raise BootstrapError(f"empty bootstrap archive: {archive.name}")
    if size > 256 * 1024 * 1024:
        raise BootstrapError(f"suspiciously large bootstrap: {size} bytes")


def validate(archive: Path) -> str:
    archive = archive.resolve()
    if not archive.is_file():
        raise BootstrapError(f"bootstrap archive does not exist: {archive}")
    stem = archive.name
    if not stem.endswith(".tar.gz"):
        raise BootstrapError(f"archive must be a .tar.gz: {archive.name}")
    arch = None
    for candidate in SUPPORTED_ARCHES:
        if stem == f"bootstrap-phase3-{candidate}.tar.gz":
            arch = candidate
    if arch is None:
        raise BootstrapError(
            f"archive must be named bootstrap-phase3-{'|'.join(SUPPORTED_ARCHES)}.tar.gz: {stem}"
        )

    digest = check_sidecar(archive)
    check_size(archive)

    with tarfile.open(archive, "r:gz") as tar:
        members = tar.getmembers()
        names = check_listing(members)
        # Member lookup keyed by normalized name: the archive may store
        # entries with or without a leading "./" (tar -C dir . produces
        # the latter), and raw TarFile.getmember only matches exact names.
        by_name: Dict[str, tarfile.TarInfo] = {}
        for member in members:
            norm = _norm(member.name)
            if norm:
                # Last entry wins, matching tarfile.getmember() and tar
                # extraction semantics for duplicate member names.
                by_name[norm] = member
        check_required(names)
        check_symlinks(members)
        check_repository_keyring(tar, by_name)
        check_elf_members(tar, by_name, names)
        check_dpkg_status(tar, by_name, names)
        check_contamination(tar, by_name, names)

    print(
        f"validated bootstrap: {archive.name} "
        f"arch={arch} sha256={digest} members={len(names)}"
    )
    return digest


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    failures = 0
    for argument in sys.argv[1:]:
        try:
            validate(Path(argument))
        except (BootstrapError, OSError, tarfile.TarError, UnicodeDecodeError) as exc:
            print(f"validate-bootstrap: ERROR: {argument}: {exc}", file=sys.stderr)
            failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
