#!/usr/bin/env python3
"""Plan Phase 3 bootstrap seeding: closure walk + alternatives wiring.

Part B of docs/NEXT_STEPS.md fixes three bootstrap content defects:

1. Build-dependency pollution: the old assembler extracted and seeded every
   built .deb (doxygen, swig, tcl, tor, ...). This tool computes the
   transitive Depends closure of the explicit seed set (default: busybox,
   bash, apt, dpkg, coreutils, less, curl) against the dpkg-deb control
   metadata of the built .debs, matching the semantics of the pinned
   upstream generate-bootstraps.sh pull_package(): one dependency spec per
   comma group, first alternative taken, "(...)" version constraints
   stripped. An unresolved dependency is a hard error (fail loudly, never
   guess).

2. Seeded packages never had their alternatives wired (postinst was never
   run), so `pager`/`editor`/`vi`/`nc` were missing until a real pkg
   install. This tool parses .alternatives files in the termux-packages
   tree (the same files termux_step_create_alternatives consumes) and
   wires the link chains + the dpkg alternatives admin database, in the
   exact on-disk format dpkg's update-alternatives writes (verified against
   a live dpkg 1.21 reference), with absolute device-prefix targets that
   the assembler later relativizes like every other in-prefix symlink.

3. (Handled by the assembler) md5sums control files for every seeded
   package, in the pinned-upstream format (paths relative to the package
   root, i.e. "data/data/..." prefix paths).

Only filesystem writes under --stage are performed; the device prefix is
never touched on the build host.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple


# --------------------------------------------------------------------------
# Dependency field parsing (mirrors pinned generate-bootstraps.sh semantics:
# split on ',', take the first '|' alternative, strip '(...)' constraints).
# We additionally trim whitespace and ':arch' qualifiers, which are harmless.
# --------------------------------------------------------------------------

def parse_dependency_names(field_text: str) -> List[str]:
    names: List[str] = []
    for group in field_text.split(","):
        first = group.split("|", 1)[0]
        first = re.sub(r"\(.*\)", "", first)
        first = first.split(":", 1)[0].strip()
        if first:
            names.append(first)
    return names


class UnresolvedDependency(Exception):
    """A dependency name is not provided by any built .deb."""


@dataclass
class DebRecord:
    name: str
    path: str
    depends: List[str] = field(default_factory=list)


def select_closure(records: Dict[str, DebRecord], roots: Sequence[str]) -> List[str]:
    """Breadth-first closure over `roots`.

    Returns package names including the roots, in deterministic order
    (visited order with dependencies sorted). Raises UnresolvedDependency
    on the first dependency name with no built .deb, like the upstream
    generator failing hard when repo metadata lacks a package.
    """
    seen: set[str] = set()
    order: List[str] = []
    queue: List[str] = []
    for root in roots:
        root = root.strip()
        if root and root not in seen:
            seen.add(root)
            queue.append(root)
    for root in queue:
        if root not in records:
            raise UnresolvedDependency(
                f"seed package '{root}' has no built .deb — the bootstrap "
                "seed list can only name packages that were built"
            )
    while queue:
        name = queue.pop(0)
        order.append(name)
        for dep in sorted(records[name].depends):
            if dep not in records:
                raise UnresolvedDependency(
                    f"package '{name}' Depends on '{dep}', which no built "
                    ".deb provides — refuse to seed an inconsistent closure"
                )
            if dep not in seen:
                seen.add(dep)
                queue.append(dep)
    return order


# --------------------------------------------------------------------------
# .alternatives parsing (termux-packages tree format):
#
#   Name: pager
#   Link: bin/pager
#   Alternative: bin/less
#   Dependents:
#     share/man/man1/pager.1.gz pager.1.gz share/man/man1/less.1.gz
#   Priority: 50 # default
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class SlaveLink:
    link: str        # e.g. share/man/man1/pager.1.gz (prefix-relative)
    name: str        # e.g. pager.1.gz
    target: str      # e.g. share/man/man1/less.1.gz (prefix-relative)


@dataclass(frozen=True)
class Alternative:
    package: str
    name: str        # group name, e.g. pager
    link: str        # master link, prefix-relative, e.g. bin/pager
    target: str      # provider path, prefix-relative, e.g. bin/less
    priority: int
    slaves: Tuple[SlaveLink, ...]


_BLOCK_KEY = re.compile(r"^(Name|Link|Alternative|Priority|Dependents)\s*:\s*(.*)$")


def parse_alternatives(text: str, package: str) -> List[Alternative]:
    """Parse all alternatives blocks from one .alternatives file."""
    blocks: List[Alternative] = []
    current: Dict[str, object] = {}

    def flush() -> None:
        if not current:
            return
        name = str(current.get("name", "")).strip()
        link = str(current.get("link", "")).strip()
        target = str(current.get("target", "")).strip()
        priority = int(str(current.get("priority", "50")).strip() or 50)
        slaves = tuple(current.get("slaves", ()))  # type: ignore[arg-type]
        if name and link and target:
            blocks.append(
                Alternative(package, name, link, target, priority, slaves)
            )

    in_dependents = False
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            if not line:
                # Blank line ends a block.
                flush()
                current = {}
                in_dependents = False
            continue
        m = _BLOCK_KEY.match(line)
        if m:
            key, value = m.group(1).lower(), m.group(2)
            # Priority may carry a trailing comment: '50 # default'.
            if key == "priority":
                value = value.split("#", 1)[0].strip()
            current[key if key != "alternative" else "target"] = value
            in_dependents = key == "dependents"
            if in_dependents:
                current.setdefault("slaves", [])
            continue
        if in_dependents:
            parts = line.split()
            if len(parts) == 3:
                slaves = current.setdefault("slaves", [])
                assert isinstance(slaves, list)
                slaves.append(SlaveLink(parts[0], parts[1], parts[2]))
                continue
        # Unknown continuation line inside a block: ignore (comments etc.).
    flush()
    return blocks


# --------------------------------------------------------------------------
# Winner selection + wiring plan/admin database.
# The admin database content replicates exactly what dpkg's
# update-alternatives writes. Measured against a live Debian dpkg 1.21:
#
#   auto
#   <abs master link>                <- from the LAST registration
#   <slave name>                     <- LAST registration's slave set
#   <abs slave link>
#   ...
#   <blank>
#   <alternative path, abs>          <- PREPEND order (most recent first)
#   <priority>
#   <own slave target, abs>          <- this alternative's declared slaves
#   <alternative path, abs>
#   ...
#   <blank>
#
# Also measured live: registering a member WITH a slave into a group that
# previously had none (busybox editor -> nano editor) succeeds silently, so
# mixed slave declarations across providers are intentional behavior, and
# the group slave block simply tracks the most recent registration.
# --------------------------------------------------------------------------

def _confine(relative_path: str) -> str:
    """Prefix-confinement guard: every wired path must stay inside the
    prefix — same spirit as the app's pkg preflight."""
    if not relative_path or relative_path.startswith("/"):
        raise ValueError(f"alternatives path must be prefix-relative: {relative_path!r}")
    parts = [seg for seg in relative_path.split("/") if seg not in ("", ".")]
    if any(seg == ".." for seg in parts):
        raise ValueError(f"alternatives path escapes the prefix: {relative_path!r}")
    return "/".join(parts)


def plan_group(
    name: str, members: List[Alternative], prefix: str
) -> Tuple[List[Tuple[str, str]], str]:
    """Return (symlink_ops, admin_content) for one alternatives group.

    `members` must arrive in *registration order* (the deterministic order
    in which the seeded packages' postinsts would run: sorted package
    names). Auto-mode semantics measured against real dpkg:

    * the master link points at the highest-priority alternative
      (ties: the most recently registered);
    * the group's slave block tracks the most recent registration;
    * the admin file lists alternatives in prepend order (most recent
      first), each with its own declared slave targets.

    symlink_ops are (link_path_absolute_device, target_absolute_device)
    pairs; the assembler creates them and then relativizes like every
    other in-prefix symlink.
    """
    if len({m.package for m in members}) != len(members):
        dupes = sorted({m.package for m in members if sum(1 for x in members if x.package == m.package) > 1})
        raise ValueError(f"group '{name}': duplicate provider package(s): {dupes}")
    # Winner: highest priority; ties broken towards the most recent
    # registration (that is what sequential postinst runs would produce).
    winner = members[0]
    for m in members[1:]:
        if m.priority >= winner.priority:
            winner = m
    last = members[-1]

    ops: List[Tuple[str, str]] = []
    master_link = f"{prefix}/{_confine(last.link)}"
    altdir_master = f"{prefix}/etc/alternatives/{_confine(name)}"
    ops.append((altdir_master, f"{prefix}/{_confine(winner.target)}"))
    ops.append((master_link, altdir_master))
    # Slave wiring follows the group block = the most recent registration;
    # slave targets track the winning alternative when it declares that
    # slave, else the most recent declaration (matches measured behavior
    # for the provider sets in the termux-packages tree).
    slaves = last.slaves if last.slaves else winner.slaves
    for slave in slaves:
        slave_link = f"{prefix}/{_confine(slave.link)}"
        altdir_slave = f"{prefix}/etc/alternatives/{_confine(slave.name)}"
        target = next(
            (s.target for s in winner.slaves if s.name == slave.name),
            slave.target,
        )
        ops.append((altdir_slave, f"{prefix}/{_confine(target)}"))
        ops.append((slave_link, altdir_slave))

    lines = ["auto", master_link]
    for slave in slaves:
        lines.append(slave.name)
        lines.append(f"{prefix}/{_confine(slave.link)}")
    lines.append("")
    for m in reversed(members):  # prepend order: most recent first
        lines.append(f"{prefix}/{_confine(m.target)}")
        lines.append(str(m.priority))
        for slave in m.slaves:
            lines.append(f"{prefix}/{_confine(slave.target)}")
    lines.append("")
    return ops, "\n".join(lines) + "\n"


def apply_plan(
    stage_dir: Path,
    prefix: str,
    per_name_ops: List[Tuple[str, str]],
    admin: Dict[str, str],
) -> List[str]:
    """Create the symlink chains and admin database inside the stage tree.

    Symlink targets stay absolute device paths here; the assembler's
    relativization pass (already used for packaged symlinks) normalizes
    them to relative targets before archiving."""
    prefix = prefix.rstrip("/")
    actions: List[str] = []
    stage = stage_dir.resolve()
    for link_abs, target_abs in per_name_ops:
        if not (link_abs + "/").startswith(prefix + "/"):
            raise ValueError(f"link path outside prefix: {link_abs}")
        rel = link_abs[len(prefix) + 1:]
        link_path = stage / rel
        link_path.parent.mkdir(parents=True, exist_ok=True)
        if link_path.is_symlink():
            link_path.unlink()
        elif link_path.exists():
            # update-alternatives never silently replaces a real packaged
            # file with a link; surface the collision as evidence instead.
            raise ValueError(
                f"refusing to replace real file with alternatives link: {rel}"
            )
        os.symlink(target_abs, link_path)
        actions.append(f"link {rel} -> {target_abs}")
    admindir = stage / "var/lib/dpkg/alternatives"
    admindir.mkdir(parents=True, exist_ok=True)
    for name, content in sorted(admin.items()):
        (admindir / name).write_text(content)
        actions.append(f"admin var/lib/dpkg/alternatives/{name}")
    return actions


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def _read_deb_record(deb_path: str) -> DebRecord:
    # Read the whole control stanza and parse it ourselves: `dpkg-deb -f`
    # with explicit field names switches output shape ("Package: name"
    # instead of bare "name") when some requested field is absent, which
    # silently corrupts positional parsing — measured against dpkg-deb
    # 1.21 with fixture .debs lacking Depends/Pre-Depends.
    out = subprocess.run(
        ["dpkg-deb", "-f", deb_path],
        capture_output=True, text=True, check=False,
    )
    if out.returncode != 0 or not out.stdout.strip():
        raise SystemExit(
            f"plan-bootstrap: cannot read control fields from {deb_path}: "
            f"{out.stderr.strip()}"
        )
    fields: Dict[str, str] = {}
    current: Optional[str] = None
    for line in out.stdout.splitlines():
        if line.startswith((" ", "\t")) and current:
            fields[current] += "\n" + line
        elif ":" in line:
            key, _, value = line.partition(":")
            current = key.strip()
            fields[current] = value.strip()
    name = fields.get("Package", "").strip()
    if not name:
        raise SystemExit(f"plan-bootstrap: no Package field in {deb_path}")
    depends_text = " ".join(
        text for text in (fields.get("Depends", ""), fields.get("Pre-Depends", ""))
        if text.strip()
    )
    return DebRecord(name=name, path=deb_path, depends=parse_dependency_names(depends_text))


def cmd_closure(argv: Sequence[str]) -> int:
    if len(argv) < 2:
        print("usage: plan-bootstrap.py closure ROOTS_CSV DEB [DEB ...]", file=sys.stderr)
        return 64
    roots_csv, deb_paths = argv[0], argv[1:]
    roots = roots_csv.split()
    records: Dict[str, DebRecord] = {}
    for path in deb_paths:
        rec = _read_deb_record(path)
        records[rec.name] = rec  # later duplicates win, as documented
    try:
        closure = select_closure(records, roots)
    except UnresolvedDependency as exc:
        print(f"plan-bootstrap: closure error: {exc}", file=sys.stderr)
        return 2
    selected = sorted((records[name] for name in closure), key=lambda r: r.name)
    for rec in selected:
        print(f"{rec.name}\t{rec.path}")
    return 0


def cmd_alternatives(argv: Sequence[str]) -> int:
    opts: Dict[str, str] = {}
    packages: List[str] = []
    i = 0
    while i < len(argv):
        if argv[i].startswith("--"):
            i += 1
            if i >= len(argv):
                print(f"plan-bootstrap: {argv[i-1]} needs a value", file=sys.stderr)
                return 64
            opts[argv[i - 1][2:]] = argv[i]
        else:
            packages.append(argv[i])
        i += 1
    tree = opts.get("tree", "")
    stage = opts.get("stage", "")
    prefix = opts.get("prefix", "")
    if not (tree and stage and prefix):
        print("usage: plan-bootstrap.py alternatives --tree SRC --stage DIR "
              "--prefix PREFIX PKG [PKG ...]", file=sys.stderr)
        return 64

    all_blocks: List[Alternative] = []
    for package in packages:
        pkg_dir = Path(tree) / "packages" / package
        if not pkg_dir.is_dir():
            continue
        for alt_file in sorted(pkg_dir.glob("*.alternatives")):
            blocks = parse_alternatives(alt_file.read_text(), package)
            if blocks:
                print(f"plan-bootstrap: {package}: {alt_file.name} "
                      f"registers {len(blocks)} group(s)", file=sys.stderr)
            all_blocks.extend(blocks)

    groups: Dict[str, List[Alternative]] = {}
    for block in all_blocks:
        groups.setdefault(block.name, []).append(block)

    ops: List[Tuple[str, str]] = []
    admin: Dict[str, str] = {}
    for name, members in sorted(groups.items()):
        group_ops, admin_content = plan_group(name, members, prefix.rstrip("/"))
        # Members arrive in registration order (= argv order = the sorted
        # seeded list); the winner is max priority, ties to the most recent.
        winner = members[0]
        for m in members[1:]:
            if m.priority >= winner.priority:
                winner = m
        print(f"plan-bootstrap: group {name}: winner {winner.package} "
              f"(priority {winner.priority}) among {[m.package for m in members]}",
              file=sys.stderr)
        ops.extend(group_ops)
        admin[name] = admin_content

    for action in apply_plan(Path(stage), prefix.rstrip("/"), ops, admin):
        print(f"plan-bootstrap: {action}", file=sys.stderr)
    return 0


def main(argv: Sequence[str]) -> int:
    if not argv:
        print("usage: plan-bootstrap.py {closure|alternatives} ...", file=sys.stderr)
        return 64
    command, rest = argv[0], argv[1:]
    if command == "closure":
        return cmd_closure(rest)
    if command == "alternatives":
        return cmd_alternatives(rest)
    print(f"plan-bootstrap: unknown command {command!r}", file=sys.stderr)
    return 64


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
