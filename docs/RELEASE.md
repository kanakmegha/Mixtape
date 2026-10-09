# Mixtape — Release, Signing & Private Distribution

## 1. Build variants

| Variant | API base URL | Cleartext HTTP | Distribute? |
|---|---|---|---|
| `debug` | `http://192.168.1.83:8081/api/` (your LAN, dev only) | allowed (`src/debug/AndroidManifest.xml`) | **No** |
| `release` | `https://backend-woad-xi-54.vercel.app/api/` | blocked (Android default) | **Yes** |

The URL is defined only in `android/app/build.gradle.kts` (`buildConfigField API_BASE_URL`).
The app contains **no** database URL, DB password, or JWT secret — those live only in Vercel.

## 2. Signing key (one-time)

> The signing key is permanent. Android only installs an update if it is signed with the
> **same key** as the installed app. If you lose it, every user must uninstall (losing app data)
> to move to a new key. Back it up in at least two secure places (e.g. password manager + encrypted drive).

Create it **outside** the repository (keytool prompts for passwords; nothing is echoed):

```bash
mkdir -p ~/secure && keytool -genkeypair -v \
  -keystore ~/secure/mixtape-release.jks \
  -alias mixtape -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Mixtape, O=Private"
```

Then:

```bash
cp android/keystore.properties.example android/keystore.properties
# edit android/keystore.properties: storeFile, storePassword, keyAlias, keyPassword
```

`keystore.properties`, `*.jks`, `*.keystore`, `*.apk` are gitignored. Alternatively export
`MIXTAPE_KEYSTORE_PATH`, `MIXTAPE_KEYSTORE_PASSWORD`, `MIXTAPE_KEY_ALIAS`, `MIXTAPE_KEY_PASSWORD`.

If no key is configured, `assembleRelease` produces an **unsigned** APK
(`app-release-unsigned.apk`) — it never falls back to the debug key.

## 3. Build the signed release APK

```bash
cd android
./gradlew clean assembleRelease
```

Output: `android/app/build/outputs/apk/release/app-release.apk`

Rename for distribution, e.g. `Mixtape-1.0.0-1.apk`.

## 4. Verify signature & checksum

```bash
BT=$(ls -d ~/Library/Android/sdk/build-tools/* | tail -1)
$BT/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
$BT/aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "package|testOnly|debuggable"
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

Expect `Verified using v2 scheme: true`, a certificate SHA-256 digest that is **identical for
every release** (record it here once known), no `testOnly`, no `debuggable`.

Release certificate SHA-256: `<record after first signed build>`

## 5. Publishing a new release

1. Increment `mixtape.versionCode` (+1, always) and update `mixtape.versionName` in `android/gradle.properties`.
2. Build with the **same** keystore (`./gradlew clean assembleRelease`).
3. Verify: certificate digest unchanged; applicationId still `com.example.privatemusiclibrary`.
4. Upload, publish the new SHA-256 checksum, notify users.

Users install the new APK over the old one — no uninstall, data is kept. Android rejects the
update if the versionCode is not higher or the signing certificate differs.

## 6. Private HTTPS distribution

Any private HTTPS file host works (e.g. Google Drive / Dropbox link restricted to invitees,
a private GitHub Release on a private repo, or a password-protected page). Recommended:

- Share the link only with invited users; rotate it if leaked.
- Publish the SHA-256 checksum alongside the link (separate channel if possible).
- Do **not** commit APKs to git; do not publish the keystore or `keystore.properties`.

## 7. Installation guide for users (copy/paste to invitees)

1. On your Android phone, open the download link in Chrome and download `Mixtape-x.y.z.apk`.
2. Tap the downloaded file (notification or **Files → Downloads**).
3. Android may say *"For your security, your phone is not allowed to install unknown apps from
   this source."* Tap **Settings**, enable **Allow from this source** for Chrome (or your file
   manager), then go back. (Android 8+: per-app permission. Android 7: Settings → Security →
   *Unknown sources*.)
4. Tap **Install**. If Google Play Protect warns about an unrecognised developer, choose
   **More details → Install anyway** (expected for privately distributed apps).
5. Open **Mixtape**, sign in or register with your invitation code (needs internet once).
6. Optional: turn "Allow from this source" back off afterwards.

Updates: download the new APK and install it the same way — your library is preserved.

## 8. What works offline vs. needs internet

| Works offline (after first sign-in) | Needs internet |
|---|---|
| Opening the app (session is checked locally) | Registration & invitation validation |
| Playing previously imported local audio, background playback (Media3) | Login / token refresh |
| Library browsing, search, favorites, playlists (Room) | Cloud sync of metadata/playlists |
| Playback state persistence | Importing new audio from a website URL |

Sync is push-only and never deletes local data; failed syncs are retried a bounded number of
times (max 5, WorkManager exponential backoff). Network failures during token refresh do not
sign the user out. A first-time user cannot get past the login screen without internet.

## 9. Future: Google Play closed testing

1. Create a Play Console developer account (one-time fee) and an app entry.
2. Change `applicationId` away from `com.example.*` **before** first Play upload (Play rejects
   `com.example`). Note: this makes it a different app from the sideloaded one.
3. Build an AAB: `./gradlew bundleRelease`; enrol in Play App Signing (upload key = your keystore).
4. Create a **Closed testing** track, add testers by email/Google Group, share the opt-in link.
5. Complete the Data safety form, privacy policy, and content rating.
