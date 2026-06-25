# HTML → PNG Converter — Android Wrapper

This is your existing "Batch HTML → PNG Converter" web app, wrapped in a minimal
native Android shell so it can run as a real app and ship to the Play Store.

The web app itself (`app/src/main/assets/www/index.html`) is your original code,
lightly modified so that:
- Multi-file picking uses Android's native file picker instead of `<input type=file>`
  (more reliable across devices when wrapped in a WebView).
- "Download PNG" / "Download All" save directly into the device's **Downloads**
  folder using Android's MediaStore API, instead of blob URLs (which don't work
  the same way in a WebView).
- Everything else — html2canvas logic, tiling, presets, trimming — is untouched.

---

## ⚠️ Three things you must do before this will build (one-time setup)

I built this entire project from a sandbox with no internet access, so there are
exactly three pieces I could not generate for you. Each is a one-time, ~5 minute
step.

### 1. Add the Gradle wrapper jar
Gradle's wrapper needs a real binary jar that I can't fabricate. Easiest fix —
run this once on your own machine (only needs Java installed, not Android Studio),
or let GitHub Actions' first failed run guide you. Simplest path:

```bash
# From the project root, with any JDK installed:
gradle wrapper --gradle-version 8.7
```

If you don't have Gradle or a JDK locally either, this can also be done with zero
local installs using GitHub Codespaces (free tier): open the repo in a Codespace,
run the command above in its terminal, commit, push.

### 2. Vendor html2canvas locally
The original app loaded html2canvas from a CDN. Since the packaged app must work
fully offline, download it once and commit it:

```bash
curl -o app/src/main/assets/www/html2canvas.min.js \
  https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js
```

### 3. Generate your release signing key
Google requires every Play Store app to be signed with a key **you** generate and
keep — I can't create this for you since it must stay private to your account.

```bash
keytool -genkey -v -keystore release.keystore -alias html2png \
  -keyalg RSA -keysize 2048 -validity 10000
```

It'll ask for passwords and your name/org — any values are fine, just remember
the passwords. **Never commit `release.keystore` to git** (it's already in
`.gitignore`).

---

## Pushing to GitHub

```bash
cd html2png-android
git init
git add .
git commit -m "Initial Android wrapper"
gh repo create html2png-android --public --source=. --push
# or manually: create the repo on github.com, then:
# git remote add origin https://github.com/<you>/html2png-android.git
# git branch -M main
# git push -u origin main
```

## Wiring up automatic signed builds (GitHub Actions)

The workflow at `.github/workflows/build.yml` builds a debug APK on every push
automatically. To also get a **signed release AAB** (the file format Play Store
requires), add these as repo secrets — GitHub repo → **Settings → Secrets and
variables → Actions → New repository secret**:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Output of `base64 -i release.keystore` (or `base64 -w0 release.keystore` on Linux) |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password you chose |
| `ANDROID_KEY_ALIAS` | `html2png` (or whatever alias you used) |
| `ANDROID_KEY_PASSWORD` | The key password you chose |

Once added, every push to `main` produces a signed `app-release.aab` under the
**Actions** tab → latest run → **Artifacts**. That's the file you upload to Play
Console.

---

## Submitting to the Play Store

1. Create a [Google Play Console](https://play.google.com/console) account
   ($25 one-time registration fee).
2. Create a new app → fill in title, description, category.
3. Under **Production** (or start with **Internal testing** to try it first),
   upload the `app-release.aab` artifact from GitHub Actions.
4. You'll need:
   - A **privacy policy URL** (required even for simple apps — a free one-page
     site or GitHub Pages doc works; state that files are processed on-device
     and nothing is uploaded, since that's true here).
   - App icon (512×512 PNG) and at least 2 screenshots — take these from running
     the app on an emulator or device.
   - Content rating questionnaire (this app will rate as suitable for all ages).
   - Data safety form — answer truthfully: this app collects no user data and
     makes no network requests once installed.
5. Submit for review. First-time app review from Google typically takes a few
   hours to a few days.

---

## Testing before you publish

Since you don't have Android Studio set up, the fastest way to test on a real
phone without installing anything heavy:
1. Download the `app-debug-apk` artifact from your GitHub Actions run.
2. Transfer it to your Android phone (email, Drive, USB).
3. Open it on the phone — you'll need to allow "install unknown apps" for
   whichever app you used to open it (Settings will prompt you automatically).

This is also a perfectly good way to share the app with others before it's on
the Play Store.

---

## Project structure

```
app/src/main/java/.../MainActivity.kt   — WebView host + native bridge (file picker, save-to-Downloads)
app/src/main/assets/www/index.html      — your original app, lightly modified
app/src/main/AndroidManifest.xml        — permissions & app entry point
.github/workflows/build.yml             — CI: builds debug APK always, signed release AAB if secrets are set
```
