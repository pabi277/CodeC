#!/usr/bin/env python3
"""Phase 50.1 one-shot: convert the six core surfaces to CodecTokens.

Explicit asserted replacements + value maps. Verifies the TokenAdoption
rules afterwards. Safe to re-run (asserts fail if already converted).
"""
import re
import sys

BASE = "app/src/main/java/com/codeci/ide/ui/screens/"
SIX = ["WelcomeScreen", "EditorScreen", "FileManagerScreen",
       "ModulesScreen", "TerminalScreen", "SettingsScreen"]

SPACE = {"0": "NONE", "1": "XXS", "2": "XXS", "3": "XS", "4": "XS",
         "5": "XS", "6": "S", "7": "S", "8": "S", "9": "S", "10": "M",
         "12": "M", "13": "M", "14": "L", "16": "L", "18": "L",
         "24": "XL", "28": "XXL", "32": "XXL", "36": "XXL",
         "44": "HUGE", "48": "HUGE"}
RADIUS = {"2": "XS", "4": "XS", "5": "XS", "6": "S", "8": "S",
          "9": "S", "10": "M", "12": "M", "14": "L", "16": "L"}
# .size() helper: S=space, I=icon
SIZE = {"10": ("S", "M"), "13": ("S", "M"), "14": ("I", "INLINE"),
        "16": ("I", "INLINE"), "18": ("I", "ACTION"), "20": ("I", "ACTION"),
        "22": ("I", "NAV"), "24": ("I", "NAV"), "28": ("S", "XXL"),
        "30": ("S", "XXL"), "44": ("S", "HUGE"), "48": ("S", "HUGE")}
KEEP = {"56", "58", "64", "72", "1.2"}


def conv_size(m):
    n = m.group(1)
    if n in KEEP:
        return m.group(0)
    assert n in SIZE, f"unmapped .size({n})"
    h, step = SIZE[n]
    fn = "icon(CodecTokens.Icon" if h == "I" else "space(Space"
    close = ")" if h == "I" else ")"
    return f".size(CodecTokens.{fn}.{step}{close})"


def main():
    for name in SIX:
        path = BASE + name + ".kt"
        src = open(path).read()
        orig = src

        # 0. Explicit specials first (match raw text, unique contexts).
        def rep(old, new, count=1):
            nonlocal src
            found = src.count(old)
            assert found == count, name + ": expected " + str(count) + "x " + repr(old[:60]) + ", found " + str(found)
            src = src.replace(old, new)

        if name == "FileManagerScreen":
            rep("start = (16 + node.depth * 24).dp",
                "start = (Space.L + node.depth * Space.XL).dp")
            rep("bottom = 96.dp", "bottom = CodecTokens.space(Space.HUGE) * 2f")
            rep("CircularProgressIndicator(\n                                                modifier = Modifier.size(16.dp),",
                "CircularProgressIndicator(\n                                                modifier = Modifier.size(CodecTokens.space(Space.L)),")
        if name == "ModulesScreen":
            rep("onClick = { onCopyCommand(item.installCommand) },\n                        modifier = Modifier.size(24.dp)",
                "onClick = { onCopyCommand(item.installCommand) },\n                        modifier = Modifier.size(CodecTokens.MIN_TOUCH)")
        if name == "EditorScreen":
            rep(".defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)",
                ".defaultMinSize(minHeight = CodecTokens.MIN_TOUCH, minWidth = CodecTokens.MIN_TOUCH)")
        if name == "SettingsScreen":
            rep(".size(18.dp)\n            .clip(CircleShape)",
                ".size(CodecTokens.space(Space.L))\n            .clip(CircleShape)")

        # 0b. Protect thickness (borders, progress stroke).
        protected = []
        def prot(m):
            protected.append(m.group(0))
            return f"\x00P{len(protected)-1}\x00"
        src = re.sub(r"strokeWidth\s*=\s*[\d.]+\.dp", prot, src)
        src = re.sub(r"\.border\(\s*[\d.]+\.dp", prot, src)
        src = re.sub(r"border\(width\s*=\s*[\d.]+\.dp", prot, src)
        src = re.sub(r"width\s*=\s*1\.dp,", prot, src)
        src = re.sub(r"BorderStroke\(\s*[\d.]+\.dp", prot, src)

        # 1. Radii.
        def conv_r(m):
            n = m.group(1)
            assert n in RADIUS, f"{name}: unmapped radius {n}"
            return f"RoundedCornerShape(CodecTokens.radius(Radius.{RADIUS[n]}))"
        src, n_r = re.subn(r"RoundedCornerShape\((\d+)\.dp\)", conv_r, src)

        # 2. Elevations.
        src, n_e0 = re.subn(r"defaultElevation = 0\.dp",
                            "defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.FLAT)", src)
        src, n_e1 = re.subn(r"defaultElevation = 1\.dp",
                            "defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.RAISED)", src)

        # 4. Global .size().
        src, n_size = re.subn(r"\.size\((\d+)\.dp\)", conv_size, src)

        # 5. Global remaining N.dp -> space().
        def conv_space(m):
            n = m.group(1)
            if n in KEEP:
                return m.group(0)
            assert n in SPACE, f"{name}: unmapped {n}.dp near ...{m.string[max(0, m.start()-60):m.start()][-60:]!r}"
            return f"CodecTokens.space(Space.{SPACE[n]})"
        src, n_space = re.subn(r"(?<!\d)(\d+)\.dp\b", conv_space, src)

        # 6. Restore thickness.
        for i, orig_text in enumerate(protected):
            src = src.replace(f"\x00P{i}\x00", orig_text)
        assert "\x00" not in src, f"{name}: placeholder leak"

        # 7. Imports.
        assert "import com.codeci.ide.ui.theme.CodecTokens\n" not in src
        anchor = "import com.codeci.ide"
        assert anchor in src, f"{name}: no com.codeci import anchor"
        src = src.replace(
            anchor,
            "import com.codeci.ide.ui.theme.CodecTokens\n"
            "import com.codeci.ide.ui.theme.CodecTokens.Radius\n"
            "import com.codeci.ide.ui.theme.CodecTokens.Space\n" + anchor,
            1,
        )
        if name in ("WelcomeScreen", "TerminalScreen"):
            assert ".dp" not in re.sub(r"import androidx\.compose\.ui\.unit\.dp\n", "", src), \
                f"{name}: raw .dp remains, keeping dp import"
            src = src.replace("import androidx.compose.ui.unit.dp\n", "")
        # (ModulesScreen keeps its sp import until the 50.3 fontSize pass.)

        open(path, "w").write(src)
        print(f"{name}: radius={n_r} elev={n_e0+n_e1} size={n_size} space={n_space} "
              f"protected={len(protected)} (was {len(orig)} chars, now {len(src)})")

    # Verification: replicate TokenAdoption rules.
    print("\n--- verification ---")
    literal = re.compile(r"(\d+(?:\.\d+)?)\.dp\b")
    spacing_kw = ["padding", "PaddingValues", "spacedBy", "defaultMinSize",
                  "size", "width", "height", "defaultElevation", "RoundedCornerShape"]
    thick_kw = ["border", "BorderStroke", "strokeWidth"]
    fails = []
    for name in SIX:
        code = open(BASE + name + ".kt").read()
        if "import com.codeci.ide.ui.theme.CodecTokens" not in code:
            fails.append(f"{name}: missing import")
        if re.search(r"RoundedCornerShape\(\s*\d", code):
            fails.append(f"{name}: raw radius")
        for m in literal.finditer(code):
            v = float(m.group(1))
            before = code[max(0, m.start() - 120):m.start()]
            ls = max([before.rfind(k) for k in spacing_kw])
            lt = max([before.rfind(k) for k in thick_kw])
            if lt > ls:
                continue
            if ls < 0:
                fails.append(f"{name}: unclassified {m.group(0)}")
            elif v <= 48:
                line = code[:m.start()].count("\n") + 1
                fails.append(f"{name}:{line}: raw {m.group(0)}")
    # IconButton headers under 48.
    for name in SIX:
        code = open(BASE + name + ".kt").read()
        for m in re.finditer(r"\bIconButton\s*\(", code):
            i = code.index("(", m.start()); d = 0; end = -1
            for j in range(i, len(code)):
                if code[j] == "(":
                    d += 1
                elif code[j] == ")":
                    d -= 1
                    if d == 0:
                        end = j
                        break
            header = code[m.start():end + 1]
            for b in re.finditer(r"\.(size|requiredSize|defaultMinSize)\(\s*(\d+)", header):
                if int(b.group(2)) < 48:
                    fails.append(f"{name}: small IconButton {b.group(0)}")
    if fails:
        print("FAILURES:")
        print("\n".join(fails))
        sys.exit(1)
    print("all TokenAdoption/TouchTarget rules hold")


if __name__ == "__main__":
    main()
