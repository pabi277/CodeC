# Phase 95 — owner device round

Check these on your phone with a fresh install (so the welcome appears):

## Welcome + agreement
- [ ] **D1.** First time you open the AI on a new install, you see the welcome card: round sparkle, title "Welcome to CodeC AI", the "Before you ask" heading, four checkmarked bullets, and one blue "I understand — start" button. The composer and action chips are NOT drawn.
- [ ] **D2.** Tapping "I understand — start" dismisses the card and reveals the normal chat surface (hint, composer, chips). Closing the app and reopening does NOT show the card again.
- [ ] **D3.** Before tapping Start you cannot accidentally send anything (no composer, no Send arrow, no chips).

## Composer clears
- [ ] **D4.** Ask a question (e.g. "what is onCreate") and tap ➤. After the answer arrives the input box is empty, ready for the next question — the previous question does not stay in it.

## History drawer
- [ ] **D5.** Tapping ☰ (top-left) slides in the drawer; you see "+ New chat" at the top, and (after one exchange lands) a **Recents** row with that question, clipped to one line.
- [ ] **D6.** Ask a second question on a blank composer, get an answer, open the drawer → **one** Recents row for this chat, holding both exchanges. Tapping another row replaces the conversation with that one whole (no merge). *(Row rewritten in Phase 96: the two-rows expectation above was the doc's error, not the app's — one chat is one row, and a new row starts at **+ New chat** or a project switch. See [P4](../chat-phase96/DEVICE_ROUND.md).)*
- [ ] **D7.** Tapping the pin icon on a row moves it to a **Pinned** section at the top; it survives New chat. Unpin moves it back.
- [ ] **D8.** The "+ New chat" pill (and the header +) asks once ("Start a new chat?" with "nothing here is saved to a file" — same sentence as before), then archives the current chat, opens an empty composer, and shows the drawer so you can see where it went.
- [ ] **D9.** Switching projects (A → B → A): A's history is back when you return; B never shows A's chats.

## Safety kept
- [ ] **D10.** Secret redaction still works (Phase 94 test string from the last round: a planted `sk-…` comes back as `[redacted: credential-shaped value]`).
- [ ] **D11.** A proposed edit still shows a diff card with Apply/Reject — no auto-apply. A run request still waits for your Run tap.
