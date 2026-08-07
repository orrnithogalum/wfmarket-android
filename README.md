# WF Market

A native Android client for [warframe.market](https://warframe.market), built with Jetpack Compose
and Material 3 dynamic color. Requires Android 12 or newer. Unofficial, and not affiliated with the
site or Digital Extremes.

## Build and install

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:installRelease   # or :app:installDebug
```

## Layout

| Module | Contents | Depends on Android? |
| --- | --- | --- |
| `api` | HTTP client (`WfmApi`), websockets (`WfmSockets`), models, error translation | No |
| `app/data` | Repositories, settings, encrypted session, on-disk catalogue cache | Yes |
| `app/notifications` | Background service, worker, notification actions | Yes |
| `app/ui` | Compose screens, charts, theme, navigation | Yes |

Screens talk only to repositories. All knowledge of the site's API lives in `api`.

## Testing

`./gradlew :api:test` runs contract tests against the live public API, including both websockets.
Set `WFM_EMAIL` and `WFM_PASSWORD` to also run the signed-in tests. They sign in once, and anything
they create (one hidden order, one hidden auction) is removed again.
Debug builds add a shell-only hook for testing notifications; see `DebugNotificationReceiver`.

## The API, as used here

The site is mid-migration, so the client uses both generations:

- **v2** (`Authorization: Bearer`): catalogue, orders, profiles.
- **v1** (`Authorization: JWT`): sign-in, auctions, chat, statistics, Ducanator, account settings.
- **Sign-in**: `POST /v1/auth/signin` with `auth_type: header` returns the token in a response header.
- **Sockets**: `wss://warframe.market/socket` carries chat and bids, authenticated by the JWT cookie.
  `wss://ws.warframe.market/socket` (subprotocol `wfm`) carries presence.
