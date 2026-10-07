# Required owner device checks — PASSED; evidence recorded

> **Required owner device checks PASSED (2026-10-07, rounds1–3).**
> Chapter8/P1/P5 passed, including audible speech and consented maintenance.
> Android16 / aarch64; CodeC reported as “latest” (exact build unspecified).
> No retest needed. Website review/license and PR/merge/deploy authority remain
> separate. [Actual evidence and compiler diagnostic](DEVICE_ROUND_1.md).

**2026-10-07 · Remaining-site batch (D33).** The owner explicitly retained these
gates. Host compiler, Bash stubs, browser tests and successful APK CI do NOT close
them. Owner-observed results are in DEVICE_ROUND_1.md; the reusable checklist
below contains expected results, not measured output. No physical Android device
is attached to this workspace. Use the actual examples in the current review ZIP.

## Before testing

1. Use a supported device and record CodeC version/build (About), Android version
   and CPU ABI. Built-in TCC is bundled only for arm64-v8a/x86_64; there is no
   engine picker. Record if automatic fallback occurs rather than calling it a
   built-in-TCC pass.
2. Export valuable projects outside app-private storage. Use disposable projects
   for the lessons. This website-only batch does not require installing a new APK.
3. Import the full website ZIP into CodeC, open index.html and RUN to read locally.
   The chapter links all work inside that project. Type example files in a separate
   practice project so you do not overwrite website/index.html or style.css.
4. For terminal/package/API exercises, complete the opt-in Linux setup if needed.
   Keep CodeC Terminal foregrounded for device API work; inspect every permission.

## W5 / Chapter 8 — four required observations

| Exercise | What to run | Expected check (NOT actual result) |
|---|---|---|
| §4 bounded input | input.c; enter `Ada 2000` | `Ada will turn 30 in 2030`; exit0 |
| §6 loop try-it | Modify sum.c to print7 times table1…10 | First result7, last70, exactly10 rows |
| §9 pointers | pointers.c | Before1/2 and *p=1; after2/1 and *p=2 |
| §10 capstone | converter.c; choices1/0,2/212,0 |32.00,100.00, clean quit |

For each: capture command, input, actual output, exit status, whether the bundled
compiler path or fallback ran, and any unexpected UI/input behavior. Expected
output on a website is not proof. Use Terminal for the interactive examples.

## W6 / P1 — calculator

Open Chapter16/P1 and enter the complete calc.c. Compile:

```sh
cc calc.c -o calc
./calc
```

Exercise `+` with `2 3`, `*` with `4 5`, `/` with `4 0`, then `q`.
Expected checks:5.00,20.00, zero-division refusal and normal exit. Also try an
unsupported operation; the menu should remain usable. Record actual transcript.
This is conservative teaching C, not a hardened arbitrary-precision calculator.

## W6 / P5 — morning automation

1. Capture actual `codec-battery` JSON. Its documented key is **percentage**;
   unavailable/null must not become a fabricated numeric result.
2. Save morning.sh from Chapter16/P5 and run `bash morning.sh` in CodeC Terminal.
   The default skips package upgrades, reads the battery and requests speech.
3. Record exact stdout/stderr and exit status; separately record **speech heard**
   or **not heard**. `PASS: speech request accepted` is not proof of audible audio.
4. The planned package-maintenance path is explicit and mutating. Only after a
   source backup and deliberate consent, run `bash morning.sh --upgrade`. It asks
   for `YES` before `pkg update` and `pkg upgrade -y`. Capture prompts/results.
   If you do not want to update packages, record **upgrade path NOT TESTED**;
   do not mark that path passed. Owner acceptance/waiver must be explicit if a
   required portion is left untested.
5. Record denied permissions, absent voice/hardware, network failures or background
   interruption honestly. Do not grant unrelated permissions merely to get PASS.

## Paste-back template

```text
Website review commit / ZIP:
CodeC version/build:
Android / device / ABI:
Optional Linux ready:
Compiler path (built-in / fallback / unknown):
Chapter8 input: command, input, output, exit, PASS/FAIL
Chapter8 loops: command, output, exit, PASS/FAIL
Chapter8 pointers: command, output, exit, PASS/FAIL
Chapter8 converter: command, input, output, exit, PASS/FAIL
P1 calculator: command, inputs, outputs, exit, PASS/FAIL
P5 battery JSON (inspect for unrelated sensitive content):
P5 default: stdout/stderr, exit, speech heard/not heard
P5 upgrade path: approved/tested OR NOT TESTED; result
Any recovery, permission denial, layout/input problem:
```

Do not include API keys, GitHub tokens, private source, identifying account data or
full unreviewed logs. Screenshots are useful for layout/input problems; text is
better for exact commands and output. The agent must record your actual report,
fix failures for cause and repeat affected checks. The owner has now supplied the required functional device evidence (rounds1–3).
Those device gates are PASS; public deployment still requires explicit authority
and the separate review/licensing checks, not an assumed blanket acceptance.
