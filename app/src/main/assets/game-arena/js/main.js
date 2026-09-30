import { createSnakeGame } from "./games/snake.js";
import { createBlockGame } from "./games/blocks.js";
import { createTicTacToeGame } from "./games/tic-tac-toe.js";
import { bestFor, statFor, updateBest } from "./storage.js";

const homeScreen = document.getElementById("home-screen");
const gameScreens = new Map(
  [...document.querySelectorAll("[data-game-screen]")].map((screen) => [screen.dataset.gameScreen, screen]),
);
let activeGame = null;

function refreshHome() {
  document.querySelectorAll("[data-best]").forEach((node) => {
    node.textContent = String(bestFor(node.dataset.best));
  });
  document.querySelectorAll("[data-wins]").forEach((node) => {
    node.textContent = String(statFor(node.dataset.wins, "wins"));
  });
  document.getElementById("snake-best").textContent = String(bestFor("snake"));
  document.getElementById("blocks-best").textContent = String(bestFor("blocks"));
  document.getElementById("ttt-wins").textContent = String(statFor("tic-tac-toe", "wins"));
  document.getElementById("ttt-draws").textContent = String(statFor("tic-tac-toe", "draws"));
}

const games = {
  snake: createSnakeGame(gameScreens.get("snake"), {
    onBest: (score) => updateBest("snake", score),
  }),
  blocks: createBlockGame(gameScreens.get("blocks"), {
    onBest: (score) => updateBest("blocks", score),
  }),
  "tic-tac-toe": createTicTacToeGame(gameScreens.get("tic-tac-toe"), {
    onResult: () => refreshHome(),
  }),
};

function openGame(name) {
  const nextScreen = gameScreens.get(name);
  if (!nextScreen || !games[name]) return;
  if (activeGame && activeGame !== name) games[activeGame].stop();
  homeScreen.hidden = true;
  gameScreens.forEach((screen) => { screen.hidden = screen !== nextScreen; });
  activeGame = name;
  games[name].start();
  nextScreen.querySelector("[data-home]")?.focus({ preventScroll: true });
  window.scrollTo(0, 0);
}

function showHome() {
  if (activeGame) games[activeGame].stop();
  activeGame = null;
  gameScreens.forEach((screen) => { screen.hidden = true; });
  homeScreen.hidden = false;
  refreshHome();
  window.scrollTo(0, 0);
}

document.addEventListener("click", (event) => {
  const launcher = event.target.closest("[data-open-game]");
  if (launcher) {
    event.preventDefault();
    openGame(launcher.dataset.openGame);
    return;
  }
  if (event.target.closest("[data-home]")) showHome();
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && activeGame) {
    event.preventDefault();
    showHome();
  }
});

refreshHome();
