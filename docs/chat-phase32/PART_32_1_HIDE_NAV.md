# CodeC Phase 32.1 — Hide bottom navigation while typing

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Target:** `MainActivity` / Scaffold `NavigationBar`, `EditorScreen`

---

## 1. Design

While `Editor` is the destination **and** (IME visible **or** CodeC Keys
visible), hide the 5-item `NavigationBar`. A thin handle or a swipe-up
restores it. Leaving the editor restores the bar.

Do not hide on Projects / Terminal / Packages / Settings.

TalkBack: handle still reachable (28.4 will deepen a11y).

## 2. Exit condition

```text
(Device)
1. Open editor, open Keys or IME — tab bar gone; more lines of code visible.
2. Navigate away — bar back.
3. Hardware back / handle restores bar without losing buffer.
PASS = all three.
```
