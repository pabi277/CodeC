package com.codeci.ide.ui.projects

import java.io.File

/**
 * Phase 58.1 — the file a brand-new user lands in.
 *
 * The reference's first open is the editor, on a page, with the bottom bar and
 * nothing to set up. This checkout had no snake sample anywhere (searched
 * 2026-09-22) and the reference's own page is not ours to copy, so the sample is
 * **written here, original**: one project, one self-contained `index.html`.
 *
 * Why HTML and not Python or C: a fresh install can honestly promise exactly two
 * runnable things — the **HTML preview** (the app's own in-process server, no
 * download) and **C** (the bundled TCC compiler). Snake is a page, so RUN ▶ on it
 * opens the preview with nothing to install; that is the first impression the
 * roadmap asks for (*"C and HTML preview must run without waiting on userland"*).
 *
 * Shape follows [DemoProjects], the sample that already ships: [ensure] seeds the
 * project exactly like the wizard would (`ProjectConfig.defaultFor` metadata
 * plus the files below), is idempotent, never overwrites an existing `snake`
 * directory (the user's edits are theirs), rolls a half-written seed back, and
 * returns the directory only when THIS call created it.
 *
 * The project TYPE stays `"web"` on purpose: every type-driven decision (the
 * preview path, the hub's kind, run detection) then treats the sample like any
 * other static page. Only the one file is ours.
 */
object SnakeSample {

    const val NAME = "snake"
    const val TYPE = "web"
    const val ENTRY_FILE = "index.html"

    /**
     * Exactly the files [writeProject] writes, in order. A getter, not a stored
     * list: [PAGE] is declared below (a page long enough to want its own
     * section), and an object's stored properties initialize in declaration
     * order — the getter is what keeps the two in any order.
     */
    val FILES: List<ScaffoldFile>
        get() = listOf(ScaffoldFile(ENTRY_FILE, PAGE))

    private val README = """
        # snake — CodeC's first-open sample

        A complete little game in one file. Tap RUN ▶ (or open Web Preview):
        no download, no setup — the page runs in the app's own server.

        - Swipe on the board, use the arrow keys, or tap the pad below the board.
        - Eat the bright squares. The tail grows; the walls and your own tail end
          the round. Space or the RESTART button starts again.

        Everything lives in `index.html` — HTML, CSS and JavaScript in one file,
        which is the point: edit it, save, and the preview reloads with your
        change (Phase 21.1's live reload).
    """.trimIndent() + "\n"

    /**
     * Makes sure the sample exists; returns the project directory only when this
     * call created it (null when it was already there, or the filesystem
     * refused). Safe to call repeatedly.
     */
    fun ensure(projectsRoot: File): File? {
        val project = File(projectsRoot, NAME)
        if (project.isDirectory) return null
        if (project.exists()) return null
        if (!project.mkdirs()) return null
        return try {
            writeProject(project)
            project
        } catch (e: Exception) {
            project.deleteRecursively()
            null
        }
    }

    /**
     * The wizard's own metadata, plus this one file. Deliberately NOT
     * `ProjectScaffold.writeFiles("web", …)`: the scaffold's page is the wizard's
     * demo and this sample is its own, so the single file is written here where
     * it can be read beside its project metadata.
     */
    private fun writeProject(project: File) {
        val config = ProjectConfig.defaultFor(NAME, TYPE)
        val metadata = File(project, ".codec")
        if (!metadata.exists() && !metadata.mkdirs()) {
            throw IllegalStateException("Could not create project metadata")
        }
        File(metadata, "project.json").writeText(config.toJsonString())
        for (file in FILES) {
            val target = File(project, file.relativePath)
            target.parentFile?.mkdirs()
            target.writeText(file.content)
        }
        File(project, "README.md").writeText(README)
    }

    /**
     * The whole game, one file. Two rules kept on purpose: no external URL of any
     * kind (a sample that needs the network is not a sample a fresh install can
     * run), and no template literals — the page is a Kotlin raw string here, and
     * `$` in it would have to be escaped at every use.
     */
    private val PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>snake</title>
<style>
  :root { color-scheme: dark; --bg:#0f1115; --grid:#161a20; --snake:#6ee08a; --head:#d9f7c9; --food:#ffb86b; --ink:#e6edf3; --dim:#8b949e; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  html, body { margin:0; height:100%; background:var(--bg); color:var(--ink);
               font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
  body { display:flex; flex-direction:column; align-items:center; justify-content:center;
         gap:14px; padding:16px; touch-action:none; }
  header { width:100%; max-width:420px; display:flex; align-items:baseline; justify-content:space-between; }
  h1 { font-size:18px; margin:0; letter-spacing:.08em; text-transform:uppercase; }
  .score { font-size:14px; color:var(--dim); }
  .score b { color:var(--head); font-size:16px; }
  .wrap { position:relative; width:100%; max-width:420px; }
  canvas { width:100%; height:auto; display:block; background:var(--grid); border-radius:12px; }
  .over { position:absolute; inset:0; display:none; flex-direction:column; gap:10px;
          align-items:center; justify-content:center; background:rgba(15,17,21,.86); border-radius:12px; text-align:center; }
  .over.show { display:flex; }
  .over p { margin:0; color:var(--dim); font-size:13px; }
  .over strong { color:var(--ink); font-size:16px; }
  button { font:inherit; color:var(--bg); background:var(--snake); border:0; border-radius:999px;
           padding:10px 18px; font-weight:700; }
  .pad { display:grid; grid-template-columns:repeat(3, 64px); grid-template-rows:repeat(3, 56px); gap:8px; }
  .pad button { background:#1d232b; color:var(--ink); border-radius:14px; font-size:20px; padding:0; }
  .pad .up { grid-area:1/2; } .pad .left { grid-area:2/1; }
  .pad .down { grid-area:3/2; } .pad .right { grid-area:2/3; }
  .hint { color:var(--dim); font-size:12px; text-align:center; max-width:420px; }
</style>
</head>
<body>
<header>
  <h1>snake</h1>
  <div class="score">score <b id="score">0</b> &middot; best <b id="best">0</b></div>
</header>
<div class="wrap">
  <canvas id="board" width="420" height="420"></canvas>
  <div class="over" id="over">
    <strong id="overTitle">Tap to start</strong>
    <p id="overText">Eat the bright squares. Walls and your own tail end the round.</p>
    <button id="again">START</button>
  </div>
</div>
<div class="pad">
  <button class="up"    data-dir="up">&#9650;</button>
  <button class="left"  data-dir="left">&#9664;</button>
  <button class="down"  data-dir="down">&#9660;</button>
  <button class="right" data-dir="right">&#9654;</button>
</div>
<p class="hint">Swipe the board, use the arrow keys, or tap the pad.</p>
<script>
(function () {
  var CELL = 21, COLS = 20, ROWS = 20;
  var canvas = document.getElementById('board');
  var ctx = canvas.getContext('2d');
  var scoreEl = document.getElementById('score');
  var bestEl = document.getElementById('best');
  var over = document.getElementById('over');
  var overTitle = document.getElementById('overTitle');
  var overText = document.getElementById('overText');
  var again = document.getElementById('again');

  var snake, dir, nextDir, food, score, best = 0, timer = null, running = false, tickMs = 140;

  function reset() {
    snake = [{x: 9, y: 10}, {x: 8, y: 10}, {x: 7, y: 10}];
    dir = {x: 1, y: 0};
    nextDir = dir;
    score = 0;
    placeFood();
    draw();
  }

  function placeFood() {
    for (var tries = 0; tries < 500; tries++) {
      var p = {x: (Math.random() * COLS) | 0, y: (Math.random() * ROWS) | 0};
      if (!snake.some(function (s) { return s.x === p.x && s.y === p.y; })) { food = p; return; }
    }
    food = {x: 0, y: 0};
  }

  function start() {
    reset();
    over.classList.remove('show');
    running = true;
    tickMs = 140;
    schedule();
    canvas.focus();
  }

  function schedule() {
    if (timer) { clearTimeout(timer); }
    timer = setTimeout(step, tickMs);
  }

  function step() {
    if (!running) { return; }
    dir = nextDir;
    var head = {x: snake[0].x + dir.x, y: snake[0].y + dir.y};
    var hitWall = head.x < 0 || head.y < 0 || head.x >= COLS || head.y >= ROWS;
    var hitSelf = snake.some(function (s, i) {
      return i < snake.length - 1 && s.x === head.x && s.y === head.y;
    });
    if (hitWall || hitSelf) { end(hitWall ? 'Wall' : 'Your own tail'); return; }
    snake.unshift(head);
    if (head.x === food.x && head.y === food.y) {
      score += 1;
      tickMs = Math.max(70, tickMs - 3);
      placeFood();
    } else {
      snake.pop();
    }
    scoreEl.textContent = String(score);
    draw();
    schedule();
  }

  function end(why) {
    running = false;
    if (timer) { clearTimeout(timer); timer = null; }
    if (score > best) { best = score; bestEl.textContent = String(best); }
    overTitle.textContent = why + ' — ' + score + (score === 1 ? ' point' : ' points');
    overText.textContent = 'Space or START plays another round.';
    again.textContent = 'RESTART';
    over.classList.add('show');
  }

  function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#161a20';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#ffb86b';
    ctx.fillRect(food.x * CELL + 3, food.y * CELL + 3, CELL - 6, CELL - 6);
    for (var i = snake.length - 1; i >= 0; i--) {
      var s = snake[i];
      ctx.fillStyle = i === 0 ? '#d9f7c9' : '#6ee08a';
      ctx.globalAlpha = i === 0 ? 1 : Math.max(0.45, 1 - i / (snake.length + 6));
      ctx.fillRect(s.x * CELL + 2, s.y * CELL + 2, CELL - 4, CELL - 4);
    }
    ctx.globalAlpha = 1;
  }

  function turn(name) {
    var want = dir;
    if (name === 'up') { want = {x: 0, y: -1}; }
    if (name === 'down') { want = {x: 0, y: 1}; }
    if (name === 'left') { want = {x: -1, y: 0}; }
    if (name === 'right') { want = {x: 1, y: 0}; }
    if (want.x === -dir.x && want.y === -dir.y) { return; }
    nextDir = want;
  }

  document.addEventListener('keydown', function (e) {
    var map = {ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
               w: 'up', s: 'down', a: 'left', d: 'right'};
    var name = map[e.key];
    if (name) { e.preventDefault(); turn(name); if (!running) { start(); } return; }
    if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); start(); }
  });

  again.addEventListener('click', start);
  over.addEventListener('click', start);

  Array.prototype.forEach.call(document.querySelectorAll('.pad button'), function (btn) {
    btn.addEventListener('click', function () {
      if (!running) { start(); }
      turn(btn.getAttribute('data-dir'));
    });
  });

  var touchStart = null;
  canvas.addEventListener('touchstart', function (e) {
    var t = e.changedTouches[0];
    touchStart = {x: t.clientX, y: t.clientY};
    e.preventDefault();
  }, {passive: false});
  canvas.addEventListener('touchend', function (e) {
    if (!touchStart) { return; }
    var t = e.changedTouches[0];
    var dx = t.clientX - touchStart.x, dy = t.clientY - touchStart.y;
    touchStart = null;
    e.preventDefault();
    if (Math.abs(dx) < 24 && Math.abs(dy) < 24) {
      if (!running) { start(); }
      return;
    }
    if (!running) { start(); }
    turn(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up'));
  }, {passive: false});

  window.addEventListener('blur', function () {
    if (!running) { return; }
    running = false;
    if (timer) { clearTimeout(timer); timer = null; }
  });

  reset();
  overTitle.textContent = 'Tap to start';
  over.classList.add('show');
})();
</script>
</body>
</html>
"""
}
