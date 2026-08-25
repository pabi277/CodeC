# Part 4.6 — Expanded package catalog (round 2) publish & clean-device acceptance gate

**Status:** COMPLETE and device-verified on real Android hardware (2026-08-25).  
**Exit condition:** ✅ MET — Published signed repository run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) (reusing CI build [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723)) to the development channel, and verified `pkg install` + execution of all 15 new package roots (`git`, `wget`, `bat`, `ripgrep`, `fd`, `htop`, `tmux`, `tree`, `patch`, `diffutils`, `zstd`, `m4`, `autoconf`, `automake`, `libtool`) on device.

---

## 1. Published Release Details

- **CI Package Build Run:** [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s, 100% green, `aarch64` + `x86_64`).
- **Publish Workflow Run:** [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) (4m 3s, 100% green, deployed to Pages `packages/dev`).
- **Development Channel:** `https://pabi277.github.io/CodeC/dev` (`stable/main`).
- **Keyring:** `codec-archive-keyring-v1.gpg` (Fingerprint `328500868CE9B0F74B62CEFC1D7D52F6F8135015`).

---

## 2. On-Device Acceptance Transcript (aarch64)

### Package Update & Catalog Resolution
```text
codec $ pkg update
Get:1 https://pabi277.github.io/CodeC/dev stable InRelease [2268 B]
Get:2 https://pabi277.github.io/CodeC/dev stable/main all Packages [1215 B]
Get:3 https://pabi277.github.io/CodeC/dev stable/main aarch64 Packages [16.3 kB]
Fetched 19.8 kB in 1s (18.0 kB/s)
Reading package lists...
```

### Installation Transaction
```text
codec $ pkg install git wget bat ripgrep fd htop tmux tree patch diffutils zstd m4 autoconf automake libtool
Reading package lists...
Building dependency tree...
Reading state information...
diffutils is already the newest version (3.12-2).
zstd is already the newest version (1.5.7-1).
The following additional packages will be installed:
  libandroid-posix-semaphore libandroid-utimes libexpat libgit2 libltdl
  libuuid perl
Recommended packages:
  openssh brotli lz4
The following NEW packages will be installed:
  autoconf automake bat fd git htop libandroid-posix-semaphore
  libandroid-utimes libexpat libgit2 libltdl libtool libuuid m4 patch perl
  ripgrep tmux tree wget
0 upgraded, 20 newly installed, 0 to remove and 3 not upgraded.
Need to get 28.0 MB of archives.
After this operation, 128 MB of additional disk space will be used.
Get:1 https://pabi277.github.io/CodeC/dev stable/main aarch64 m4 aarch64 1.4.19-5 [192 kB]
Get:2 https://pabi277.github.io/CodeC/dev stable/main aarch64 libandroid-utimes aarch64 0.4 [3128 B]
Get:3 https://pabi277.github.io/CodeC/dev stable/main aarch64 perl aarch64 5.42.2 [15.4 MB]
Get:4 https://pabi277.github.io/CodeC/dev stable/main aarch64 autoconf all 2.73 [625 kB]
Get:5 https://pabi277.github.io/CodeC/dev stable/main aarch64 automake all 1.18.1 [577 kB]
Get:6 https://pabi277.github.io/CodeC/dev stable/main aarch64 libgit2 aarch64 1.9.7 [721 kB]
Get:7 https://pabi277.github.io/CodeC/dev stable/main aarch64 bat aarch64 0.26.1-1 [2270 kB]
Get:8 https://pabi277.github.io/CodeC/dev stable/main aarch64 fd aarch64 10.4.2 [944 kB]
Get:9 https://pabi277.github.io/CodeC/dev stable/main aarch64 libexpat aarch64 2.8.3 [96.2 kB]
Get:10 https://pabi277.github.io/CodeC/dev stable/main aarch64 git aarch64 2.55.0 [4546 kB]
Get:11 https://pabi277.github.io/CodeC/dev stable/main aarch64 htop aarch64 3.5.3 [131 kB]
Get:12 https://pabi277.github.io/CodeC/dev stable/main aarch64 libandroid-posix-semaphore aarch64 0.1-4 [4100 B]
Get:13 https://pabi277.github.io/CodeC/dev stable/main aarch64 libltdl aarch64 2.6.2 [18.0 kB]
Get:14 https://pabi277.github.io/CodeC/dev stable/main aarch64 libtool aarch64 2.6.2 [401 kB]
Get:15 https://pabi277.github.io/CodeC/dev stable/main aarch64 libuuid aarch64 2.42.1-4 [15.4 kB]
Get:16 https://pabi277.github.io/CodeC/dev stable/main aarch64 patch aarch64 2.8-1 [74.0 kB]
Get:17 https://pabi277.github.io/CodeC/dev stable/main aarch64 ripgrep aarch64 15.2.0 [1220 kB]
Get:18 https://pabi277.github.io/CodeC/dev stable/main aarch64 tmux aarch64 3.7c [386 kB]
Get:19 https://pabi277.github.io/CodeC/dev stable/main aarch64 tree aarch64 2.3.2 [35.9 kB]
Get:20 https://pabi277.github.io/CodeC/dev stable/main aarch64 wget aarch64 1.25.0-1 [282 kB]
Fetched 28.0 MB in 13s (2126 kB/s)
```

### Execution Verification (All 15 package roots)
```text
codec $ git --version
git version 2.55.0

codec $ wget --version
GNU Wget 1.25.0 built on linux-android.

codec $ bat --version
bat 0.26.1 (12f12f00)

codec $ rg --version
ripgrep 15.2.0 (rev edff6ca275)

codec $ fd --version
fd 10.4.2

codec $ htop --version
htop 3.5.3

codec $ tmux -V
tmux 3.7c

codec $ tree --version
tree v2.3.2 © 1996 - 2024 by Steve Baker, Thomas Moore, Francesc Rocher, Florian Sesser, Kyosuke Tokoro

codec $ patch --version
GNU patch 2.8

codec $ diff --version
diff (GNU diffutils) 3.12

codec $ zstd --version
*** zstd command line interface 64-bits v1.5.7 ***

codec $ m4 --version
m4 (GNU M4) 1.4.19

codec $ autoconf --version
autoconf (GNU Autoconf) 2.73

codec $ automake --version
automake (GNU automake) 1.18.1

codec $ libtool --version
libtool (GNU libtool) 2.6.2
```

---

## 3. Findings & Fixes Applied During Acceptance

1. **Client-side `pkg` maintainer script allowlist (`ShellEnvironment.kt`)**:
   While `repository_lib.py` on the build server permitted `bat` and `util-linux` alternatives maintainer scripts, `ShellEnvironment.kt`'s `pkgScript()` on the client side still had the older 3-package allowlist (`coreutils`, `less`, `nano`).
   - **Fix:** Added `bat` and `util-linux` to `validate_control_scripts` in `ShellEnvironment.kt`, bumped `BOOTSTRAP_VERSION` to `"22"`, and committed (`8147b81`).

---

## Exit Conditions Met

- ✅ Published run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) deployed to `https://pabi277.github.io/CodeC/dev`.
- ✅ Signed repository metadata (`InRelease`) validated without warnings.
- ✅ All 15 newly added roots (`git`, `wget`, `bat`, `ripgrep`, `fd`, `htop`, `tmux`, `tree`, `patch`, `diffutils`, `zstd`, `m4`, `autoconf`, `automake`, `libtool`) downloaded, installed, and executed successfully on a real arm64 device.
