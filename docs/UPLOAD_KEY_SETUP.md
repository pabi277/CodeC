# CodeC — the release upload key (one-time owner setup)

> Phase 42.1. The release channel (signed, non-debuggable APKs + published
> GitHub Releases) needs three Actions secrets: `KEYSTORE_B64`,
> `STORE_PASSWORD`, `KEY_PASSWORD`. Every push builds fine **without** them
> (CI just skips the release artifact with a notice); a **publish** run
> (an `app-v*` tag) fails early and names the missing secret until they
> exist — on purpose: no unsigned or debug-signed APK is ever published.
>
> **2026-09-11 attempt log:** the agent tried generating the key in the
> sandbox and setting the secrets via `gh secret set` — the automation
> token answered `HTTP 403` on the secrets API (no permission). The key was
> then generated **in the sandbox** (the spec's other sanctioned route —
> `keytool` via the jdk4py JRE, JKS / RSA-2048 / SHA-256 with RSA /
> 30-year validity, DN `CN=CodeC Upload, O=pabi277, C=US`, alias
> `codec-upload`), the three values (`KEYSTORE_B64` base64,
> `STORE_PASSWORD`, `KEY_PASSWORD` — two independent 24-byte hex secrets)
> were printed **once** into the owner's chat for the owner to enter at
> *GitHub → repo **CodeC** → Settings → Secrets and variables → Actions →
> New repository secret*, and every local copy of the keystore and
> passwords was deleted in the same call (kept nowhere outside the owner's
> Secrets page). Public certificate fingerprint (safe to share, pinned
> here for verification):
> `SHA-256 D5:8F:ED:4B:79:31:3E:CB:43:2F:CD:C1:6B:3C:EE:17:A6:E9:24:80:62:18:0D:18:F1:EB:75:D2:20:5A:46:38` ·
> `SHA-1 1F:AF:A5:AF:1B:CE:69:A0:D8:32:85:E9:54:74:D4:75:60:66:39:9C`.
> If the three secrets must ever be REGENERATED, the recipe below still
> runs in **Termux on the owner's phone** (or any desktop with openssl)
> and takes about two minutes.

## Why this matters (read once)

- The upload key is **permanent**: Android updates an installed app only
  over an APK signed with the SAME key. The first release notes already
  tell testers the debug→release switch is a fresh install; a *lost* key
  later would force the same wipe on everyone, forever.
- Keep an offline copy of `codec-upload.keystore` and its password
  (password manager, or a USB/cloud drive you control). The GitHub secrets
  hold a copy, but secrets cannot be read back — if they are ever deleted,
  the offline copy is the only way to keep updating.
- The key NEVER enters the git repository, the APK, or any commit.

## Recipe (Termux on the phone, ~2 min)

1. In Termux:

   ```sh
   pkg install openssl-tool -y
   cd ~
   PASS="$(openssl rand -hex 24)"
   openssl genrsa -out codec-upload.key 3072
   openssl req -new -x509 -key codec-upload.key -out codec-upload.crt \
     -days 10950 -subj "/CN=CodeC Upload/O=CodeC/C=IN"
   openssl pkcs12 -export -out codec-upload.keystore \
     -inkey codec-upload.key -in codec-upload.crt -name upload \
     -password "pass:$PASS"
   base64 -w0 codec-upload.keystore > codec-upload.b64
   echo "PASSWORD: $PASS"
   wc -c codec-upload.b64      # ~4.6 KB of text
   ```

2. Copy two things out of Termux (long-press → select):
   - the **PASSWORD** line (48 hex chars),
   - the whole `codec-upload.b64` text
     (`cat codec-upload.b64`, select all, copy).

3. On github.com (browser): **CodeC → Settings → Secrets and variables →
   Actions → New repository secret**, three times:
   - `KEYSTORE_B64` — paste the base64 text (one long line)
   - `STORE_PASSWORD` — paste the 48-char password
   - `KEY_PASSWORD` — paste the same password again

4. Back up `codec-upload.keystore` + the password somewhere off the phone
   (this file + that password ARE the update channel's identity), then in
   Termux remove the loose pieces:

   ```sh
   shred -u codec-upload.key codec-upload.crt codec-upload.b64 2>/dev/null \
     || rm -f codec-upload.key codec-upload.crt codec-upload.b64
   ```

   Keep `codec-upload.keystore` itself (the offline backup) or move it
   somewhere safe **you** can reach.

5. Verify: any push's **Build APK** run then shows a `CodeC-IDE-release`
   artifact; the readiness check in the run log says signing material was
   found. From then on, tagging `app-v<X.Y.Z>` publishes a signed release.

## Recovery / rotation

If the key is lost: repeat the recipe, overwrite the three secrets, and say
in that release's notes that everyone must uninstall and reinstall (app
data is lost — Projects should be exported first). The sooner this happens
in the beta the cheaper it is.
