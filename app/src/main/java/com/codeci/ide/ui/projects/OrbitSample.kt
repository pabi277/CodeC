package com.codeci.ide.ui.projects

import java.io.File

/**
 * CodeC's new-user sample: Orbit Shift, a tiny, original, one-thumb arcade game.
 *
 * The game is deliberately one self-contained HTML file. A fresh install can
 * open its preview immediately (no userland, package, account, network, or
 * permission setup), while the editor opens on real, readable code the user can
 * change. The sample is seeded once; deleting it stays the user's choice.
 */
object OrbitSample {

    const val NAME = "orbit-shift"
    const val TYPE = "web"
    const val ENTRY_FILE = "index.html"
    const val DISPLAY_NAME = "Orbit Shift"

    private const val MARKER = ".orbit-shift-seeded-v1"
    private const val MARKER_TEXT = "seeded once; a deleted Orbit Shift sample stays deleted\n"

    val FILES: List<ScaffoldFile>
        get() = listOf(ScaffoldFile(ENTRY_FILE, PAGE))

    private val README = """
        # Orbit Shift — CodeC's first game

        A one-thumb arcade game in one file. Open `index.html` and tap RUN ▶ to
        play; the in-app web preview works offline and needs no install.

        - Tap **INNER** or **OUTER** to switch orbits. On a keyboard, use ↑ / ↓
          (or I / O); Space switches to the other orbit.
        - Collect gold light fragments and avoid red comets. You have three
          shields. Your best score stays on this device in the browser's local
          storage when available.
        - Try changing the colours near the top of `index.html`, then save and
          run it again. The whole game is yours to edit.

        No remote scripts, images, fonts, libraries, or requests are used.
    """.trimIndent() + "\n"

    /**
     * Create the project only once. Existing work is never rewritten, and a
     * marker left after deletion prevents the first-run sample from silently
     * coming back if the user replays the introduction later.
     */
    fun ensure(projectsRoot: File): File? {
        val project = File(projectsRoot, NAME)
        val marker = File(projectsRoot, MARKER)
        if (project.isDirectory) {
            if (!marker.exists()) runCatching { marker.writeText(MARKER_TEXT) }
            return null
        }
        if (project.exists() || marker.exists()) return null
        if (!project.mkdirs()) return null
        return try {
            writeProject(project)
            marker.writeText(MARKER_TEXT)
            project
        } catch (_: Exception) {
            project.deleteRecursively()
            null
        }
    }

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
     * Original canvas game: hold a lane, catch the gold shards, and dodge the
     * comets as they sweep past your tiny ship. No template literals are used so
     * this page can live unchanged inside Kotlin's raw string.
     */
    private val PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="theme-color" content="#08111f">
<title>Orbit Shift — CodeC Arcade</title>
<style>
  :root {
    color-scheme: dark;
    --night:#08111f; --panel:#101b2e; --line:#27344a;
    --ink:#f4f7ff; --muted:#a8b5c9; --mint:#73f0ce;
    --gold:#ffd166; --violet:#a99aff; --coral:#ff7188;
  }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  html { height:100%; }
  html, body {
    margin:0; background:var(--night); color:var(--ink);
    font-family:system-ui,-apple-system,"Segoe UI",sans-serif;
  }
  body {
    min-height:100%; display:flex; flex-direction:column; align-items:center;
    justify-content:center; gap:16px; padding:20px 16px 28px;
    background:
      radial-gradient(ellipse at 50% 18%, rgba(68,108,173,.23), transparent 53%),
      var(--night);
  }
  main { width:min(100%,460px); }
  header { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; margin:0 0 16px; }
  .eyebrow { color:var(--mint); font-size:10px; font-weight:800; letter-spacing:.2em; }
  h1 { margin:5px 0 0; font-size:25px; line-height:1; letter-spacing:.06em; }
  h1 span { color:var(--mint); }
  .tagline { margin:8px 0 0; color:var(--muted); font-size:13px; }
  .offline {
    border:1px solid rgba(115,240,206,.35); color:var(--mint); background:rgba(115,240,206,.08);
    border-radius:999px; padding:8px 10px; font-size:10px; font-weight:800;
    letter-spacing:.08em; white-space:nowrap;
  }
  .stats { display:grid; grid-template-columns:1fr 1fr 1fr; gap:8px; margin-bottom:10px; }
  .stat {
    min-height:50px; display:flex; align-items:center; justify-content:space-between; gap:6px;
    padding:9px 11px; border:1px solid var(--line); border-radius:15px;
    background:rgba(16,27,46,.92); color:var(--muted); font-size:10px;
    font-weight:800; letter-spacing:.08em;
  }
  .stat b { color:var(--ink); font-size:14px; letter-spacing:0; }
  .stat .score { color:var(--gold); }
  .stat .lives { color:var(--coral); font-size:13px; letter-spacing:.06em; }
  .board {
    position:relative; width:100%; aspect-ratio:1.15; overflow:hidden;
    border:1px solid rgba(169,154,255,.32); border-radius:24px;
    background:#0b1424; box-shadow:0 16px 46px rgba(0,0,0,.3);
    touch-action:manipulation;
  }
  canvas { display:block; width:100%; height:100%; }
  .overlay {
    position:absolute; inset:0; display:flex; flex-direction:column;
    align-items:center; justify-content:center; gap:10px; padding:24px;
    text-align:center; background:linear-gradient(180deg,rgba(8,17,31,.24),rgba(8,17,31,.88));
  }
  .overlay[hidden] { display:none; }
  .over-kicker { color:var(--mint); font-size:10px; font-weight:800; letter-spacing:.2em; }
  .overlay h2 { margin:0; font-size:23px; line-height:1.1; }
  .overlay p { max-width:260px; margin:0; color:#d2d9e7; font-size:13px; line-height:1.5; }
  button {
    min-height:52px; border:1px solid transparent; border-radius:16px;
    color:var(--night); background:var(--mint); font:inherit; font-size:12px;
    font-weight:900; letter-spacing:.1em; padding:0 20px;
    box-shadow:0 7px 22px rgba(115,240,206,.2); cursor:pointer;
  }
  button:focus-visible { outline:3px solid var(--gold); outline-offset:3px; }
  button:active { transform:translateY(1px); }
  .controls { display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:12px; }
  .controls button { color:var(--ink); background:var(--panel); border-color:var(--line); box-shadow:none; }
  .controls button[aria-pressed="true"] { color:var(--night); background:var(--violet); border-color:var(--violet); }
  .hint { margin:11px 2px 0; color:var(--muted); font-size:12px; line-height:1.5; text-align:center; }
  .hint strong { color:var(--ink); }
  .status { min-height:18px; margin:8px 0 0; color:var(--mint); font-size:12px; text-align:center; }
  .footer { margin:8px 0 0; color:#8391a8; font-size:10px; letter-spacing:.08em; text-align:center; }
  @media (prefers-reduced-motion:reduce) {
    *, *::before, *::after { scroll-behavior:auto !important; transition-duration:.01ms !important; }
  }
</style>
</head>
<body>
<main>
  <header>
    <div>
      <div class="eyebrow">CODEC ARCADE / 01</div>
      <h1>ORBIT <span>SHIFT</span></h1>
      <p class="tagline">One small ship. Two brave orbits.</p>
    </div>
    <div class="offline" aria-label="Works offline">OFFLINE READY</div>
  </header>
  <section class="stats" aria-label="Game status">
    <div class="stat"><span>SHARDS</span><b class="score" id="score">00</b></div>
    <div class="stat"><span>SHIELDS</span><b class="lives" id="lives">● ● ●</b></div>
    <div class="stat"><span>BEST</span><b id="best">00</b></div>
  </section>
  <div class="board" id="board">
    <canvas id="game" width="420" height="360" role="img"
      aria-label="Orbit Shift playfield. Switch between the inner and outer ring, collect gold light shards, and avoid red comets.">
      Orbit Shift: use the INNER and OUTER buttons to change rings, collect gold shards, and dodge red comets.
    </canvas>
    <div class="overlay" id="overlay">
      <div class="over-kicker">A ONE-THUMB ARCADE GAME</div>
      <h2 id="overlayTitle">Ready to shift?</h2>
      <p id="overlayText">Tap INNER or OUTER to change rings. Catch gold light. Let red comets pass.</p>
      <button id="startButton" type="button">LAUNCH RUN</button>
    </div>
  </div>
  <div class="controls" aria-label="Orbit controls">
    <button type="button" class="lane" data-lane="inner" aria-pressed="false">INNER ORBIT</button>
    <button type="button" class="lane" data-lane="outer" aria-pressed="true">OUTER ORBIT</button>
  </div>
  <p class="hint"><strong>Tap a ring</strong> to switch. Keyboard: ↑ / I = inner · ↓ / O = outer · Space = shift.</p>
  <p class="status" id="status" role="status" aria-live="polite">Your first run is ready. No install or setup.</p>
  <p class="footer">MAKE IT YOURS · EDIT index.html IN CODEC</p>
</main>
<script>
(function () {
  "use strict";
  var canvas = document.getElementById("game");
  var ctx = canvas.getContext("2d");
  var board = document.getElementById("board");
  var overlay = document.getElementById("overlay");
  var overlayTitle = document.getElementById("overlayTitle");
  var overlayText = document.getElementById("overlayText");
  var startButton = document.getElementById("startButton");
  var scoreNode = document.getElementById("score");
  var livesNode = document.getElementById("lives");
  var bestNode = document.getElementById("best");
  var statusNode = document.getElementById("status");
  var laneButtons = Array.prototype.slice.call(document.querySelectorAll(".lane"));
  var TAU = Math.PI * 2;
  var reducedMotion = false;
  try { reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches; } catch (_) {}

  var mode = "ready";
  var lane = "outer";
  var score = 0;
  var shields = 3;
  var best = 0;
  var elapsed = 0;
  var spawnClock = 0;
  var spawnIndex = 0;
  var previousFrame = 0;
  var frameScheduled = false;
  var items = [];
  var sparks = [];
  var stars = [];
  var width = 420;
  var height = 360;
  var shake = 0;

  try { best = Number(window.localStorage.getItem("codec-orbit-best") || 0) || 0; } catch (_) { best = 0; }
  bestNode.textContent = String(best).padStart(2, "0");

  for (var i = 0; i < 34; i++) {
    stars.push({ x: (i * 67 + 19) % 420, y: (i * 113 + 37) % 360, r: i % 5 === 0 ? 1.5 : 0.8, a: 0.22 + (i % 4) * 0.11 });
  }

  function resizeCanvas() {
    var rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = rect.width;
    height = rect.height;
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    draw();
  }

  function setLane(next) {
    if (next !== "inner" && next !== "outer") return;
    lane = next;
    laneButtons.forEach(function (button) {
      button.setAttribute("aria-pressed", String(button.getAttribute("data-lane") === lane));
    });
    if (mode === "ready") statusNode.textContent = "";
  }

  function toggleLane() { setLane(lane === "inner" ? "outer" : "inner"); }

  function updateHud() {
    scoreNode.textContent = String(score).padStart(2, "0");
    livesNode.textContent = shields > 0 ? Array(shields + 1).join("● ").trim() : "—";
    bestNode.textContent = String(best).padStart(2, "0");
  }

  function launch(startLane) {
    score = 0;
    shields = 3;
    elapsed = 0;
    spawnClock = 0;
    spawnIndex = 0;
    items = [];
    sparks = [];
    shake = 0;
    if (startLane === "inner" || startLane === "outer") lane = startLane;
    setLane(lane);
    updateHud();
    mode = "playing";
    overlay.hidden = true;
    statusNode.textContent = "Run started. Gold shards add points; comets cost one shield.";
    previousFrame = performance.now();
    scheduleFrame();
  }

  function spawnItem() {
    var kind = spawnIndex % 4 === 3 ? "comet" : "shard";
    var ring = spawnIndex % 2 === 0 ? "inner" : "outer";
    items.push({
      kind: kind,
      lane: ring,
      angle: 2.3 + Math.random() * 3.4,
      speed: 1.05 + Math.min(0.75, score * 0.012),
      radius: 8 + Math.random() * 2,
      resolved: false
    });
    spawnIndex++;
  }

  function burst(x, y, color) {
    if (reducedMotion) return;
    for (var i = 0; i < 9; i++) {
      var angle = Math.PI * 2 * i / 9;
      sparks.push({ x: x, y: y, vx: Math.cos(angle) * (28 + i % 3 * 9), vy: Math.sin(angle) * (28 + i % 3 * 9), life: 0.38, color: color });
    }
  }

  function collide(item) {
    item.resolved = true;
    if (item.lane !== lane) return;
    var center = centerPoint();
    var radius = orbitRadius(item.lane);
    var x = center.x + Math.cos(0) * radius;
    var y = center.y + Math.sin(0) * radius;
    if (item.kind === "shard") {
      score += 10;
      statusNode.textContent = "Shard collected. " + score + " points.";
      burst(x, y, "#ffd166");
      if (score > best) {
        best = score;
        try { window.localStorage.setItem("codec-orbit-best", String(best)); } catch (_) {}
      }
    } else {
      shields -= 1;
      statusNode.textContent = "Comet hit. " + shields + (shields === 1 ? " shield left." : " shields left.");
      if (!reducedMotion) shake = 0.2;
      burst(x, y, "#ff7188");
      if (shields <= 0) finish();
    }
    updateHud();
  }

  function finish() {
    mode = "over";
    overlay.hidden = false;
    overlayTitle.textContent = "Orbit complete";
    overlayText.textContent = "You caught " + score + " points of starlight. Ready for another run?";
    startButton.textContent = "FLY AGAIN";
    statusNode.textContent = "Run complete. Best score: " + best + ".";
  }

  function centerPoint() { return { x: width / 2, y: height / 2 }; }
  function orbitRadius(which) {
    var limit = Math.min(width * 0.43, height * 0.43);
    return which === "inner" ? limit * 0.57 : limit;
  }

  function drawStar(x, y, radius, color) {
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = color;
    ctx.beginPath();
    for (var i = 0; i < 10; i++) {
      var r = i % 2 === 0 ? radius : radius * 0.46;
      var angle = -Math.PI / 2 + i * Math.PI / 5;
      var px = Math.cos(angle) * r;
      var py = Math.sin(angle) * r;
      if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }

  function drawShip(x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = "rgba(115,240,206,.2)";
    ctx.beginPath(); ctx.arc(0, 0, 17, 0, TAU); ctx.fill();
    ctx.fillStyle = "#73f0ce";
    ctx.beginPath(); ctx.moveTo(12, 0); ctx.lineTo(-8, -8); ctx.lineTo(-5, 0); ctx.lineTo(-8, 8); ctx.closePath(); ctx.fill();
    ctx.fillStyle = "#f4f7ff";
    ctx.beginPath(); ctx.arc(1, 0, 2.1, 0, TAU); ctx.fill();
    ctx.restore();
  }

  function draw() {
    if (!ctx || !width || !height) return;
    ctx.clearRect(0, 0, width, height);
    var background = ctx.createRadialGradient(width * 0.5, height * 0.48, 4, width * 0.5, height * 0.48, Math.max(width, height) * 0.75);
    background.addColorStop(0, "#14233a");
    background.addColorStop(1, "#08111f");
    ctx.fillStyle = background;
    ctx.fillRect(0, 0, width, height);

    stars.forEach(function (star, index) {
      var twinkle = reducedMotion ? 1 : 0.78 + Math.sin(elapsed * 1.8 + index) * 0.18;
      ctx.globalAlpha = star.a * twinkle;
      ctx.fillStyle = index % 7 === 0 ? "#73f0ce" : "#dbe6ff";
      ctx.beginPath(); ctx.arc(star.x / 420 * width, star.y / 360 * height, star.r, 0, TAU); ctx.fill();
    });
    ctx.globalAlpha = 1;

    var center = centerPoint();
    var inner = orbitRadius("inner");
    var outer = orbitRadius("outer");
    [outer, inner].forEach(function (radius, index) {
      ctx.beginPath(); ctx.arc(center.x, center.y, radius, 0, TAU);
      ctx.setLineDash(index === 0 ? [2, 8] : [2, 6]);
      ctx.lineWidth = 1;
      ctx.strokeStyle = index === 0 ? "rgba(169,154,255,.46)" : "rgba(115,240,206,.34)";
      ctx.stroke();
    });
    ctx.setLineDash([]);

    var planetRadius = Math.min(width, height) * 0.105;
    var planet = ctx.createRadialGradient(center.x - planetRadius * 0.35, center.y - planetRadius * 0.4, 2, center.x, center.y, planetRadius * 1.2);
    planet.addColorStop(0, "#bdb1ff");
    planet.addColorStop(1, "#635ca8");
    ctx.fillStyle = planet;
    ctx.beginPath(); ctx.arc(center.x, center.y, planetRadius, 0, TAU); ctx.fill();
    ctx.strokeStyle = "rgba(224,218,255,.68)"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.ellipse(center.x, center.y, planetRadius * 1.45, planetRadius * 0.43, -0.25, 0, TAU); ctx.stroke();
    ctx.fillStyle = "rgba(255,255,255,.16)";
    ctx.beginPath(); ctx.arc(center.x - planetRadius * 0.32, center.y - planetRadius * 0.19, planetRadius * 0.22, 0, TAU); ctx.fill();

    if (mode !== "playing") {
      var ambientAngle = reducedMotion ? 2.45 : 2.45 + Math.sin(elapsed * 0.4) * 0.55;
      var ambientRadius = orbitRadius("inner");
      drawStar(center.x + Math.cos(ambientAngle) * ambientRadius, center.y + Math.sin(ambientAngle) * ambientRadius, 5, "#ffd166");
      var ambientComet = 4.1;
      ctx.strokeStyle = "rgba(255,113,136,.7)"; ctx.lineWidth = 3; ctx.lineCap = "round";
      ctx.beginPath();
      ctx.moveTo(center.x + Math.cos(ambientComet) * outer, center.y + Math.sin(ambientComet) * outer);
      ctx.lineTo(center.x + Math.cos(ambientComet) * outer - 10, center.y + Math.sin(ambientComet) * outer + 4);
      ctx.stroke();
    }

    items.forEach(function (item) {
      if (item.resolved) return;
      var r = orbitRadius(item.lane);
      var x = center.x + Math.cos(item.angle) * r;
      var y = center.y + Math.sin(item.angle) * r;
      if (item.kind === "shard") {
        ctx.globalAlpha = 0.22;
        ctx.fillStyle = "#ffd166"; ctx.beginPath(); ctx.arc(x, y, item.radius * 1.8, 0, TAU); ctx.fill();
        ctx.globalAlpha = 1;
        drawStar(x, y, item.radius, "#ffd166");
      } else {
        ctx.save(); ctx.translate(x, y); ctx.rotate(item.angle + Math.PI / 2);
        ctx.strokeStyle = "rgba(255,113,136,.56)"; ctx.lineWidth = 3; ctx.lineCap = "round";
        ctx.beginPath(); ctx.moveTo(-12, 0); ctx.lineTo(-3, 0); ctx.stroke();
        ctx.fillStyle = "#ff7188"; ctx.beginPath(); ctx.arc(2, 0, item.radius * 0.72, 0, TAU); ctx.fill();
        ctx.restore();
      }
    });

    sparks = sparks.filter(function (spark) { return spark.life > 0; });
    sparks.forEach(function (spark) {
      ctx.globalAlpha = Math.max(0, spark.life / 0.38);
      ctx.fillStyle = spark.color;
      ctx.beginPath(); ctx.arc(spark.x, spark.y, 2, 0, TAU); ctx.fill();
    });
    ctx.globalAlpha = 1;

    var shipRadius = orbitRadius(lane);
    var shipX = center.x + shipRadius;
    var shipY = center.y;
    if (shake > 0 && !reducedMotion) {
      shipX += (Math.random() - 0.5) * 5;
      shipY += (Math.random() - 0.5) * 5;
    }
    drawShip(shipX, shipY);
  }

  function update(dt) {
    elapsed += dt;
    spawnClock += dt;
    if (spawnClock > 1.25 && items.length < 7) {
      spawnClock = 0;
      spawnItem();
    }
    items.forEach(function (item) {
      if (item.resolved) return;
      var before = item.angle;
      item.angle = (item.angle + item.speed * dt) % TAU;
      if (item.angle < before) collide(item);
    });
    items = items.filter(function (item) { return !item.resolved; });
    sparks.forEach(function (spark) {
      spark.x += spark.vx * dt;
      spark.y += spark.vy * dt;
      spark.life -= dt;
    });
    shake = Math.max(0, shake - dt);
  }

  function scheduleFrame() {
    if (!frameScheduled && (mode === "playing" || !reducedMotion)) {
      frameScheduled = true;
      requestAnimationFrame(frame);
    }
  }

  function frame(now) {
    frameScheduled = false;
    if (mode === "done") return;
    var dt = Math.min(0.05, Math.max(0, (now - previousFrame) / 1000));
    previousFrame = now;
    if (mode === "playing") update(dt);
    else if (!reducedMotion) elapsed += dt * 0.35;
    draw();
    scheduleFrame();
  }

  startButton.addEventListener("click", function () { launch(lane); });
  laneButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      setLane(button.getAttribute("data-lane"));
      if (mode === "ready") launch(button.getAttribute("data-lane"));
    });
  });
  canvas.addEventListener("pointerdown", function () {
    if (mode === "playing") toggleLane();
  });
  document.addEventListener("keydown", function (event) {
    var key = event.key.toLowerCase();
    if (key === "arrowup" || key === "i") { event.preventDefault(); setLane("inner"); if (mode !== "playing") launch("inner"); }
    else if (key === "arrowdown" || key === "o") { event.preventDefault(); setLane("outer"); if (mode !== "playing") launch("outer"); }
    else if (key === " " || key === "enter") { event.preventDefault(); if (mode === "playing") toggleLane(); else launch(); }
  });
  window.addEventListener("resize", resizeCanvas);
  if (window.ResizeObserver) new ResizeObserver(resizeCanvas).observe(board);
  resizeCanvas();
  updateHud();
  scheduleFrame();
})();
</script>
</body>
</html>
"""
}
