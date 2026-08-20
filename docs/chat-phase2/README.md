# Phase 2 — bootstrap userland (this chat)

App **1.3.14** (`versionCode` 18). Phase 1 behaviour is unchanged: offline `cc` / `./a.out`.

## What landed

| Piece | Where |
|---|---|
| Prefix overlay (GPL-3.0) | `codec-packages/` — `TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr` |
| CI | `.github/workflows/bootstrap-userland.yml` — overlay syntax on PRs; docker build on dispatch/`main` |
| Download + SHA-256 + extract | `UserlandInstaller`, `TarGzExtractor` |
| Prefer real bash | `ShellEnvironment.resolveShell` / do not overwrite ELF bash |
| Install action | Term toolbar download icon |

`apt` / `dpkg` / `pkg install` = **Phase 3**. Do not start that here.

## After APK

Uninstall the old APK. Install **CodeC-IDE** from Actions → Build APK. Term → refresh.

Offline (no `userland-v1` release yet): Term still opens; `cc` still works.

When a bootstrap artifact is on Releases tag `userland-v1`, Term downloads it (or tap the download icon). Then:

```
uname -a
```

```
echo $PREFIX
```

```
which bash
```

```
ls
```

```
cc main.c -o a.out
```

```
./a.out
```

`which bash` must be under `/data/data/com.codeci.ide/files/usr`. If `nano` is in the tarball, `nano` then Ctrl+X.

## Next chat (Phase 2 finish or Phase 3)

- Run **Bootstrap userland** workflow (needs Docker + ~hours). Attach `bootstrap-aarch64.tar.gz` + `.sha256` to release `userland-v1`.
- GitHub App **lacks `workflows` permission**. Copy
  [bootstrap-userland.yml.patch](bootstrap-userland.yml.patch) to
  `.github/workflows/bootstrap-userland.yml` with a PAT that has `workflows`.
- Then Phase 3: apt/dpkg + our repo. Do not copy Termux `.deb`s.
