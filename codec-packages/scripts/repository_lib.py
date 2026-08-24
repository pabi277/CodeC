#!/usr/bin/env python3
"""Shared, dependency-free validation and metadata helpers for CodeC packages.

The .deb payloads are built by the official Termux package builder. This module
only creates/validates the static repository; it never installs packages.
"""
from __future__ import annotations

import hashlib
import json
import os
import posixpath
import re
import shutil
import shlex
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

CODEC_PACKAGE = "com.codeci.ide"
CODEC_PREFIX = f"data/data/{CODEC_PACKAGE}/files/usr"
CODEC_RUNTIME_PREFIX = f"/data/data/{CODEC_PACKAGE}/files/usr"
SUPPORTED_ARCHES = {"all", "aarch64", "x86_64"}
FORBIDDEN_BYTES = (
    b"/data/data/com.termux",
    b"data/data/com.termux",
    b"com.termux/files/usr",
)


class PackageError(ValueError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(*args: str) -> str:
    try:
        return subprocess.check_output(args, stderr=subprocess.PIPE, text=True)
    except FileNotFoundError as exc:
        raise PackageError(f"required host tool is missing: {args[0]}") from exc
    except subprocess.CalledProcessError as exc:
        detail = exc.stderr.strip() or "command failed"
        raise PackageError(f"{' '.join(args)}: {detail}") from exc


def parse_control_text(text: str) -> Dict[str, str]:
    fields: Dict[str, str] = {}
    current = None
    for line in text.splitlines():
        if not line:
            continue
        if line[0].isspace():
            if current is not None:
                fields[current] += "\n" + line
            continue
        if ":" not in line:
            raise PackageError(f"invalid control line: {line!r}")
        key, value = line.split(":", 1)
        current = key
        fields[key] = value.lstrip()
    return fields


def control(path: Path) -> Dict[str, str]:
    return parse_control_text(run("dpkg-deb", "-f", str(path)))


def _payload_line(line: str) -> Tuple[str, str | None]:
    # dpkg-deb --contents emits paths beginning with ./ and uses ` -> ` for
    # symbolic links. Package paths in the official recipes do not contain
    # newlines; preserving spaces in a future package is intentionally not
    # supported by this conservative parser.
    match = re.search(r"(\./\S*)(?: -> (.*))?$", line)
    if not match:
        raise PackageError(f"cannot parse package member: {line!r}")
    return match.group(1), match.group(2)


def _safe_relative(path: str, *, package: Path) -> str:
    if path.startswith("/"):
        raise PackageError(f"{package.name}: absolute payload path {path}")
    raw = path[2:] if path.startswith("./") else path
    parts = raw.split("/")
    if any(part in ("", ".") for part in parts[:-1]):
        # Empty interior components are ambiguous in archive names.
        raise PackageError(f"{package.name}: malformed payload path {path}")
    if ".." in parts:
        raise PackageError(f"{package.name}: path traversal in {path}")
    return posixpath.normpath(raw)


def validate_payload(path: Path) -> List[str]:
    members: List[str] = []
    output = run("dpkg-deb", "--contents", str(path))
    prefix = CODEC_PREFIX
    for line in output.splitlines():
        member, target = _payload_line(line)
        if member in {"./", "."}:
            continue
        relative = _safe_relative(member, package=path)
        prefix_parts = prefix.split("/")
        prefix_ancestors = {
            "/".join(prefix_parts[:index])
            for index in range(1, len(prefix_parts) + 1)
        }
        if relative not in prefix_ancestors and not relative.startswith(prefix + "/"):
            raise PackageError(
                f"{path.name}: payload is outside CodeC prefix: {member}"
            )
        if target is not None:
            if target.startswith("/"):
                raise PackageError(f"{path.name}: absolute symlink target {target}")
            resolved = posixpath.normpath(
                posixpath.join(posixpath.dirname(relative), target)
            )
            if resolved != prefix and not resolved.startswith(prefix + "/"):
                raise PackageError(
                    f"{path.name}: symlink escapes CodeC prefix: {member} -> {target}"
                )
        members.append(relative)
    if not members:
        raise PackageError(f"{path.name}: package has no payload members")
    return members


def _validate_alternatives_script(script: Path, package_name: str, script_name: str) -> None:
    """Allow only reviewed Termux alternatives output for starter packages."""
    expected = {
        "coreutils": {
            "name": "pager", "link": "bin/pager", "alternative": "libexec/coreutils/cat",
            "priority": "1", "slave": ("share/man/man1/pager.1.gz", "pager.1.gz", "share/man/man1/cat.1.gz"),
        },
        "less": {
            "name": "pager", "link": "bin/pager", "alternative": "bin/less",
            "priority": "50", "slave": ("share/man/man1/pager.1.gz", "pager.1.gz", "share/man/man1/less.1.gz"),
        },
        "nano": {
            "name": "editor", "link": "bin/editor", "alternative": "bin/nano",
            "priority": "50", "slave": ("share/man/man1/editor.1.gz", "editor.1.gz", "share/man/man1/nano.1.gz"),
        },
    }.get(package_name)
    if expected is None:
        raise PackageError(f"{script.name}: maintainer scripts are not approved for {package_name}")
    try:
        lines = script.read_text().splitlines()
    except UnicodeDecodeError as exc:
        raise PackageError(f"{script.name}: maintainer script is not UTF-8") from exc
    if not lines or lines[0] != f"#!{CODEC_RUNTIME_PREFIX}/bin/sh":
        raise PackageError(f"{script.name}: unexpected CodeC maintainer-script shebang")
    known_conditions = {
        "if [ \"$1\" = 'configure' ] || [ \"$1\" = 'abort-upgrade' ] || [ \"$1\" = 'abort-deconfigure' ] || [ \"$1\" = 'abort-remove' ]; then",
        "if [ \"$1\" = 'remove' ] || [ \"$1\" != 'upgrade' ]; then",
        f"if [ -x \"{CODEC_RUNTIME_PREFIX}/bin/update-alternatives\" ]; then",
    }
    forbidden = re.compile(r"(?:\$\(|`|com\.termux|/system/|\b(?:rm|curl|wget|chmod|chown|ln|cp|mv|dd|eval|exec|source)\b)")
    for raw_line in lines[1:]:
        stripped = raw_line.strip()
        if stripped in known_conditions:
            continue
        if forbidden.search(stripped):
            raise PackageError(f"{script.name}: unsafe command in alternatives script")
    continuation = False
    saw_install = saw_slave = saw_remove = False
    for raw_line in lines[1:]:
        line = raw_line.strip()
        if not line or line.startswith("#") or line in {"fi", "then"} or line in known_conditions:
            continue
        line_without_continuation = line[:-1].rstrip() if line.endswith("\\") else line
        if line_without_continuation == "update-alternatives":
            continuation = True
            continue
        if line_without_continuation.startswith("--install "):
            if not continuation:
                raise PackageError(f"{script.name}: orphaned --install command")
            tokens = shlex.split(line_without_continuation)
            if len(tokens) != 5 or tokens[0] != "--install":
                raise PackageError(f"{script.name}: invalid alternatives --install command")
            if tokens[1:] != [f"{CODEC_RUNTIME_PREFIX}/{expected['link']}", expected['name'], f"{CODEC_RUNTIME_PREFIX}/{expected['alternative']}", expected['priority']]:
                raise PackageError(f"{script.name}: unexpected alternatives target")
            saw_install = True
            continuation = line.endswith("\\")
            continue
        if line_without_continuation.startswith("--slave "):
            if not continuation:
                raise PackageError(f"{script.name}: orphaned --slave command")
            tokens = shlex.split(line_without_continuation)
            if len(tokens) != 4 or tokens[0] != "--slave":
                raise PackageError(f"{script.name}: invalid alternatives --slave command")
            slave = expected['slave']
            if tokens[1:] != [f"{CODEC_RUNTIME_PREFIX}/{slave[0]}", slave[1], f"{CODEC_RUNTIME_PREFIX}/{slave[2]}"]:
                raise PackageError(f"{script.name}: unexpected alternatives slave target")
            saw_slave = True
            continuation = line.endswith("\\")
            continue
        if line_without_continuation.startswith("update-alternatives --remove "):
            tokens = shlex.split(line_without_continuation)
            if tokens != ["update-alternatives", "--remove", expected['name'], f"{CODEC_RUNTIME_PREFIX}/{expected['alternative']}"]:
                raise PackageError(f"{script.name}: unexpected alternatives removal")
            saw_remove = True
            continuation = False
            continue
        raise PackageError(f"{script.name}: unapproved alternatives line: {raw_line}")
    if script_name == "postinst" and not (saw_install and saw_slave) or script_name == "prerm" and not saw_remove:
        raise PackageError(f"{script.name}: incomplete alternatives operation")


def validate_control_scripts(path: Path, package_name: str) -> None:
    with tempfile.TemporaryDirectory(prefix="codec-control-") as tmp:
        control_dir = Path(tmp) / "control"
        control_dir.mkdir()
        run("dpkg-deb", "--control", str(path), str(control_dir))
        scripts = sorted(item.name for item in control_dir.iterdir() if item.name in {"preinst", "postinst", "prerm", "postrm"})
        if not scripts:
            return
        if package_name not in {"coreutils", "less", "nano"} or set(scripts) != {"postinst", "prerm"}:
            raise PackageError(f"{path.name}: maintainer scripts are not allowed: {', '.join(scripts)}")
        for script_name in scripts:
            _validate_alternatives_script(control_dir / script_name, package_name, script_name)

def inspect_package(path: Path, allowed_arches: Iterable[str] = SUPPORTED_ARCHES) -> Dict:
    if not path.is_file() or path.suffix != ".deb":
        raise PackageError(f"not a .deb file: {path}")
    blob = path.read_bytes()
    for forbidden in FORBIDDEN_BYTES:
        if forbidden in blob:
            raise PackageError(f"{path.name}: contains forbidden Termux identity")
    fields = control(path)
    required = ("Package", "Version", "Architecture")
    missing = [key for key in required if not fields.get(key)]
    if missing:
        raise PackageError(f"{path.name}: missing control field(s): {', '.join(missing)}")
    architecture = fields["Architecture"].strip()
    allowed = set(allowed_arches)
    if architecture not in allowed:
        raise PackageError(
            f"{path.name}: architecture {architecture!r} is not one of {sorted(allowed)}"
        )
    validate_payload(path)
    validate_control_scripts(path, fields["Package"].strip())
    return {
        "name": fields["Package"].strip(),
        "version": fields["Version"].strip(),
        "architecture": architecture,
        "depends": fields.get("Depends", "").strip(),
        "description": fields.get("Description", "").strip(),
        "filename": "",
        "size": path.stat().st_size,
        "sha256": sha256(path),
        "path": path,
        "control": fields,
    }


def stanza(record: Dict) -> str:
    fields = record["control"]
    # Keep the control fields, including multiline Description/Depends, so apt
    # receives the same metadata as dpkg. Fields added below are repository
    # metadata and are intentionally deterministic.
    lines: List[str] = []
    for key, value in fields.items():
        if key in {"Filename", "Size", "MD5sum", "SHA1", "SHA256"}:
            continue
        lines.append(f"{key}: {value}")
    lines.extend(
        [
            f"Filename: {record['filename']}",
            f"Size: {record['size']}",
            f"SHA256: {record['sha256']}",
        ]
    )
    return "\n".join(lines)


def parse_packages(text: str) -> List[Dict[str, str]]:
    result = []
    for block in re.split(r"\n\s*\n", text.strip()):
        if not block.strip():
            continue
        result.append(parse_control_text(block))
    return result


def write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def release_hashes(release_dir: Path) -> List[Tuple[str, int, str, str]]:
    """Hashes named relative to Release, as required by APT."""
    files = sorted(
        item for item in release_dir.rglob("*") if item.is_file() and item.name != "Release"
    )
    return [
        (sha256(item), item.stat().st_size, item.relative_to(release_dir).as_posix(), item.name)
        for item in files
        if item.name in {"Packages", "Packages.gz"}
    ]


def json_dump(path: Path, value: Dict) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
