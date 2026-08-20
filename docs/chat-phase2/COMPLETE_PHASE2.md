# How you finish Phase 2 (operator guide)

Read this on a laptop with GitHub access. The phone only comes in at **step 7**.

Phase 2 is **done** only when a **real** `bash` or `busybox` (ELF, rebuilt with **our** prefix) runs inside CodeC Term. The 1.3.14 APK only **downloads and extracts**. It cannot invent that tarball.

`pkg install` / apt / clang = **Phase 3**. Ignore those until this guide’s smoke test passes.

---

## 0. What is already done (do not redo)

| Already in PR #6 / branch `arena/01a01e7c-codec` | You still must do |
|---|---|
| App 1.3.14: download, SHA-256, extract, prefer ELF bash, keep `cc` | Put the **workflow file** on GitHub (App cannot push `.github/workflows/*`) |
| `codec-packages/` overlay: `TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr` | Run the **Bootstrap userland** job (hours, Docker) |
| Term toolbar download icon | Publish tarball as release tag **`userland-v1`** |
| Offline TCC `cc` | Install 1.3.14, download userland, smoke-test on phone |

**Hard rules (do not break):**

- Prefix baked into every binary must be  
  `/data/data/com.codeci.ide/files/usr`  
  **Never** `/data/data/com.termux/files/usr`.
- Do **not** copy Termux’s prebuilt `.deb`s.
- Do **not** use proot or Alpine.
- Do **not** bake 20–40 MB into the APK.
- `targetSdk` stays **28**.
- Do not delete `cc`. Do not start apt.

The app looks for **exactly** these URLs (see `UserlandManifest`):

```
https://github.com/pabi277/CodeC/releases/download/userland-v1/bootstrap-aarch64.tar.gz
https://github.com/pabi277/CodeC/releases/download/userland-v1/bootstrap-aarch64.tar.gz.sha256
```

(x86_64 emulator: `bootstrap-x86_64.tar.gz` — optional, later.)

Phone CPU for a real device is almost always **arm64** → `aarch64`.

---

## 1. Merge / checkout the Phase 2 branch

On your computer:

```
git clone https://github.com/pabi277/CodeC.git
```

```
cd CodeC
```

```
git fetch origin
```

```
git checkout arena/01a01e7c-codec
```

If PR #6 is already merged to `main`, use `main` instead.

Confirm overlay:

```
grep TERMUX_APP_PACKAGE codec-packages/properties.codec.sh
```

You must see `com.codeci.ide`.

---

## 2. Put the GitHub Actions workflow on the repo

Arena’s GitHub App **refused** to push workflow files. You do this once with **your** GitHub login.

### 2a. Permission

You need a personal access token (classic) with **`workflow`** + **`repo`**, **or** use GitHub’s website while logged in as `pabi277`.

Settings → Developer settings → Personal access tokens → classic → generate:

- `repo`
- `workflow`

### 2b. Copy the file

From the repo root:

```
mkdir -p .github/workflows
```

```
cp docs/chat-phase2/bootstrap-userland.yml.patch .github/workflows/bootstrap-userland.yml
```

The `.patch` file is already valid YAML (the name is only because we could not store it under `.github/workflows/`).

### 2c. Commit and push (your machine)

```
git add .github/workflows/bootstrap-userland.yml
```

```
git commit -m "ci: add Bootstrap userland workflow"
```

```
git push origin arena/01a01e7c-codec
```

If `main` is protected, push to this branch then merge PR #6, **or** push the workflow commit to `main` yourself.

**Check:** GitHub → **Actions** tab → a workflow named **Bootstrap userland** exists.

If push is rejected with `workflows permission`, you are still using the Arena app token. Use your PAT:

```
git remote set-url origin https://YOUR_GITHUB_USERNAME:YOUR_PAT@github.com/pabi277/CodeC.git
```

```
git push origin arena/01a01e7c-codec
```

Then set the remote back to the normal HTTPS URL.

---

## 3. (If clone of termux-packages fails) pin a real ref

`codec-packages/properties.codec.sh` has:

```
TERMUX_PACKAGES_REF=v0.1240
```

That string must be a **real tag or branch** on https://github.com/termux/termux-packages .

Open that repo → **Tags**. If `v0.1240` is missing, pick a current tag (example shape: `v0.12xx` or a commit SHA).

Edit **only** `TERMUX_PACKAGES_REF=...` in `properties.codec.sh`. Commit. Push. Do not change `TERMUX_PREFIX`.

---

## 4. Run the bootstrap build

GitHub → **Actions** → **Bootstrap userland** → **Run workflow**.

- Branch: `main` if merged, else `arena/01a01e7c-codec`
- `arch`: `aarch64` (phones)

Click **Run workflow**.

### What it does

1. Checks the prefix overlay.
2. Pulls Docker image `ghcr.io/termux/package-builder`.
3. Clones termux-packages, rewrites `TERMUX_APP_PACKAGE` to `com.codeci.ide`.
4. Builds: busybox, bash, coreutils, grep, sed, gawk, tar, gzip, nano, less, make, file, termux-exec.
5. Packs them into `codec-packages/dist/bootstrap-aarch64.tar.gz` + `.sha256`.

### Time / cost

First run: **1–4 hours**. GitHub-hosted runners have a 6 hour cap (`timeout-minutes: 360`). Disk is tight; the workflow deletes unused SDKs.

If it **fails**:

| Symptom | What to do |
|---|---|
| `No space left on device` | Re-run; cache may help. Or run on a bigger runner later. |
| Docker pull denied | Public `ghcr.io/termux/package-builder` should work anonymous. Retry. |
| `apply-prefix: PREFIX mismatch` | Overlay did not stick. Do not continue with Termux’s prefix. |
| Package `gawk` / `file` recipe error | Drop that name from `CODEC_BOOTSTRAP_PACKAGES` (keep bash + busybox). Commit, re-run. |
| Job cancelled at 6h | Re-run; cache under `codec-packages/.work` should resume faster. |

**Do not** “fix” a failed build by downloading Termux’s own bootstrap.

### Success

Job green. Artifact named **`bootstrap-aarch64`**. Download it from the run page (Artifacts). Inside:

- `bootstrap-aarch64.tar.gz` (tens of MB)
- `bootstrap-aarch64.tar.gz.sha256` (one hex line)

Sanity on your laptop:

```
sha256sum -c bootstrap-aarch64.tar.gz.sha256
```

(If the `.sha256` file is only the hash, run:)

```
echo "$(cat bootstrap-aarch64.tar.gz.sha256)  bootstrap-aarch64.tar.gz" | sha256sum -c
```

Optional peek (must **not** contain `com.termux`):

```
tar -tzf bootstrap-aarch64.tar.gz | head
```

You want `bin/bash`, `bin/busybox`, etc. at the **root of the archive** (or under `usr/` — the app strips a leading `usr/`).

```
tar -xOf bootstrap-aarch64.tar.gz bin/bash | head -c 4 | xxd
```

First bytes should be ELF (`7f 45 4c 46`), not `#!/system/bin/sh`.

---

## 5. Publish GitHub Release `userland-v1`

The app **does not** use Actions artifacts. It only hits **Releases**.

GitHub → **Releases** → **Draft a new release**.

| Field | Value |
|---|---|
| Tag | **`userland-v1`** (exact) |
| Target | commit that has 1.3.14 |
| Title | `userland-v1` |
| Files | `bootstrap-aarch64.tar.gz` **and** `bootstrap-aarch64.tar.gz.sha256` |

Publish (not pre-release if you want the app to treat it as real; pre-release still works if the files are on that tag).

**Check in a browser (incognito):**

https://github.com/pabi277/CodeC/releases/download/userland-v1/bootstrap-aarch64.tar.gz.sha256

Must be **200**, a 64-character hex string, not an HTML 404 page.

The `.sha256` file must be **plain text**, first token 64 hex chars. The Kotlin parser takes `trim()`, then text before the first space.

CLI alternative:

```
gh release create userland-v1 bootstrap-aarch64.tar.gz bootstrap-aarch64.tar.gz.sha256 --repo pabi277/CodeC --title userland-v1 --notes "Phase 2 aarch64 userland, prefix com.codeci.ide"
```

---

## 6. Confirm Build APK is green

Actions → **Build APK** on the same branch: green.

Install **that** APK (1.3.14 / versionCode 18), not an older 1.3.13.

---

## 7. Phone: uninstall, install, Term

1. Android: **Uninstall** CodeC IDE (old `$PREFIX` shims can confuse you).
2. Download **CodeC-IDE** artifact from **Build APK**.
3. Allow unknown sources. Install.
4. Open CodeC → **Term**.
5. Wait. You should see progress, not a frozen screen, for example:
   - `userland: fetching SHA-256…`
   - `userland: downloading bootstrap-aarch64.tar.gz…`
   - `userland: verifying SHA-256…`
   - `userland: extracting into /data/data/com.codeci.ide/files/usr`
   - `userland: ready`
6. Tap **refresh** (circular arrow) so the shell restarts with ELF bash.

If you already opened Term before the release existed, tap the **download** icon in the Term app bar (Install userland), wait for ready, then refresh.

### If download fails

| Message | Meaning |
|---|---|
| `offline — using built-in cc` | Turn on Wi‑Fi. TCC still works. |
| `HTTP 404` / `no release yet` | Tag or filename wrong. Fix Releases. |
| `SHA-256 mismatch` | Re-upload `.sha256` matching that tarball. |
| `no bootstrap for this ABI` | Device is not arm64/x86_64 (rare). |

TCC must still work even when download fails:

```
cc main.c -o a.out
```

```
./a.out
```

---

## 8. Smoke test (this is the pass/fail)

Type **one line**, Enter, wait for the prompt, then the next. Do **not** use `&&`.

Save a `main.c` in the Editor first if you have no `.c` file.

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

Optional:

```
nano
```

Then Ctrl+X to quit.

### Pass

| Command | Must see |
|---|---|
| `echo $PREFIX` | `/data/data/com.codeci.ide/files/usr` (or `/data/user/0/com.codeci.ide/files/usr` — same place) |
| `which bash` | `.../com.codeci.ide/files/usr/bin/bash` |
| `cc` / `./a.out` | compile and run as in Phase 1 |
| `nano` | editor UI, not `not found` |

### Fail (Phase 2 not complete)

| Symptom | Cause |
|---|---|
| `which bash` → `/system/bin/sh` or empty | Extract did not run or bash is still the shim |
| `can't execute: Permission denied` on bash | Wrong prefix binary, or not ELF, or W^X — you used Termux debs |
| `cc` disappeared | Extract overwrote without rewrite; reinstall APK, refresh (app should always rewrite `cc`) |
| Prompt never returns | Hang during download — we treat that as a bug; paste Term text |

To see if bash is ELF, after extract you can also run:

```
busybox
```

A real busybox prints a help list.

---

## 9. Checklist — tick when all true

- [ ] `.github/workflows/bootstrap-userland.yml` exists on GitHub
- [ ] **Bootstrap userland** run is **green** for `aarch64`
- [ ] Artifact SHA-256 matches the tarball
- [ ] Tarball paths are our prefix layout (`bin/…`), not Termux’s
- [ ] Release tag **`userland-v1`** has `.tar.gz` + `.sha256`
- [ ] Browser download of the `.sha256` URL is 200
- [ ] Phone on **1.3.14**, old APK uninstalled
- [ ] Term showed extract **ready**, then refresh
- [ ] `which bash` is under `com.codeci.ide`
- [ ] `cc` / `./a.out` still work
- [ ] Offline: airplane mode, Term still opens, `cc` still works (userland already on disk)

When that list is ticked, **Phase 2 is complete**. Next chat is Phase 3 only.

---

## 10. What you should not do

- Do not add `.` to `PATH`.
- Do not tell users to `fflush` in C.
- Do not install Termux and copy `/data/data/com.termux/files/usr`.
- Do not bump `targetSdk` above 28 for this.
- Do not put the tarball in `app/src/main/assets`.
- Do not implement `pkg` / apt here.

---

## 11. Optional: x86_64 emulator

Run the workflow again with `arch` = `x86_64` if you add that input to the docker script (today the YAML job is hard-coded `aarch64`). For emulator-only testing, run `codec-packages/scripts/build-bootstrap.sh x86_64` on a machine with Docker, then attach `bootstrap-x86_64.tar.gz` + `.sha256` to the **same** `userland-v1` release.

The app picks `aarch64` if the device lists `arm64-v8a`, else `x86_64`.
