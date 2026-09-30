const SIZE = 8;
const TONES = ["lime", "orange", "blue", "violet"];
const SHAPES = [
  { name: "Line", cells: [[0, 0], [1, 0], [2, 0]], tone: "lime" },
  { name: "Corner", cells: [[0, 0], [0, 1], [1, 1]], tone: "orange" },
  { name: "Square", cells: [[0, 0], [1, 0], [0, 1], [1, 1]], tone: "blue" },
  { name: "Long line", cells: [[0, 0], [1, 0], [2, 0], [3, 0]], tone: "violet" },
  { name: "Small T", cells: [[0, 0], [1, 0], [2, 0], [1, 1]], tone: "orange" },
  { name: "Step", cells: [[0, 0], [1, 0], [1, 1], [2, 1]], tone: "lime" },
  { name: "L shape", cells: [[0, 0], [0, 1], [0, 2], [1, 2]], tone: "violet" },
  { name: "Dot", cells: [[0, 0]], tone: "blue" },
  { name: "Bar", cells: [[0, 0], [0, 1], [0, 2]], tone: "orange" },
  { name: "Zigzag", cells: [[0, 0], [1, 0], [1, 1], [2, 1]], tone: "blue" },
];
const FIRST_HAND = [
  { ...SHAPES[3], tone: "lime" },
  { ...SHAPES[3], tone: "orange" },
  { ...SHAPES[7], tone: "blue" },
];

export function createBlockGame(root, { onBest = () => {} } = {}) {
  const grid = root.querySelector("#block-grid");
  const tray = root.querySelector("#piece-tray");
  const status = root.querySelector("#block-status");
  const scoreNode = root.querySelector("#blocks-score");
  const bestNode = root.querySelector("#blocks-best");
  const linesNode = root.querySelector("#blocks-lines");
  let board = emptyBoard();
  let hand = FIRST_HAND.map((shape) => ({ ...shape, cells: shape.cells.map((cell) => [...cell]) }));
  let used = [false, false, false];
  let selected = 0;
  let score = 0;
  let lineCount = 0;
  let active = false;
  let clearCells = new Set();
  let clearTimer = null;

  function emptyBoard() { return Array.from({ length: SIZE }, () => Array(SIZE).fill(null)); }

  function cellIndex(x, y) { return y * SIZE + x; }

  function canPlace(shape, x, y) {
    return shape.cells.every(([dx, dy]) => {
      const col = x + dx;
      const row = y + dy;
      return col >= 0 && row >= 0 && col < SIZE && row < SIZE && board[row][col] === null;
    });
  }

  function anyFit(shape) {
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x < SIZE; x += 1) {
        if (canPlace(shape, x, y)) return true;
      }
    }
    return false;
  }

  function anchorSet() {
    const anchors = new Set();
    if (!active || selected < 0 || used[selected]) return anchors;
    const shape = hand[selected];
    if (!shape) return anchors;
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x < SIZE; x += 1) {
        if (canPlace(shape, x, y)) anchors.add(cellIndex(x, y));
      }
    }
    return anchors;
  }

  function renderGrid() {
    const anchors = anchorSet();
    const buttons = [];
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x < SIZE; x += 1) {
        const value = board[y][x];
        const index = cellIndex(x, y);
        const classes = ["block-cell"];
        if (value) classes.push("filled", `tone-${value}`);
        if (anchors.has(index)) classes.push("can-place");
        if (clearCells.has(index)) classes.push("clearing");
        const content = value ? `<span class="sr-only">filled</span>` : "";
        const anchorText = anchors.has(index) ? ", highlighted starting square" : "";
        buttons.push(`<button class="${classes.join(" ")}" type="button" data-cell="${index}" aria-label="Row ${y + 1}, column ${x + 1}, ${value ? "filled" : "empty"}${anchorText}"${value ? " disabled" : ""} style="--cell-color:var(--tone-${value || "lime"})">${content}</button>`);
      }
    }
    grid.innerHTML = buttons.join("");
  }

  function renderTray() {
    tray.innerHTML = hand.map((shape, index) => {
      const unavailable = used[index] || !anyFit(shape);
      const pressed = selected === index && !used[index];
      const cells = Array.from({ length: 16 }, (_, cell) => {
        const x = cell % 4;
        const y = Math.floor(cell / 4);
        const on = shape.cells.some(([sx, sy]) => sx === x && sy === y);
        return `<i class="${on ? "on" : ""}"${on ? ` style="--shape-color:var(--tone-${shape.tone})"` : ""}></i>`;
      }).join("");
      return `<button class="piece-option" type="button" data-piece="${index}" aria-label="${shape.name}${unavailable ? ", unavailable" : ", available"}" aria-pressed="${pressed}"${unavailable ? " disabled" : ""}><span class="mini-shape" aria-hidden="true">${cells}</span><span>${shape.name.toUpperCase()}</span></button>`;
    }).join("");
  }

  function render() {
    renderGrid();
    renderTray();
    scoreNode.textContent = String(score);
    linesNode.textContent = `LINES ${lineCount}`;
  }

  function updateBest() {
    const best = onBest(score);
    bestNode.textContent = String(best);
  }

  function checkEnd() {
    const remaining = hand.map((shape, index) => ({ shape, index })).filter(({ index }) => !used[index]);
    if (remaining.length && remaining.every(({ shape }) => !anyFit(shape))) {
      status.textContent = `No pieces fit. Final score: ${score}. Tap New Game to try a fresh board.`;
      return true;
    }
    return false;
  }

  function fullLines() {
    const rows = [];
    const cols = [];
    for (let y = 0; y < SIZE; y += 1) {
      if (board[y].every(Boolean)) rows.push(y);
    }
    for (let x = 0; x < SIZE; x += 1) {
      if (board.every((row) => row[x])) cols.push(x);
    }
    const cleared = new Set();
    rows.forEach((row) => { for (let x = 0; x < SIZE; x += 1) cleared.add(cellIndex(x, row)); });
    cols.forEach((col) => { for (let y = 0; y < SIZE; y += 1) cleared.add(cellIndex(col, y)); });
    cleared.forEach((index) => { board[Math.floor(index / SIZE)][index % SIZE] = null; });
    lineCount += rows.length + cols.length;
    return { count: rows.length + cols.length, cells: cleared };
  }

  function place(index) {
    if (!active || selected < 0 || used[selected]) return;
    const x = index % SIZE;
    const y = Math.floor(index / SIZE);
    const shape = hand[selected];
    const openingLongLine = score === 0 && shape.name === "Long line";
    if (!canPlace(shape, x, y)) {
      status.textContent = "That shape does not fit there. Try one of the highlighted starting squares.";
      return;
    }
    shape.cells.forEach(([dx, dy]) => { board[y + dy][x + dx] = shape.tone; });
    used[selected] = true;
    score += shape.cells.length;
    const result = fullLines();
    if (result.count) {
      score += result.count * 12;
      clearCells = result.cells;
      window.clearTimeout(clearTimer);
      clearTimer = window.setTimeout(() => { clearCells = new Set(); render(); }, 220);
      status.textContent = result.count === 1 ? "Nice clear! One full line opens up the board." : `Big clear! ${result.count} lines at once.`;
    } else if (openingLongLine && lineCount === 0) {
      status.textContent = "Good first placement. Line up the other long piece on this row for a clear.";
    } else {
      status.textContent = "Good placement. Pick another piece or set up a line.";
    }
    updateBest();
    const nextAvailable = hand.findIndex((piece, pieceIndex) => !used[pieceIndex] && anyFit(piece));
    selected = nextAvailable;
    if (used.every(Boolean)) {
      hand = Array.from({ length: 3 }, () => randomShape());
      used = [false, false, false];
      selected = 0;
      status.textContent = result.count ? "Great run! New pieces are ready." : "New pieces are ready. Keep building.";
    }
    render();
    checkEnd();
  }

  function randomShape() {
    const shape = SHAPES[Math.floor(Math.random() * SHAPES.length)];
    const tone = TONES[Math.floor(Math.random() * TONES.length)];
    return { ...shape, cells: shape.cells.map((cell) => [...cell]), tone };
  }

  function newGame() {
    window.clearTimeout(clearTimer);
    clearCells = new Set();
    board = emptyBoard();
    hand = FIRST_HAND.map((shape) => ({ ...shape, cells: shape.cells.map((cell) => [...cell]) }));
    used = [false, false, false];
    selected = 0;
    score = 0;
    lineCount = 0;
    status.textContent = "The first two long pieces can clear a row when placed end-to-end. Tap a highlighted top-left square.";
    render();
  }

  grid.addEventListener("click", (event) => {
    const cell = event.target.closest("[data-cell]");
    if (cell) place(Number(cell.dataset.cell));
  });
  tray.addEventListener("click", (event) => {
    const button = event.target.closest("[data-piece]");
    if (!button || button.disabled) return;
    selected = Number(button.dataset.piece);
    status.textContent = "Choose a highlighted top-left square to place the selected piece.";
    render();
  });
  root.querySelector("#blocks-new-game").addEventListener("click", newGame);

  return {
    start() {
      active = true;
      bestNode.textContent = String(onBest(0));
      render();
      if (score === 0 && board.every((row) => row.every((cell) => cell === null))) {
        status.textContent = "The first two long pieces can clear a row when placed end-to-end. Tap a highlighted top-left square.";
      }
    },
    stop() { active = false; },
  };
}
