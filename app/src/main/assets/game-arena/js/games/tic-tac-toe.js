import { incrementStat, statFor } from "../storage.js";

const WINS = [
  [0, 1, 2], [3, 4, 5], [6, 7, 8],
  [0, 3, 6], [1, 4, 7], [2, 5, 8],
  [0, 4, 8], [2, 4, 6],
];

export function createTicTacToeGame(root, { onResult = () => {} } = {}) {
  const boardElement = root.querySelector("#ttt-board");
  const status = root.querySelector("#ttt-status");
  const winsNode = root.querySelector("#ttt-wins");
  const drawsNode = root.querySelector("#ttt-draws");
  const squares = [...boardElement.querySelectorAll("[data-square]")];
  let board = Array(9).fill("");
  let over = false;
  let active = false;
  let cpuPending = false;
  let cpuTimer = null;
  let wins = statFor("tic-tac-toe", "wins");
  let draws = statFor("tic-tac-toe", "draws");

  function winningLine(mark, state = board) {
    return WINS.find((line) => line.every((index) => state[index] === mark)) || null;
  }

  function render() {
    squares.forEach((square, index) => {
      const mark = board[index];
      square.textContent = mark === "X" ? "×" : mark === "O" ? "○" : "";
      square.dataset.mark = mark;
      square.disabled = !active || over || Boolean(mark);
      square.setAttribute("aria-label", `Row ${Math.floor(index / 3) + 1}, column ${(index % 3) + 1}, ${mark || "empty"}`);
      square.classList.toggle("winning", Boolean(winningLine("X")?.includes(index) || winningLine("O")?.includes(index)));
    });
    winsNode.textContent = String(wins);
    drawsNode.textContent = String(draws);
  }

  function endRound(result) {
    over = true;
    if (result === "X") {
      wins = incrementStat("tic-tac-toe", "wins");
      status.textContent = "Three in a row! You got it. Start another round?";
      onResult("win");
    } else if (result === "O") {
      status.textContent = "The pocket CPU got this one. Want a rematch?";
      onResult("loss");
    } else {
      draws = incrementStat("tic-tac-toe", "draws");
      status.textContent = "A draw! Nicely matched. Play again?";
      onResult("draw");
    }
    render();
  }

  function move(index, mark) {
    if (over || board[index]) return false;
    board[index] = mark;
    const line = winningLine(mark);
    if (line) {
      endRound(mark);
      return true;
    }
    if (board.every(Boolean)) {
      endRound("draw");
      return true;
    }
    render();
    return true;
  }

  function findWinningMove(mark) {
    for (let index = 0; index < board.length; index += 1) {
      if (board[index]) continue;
      const trial = board.slice();
      trial[index] = mark;
      if (winningLine(mark, trial)) return index;
    }
    return -1;
  }

  function computerChoice() {
    const win = findWinningMove("O");
    if (win >= 0) return win;
    const block = findWinningMove("X");
    if (block >= 0) return block;
    if (!board[4]) return 4;
    const corners = [0, 2, 6, 8].filter((index) => !board[index]);
    if (corners.length) return corners[Math.floor(Math.random() * corners.length)];
    const open = board.map((value, index) => value ? -1 : index).filter((index) => index >= 0);
    return open[Math.floor(Math.random() * open.length)] ?? -1;
  }

  function scheduleComputer() {
    if (over || !cpuPending) return;
    status.textContent = "Pocket CPU is thinking…";
    render();
    window.clearTimeout(cpuTimer);
    cpuTimer = window.setTimeout(() => {
      if (!active || over) return;
      cpuPending = false;
      move(computerChoice(), "O");
      if (!over) status.textContent = "Your turn. Choose any open square.";
      render();
    }, 240);
  }

  function playerMove(index) {
    if (!active || over || board[index]) return;
    move(index, "X");
    if (over) return;
    cpuPending = true;
    scheduleComputer();
  }

  function newRound() {
    window.clearTimeout(cpuTimer);
    cpuPending = false;
    board = Array(9).fill("");
    over = false;
    status.textContent = "Your turn. Choose any open square.";
    render();
  }

  squares.forEach((square) => {
    square.addEventListener("click", () => playerMove(Number(square.dataset.square)));
  });
  root.querySelector("#ttt-reset").addEventListener("click", newRound);

  return {
    start() {
      active = true;
      render();
      if (cpuPending) scheduleComputer();
      else if (!over) status.textContent = "Your turn. Choose any open square.";
    },
    stop() {
      active = false;
      window.clearTimeout(cpuTimer);
      render();
    },
  };
}
