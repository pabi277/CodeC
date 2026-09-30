# CodeC Arcade

A small, offline-ready game arena built from separate HTML, CSS, and JavaScript files. Open `index.html` in CodeC and tap **RUN** to play. It uses no packages, network requests, external fonts, or downloaded images.

## Games

- **Snake** — swipe the board, tap the arrow pad, or use the keyboard. Eat fruit, avoid the walls and your tail, and beat your saved best.
- **Block Party** — an original block-blast-style puzzle. Choose a shape, then tap one of the highlighted starting squares. Fill a full row or column to clear it. There is no timer.
- **Tic-Tac-Toe** — play as X against the pocket CPU. Choose a square; the game announces wins, draws, and the next move.

## Project map

- `index.html` — the home screen and the three game screens.
- `styles/arena.css` — colors, layout, responsive rules, and motion.
- `js/main.js` — home-to-game navigation and startup.
- `js/storage.js` — best scores and small local records.
- `js/games/snake.js` — Snake rules, canvas drawing, and touch/keyboard controls.
- `js/games/blocks.js` — the block-placement puzzle, board rules, and score.
- `js/games/tic-tac-toe.js` — the board, win checks, and pocket CPU.

## Make it yours

Try changing the palette in `styles/arena.css`, then save and reload the preview. To tune the Snake speed, edit the tick interval near the top of `js/games/snake.js`. The block shapes and board size are easy to find in `js/games/blocks.js`; the computer's choices live in `js/games/tic-tac-toe.js`.

All high scores stay in this browser on this device. The games do not need an account or internet connection.
