# Handoff — after Phase 2 overlay

Read [README.md](README.md) and [../chat-phase1/PROBLEMS.md](../chat-phase1/PROBLEMS.md).

## Do

- Keep TCC link order (`-o` last, `codec_stdio.o`, archives twice).
- Keep `cc` chmod 755; never `exec tcc`; no `\\` in the script.
- Never overwrite ELF `$PREFIX/bin/bash` with the mksh shim.
- Never bake the 20–40 MB tarball into the APK.
- Projects stay on executable `filesDir/CodeC/projects`.

## Do not

- Do not start apt/dpkg/`pkg install` unless asked (Phase 3).
- Do not install Termux prebuilt `.deb`s.
- Do not add `.` to `PATH`.
- Do not “fix” RUN stdin / exit 124 unless asked.
