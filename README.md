# mrm-phone

Android reader for the shared [mrm](https://github.com/Lucasldab/mrm) library.
Talks only to `mrm serve` on hearth over the tailnet; marking a chapter read
here is the same DB write as mrm's TUI.

- Library grid, series detail (status, read/unread on long-press, refresh),
  vertical webtoon reader (pinch / double-tap zoom, keeps the screen on),
  search + add, settings.
- Read rule: a chapter is marked read when its last page has been on screen
  (loaded). Leaving early marks nothing.
- Offline: read marks are queued and sent by WorkManager when the network
  returns; the next 2 unread chapters are prefetched into the app cache
  (500 MB cap by default, read chapters evicted first).
- Deep links: `mrmphone://series/{id}`, `mrmphone://library` (ntfy taps).
- Cleartext HTTP is allowed only for `hearth.tailda34b8.ts.net`.

Package `io.lldab.mrmphone`. Look and build setup follow nocturne's phone app.

## Build

    ./gradlew :app:testDebugUnitTest
    ./gradlew :app:assembleDebug

Release signing reads `MRM_PHONE_KEYSTORE` (default
`~/keystore/nocturne-release.jks`), `MRM_PHONE_KEYSTORE_PASSWORD`,
`MRM_PHONE_KEY_ALIAS` (default `nocturne`) and `MRM_PHONE_KEY_PASSWORD`
(default: the store password):

    MRM_PHONE_KEYSTORE_PASSWORD="$(cat ~/keystore/nocturne-keystore-password)" \
      ./gradlew :app:assembleRelease

Releases: push a `v*` tag; `.github/workflows/release.yml` builds the signed
APK (secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`) and attaches it.

## Setup on the phone

Settings → server URL (default `http://hearth.tailda34b8.ts.net:8102`) and the
token from hearth's `~/.config/mrm/serve_token`, then "test connection".
