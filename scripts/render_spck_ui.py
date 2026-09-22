#!/usr/bin/env python3
"""Spec drawings of the accepted phone UI. Not photos, not store art.

Renders the chrome agreed from the 2026-09-22 SPCK phone shots:
no bottom project bar, rooms in the side panel, status line + touch row
at the bottom. Output is docs/spck-ui/*.png.
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "spck-ui")

SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
SANSB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

# Logical phone. Drawn at 2x and downsampled so edges stay smooth.
W, H, SCALE = 420, 900, 2

C = {
    "bg": (17, 19, 23),
    "bar": (23, 26, 31),
    "panel": (22, 25, 30),
    "strip": (17, 19, 23),
    "line": (46, 50, 58),
    "gutter": (92, 99, 110),
    "text": (226, 230, 236),
    "muted": (138, 146, 158),
    "faint": (96, 104, 116),
    "green": (52, 211, 122),
    "blue": (78, 163, 255),
    "tag": (86, 166, 255),
    "attr": (230, 192, 104),
    "string": (152, 195, 121),
    "comment": (110, 118, 130),
    "punct": (180, 186, 196),
    "orange": (224, 122, 61),
    "tab": (232, 96, 74),
    "cursor": (70, 156, 255),
    "current": (34, 38, 46),
    "chip": (38, 42, 50),
    "key": (46, 50, 58),
    "key_hi": (58, 63, 72),
    "pill": (36, 39, 46),
    "button": (47, 120, 230),
    "white": (244, 246, 250),
    "sel": (40, 58, 82),
    "card": (32, 36, 44),
    "card_on": (44, 62, 86),
    "kb": (28, 30, 34),
    "kb_key": (62, 66, 74),
    "warn": (255, 196, 92),
}


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size * SCALE)


class P:
    """Logical-pixel drawing surface, rendered at SCALE and downsampled."""

    def __init__(self, w=W, h=H, bg=None):
        self.w, self.h = w, h
        self.im = Image.new("RGB", (w * SCALE, h * SCALE), bg or C["bg"])
        self.d = ImageDraw.Draw(self.im)
        self.f = font
        self._fonts = {}

    def font(self, size: int, bold=False, mono=False):
        key = (size, bold, mono)
        if key not in self._fonts:
            path = MONO if mono else (SANSB if bold else SANS)
            self._fonts[key] = font(path, size)
        return self._fonts[key]

    def _b(self, box):
        x, y, w, h = box
        return [x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE]

    def rect(self, box, fill=None, outline=None, width=1, radius=0):
        xy = self._b(box)
        if radius:
            self.d.rounded_rectangle(
                xy, radius=radius * SCALE, fill=fill, outline=outline, width=max(1, width * SCALE)
            )
        else:
            self.d.rectangle(xy, fill=fill, outline=outline, width=max(1, width * SCALE))

    def line(self, a, b, fill, width=1):
        self.d.line(
            [(a[0] * SCALE, a[1] * SCALE), (b[0] * SCALE, b[1] * SCALE)],
            fill=fill,
            width=max(1, width * SCALE),
        )

    def text(self, xy, s, fill, size=13, bold=False, mono=False, anchor="lt"):
        self.d.text(
            (xy[0] * SCALE, xy[1] * SCALE),
            s,
            font=self.font(size, bold, mono),
            fill=fill,
            anchor=anchor,
        )

    def text_w(self, s, size=13, bold=False, mono=False) -> float:
        b = self.font(size, bold, mono).getbbox(s)
        return (b[2] - b[0]) / SCALE

    def ellipse(self, box, fill=None, outline=None, width=1):
        self.d.ellipse(self._b(box), fill=fill, outline=outline, width=max(1, width * SCALE))

    def poly(self, pts, fill):
        self.d.polygon([(x * SCALE, y * SCALE) for x, y in pts], fill=fill)

    def save(self, path):
        out = self.im.resize((self.w, self.h), Image.Resampling.LANCZOS)
        out.save(path, "PNG", optimize=True)


def hamburger(p: P, x, y, color=None):
    color = color or C["text"]
    for i in range(3):
        p.line((x, y + i * 5), (x + 16, y + i * 5), color, 2)


def search_icon(p: P, cx, cy, r=7, color=None):
    color = color or C["text"]
    p.ellipse((cx - r, cy - r, r * 2, r * 2), outline=color, width=2)
    p.line((cx + r * 0.6, cy + r * 0.6), (cx + r * 1.7, cy + r * 1.7), color, 2)


def play(p: P, x, y, s=12, color=None):
    color = color or C["green"]
    p.poly([(x, y), (x, y + s), (x + s * 0.86, y + s / 2)], color)


def nav_triangle(p: P, x, y, color):
    """Outline triangle. Navigation, not the green play button."""
    p.poly([(x, y + 12), (x + 6, y), (x + 12, y + 12)], color)
    p.poly([(x + 3, y + 10), (x + 6, y + 4), (x + 9, y + 10)], C["panel"])


def folder_icon(p: P, x, y, color=None):
    color = color or C["muted"]
    p.rect((x, y + 3, 15, 10), fill=color, radius=2)
    p.rect((x, y + 1, 7, 4), fill=color, radius=1)


def branch_icon(p: P, x, y, color=None):
    color = color or C["muted"]
    p.ellipse((x, y, 5, 5), outline=color, width=1)
    p.ellipse((x, y + 10, 5, 5), outline=color, width=1)
    p.ellipse((x + 10, y + 5, 5, 5), outline=color, width=1)
    p.line((x + 2, y + 5), (x + 2, y + 10), color, 1)
    p.line((x + 2, y + 7), (x + 10, y + 7), color, 1)


def person_icon(p: P, x, y, color=None):
    color = color or C["muted"]
    p.ellipse((x + 3, y, 8, 8), outline=color, width=1)
    p.d.arc(
        [(x) * SCALE, (y + 9) * SCALE, (x + 14) * SCALE, (y + 20) * SCALE],
        200,
        340,
        fill=color,
        width=SCALE,
    )


def pin_icon(p: P, x, y, color=None):
    color = color or C["muted"]
    p.ellipse((x + 3, y, 8, 8), outline=color, width=1)
    p.line((x + 7, y + 8), (x + 7, y + 14), color, 1)
    p.line((x + 4, y + 14), (x + 10, y + 14), color, 1)


def page_icon(p: P, x, y, color=None):
    color = color or C["orange"]
    p.rect((x, y, 14, 16), fill=color, radius=3)
    p.line((x + 3, y + 8), (x + 6, y + 5), C["white"], 1)
    p.line((x + 3, y + 8), (x + 6, y + 11), C["white"], 1)
    p.line((x + 8, y + 5), (x + 11, y + 8), C["white"], 1)
    p.line((x + 8, y + 11), (x + 11, y + 8), C["white"], 1)


def doc_icon(p: P, x, y, color=None):
    color = color or C["muted"]
    p.rect((x, y, 12, 15), outline=color, width=1, radius=1)
    p.line((x + 3, y + 5), (x + 9, y + 5), color, 1)
    p.line((x + 3, y + 8), (x + 9, y + 8), color, 1)


def chevron(p: P, x, y, open_=False, color=None):
    color = color or C["faint"]
    if open_:
        p.poly([(x, y), (x + 8, y), (x + 4, y + 5)], color)
    else:
        p.poly([(x, y), (x + 5, y + 4), (x, y + 8)], color)


def dots(p: P, x, y, color=None):
    color = color or C["faint"]
    for i in range(3):
        p.ellipse((x, y + i * 5, 3, 3), fill=color)


def system_status(p: P):
    p.rect((0, 0, W, 28), fill=C["bg"])
    p.text((16, 8), "12:41", C["text"], 11, bold=True)
    p.text((W - 78, 8), "5G  86%", C["text"], 11)


def gesture_bar(p: P, y):
    p.rect((p.w / 2 - 48, y + 8, 96, 4), fill=C["line"], radius=2)


def app_bar(p: P, y=28):
    p.rect((0, y, W, 52), fill=C["bar"])
    hamburger(p, 16, y + 20)
    page_icon(p, 48, y + 18)
    p.text((68, y + 17), "index.html", C["text"], 15, bold=True)
    search_icon(p, W - 58, y + 26, 8)
    play(p, W - 28, y + 18, 16)
    p.line((0, y + 51), (W, y + 51), C["line"], 1)
    return y + 52


def tab_row(p: P, y):
    p.rect((0, y, W, 40), fill=C["bar"])
    p.rect((12, y, 132, 3), fill=C["tab"])
    page_icon(p, 16, y + 13)
    p.text((34, y + 12), "index.html", C["text"], 12)
    # box with a down arrow — action not confirmed
    p.rect((W - 36, y + 10, 18, 18), outline=C["faint"], width=1, radius=3)
    p.poly(
        [(W - 31, y + 16), (W - 23, y + 16), (W - 27, y + 21)],
        C["faint"],
    )
    p.line((0, y + 39), (W, y + 39), C["line"], 1)
    return y + 40


HTML = [
    (74, [("tag", "    <button "), ("attr", "class"), ("punct", "="), ("string", '"nav-btn"'), ("tag", ">")]),
    (75, [("text", "      Content")]),
    (76, [("tag", "    </button>")]),
    (77, [("tag", "    <button "), ("attr", "class"), ("punct", "="), ("string", '"nav-btn"'), ("tag", ">")]),
    (78, [("text", "      Home")]),
    (79, [("tag", "    </button>")]),
    (80, [("tag", "    <button "), ("attr", "class"), ("punct", "="), ("string", '"nav-btn"'), ("tag", ">")]),
    (81, [("text", "      More")]),
    (82, [("tag", "    </button>")]),
    (83, [("tag", "  </nav>")]),
    (84, [("tag", "</body>")]),
    (85, [("tag", "</html>")]),
]

TONE = {
    "tag": C["tag"],
    "attr": C["attr"],
    "string": C["string"],
    "comment": C["comment"],
    "punct": C["punct"],
    "text": C["text"],
}


def code_view(p: P, y, bottom, caret_line=85):
    p.rect((0, y, W, bottom - y), fill=C["bg"])
    gutter = 42
    row_h = 22
    visible = []
    yy = y + 8
    for num, parts in HTML:
        if yy + row_h > bottom - 4:
            break
        visible.append((num, parts, yy))
        yy += row_h
    for num, parts, yy in visible:
        if num == caret_line:
            p.rect((0, yy - 2, W, row_h), fill=C["current"])
        p.text((8, yy), str(num), C["gutter"], 11, mono=True)
        # indent guide
        if num >= 4:
            p.line((gutter + 10, yy), (gutter + 10, yy + row_h), C["line"], 1)
        x = gutter
        for kind, chunk in parts:
            p.text((x, yy), chunk, TONE[kind], 12, mono=True)
            x += p.text_w(chunk, 12, mono=True)
        if num == caret_line:
            caret_x = gutter + p.text_w("</html>", 12, mono=True) + 1
            p.line((caret_x, yy + 1), (caret_x, yy + 14), C["cursor"], 2)
            p.ellipse((caret_x - 3, yy + 14, 6, 6), fill=C["cursor"])
    return bottom


def status_line(p: P, y, label="Ln 85, Col 8"):
    p.rect((0, y, p.w, 28), fill=C["bar"])
    p.line((0, y), (p.w, y), C["line"], 1)
    p.line((0, y + 27), (p.w, y + 27), C["line"], 1)
    p.text((12, y + 7), label, C["muted"], 11)
    right = "Sp: 4   HTML   LF   UTF-8"
    p.text((p.w - 12 - p.text_w(right, 11), y + 7), right, C["muted"], 11)
    return y + 28


def touch_row(p: P, y, h=48):
    p.rect((0, y, p.w, h), fill=C["bar"])
    p.line((0, y), (p.w, y), C["line"], 1)
    keys = ["Tab", "↑", "↓", "←", "→", "\"", "{ }", ">>"]
    gap = 6
    side = 8
    kw = (p.w - side * 2 - gap * (len(keys) - 1)) / len(keys)
    for i, k in enumerate(keys):
        x = side + i * (kw + gap)
        p.rect((x, y + 8, kw, h - 16), fill=C["key"], radius=8)
        p.text((x + kw / 2, y + h / 2), k, C["text"], 12, anchor="mm")
    return y + h


def predictive_row(p: P, y, h=42):
    p.rect((0, y, W, h), fill=C["bar"])
    chips = ["<tag>", "div", "class", "=", '""']
    x = 10
    for chip in chips:
        tw = p.text_w(chip, 12, mono=True) + 16
        p.rect((x, y + 7, tw, h - 14), fill=C["chip"], radius=8)
        p.text((x + 8, y + 12), chip, C["text"], 12, mono=True)
        x += tw + 6
    # right icon: purpose not confirmed
    p.rect((W - 36, y + 9, 24, 24), fill=C["chip"], radius=6)
    p.rect((W - 32, y + 14, 16, 10), outline=C["muted"], width=1, radius=2)
    p.line((W - 28, y + 18), (W - 20, y + 18), C["muted"], 1)
    return y + h


def keyboard(p: P, y):
    p.rect((0, y, W, H - y), fill=C["kb"])
    rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    yy = y + 10
    for ri, row in enumerate(rows):
        inset = 16 + ri * 10
        n = len(row)
        gap = 5
        kw = (W - inset * 2 - gap * (n - 1)) / n
        for i, ch in enumerate(row):
            x = inset + i * (kw + gap)
            p.rect((x, yy, kw, 36), fill=C["kb_key"], radius=6)
            p.text((x + kw / 2, yy + 18), ch, C["white"], 14, anchor="mm")
        yy += 42
    # space
    p.rect((70, yy, W - 140, 34), fill=C["kb_key"], radius=6)
    gesture_bar(p, H - 24)


def editor_down():
    p = P()
    system_status(p)
    y = app_bar(p)
    y = tab_row(p, y)
    touch_top = H - 24 - 48 - 28
    code_view(p, y, touch_top)
    y = status_line(p, touch_top)
    y = touch_row(p, y)
    gesture_bar(p, y)
    return p


def editor_up():
    p = P()
    system_status(p)
    y = app_bar(p)
    y = tab_row(p, y)
    kb_top = H - 210
    pred_top = kb_top - 42
    touch_top = pred_top - 48
    code_view(p, y, touch_top, caret_line=81)
    y = predictive_row(p, touch_top)
    y = touch_row(p, y)
    keyboard(p, y)
    return p


def rail(p: P, selected: int, panel_w: int, y=36):
    icons = ["nav", "files", "search", "git", "account"]
    gap = panel_w / len(icons)
    for i, name in enumerate(icons):
        cx = gap * i + gap / 2
        color = C["white"] if i == selected else C["faint"]
        if name == "nav":
            play(p, cx - 6, y + 4, 12, color)
        elif name == "files":
            folder_icon(p, cx - 7, y + 2, color)
        elif name == "search":
            search_icon(p, cx, y + 10, 6, color)
        elif name == "git":
            branch_icon(p, cx - 8, y + 2, color)
        else:
            person_icon(p, cx - 7, y + 1, color)
        if i == selected:
            p.line((cx - 12, y + 26), (cx + 12, y + 26), C["white"], 2)
    return y + 36


def editor_strip(p: P, panel_w: int):
    """The sliver the panel does not cover. Play stays tappable."""
    p.rect((panel_w, 28, W - panel_w, H - 28), fill=C["bg"])
    play(p, W - 26, 44, 14)
    # a few code marks, so the strip reads as the editor, not a second bar
    for i, yy in enumerate(range(88, H - 70, 22)):
        p.line((panel_w + 8, yy), (W - 10, yy), C["line"], 1)
    p.text((W - 26, H - 40), ">>", C["muted"], 11)


def panel_shell(title: str, selected: int):
    p = P()
    system_status(p)
    panel_w = int(W * 0.84)
    editor_strip(p, panel_w)
    p.rect((0, 28, panel_w, H - 28), fill=C["panel"])
    rail(p, selected, panel_w)
    p.text((16, 78), title, C["muted"], 11, bold=True)
    p.line((panel_w, 28), (panel_w, H), (0, 0, 0), 1)
    return p, panel_w


def grid_cell(p: P, x, y, w, h, label, kind, on=False):
    p.rect((x, y, w, h), fill=C["card_on"] if on else C["card"], radius=12)
    cx, cy = x + w / 2, y + 28
    color = C["white"] if on else C["muted"]
    if kind == "projects":
        folder_icon(p, cx - 7, cy - 8, color)
    elif kind == "editor":
        page_icon(p, cx - 6, cy - 8, C["orange"] if on else color)
    elif kind == "terminal":
        p.text((cx, cy), ">_", color, 14, bold=True, anchor="mm")
    elif kind == "packages":
        p.rect((cx - 7, cy - 6, 14, 12), outline=color, width=1, radius=2)
    elif kind == "settings":
        p.ellipse((cx - 7, cy - 7, 14, 14), outline=color, width=1)
    elif kind == "guide":
        p.text((cx, cy), "?", color, 16, bold=True, anchor="mm")
    p.text((x + w / 2, y + h - 22), label, C["white"] if on else C["text"], 12, anchor="mt")


def navigation():
    p, panel_w = panel_shell("NAVIGATION", 0)
    cells = [
        ("Projects", "projects", False),
        ("Editor", "editor", True),
        ("Terminal", "terminal", False),
        ("Packages", "packages", False),
        ("Settings", "settings", False),
        ("Guide", "guide", False),
    ]
    side = 16
    gap = 10
    cw = (panel_w - side * 2 - gap) / 2
    ch = 78
    top = 104
    for i, (label, kind, on) in enumerate(cells):
        col, row = i % 2, i // 2
        grid_cell(
            p,
            side + col * (cw + gap),
            top + row * (ch + gap),
            cw,
            ch,
            label,
            kind,
            on,
        )
    y = top + 3 * (ch + gap) + 8
    p.text((16, y), "RECENT", C["faint"], 11, bold=True)
    y += 26
    p.rect((8, y, panel_w - 16, 52), fill=C["card"], radius=10)
    folder_icon(p, 20, y + 16, C["blue"])
    p.text((42, y + 10), "1st semester", C["text"], 13, bold=True)
    p.text((42, y + 28), "2 hours ago", C["muted"], 11)
    # touch-row peek on the editor strip
    gesture_bar(p, H - 24)
    return p


def files():
    p, panel_w = panel_shell("FILES", 1)
    # header actions
    ax = panel_w - 22
    for _ in range(5):
        ax -= 22
    # five actions, evenly spaced, kept inside the panel
    slot = 26
    ax = panel_w - 14 - slot * 5
    search_icon(p, ax + 8, 82, 6, C["muted"])
    pin_icon(p, ax + slot + 4, 74, C["muted"])
    p.text((ax + slot * 2 + 6, 70), "+", C["muted"], 16, bold=True)
    folder_icon(p, ax + slot * 3 + 4, 74, C["muted"])
    dots(p, ax + slot * 4 + 8, 76)
    rows = [
        (0, "dir", "1st semester", True, False),
        (1, "dir", "Chemistry", False, False),
        (1, "dir", "English", False, False),
        (1, "dir", "Math", False, False),
        (1, "dir", "Physics", False, False),
        (1, "html", "index.html", False, True),
        (1, "doc", "notes.txt", False, False),
    ]
    y = 112
    for indent, kind, name, open_, selected in rows:
        if selected:
            p.rect((0, y - 4, panel_w, 32), fill=C["sel"])
        x = 14 + indent * 16
        if kind == "dir":
            chevron(p, x, y + 4, open_)
            folder_icon(p, x + 14, y + 1, C["blue"] if open_ else C["muted"])
            p.text((x + 34, y), name, C["text"], 13, bold=open_)
        elif kind == "html":
            play(p, x, y + 3, 10)
            page_icon(p, x + 16, y, C["orange"])
            p.text((x + 34, y), name, C["text"], 13)
        else:
            doc_icon(p, x + 16, y)
            p.text((x + 34, y), name, C["text"], 13)
        dots(p, panel_w - 18, y + 2)
        y += 34
    # accepted error: a pill, not a dialog
    pill = "Error opening file."
    pw = p.text_w(pill, 12) + 36
    px = (panel_w - pw) / 2
    py = H - 78
    p.rect((px, py, pw, 32), fill=C["pill"], radius=16)
    p.ellipse((px + 10, py + 10, 12, 12), fill=C["orange"])
    p.text((px + 28, py + 8), pill, C["text"], 12)
    gesture_bar(p, H - 24)
    return p


def search():
    p, panel_w = panel_shell("SEARCH", 2)
    # toggles
    labels = [".*", "Aa", "ab", "▾"]
    x = panel_w - 12
    for lab in reversed(labels):
        tw = p.text_w(lab, 10) + 10
        x -= tw + 4
        p.rect((x, 72, tw, 20), fill=C["chip"], radius=5)
        p.text((x + tw / 2, 82), lab, C["muted"], 10, anchor="mm")
    p.rect((16, 108, panel_w - 32, 40), fill=C["card"], radius=8)
    p.text((28, 118), "Find Text", C["faint"], 14)
    p.text((16, 164), "RESULTS", C["faint"], 11, bold=True)
    # collapse, refresh, clear — no result row in the shots
    p.text((panel_w - 92, 162), "–", C["faint"], 14)
    p.text((panel_w - 64, 162), "↻", C["faint"], 14)
    p.text((panel_w - 32, 162), "×", C["faint"], 14)
    p.line((16, 186), (panel_w - 16, 186), C["line"], 1)
    gesture_bar(p, H - 24)
    return p


def repository():
    p, panel_w = panel_shell("REPOSITORY", 3)
    search_icon(p, panel_w - 22, 82, 6, C["muted"])
    msg = "No Git repository initialized."
    msg2 = "Initialize one to version your work."
    p.text((panel_w / 2, 280), msg, C["text"], 13, anchor="mm")
    p.text((panel_w / 2, 302), msg2, C["muted"], 12, anchor="mm")
    bw, bh = 196, 40
    p.rect(((panel_w - bw) / 2, 328, bw, bh), fill=C["button"], radius=8)
    p.text((panel_w / 2, 348), "Initialize Repository", C["white"], 13, bold=True, anchor="mm")
    gesture_bar(p, H - 24)
    return p


def about():
    p, panel_w = panel_shell("ABOUT", 4)
    p.text((20, 112), "Not a store.", C["text"], 16, bold=True)
    p.text((20, 140), "No upgrade. No credits. No AI model.", C["muted"], 12)
    rows = [("Version", "CodeC"), ("Licenses", "Open source notices"), ("Feedback", "Send a note")]
    y = 184
    for title, sub in rows:
        p.rect((12, y, panel_w - 24, 56), fill=C["card"], radius=10)
        p.text((28, y + 10), title, C["text"], 14, bold=True)
        p.text((28, y + 30), sub, C["muted"], 12)
        y += 66
    p.text((W - 28, H - 36), ">>", C["muted"], 12)
    gesture_bar(p, H - 24)
    return p


def today_bottom(p: P, y):
    """The strip CodeC keeps today: five labeled tabs."""
    p.rect((0, y, p.w, 64), fill=(28, 30, 34))
    p.line((0, y), (p.w, y), C["line"], 1)
    tabs = [("Projects", True), ("Editor", False), ("Terminal", False), ("Packages", False), ("Settings", False)]
    tw = p.w / 5
    for i, (name, on) in enumerate(tabs):
        color = C["blue"] if on else C["faint"]
        cx = tw * i + tw / 2
        p.rect((cx - 8, y + 10, 16, 12), outline=color, width=1, radius=2)
        p.text((cx, y + 40), name, color, 9, anchor="mt")
    return y + 64


def comparison():
    """Wide image: today's project bar vs the bottom the shots actually use."""
    pw, ph = 390, 430
    gap = 24
    page_w = pw * 2 + gap + 48
    page_h = ph + 110
    page = Image.new("RGB", (page_w * SCALE, page_h * SCALE), (12, 13, 16))
    d = ImageDraw.Draw(page)

    def paste(screen: P, x, y):
        page.paste(screen.im, (x * SCALE, y * SCALE))

    def caption(text, x, y, fill):
        d.text((x * SCALE, y * SCALE), text, font=font(SANSB, 14), fill=fill)

    caption("Today. Five tabs keep a strip.", 24, 16, C["warn"])
    caption("After. That strip is gone.", pw + gap + 24, 16, C["green"])

    left = P(pw, ph)
    left.rect((0, 0, pw, 40), fill=C["bar"])
    hamburger(left, 14, 14)
    left.text((40, 12), "index.html", C["text"], 13, bold=True)
    left.text((pw - 58, 12), "RUN", C["green"], 13, bold=True)
    left.rect((0, 40, pw, ph - 40 - 48 - 28 - 64), fill=C["bg"])
    left.text((16, 56), "code", C["faint"], 13)
    status_line(left, ph - 48 - 28 - 64, "Ln 12, Col 4")
    touch_row(left, ph - 48 - 64, 48)
    today_bottom(left, ph - 64)

    right = P(pw, ph)
    right.rect((0, 0, pw, 40), fill=C["bar"])
    hamburger(right, 14, 14)
    page_icon(right, 40, 12)
    right.text((60, 12), "index.html", C["text"], 13, bold=True)
    play(right, pw - 28, 13, 14)
    # Explicit bands, with a gap, so the status line cannot sit on the keys.
    right.rect((0, 40, pw, 250), fill=C["bg"])
    right.text((16, 56), "code", C["faint"], 13)
    status_line(right, 298)
    touch_row(right, 336, 48)
    gesture_bar(right, ph - 22)

    paste(left, 24, 48)
    paste(right, 24 + pw + gap, 48)
    note = "Rooms move into the side panel. Bottom keeps the status line and the touch row."
    d.text((24 * SCALE, (page_h - 32) * SCALE), note, font=font(SANS, 12), fill=C["muted"])
    return page.resize((page_w, page_h), Image.Resampling.LANCZOS)


def wrap_caption(caption: str, width: int, size: int = 15) -> list[str]:
    words = caption.split()
    lines, cur = [], ""
    fnt = font(SANSB, size)
    for word in words:
        trial = word if not cur else cur + " " + word
        if fnt.getbbox(trial)[2] / SCALE <= width and cur:
            cur = trial
        elif fnt.getbbox(word)[2] / SCALE <= width:
            if cur:
                lines.append(cur)
            cur = word
        else:
            if cur:
                lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines or [caption]


def frame(screen: P, caption: str) -> Image.Image:
    pad_x, pad_bot = 28, 28
    lines = wrap_caption(caption, screen.w)
    pad_top = 22 + 22 * len(lines)
    w = screen.w + pad_x * 2
    h = screen.h + pad_top + pad_bot
    page = Image.new("RGB", (w * SCALE, h * SCALE), (12, 13, 16))
    d = ImageDraw.Draw(page)
    for i, line in enumerate(lines):
        d.text(
            (pad_x * SCALE, (16 + i * 22) * SCALE),
            line,
            font=font(SANSB, 15),
            fill=C["white"],
        )
    # rounded phone
    x, y = pad_x, pad_top
    d.rounded_rectangle(
        [x * SCALE - 2, y * SCALE - 2, (x + screen.w) * SCALE + 2, (y + screen.h) * SCALE + 2],
        radius=28 * SCALE,
        fill=(8, 9, 11),
    )
    # paste screen, then mask corners by redrawing a rounded clip via composite
    shot = screen.im
    mask = Image.new("L", shot.size, 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, shot.size[0], shot.size[1]], radius=24 * SCALE, fill=255)
    rounded = Image.new("RGB", shot.size, (12, 13, 16))
    rounded.paste(shot, (0, 0), mask)
    page.paste(rounded, (x * SCALE, y * SCALE))
    return page.resize((w, h), Image.Resampling.LANCZOS)


def overview(images):
    """Contact sheet. Three columns so GitHub can show it without a mile of scroll."""
    thumbs = []
    tw = 300
    for im in images:
        th = int(im.height * (tw / im.width))
        thumbs.append(im.resize((tw, th), Image.Resampling.LANCZOS))
    gap = 14
    cols = 3
    rows = (len(thumbs) + cols - 1) // cols
    cell_h = max(t.height for t in thumbs)
    page_w = cols * tw + gap * (cols + 1)
    page_h = 56 + rows * (cell_h + gap)
    page = Image.new("RGB", (page_w, page_h), (12, 13, 16))
    d = ImageDraw.Draw(page)
    d.text((gap, 16), "Phone UI spec  ·  no bottom project bar", font=ImageFont.truetype(SANSB, 18), fill=C["white"])
    for i, th in enumerate(thumbs):
        c, r = i % cols, i // cols
        x = gap + c * (tw + gap)
        y = 52 + r * (cell_h + gap)
        page.paste(th, (x, y))
    return page


def main():
    os.makedirs(OUT, exist_ok=True)
    shots = [
        ("01-editor-keyboard-down.png", editor_down(), "Editor, keyboard down. No project bar."),
        ("02-editor-keyboard-up.png", editor_up(), "Editor, keyboard up. Status line hides."),
        ("03-navigation.png", navigation(), "Side panel. Rooms live here, not in a bottom bar."),
        ("04-files.png", files(), "Files. The project name is the tree root."),
        ("05-search.png", search(), "Search. Empty means empty."),
        ("06-repository.png", repository(), "Repository, before git exists."),
        ("07-about.png", about(), "Fifth icon. About, not a shop."),
    ]
    framed = []
    for name, screen, caption in shots:
        im = frame(screen, caption)
        path = os.path.join(OUT, name)
        im.save(path, "PNG", optimize=True)
        framed.append(im)
        print(f"{name:32} {im.size[0]}x{im.size[1]}  {os.path.getsize(path)//1024} KB")

    comp = comparison()
    cpath = os.path.join(OUT, "08-bottom-comparison.png")
    comp.save(cpath, "PNG", optimize=True)
    print(f"{'08-bottom-comparison.png':32} {comp.size[0]}x{comp.size[1]}  {os.path.getsize(cpath)//1024} KB")

    ov = overview(framed)
    opath = os.path.join(OUT, "00-overview.png")
    ov.save(opath, "PNG", optimize=True)
    print(f"{'00-overview.png':32} {ov.size[0]}x{ov.size[1]}  {os.path.getsize(opath)//1024} KB")


if __name__ == "__main__":
    main()
