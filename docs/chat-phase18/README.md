# CodeC Phase 18 Documentation — CodeCApi Device Capabilities & Final Polish

Phase 18 completes the `CodeCApi` Android device bridge (`[client-only]`), adding hardware access (`codec-battery`, `codec-sensor`, `codec-tts`, `codec-camera`, `codec-intent`) and final UI/theme parity across CodeC.

**Status (2026-09-01):** ⚙️ **IMPLEMENTED on `arena/01a05b12-codec`** — all five
CLI scripts + wire ops, runtime-CAMERA park/resume flow, full host-test suite;
**CI + device recipe pending** (see the part doc §5).

## Contents & References
- **[Part 18.1 — Hardware APIs, Sensor/Battery/TTS & Intent Bridge](PART_18_CODEAPI.md)** — spec §1–4 + implementation record §5 (design decisions D1–D9, research notes, files, exit-condition status).
