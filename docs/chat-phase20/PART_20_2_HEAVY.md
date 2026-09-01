# CodeC Phase 20.2 — Optional heavy compilers (Go, Rust — on-demand)

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[repo-build-heavy]` (guarded)
· **Depends on:** Phase 20.1 (CI pipeline established, repo healthy)
· **Target files:** `codec-packages/properties.codec.sh`
· **CI workflow:** `.github/workflows/package-repository.yml` (with `[repo-build-heavy]` tag)

---

## 1. Context & motivation

Go and Rust are large (Go ~80 MB compressed, Rust ~200 MB+) and infrequently
needed on a phone. Including them in the normal `[repo-build]` CI path would
add 60–120 min to every build. This part adds them as **opt-in packages** —
available in the CodeC repo, never auto-installed, always behind a user
confirmation when Phase 21's auto-install gate fires.

**Packages:**

| Package | Runtime | Compressed size (est.) | CI build time (est.) |
|---|---|---|---|
| `golang` | `go` compiler + stdlib + `gofmt` | ~80 MB | ~30 min |
| `rust` | `rustc` + Cargo + `rustfmt` | ~200 MB | ~90 min |

---

## 2. Implementation steps

### Step 1 — Guard the build with `[repo-build-heavy]`

Modify `package-repository.yml` to split the build into two jobs:
- **`build-core`**: triggered on every `[repo-build]` commit — the Phase 20.1
  packages + Python.
- **`build-heavy`**: triggered only when the commit message contains
  `[repo-build-heavy]` — runs `golang` and `rust` builds.

```yaml
# In package-repository.yml, add a condition to the heavy job:
build-heavy:
  if: contains(github.event.head_commit.message, '[repo-build-heavy]')
  ...
```

> This pattern means triggering the heavy build is explicit and intentional —
> you cannot accidentally add 90 min to CI by committing a routine change.

### Step 2 — Add packages to `CODEC_REPOSITORY_PACKAGES` under a comment

```sh
# Heavy compilers — built only with [repo-build-heavy]; install on demand
# golang    # ~80 MB; enable when needed
# rust      # ~200 MB; enable when needed
```

They are commented out by default. When the owner wants Go or Rust support,
uncomment, commit with `[repo-build-heavy]`, and trigger the heavy build job.

### Step 3 — Size warnings in the auto-install prompt (app side, Phase 21 hook)

Phase 21's auto-install gate in `EditorViewModel` already shows "Install X to
run Y files?" When `requiredPackage` is `golang` or `rust`, the prompt must
include the size warning:

```
Install golang (~80 MB) to run Go files?
This downloads once and works offline after that.
[ Cancel ]  [ Install ]
```

The size string is a field in `LanguageRunProfile.installSizeHint: String?`
(added in Phase 21). Phase 20.2 just documents the requirement; Phase 21 implements it.

---

## 3. Exit condition & device recipe

```sh
# After the owner triggers [repo-build-heavy] and the run completes:

# 1. Go
pkg install golang
go version          # EXPECT: go version go1.x.x linux/arm64
echo 'package main
import "fmt"
func main() { fmt.Println("Hello Go") }' > /tmp/hello.go
go run /tmp/hello.go
# EXPECT: Hello Go

# 2. Rust
pkg install rust
rustc --version     # EXPECT: rustc 1.x.x ...
echo 'fn main() { println!("Hello Rust"); }' > /tmp/hello.rs
rustc /tmp/hello.rs -o /tmp/hello && /tmp/hello
# EXPECT: Hello Rust
```

**PASS** = both tools compile and run their hello-world on device.

---

## 4. Non-goals & invariants

- **Not in C.2:** app code (→ D); the `installSizeHint` field (→ D.2).
- Go and Rust are **never auto-installed without the user seeing the size
  warning** (Phase 21 guarantees this via `installSizeHint`).
- **Never include in the bootstrap seed** — these are pure opt-in packages.

---

## 5. Design decisions

- **D1 — commented-out by default:** prevents accidental heavy builds and makes
  the intent explicit. An owner wanting Go support uncomments one line and
  commits with the right tag.
- **D2 — size hint in the prompt:** users on constrained data plans must know
  what they are downloading. The hint is stored in `LanguageRunProfile` so it
  appears in the same auto-install dialog as lighter packages — no separate
  flow.
- **D3 — separate CI job, not a separate workflow:** keeps the repo structure
  simple; the heavy job is the same pipeline with more packages enabled.

---

## 6. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Check the exact Termux recipe sizes for `golang` and `rust` at the pinned ref.
> - Verify `gofmt` is bundled with `golang` (not a separate sub-package).
> - Verify `rustfmt` is bundled with `rust` (or check `rustup` alternative).
> - Confirm the `package-repository.yml` job structure supports a second job
>   with the `[repo-build-heavy]` condition without breaking the existing
>   `build-core` trigger.
> - Record sizes: golang compressed ___ MB, rust compressed ___ MB.
