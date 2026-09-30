const GRID = 20;
const CELL = 18;
const BOARD = GRID * CELL;
const START_SPEED = 175;
const MIN_SPEED = 115;

export function createSnakeGame(root, { onBest = () => {} } = {}) {
  const canvas = root.querySelector("#snake-board");
  const ctx = canvas.getContext("2d");
  const overlay = root.querySelector("#snake-overlay");
  const overlayTitle = root.querySelector("#snake-overlay-title");
  const overlayCopy = root.querySelector("#snake-overlay-copy");
  const overlayKicker = root.querySelector("#snake-overlay-kicker");
  const scoreNode = root.querySelector("#snake-score");
  const bestNode = root.querySelector("#snake-best");
  const statusNode = root.querySelector("#snake-status");
  const pauseButton = root.querySelector("#snake-pause");
  const againButton = root.querySelector("#snake-again");
  const stage = root.querySelector("#snake-stage");
  let snake = [];
  let direction = { x: 1, y: 0 };
  let queuedDirection = { x: 1, y: 0 };
  let fruit = { x: 14, y: 10 };
  let score = 0;
  let best = 0;
  let timer = null;
  let mode = "idle";
  let touchStart = null;
  let active = false;

  function resetBoard() {
    snake = [{ x: 8, y: 10 }, { x: 7, y: 10 }, { x: 6, y: 10 }];
    direction = { x: 1, y: 0 };
    queuedDirection = { x: 1, y: 0 };
    fruit = { x: 14, y: 10 };
    score = 0;
    scoreNode.textContent = "0";
    hideOverlay();
    draw();
  }

  function placeFruit() {
    const open = [];
    for (let y = 0; y < GRID; y += 1) {
      for (let x = 0; x < GRID; x += 1) {
        if (!snake.some((piece) => piece.x === x && piece.y === y)) open.push({ x, y });
      }
    }
    fruit = open[Math.floor(Math.random() * open.length)] || { x: -1, y: -1 };
  }

  function draw() {
    ctx.clearRect(0, 0, BOARD, BOARD);
    ctx.fillStyle = "#151912";
    ctx.fillRect(0, 0, BOARD, BOARD);
    ctx.strokeStyle = "rgba(234, 242, 216, .045)";
    ctx.lineWidth = 1;
    for (let i = 1; i < GRID; i += 1) {
      const point = i * CELL + 0.5;
      ctx.beginPath(); ctx.moveTo(point, 0); ctx.lineTo(point, BOARD); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0, point); ctx.lineTo(BOARD, point); ctx.stroke();
    }
    if (fruit.x >= 0) {
      ctx.fillStyle = "#ff9b69";
      ctx.beginPath();
      ctx.arc(fruit.x * CELL + CELL / 2, fruit.y * CELL + CELL / 2, CELL * 0.32, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "rgba(255,255,255,.42)";
      ctx.beginPath();
      ctx.arc(fruit.x * CELL + CELL * 0.39, fruit.y * CELL + CELL * 0.38, CELL * 0.09, 0, Math.PI * 2);
      ctx.fill();
    }
    snake.forEach((piece, index) => {
      const inset = index === 0 ? 2 : 3;
      ctx.fillStyle = index === 0 ? "#e7ffac" : `rgba(212,245,107,${Math.max(.34, .92 - index * .035)})`;
      ctx.fillRect(piece.x * CELL + inset, piece.y * CELL + inset, CELL - inset * 2, CELL - inset * 2);
    });
    const head = snake[0];
    if (head) {
      ctx.fillStyle = "#28351a";
      const eyeX = head.x * CELL + (direction.x < 0 ? 5 : direction.x > 0 ? 12 : 9);
      const eyeY = head.y * CELL + (direction.y < 0 ? 5 : direction.y > 0 ? 12 : 5);
      ctx.fillRect(eyeX, eyeY, 2, 2);
    }
  }

  function setDirection(next) {
    if (!active || mode !== "running") return;
    if (next.x === -direction.x && next.y === -direction.y) return;
    queuedDirection = next;
  }

  function finish() {
    clearInterval(timer);
    timer = null;
    mode = "over";
    active = true;
    pauseButton.disabled = true;
    pauseButton.textContent = "PAUSE";
    overlayKicker.textContent = "NICE RUN";
    overlayTitle.textContent = "Run complete";
    overlayCopy.textContent = `You scored ${score}. Your best is ${best}. Ready to try again?`;
    againButton.textContent = "PLAY AGAIN ↗";
    overlay.hidden = false;
    statusNode.textContent = "Round over. Your best score is saved on this device.";
  }

  function tick() {
    direction = queuedDirection;
    const head = { x: snake[0].x + direction.x, y: snake[0].y + direction.y };
    const eating = head.x === fruit.x && head.y === fruit.y;
    const bodyToCheck = eating ? snake : snake.slice(0, -1);
    const crashed = head.x < 0 || head.y < 0 || head.x >= GRID || head.y >= GRID ||
      bodyToCheck.some((piece) => piece.x === head.x && piece.y === head.y);
    if (crashed) {
      finish();
      return;
    }
    snake.unshift(head);
    if (eating) {
      score += 10;
      scoreNode.textContent = String(score);
      best = onBest(score);
      bestNode.textContent = String(best);
      placeFruit();
      statusNode.textContent = `Fruit collected. Score ${score}. Keep steering!`;
      const nextSpeed = Math.max(MIN_SPEED, START_SPEED - Math.floor(score / 50) * 7);
      clearInterval(timer);
      timer = window.setInterval(tick, nextSpeed);
    } else {
      snake.pop();
    }
    draw();
  }

  function startNew() {
    clearInterval(timer);
    resetBoard();
    mode = "running";
    active = true;
    pauseButton.disabled = false;
    pauseButton.textContent = "PAUSE";
    statusNode.textContent = "Swipe, tap an arrow, or use your keyboard to steer.";
    timer = window.setInterval(tick, START_SPEED);
  }

  function pause() {
    if (mode !== "running") return;
    clearInterval(timer);
    timer = null;
    mode = "paused";
    overlayKicker.textContent = "TAKE YOUR TIME";
    overlayTitle.textContent = "Paused";
    overlayCopy.textContent = `Score ${score}. Continue when you're ready.`;
    againButton.textContent = "RESUME ↗";
    overlay.hidden = false;
    pauseButton.textContent = "RESUME";
    statusNode.textContent = "Game paused.";
  }

  function resume() {
    if (mode !== "paused") return;
    overlay.hidden = true;
    mode = "running";
    pauseButton.textContent = "PAUSE";
    statusNode.textContent = "Back in the game. Keep steering!";
    timer = window.setInterval(tick, START_SPEED);
  }

  function activateAction() {
    if (mode === "paused") resume();
    else startNew();
  }

  function onKeyDown(event) {
    if (!active || root.hidden) return;
    const key = event.key.toLowerCase();
    const moves = {
      arrowup: { x: 0, y: -1 }, w: { x: 0, y: -1 },
      arrowdown: { x: 0, y: 1 }, s: { x: 0, y: 1 },
      arrowleft: { x: -1, y: 0 }, a: { x: -1, y: 0 },
      arrowright: { x: 1, y: 0 }, d: { x: 1, y: 0 },
    };
    if (moves[key]) {
      event.preventDefault();
      setDirection(moves[key]);
    } else if (key === "p" || key === " ") {
      event.preventDefault();
      if (mode === "running") pause();
      else if (mode === "paused") resume();
    }
  }

  root.querySelectorAll("[data-direction]").forEach((button) => {
    button.addEventListener("click", () => {
      const directions = {
        up: { x: 0, y: -1 }, down: { x: 0, y: 1 },
        left: { x: -1, y: 0 }, right: { x: 1, y: 0 },
      };
      setDirection(directions[button.dataset.direction]);
    });
  });
  pauseButton.addEventListener("click", () => {
    if (mode === "running") pause();
    else if (mode === "paused") resume();
  });
  againButton.addEventListener("click", activateAction);
  stage.addEventListener("pointerdown", (event) => {
    touchStart = { x: event.clientX, y: event.clientY };
  });
  stage.addEventListener("pointerup", (event) => {
    if (!touchStart) return;
    const dx = event.clientX - touchStart.x;
    const dy = event.clientY - touchStart.y;
    touchStart = null;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 18) return;
    if (Math.abs(dx) > Math.abs(dy)) setDirection({ x: dx > 0 ? 1 : -1, y: 0 });
    else setDirection({ x: 0, y: dy > 0 ? 1 : -1 });
  });
  stage.addEventListener("pointercancel", () => { touchStart = null; });
  document.addEventListener("keydown", onKeyDown);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && active && mode === "running") pause();
  });

  return {
    start() {
      active = true;
      best = onBest(0);
      bestNode.textContent = String(best);
      clearInterval(timer);
      timer = null;
      resetBoard();
      mode = "ready";
      pauseButton.disabled = true;
      pauseButton.textContent = "PAUSE";
      overlayKicker.textContent = "READY WHEN YOU ARE";
      overlayTitle.textContent = "Take the first turn.";
      overlayCopy.textContent = "Swipe, tap an arrow, or use your keyboard. Eat fruit and avoid the walls and your tail.";
      againButton.textContent = "START SNAKE ↗";
      overlay.hidden = false;
      statusNode.textContent = "The run starts when you tap Start Snake.";
    },
    stop() {
      active = false;
      clearInterval(timer);
      timer = null;
      mode = "idle";
      hideOverlay();
    },
  };

  function hideOverlay() { overlay.hidden = true; }
}
