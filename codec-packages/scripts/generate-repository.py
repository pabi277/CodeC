#!/usr/bin/env python3
"""Build a static CodeC APT repository from CodeC-built .deb files.

This is intentionally a repository generator, not an installer. It mirrors the
layout used by the official Termux apt repository while adding a CodeC manifest
and strict package validation.
"""
from __future__ import annotations

import argparse
import gzip
import os
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

from repository_lib import (
    CODEC_PACKAGE,
    CODEC_PREFIX,
    PackageError,
    SUPPORTED_ARCHES,
    inspect_package,
    json_dump,
    release_hashes,
    sha256,
    stanza,
    write_bytes,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="directory containing built .deb files")
    parser.add_argument("output", type=Path, help="new repository directory")
    parser.add_argument(
        "--architectures",
        nargs="+",
        default=["aarch64", "x86_64"],
        choices=["aarch64", "x86_64"],
        help="architecture-specific indexes to create (default: aarch64 x86_64)",
    )
    parser.add_argument("--suite", default="stable")
    parser.add_argument("--component", default="main")
    return parser.parse_args()


def safe_deb_filename(record: dict) -> str:
    """Return a static-host/artifact-safe filename without changing Version."""
    safe_version = re.sub(r"[^A-Za-z0-9.+~_-]", "_", record["version"])
    safe_name = re.sub(r"[^A-Za-z0-9.+-]", "_", record["name"])
    safe_arch = re.sub(r"[^A-Za-z0-9.+-]", "_", record["architecture"])
    return f"{safe_name}_{safe_version}_{safe_arch}.deb"


def build(args: argparse.Namespace) -> None:
    if not args.input.is_dir():
        raise PackageError(f"input directory does not exist: {args.input}")
    if args.output.exists():
        if args.output.is_file():
            raise PackageError(f"output is a file: {args.output}")
        shutil.rmtree(args.output)
    args.output.mkdir(parents=True)

    debs = sorted(args.input.rglob("*.deb"))
    if not debs:
        raise PackageError(f"no .deb files found under {args.input}")

    records = []
    seen = set()
    for deb in debs:
        record = inspect_package(deb, SUPPORTED_ARCHES)
        key = (record["name"], record["architecture"])
        if key in seen:
            raise PackageError(f"duplicate package/architecture: {key[0]} {key[1]}")
        seen.add(key)
        records.append(record)

    release_dir = args.output / "dists" / args.suite
    component_dir = release_dir / args.component
    published = []
    published_names = set()
    for record in sorted(records, key=lambda item: (item["architecture"], item["name"])):
        arch_dir = component_dir / f"binary-{record['architecture']}"
        arch_dir.mkdir(parents=True, exist_ok=True)
        destination_name = safe_deb_filename(record)
        if destination_name in published_names:
            raise PackageError(f"filename collision after sanitizing package names: {destination_name}")
        published_names.add(destination_name)
        destination = arch_dir / destination_name
        shutil.copy2(record["path"], destination)
        record = dict(record)
        record["filename"] = str(destination.relative_to(args.output)).replace(os.sep, "/")
        published.append(record)

    index_arches = ["all", *args.architectures]
    for arch in index_arches:
        selected = [
            record
            for record in published
            if record["architecture"] == "all"
            or (arch != "all" and record["architecture"] == arch)
        ]
        # binary-all contains only architecture-independent packages; an
        # architecture-specific index contains its native packages and apt's
        # separate binary-all index supplies the common packages.
        if arch == "all":
            selected = [record for record in published if record["architecture"] == "all"]
        selected.sort(key=lambda item: (item["name"], item["version"]))
        body = "\n\n".join(stanza(record) for record in selected) + ("\n\n" if selected else "")
        package_path = component_dir / f"binary-{arch}" / "Packages"
        write_bytes(package_path, body.encode("utf-8"))
        write_bytes(
            package_path.with_name("Packages.gz"),
            gzip.compress(body.encode("utf-8"), compresslevel=9, mtime=0),
        )

    epoch_value = os.environ.get("SOURCE_DATE_EPOCH")
    epoch = int(epoch_value) if epoch_value is not None else None
    if epoch is not None:
        date = datetime.fromtimestamp(epoch, timezone.utc)
    else:
        date = datetime.now(timezone.utc)
    date_text = date.strftime("%a, %d %b %Y %H:%M:%S UTC")
    release_lines = [
        "Origin: CodeC",
        "Label: CodeC packages",
        f"Suite: {args.suite}",
        f"Codename: {args.suite}",
        f"Version: {epoch if epoch is not None else 1}",
        "Components: " + args.component,
        "Architectures: " + " ".join(index_arches),
        f"Date: {date_text}",
        "Description: CodeC packages rebuilt for com.codeci.ide",
    ]
    hashes = release_hashes(args.output, release_dir)
    release_lines.extend(["", "SHA256:"])
    for digest, size, relative, _ in hashes:
        release_lines.append(f" {digest} {size:16d} {relative}")
    release_lines.extend(["", "MD5Sum:"])
    # Keep the traditional MD5 section for apt clients that expect it; SHA256
    # remains the authoritative integrity value in Packages and the manifest.
    import hashlib

    for item in sorted(
        item for item in release_dir.rglob("*") if item.is_file() and item.name in {"Packages", "Packages.gz"}
    ):
        digest = hashlib.md5(item.read_bytes()).hexdigest()
        release_lines.append(
            f" {digest} {item.stat().st_size:16d} {item.relative_to(args.output).as_posix()}"
        )
    release_path = release_dir / "Release"
    release_path.write_text("\n".join(release_lines) + "\n")
    write_bytes(
        release_path.with_name("Release.sha256"),
        (sha256(release_path) + "  Release\n").encode("ascii"),
    )

    manifest_packages = []
    for record in sorted(published, key=lambda item: (item["name"], item["architecture"])):
        manifest_packages.append(
            {
                "name": record["name"],
                "version": record["version"],
                "architecture": record["architecture"],
                "depends": record["depends"],
                "description": record["description"],
                "filename": record["filename"],
                "size": record["size"],
                "sha256": record["sha256"],
            }
        )
    manifest = {
        "schema": 1,
        "repository": "codec",
        "package": CODEC_PACKAGE,
        "prefix": "/data/data/com.codeci.ide/files/usr",
        "suite": args.suite,
        "component": args.component,
        "architectures": index_arches,
        "packages": manifest_packages,
    }
    manifest_path = args.output / "repository.json"
    json_dump(manifest_path, manifest)
    write_bytes(
        manifest_path.with_name("repository.json.sha256"),
        (sha256(manifest_path) + "  repository.json\n").encode("ascii"),
    )

    # A small operator-readable marker prevents accidental publishing of an
    # official Termux tree to the CodeC endpoint.
    (args.output / "CODEC-REPOSITORY").write_text(
        "CodeC package repository\n"
        f"package={CODEC_PACKAGE}\n"
        f"prefix=/{CODEC_PREFIX}\n"
    )


def main() -> int:
    try:
        build(parse_args())
    except PackageError as exc:
        print(f"generate-repository: ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
