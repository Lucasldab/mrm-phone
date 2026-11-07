# mrm-phone

Android reader for the shared [mrm](https://github.com/Lucasldab/mrm) library.
Talks only to `mrm serve` on your server, for example over a private network
like a tailnet; marking a chapter read here is the same DB write as mrm's TUI.

- Library grid, series detail (status, read/unread on long-press, refresh),
  vertical webtoon reader (pinch / double-tap zoom, keeps the screen on),
  search + add, settings.
- Read rule: a chapter is marked read when its last page has been on screen
  (loaded). Leaving early marks nothing.
- Offline: read marks are queued and sent by WorkManager when the network
  returns; the next 2 unread chapters are prefetched into the app cache
  (500 MB cap by default, read chapters evicted first).
- Deep links: `mrmphone://series/{id}`, `mrmphone://library` (ntfy taps).
- Cleartext HTTP is allowed only for the one server host named in
  `app/src/main/res/xml/network_security_config.xml`.

Package `io.lldab.mrmphone`. Look and build setup follow nocturne's phone app.

## Build

    ./gradlew :app:testDebugUnitTest
    ./gradlew :app:assembleDebug

Release signing reads `MRM_PHONE_KEYSTORE` (path to the `.jks`),
`MRM_PHONE_KEYSTORE_PASSWORD`, `MRM_PHONE_KEY_ALIAS` and
`MRM_PHONE_KEY_PASSWORD` (default: the store password):

    MRM_PHONE_KEYSTORE=/path/to/release.jks \
    MRM_PHONE_KEYSTORE_PASSWORD="$(cat /path/to/keystore-password)" \
      ./gradlew :app:assembleRelease

Releases: push a `v*` tag; `.github/workflows/release.yml` builds the signed
APK (secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`) and attaches it.

## Setup on the phone

Settings → server URL (the address of `mrm serve`, port 8102 by default) and
the token from the server's `~/.config/mrm/serve_token`, then "test connection".
