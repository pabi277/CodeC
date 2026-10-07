# Owner device round 1 — partial acceptance, 2026-10-07

> **Latest status — required Chapter8/P1/P5 device checks PASSED.**
> Owner confirmed audible speech and Android16 in round3 below. CodeC version
> reported as “latest”; exact version/build remains unspecified, not inferred.
> Earlier round states below are historical. No merge/deployment authorization.

Source: owner's pasted CodeC Terminal transcript following the complete six-file
setup supplied in chat. These are owner-observed Android results, not host stubs.
Website review: 802532d43814e1b7837760ceb5769d0b794ceac7. The installed app version,
Android version and phone model were NOT supplied; do not infer them from the
website release CTA. `uname -m` reported `aarch64`.

## Observed results

| Test | Actual observation | Status |
|---|---|---|
| Compiler | `/data/data/com.codeci.ide/files/usr/bin/cc`; TCC 0.9.27 HEAD:6a24b762, AArch64 Linux; all five C programs compiled and ran | Built-in TCC frontend evidenced; version-only diagnostic failed separately |
| Input | Entered `2000` as name, then `2000` as year; `2000 will turn 30 in 2030`; exit 0 | PASS observed valid two-field input; literal Ada fixture not run |
| Loop | All ten rows `7 x 1 = 7` through `7 x 10 = 70`; exit 0 | PASS |
| Pointers | `Before: 1 2; *p=1`, then `After: 2 1; *p=2`; exit 0 | PASS |
| Converter | Choice 1, temperature 45 → `113.00`; choice 0 quits; exit 0 | C→F and quit PASS; F→C not tested |
| P1 calculator | `44 + 33` → `77.00`; `5 * 67` → `335.00`; q quits; exit 0 | Addition/multiplication/quit PASS; zero-division refusal and unsupported-operation recovery not tested |
| P5 battery | percentage 84, discharging, temperature 34.6, health good, voltage 4137, plugged unknown | Actual JSON observed; unknown field remains unknown |
| P5 default | Package update explicitly skipped; `Battery: 84%`; `OK`; `PASS: speech request accepted`; exit 0 | Default control flow/API request PASS; audible voice not confirmed |
| P5 approved maintenance | Owner entered YES; signed /dev metadata fetched; ncurses/sed transaction preflight passed; unpack/setup completed; `pkg: upgraded CodeC packages`; `PASS: packages updated`; battery/speech request completed; exit 0 | Consented maintenance path PASS; no repeat requested |

The numeric name is valid for `%19s`: this example accepts a single text token,
not alphabetic-only names. Its output is consistent, not evidence of input loss.
The maintenance transcript shows unpacking over identical printed package-version
strings; it demonstrates successful transaction execution, not a proven version
increase. No additional package upgrade is needed for this website test.

## `cc --version` diagnostic — read-only source diagnosis

Owner saw the version banner followed by crt/library names and
`tcc: error: undefined symbol 'main'`. This was the version-only command, NOT one
of the successful lesson compilations. Current source
`app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt`, `ccScript()`
(lines 116–198 at app tree d4231f0), unconditionally supplies startup objects and
static libraries to TCC and has no version-only early return. This explains the
attempt to link without a source `main`. The earlier agent recommendation to use
`cc --version` as a clean metadata probe was unsuitable for this wrapper.

Record this diagnostic limitation; do not reinstall, change engine, weaken tests,
or edit app source under website scope. No app/compiler fix is claimed.

## Remaining small follow-up

Use the existing binaries in `$HOME/codec-website-tests`; no source rewrite or
package maintenance repeat required. Interactive terminal input was already
observed; these piped inputs exercise the missing branches on the same device.

```sh
printf '2\n212\n0\n' | ./converter; printf '\nExit code: %s\n' "$?"
printf '/\n4 0\nx\nq\n' | ./calc; printf '\nExit code: %s\n' "$?"
```

Expect converter `100.00` and exit 0. Expect calculator `Cannot divide by zero`,
then `Choose a listed operation`, and exit 0. These remain EXPECTED, not measured.
Also ask whether speech was actually heard, and request CodeC/Android versions.
No need to repeat successful loops/pointers/arithmetic or approved package updates.

**Gate state:** W5 converter and W6 P1 remaining branches OPEN; P5 audible speech
confirmation OPEN. Overall acceptance remains partial, not a blanket device pass.
No PR/merge/deploy/commit/push authorized by this test-result message. Website and
ZIP unchanged. Device evidence/documentation only; no new CI run required yet.


## Owner follow-up / round 2 — 2026-10-07

The owner supplied the requested piped-input run on the same test binaries.
The pasted prompt contains duplicated/wrapped command text and HTML-escaped
arrows; the program output and exit codes are clear. No shell/paste defect is
inferred from those transcript artifacts alone.

Observed converter output (arrows normalized for readability):

```text
1 C->F, 2 F->C, 0 quit
Temperature?
100.00
1 C->F, 2 F->C, 0 quit

Exit code: 0
```

Observed calculator output:

```text
Operation (+ - * /), q quit:
Two numbers:
Cannot divide by zero
Operation (+ - * /), q quit:
Choose a listed operation
Operation (+ - * /), q quit:

Exit code: 0
```

**Results:** F→C (212°F→100°C), division-by-zero refusal, unsupported-operation
recovery and clean exits PASS on the owner's device. Together with round1,
Chapter8 and P1 requested functional checks are evidenced by actual runs. The
round1 input/arithmetic used valid alternative values, as recorded above; do not
claim the literal Ada/2+3 fixtures were executed. No more code-test repeats needed.

**Current remaining evidence:** whether the P5 voice was actually heard, plus
CodeC and Android versions. P5 battery/default/consented maintenance request paths
already passed; do not rerun the package upgrade. Overall device acceptance stays
open until the remaining confirmation, not because converter/P1 are still untested.
Website/ZIP/app/workflows unchanged; no commit, push, CI, PR, merge or deployment.


## Owner confirmation / round 3 — 2026-10-07

Owner's exact answers:

```text
Did you hear the “Good morning…” voice? Yes
CodeC version:latest
Android version:16
```

**P5 audibility confirmed.** Together with rounds1–2, the required Chapter8 input,
loops, pointers and converter checks, P1 calculator success/error/quit paths, and
P5 battery/default/speech/explicitly approved maintenance path are DEVICE-PASSED
for this reported device. Android16; aarch64 from round1; CodeC version recorded
as **owner-reported “latest”**, with no exact versionCode/build/APK hash supplied.
Do not silently assign app-v1.3.18 or a particular CI artifact to this device.
This provenance limitation is recorded, not a reason to repeat successful tests.

No remaining requested functional device test or package-upgrade repeat. These
results do not certify other Android versions/ABIs, exhaustive input robustness,
manual accessibility, full-site visual approval, or a fix to `cc --version`.
The diagnostic-wrapper issue remains separately recorded and app source untouched.

**Required W5/W6 device gates CLOSED / PASS.** Website review/owner decisions,
including O7 course license, and explicit PR/merge/deployment authority remain
separate. Device confirmation is not deployment authorization. Website/ZIP bytes
unchanged. This follow-up changes evidence/living records only; no commit, push,
new CI, PR, merge, release or deployment performed.
