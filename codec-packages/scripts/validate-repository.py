#!/usr/bin/env python3
"""Validate a generated CodeC APT repository without installing anything."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import posixpath
import sys
from pathlib import Path

from repository_lib import (
    PackageError,
    SUPPORTED_ARCHES,
    inspect_package,
    parse_packages,
    sha256,
)


def args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("--architectures", nargs="+", default=["aarch64", "x86_64"])
    return parser.parse_args()


def check_sidecar(path: Path, expected_name: str) -> None:
    sidecar = path.with_name(path.name + ".sha256")
    if not sidecar.is_file():
        raise PackageError(f"missing checksum sidecar: {sidecar}")
    token = sidecar.read_text().split()[0] if sidecar.read_text().split() else ""
    if token.lower() != sha256(path):
        raise PackageError(f"checksum sidecar mismatch: {path}")
    if expected_name not in sidecar.read_text():
        raise PackageError(f"checksum sidecar names the wrong file: {sidecar}")


def parse_release_hashes(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    in_sha = False
    for line in text.splitlines():
        if line == "SHA256:":
            in_sha = True
            continue
        if in_sha and line and not line.startswith(" "):
            in_sha = False
        if in_sha and line.startswith(" "):
            fields = line.split()
            if len(fields) == 3:
                result[fields[2]] = fields[0]
    return result


def safe_manifest_path(root: Path, value: str) -> Path:
    if value.startswith("/") or ".." in Path(value).parts:
        raise PackageError(f"manifest path traversal: {value}")
    result = (root / value).resolve()
    if result != root.resolve() and root.resolve() not in result.parents:
        raise PackageError(f"manifest path escapes repository: {value}")
    return result


def validate(root: Path, requested_arches: list[str]) -> None:
    root = root.resolve()
    if not root.is_dir():
        raise PackageError(f"repository does not exist: {root}")
    marker = root / "CODEC-REPOSITORY"
    if not marker.is_file() or "com.codeci.ide" not in marker.read_text():
        raise PackageError("missing CodeC repository marker")
    manifest_path = root / "repository.json"
    if not manifest_path.is_file():
        raise PackageError("missing repository.json")
    check_sidecar(manifest_path, "repository.json")
    manifest = json.loads(manifest_path.read_text())
    if manifest.get("package") != "com.codeci.ide":
        raise PackageError("repository package identity is not com.codeci.ide")
    if manifest.get("prefix") != "/data/data/com.codeci.ide/files/usr":
        raise PackageError("repository prefix is not the CodeC prefix")

    suite = manifest.get("suite", "stable")
    component = manifest.get("component", "main")
    release_dir = root / "dists" / suite
    release_path = release_dir / "Release"
    if not release_path.is_file():
        raise PackageError(f"missing {release_path}")
    check_sidecar(release_path, "Release")
    release_hashes = parse_release_hashes(release_path.read_text())

    records = manifest.get("packages")
    if not isinstance(records, list) or not records:
        raise PackageError("repository manifest has no packages")
    seen = set()
    for record in records:
        if not isinstance(record, dict):
            raise PackageError("invalid package record")
        key = (record.get("name"), record.get("architecture"))
        if key in seen:
            raise PackageError(f"duplicate manifest record: {key}")
        seen.add(key)
        if record.get("architecture") not in SUPPORTED_ARCHES:
            raise PackageError(f"unsupported package architecture: {key}")
        deb = safe_manifest_path(root, str(record.get("filename", "")))
        if not deb.is_file():
            raise PackageError(f"manifest package is missing: {deb}")
        inspected = inspect_package(deb, SUPPORTED_ARCHES)
        for field in ("name", "version", "architecture", "size", "sha256"):
            actual_field = "size" if field == "size" else field
            if inspected[actual_field] != record.get(field):
                raise PackageError(f"manifest mismatch for {deb.name}: {field}")
        relative = deb.relative_to(root).as_posix()
        if release_hashes.get(relative) is not None:
            # Release files cover indexes, not individual package files.
            pass

    for arch in ["all", *requested_arches]:
        package_path = release_dir / component / f"binary-{arch}" / "Packages"
        compressed_path = package_path.with_name("Packages.gz")
        if not package_path.is_file() or not compressed_path.is_file():
            raise PackageError(f"missing package indexes for {arch}")
        relative = package_path.relative_to(root).as_posix()
        if release_hashes.get(relative) != sha256(package_path):
            raise PackageError(f"Release SHA256 mismatch: {relative}")
        compressed_relative = compressed_path.relative_to(root).as_posix()
        if release_hashes.get(compressed_relative) != sha256(compressed_path):
            raise PackageError(f"Release SHA256 mismatch: {compressed_relative}")
        if gzip.decompress(compressed_path.read_bytes()) != package_path.read_bytes():
            raise PackageError(f"Packages.gz does not match Packages for {arch}")
        for stanza in parse_packages(package_path.read_text()):
            filename = stanza.get("Filename")
            if not filename:
                raise PackageError(f"index entry has no Filename: {package_path}")
            deb = safe_manifest_path(root, filename)
            if not deb.is_file():
                raise PackageError(f"index package is missing: {filename}")
            if stanza.get("SHA256") != sha256(deb):
                raise PackageError(f"index SHA256 mismatch: {filename}")
            if int(stanza.get("Size", "-1")) != deb.stat().st_size:
                raise PackageError(f"index size mismatch: {filename}")

    print(
        f"validated CodeC repository: {len(records)} packages, "
        f"architectures={','.join(requested_arches)}"
    )


def main() -> int:
    try:
        validate(args().repository, args().architectures)
    except (PackageError, OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"validate-repository: ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
