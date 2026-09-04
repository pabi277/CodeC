#!/usr/bin/env python3
"""Phase 25.1 bench corpus generator (deterministic, seeded).

Generates the two corpus files checked into bench/src/main/assets/bench/:
  bench.c    ~5 000 lines / ~200 kB of C with mixed comments, strings,
             preprocessor lines and varied identifiers (so tokenizers do
             real work).
  bench.html exactly 517 lines / ~25 kB of HTML+CSS+JS — the span-density
             worst case for CodeC's HTML_CSS tokenizer (every tag name,
             attribute string and number is its own token).

Run from the repo root:
  python3 bench/tools/generate_corpus.py
"""
import random

rng = random.Random(251)

# ---------------------------------------------------------------- bench.c ---
def c_corpus():
    types = ["int", "long", "size_t", "double", "unsigned"]
    out = []
    out.append("/*")
    out.append(" * bench.c — Phase 25.1 spike corpus.")
    out.append(" * Generated deterministically by bench/tools/generate_corpus.py;")
    out.append(" * do not edit by hand. ~5 000 lines of mixed comments, strings")
    out.append(" * and identifiers so every tokenizer under test does real work.")
    out.append(" */")
    out.append("#include <stdio.h>")
    out.append("#include <stdlib.h>")
    out.append("#include <string.h>")
    out.append("")
    out.append("#define BENCH_MAX_SAMPLES 4096")
    out.append("#define BENCH_SCALE(x) ((x) * 1.5 + 0.25)")
    out.append("")
    i = 0
    while len(out) < 4980:
        i += 1
        kind = i % 5
        name = f"bench_compute_{i:04d}"
        out.append(f"/* Kernel {i}: exercises strings, numbers and nested control flow. */")
        out.append(f"static double {name}(const char *label, {rng.choice(types)} count) {{")
        out.append(f'    double total = {rng.uniform(0, 100):.4f};')
        out.append(f'    const char *mode = "{rng.choice(["fast", "safe", "simd", "fallback", "turbo"])}-{i}";'
                   f' /* variant {rng.choice(["baseline", "tuned", "unrolled", "branchless", "cached"])}'
                   f'-{rng.choice(["x1", "x2", "x4"])} rev {i % 100} */')
        out.append(f"    if (count > BENCH_MAX_SAMPLES) {{")
        out.append(f'        fprintf(stderr, "%s: sample overflow in {name} (%s)\\n", label, mode);')
        out.append("        return -1.0;")
        out.append("    }")
        if kind == 0:
            out.append("    /* Accumulate the weighted series; the branch predictor")
            out.append("       hates this one simple trick. */")
            out.append("    for (int j = 0; j < count; j++) {")
            out.append(f"        total += 0.5 * j + BENCH_SCALE(j % 16);")
            out.append("    }")
        elif kind == 1:
            out.append("    int steps = 0;")
            out.append("    while (steps < count && total > 0.0) {")
            out.append("        total -= total / 8.0;")
            out.append("        steps++;")
            out.append("    }")
            out.append(f'    (void)mode;')
        elif kind == 2:
            out.append(f"    char buffer[64];")
            out.append(f'    snprintf(buffer, sizeof(buffer), "{name}=%.3f", total);')
            out.append(f"    total += (double)strlen(buffer) * 0.25;")
        elif kind == 3:
            out.append(f"    switch (count % 4) {{")
            out.append(f"    case 0: total *= 2.0; break;")
            out.append(f"    case 1: total /= 1.5; break;")
            out.append(f"    case 2: total += 0x1F; break;")
            out.append(f"    default: total -= 0.001; break;")
            out.append(f"    }}")
        else:
            out.append(f"    unsigned hash = 2166136261u;")
            out.append(f"    for (const char *p = label; *p != '\\0'; p++) {{")
            out.append(f"        hash ^= (unsigned)(*p);")
            out.append(f"        hash *= 16777619u;")
            out.append(f"    }}")
            out.append(f"    total += (double)(hash % 997);")
        out.append("    return total;")
        out.append("}")
        out.append("")
    out.append("int main(void) {")
    out.append("    double acc = 0.0;")
    out.append("    for (int k = 0; k < 64; k++) {")
    out.append("        acc += bench_compute_0001(\"main\", k * 16);")
    out.append("    }")
    out.append('    printf("bench total = %.4f\\n", acc);')
    out.append("    return 0;")
    out.append("}")
    return out

# -------------------------------------------------------------- bench.html ---
def html_corpus():
    out = []
    out.append("<!DOCTYPE html>")
    out.append("<!-- bench.html — Phase 25.1 spike corpus (span-density worst case). -->")
    out.append("<!-- Generated deterministically by bench/tools/generate_corpus.py. -->")
    out.append('<html lang="en">')
    out.append("<head>")
    out.append('  <meta charset="utf-8">')
    out.append('  <meta name="viewport" content="width=device-width, initial-scale=1">')
    out.append("  <title>Bench 25.1 — span density worst case</title>")
    out.append("  <style>")
    out.append("    :root { --bg: #1e1f22; --fg: #e6e6e6; --accent: #ff79c6; --muted: #6272a4; }")
    out.append("    body { margin: 0; padding: 24px; background: var(--bg); color: var(--fg);")
    out.append("           font: 15px/1.55 'Monospace', monospace; }")
    out.append("    .card { border: 1px solid #44475a; border-radius: 8px; padding: 12px 16px;")
    out.append("            margin: 8px 0; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35); }")
    out.append("    .card h2 { font-size: 18px; margin: 0 0 8px; color: var(--accent); }")
    out.append("    .card p  { font-size: 14px; margin: 4px 0; color: #bd93f9; }")
    out.append("    .tag    { display: inline-block; padding: 2px 8px; border-radius: 4px;")
    out.append("              background: #44475a; color: #f1fa8c; font-size: 12px; }")
    # CSS rules
    for i in range(1, 60):
        out.append(f"    .rule-{i:03d} {{ padding: {i % 16}px {i % 24}px; opacity: 0.{i % 10};")
        out.append(f"            transform: translateY({i % 7}px) scale(1.{i % 5}); z-index: {100 + i}; }}")
    out.append("  </style>")
    out.append("</head>")
    out.append("<body>")
    out.append('  <header class="card" id="top">')
    out.append('    <h2>Phase 25.1 — <span class="tag">BENCH</span> HTML corpus</h2>')
    out.append('    <p>Every tag name, attribute string and numeric literal below is a')
    out.append('       separate token for CodeC\u2019s HTML_CSS tokenizer \u2014 this file is the')
    out.append('       worst case from Phase 22 (CMP-4023: layout cost scales with span count).</p>')
    out.append("  </header>")
    section = 1
    suffix = [
        '  <footer class="card" id="about">',
        '    <p class="tag">generated corpus &mdash; do not edit by hand</p>',
        "  </footer>",
        '  <script>',
        '    document.addEventListener("DOMContentLoaded", function () {',
        '      var cards = document.querySelectorAll(".card");',
        '      for (var i = 0; i < cards.length; i++) {',
        '        cards[i].dataset.bench = String(i * 1.5);',
        '      }',
        '      console.log("bench.html ready:", cards.length, "cards");',
        '    });',
        "  </script>",
        "</body>",
        "</html>",
    ]
    while True:
        block = []
        s = section
        block.append(f'  <section class="card rule-{s:03d}" data-index="{s}" aria-label="section {s}">')
        block.append(f'    <h2>Section {s:04d} &mdash; <span class="tag">alpha</span> <span class="tag">beta</span></h2>')
        block.append(f'    <p class="note">Value <code>{s * 3.14159:.5f}</code> at scale 1.{s % 9}; '
                     f'status <strong>OK</strong>, retry <em>{s % 7}</em>.</p>')
        block.append(f'    <div class="row" style="--i: {s}; padding-left: {s % 32}px">')
        block.append(f'      <span class="tag">id-{s:05d}</span>')
        block.append(f'      <a href="/details/{s:04d}?view=full&amp;sort=asc">Details {s:04d}</a>')
        block.append(f'      <img src="/img/plot-{s:03d}.png" alt="plot {s}" width="240" height="135" loading="lazy">')
        block.append("    </div>")
        block.append("  </section>")
        if s % 8 == 0:
            block.append(f'  <script>console.log("section {s}: bench", {s} * 1.25, "ok");</script>')
        if len(out) + len(block) + len(suffix) > 517:
            break
        out.extend(block)
        section += 1
    # Pad with deterministic filler comment lines so the file is EXACTLY
    # 517 lines (the Phase 22 measurement basis).
    pad = 517 - len(out) - len(suffix)
    for n in range(pad):
        out.append(f'  <!-- bench pad {n + 1:03d}: filler <span class="tag">pad-{n + 1:03d}</span> value 0.{n % 10}px -->')
    out.extend(suffix)
    return out

c_lines = c_corpus()
with open("bench/src/main/assets/bench/bench.c", "w") as f:
    f.write("\n".join(c_lines) + "\n")
h_lines = html_corpus()
assert len(h_lines) == 517, f"bench.html must be exactly 517 lines, got {len(h_lines)}"
with open("bench/src/main/assets/bench/bench.html", "w") as f:
    f.write("\n".join(h_lines) + "\n")
import os
for p in ("bench/src/main/assets/bench/bench.c", "bench/src/main/assets/bench/bench.html"):
    size = os.path.getsize(p)
    print(f"{p}: {len(open(p).read().splitlines())} lines, {size} bytes")
