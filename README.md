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

## Icons

The sources are in `app/src/main/art`. Android launcher icons must be vector drawables rather than
SVG files, so `ic_launcher.svg` is mirrored by the `ic_launcher_foreground` and `ic_splash` drawables.
`ic_launcher_monochrome` and `ic_stat_notification` use its hood and eye without the transparent
layers, since those icons are single-color. The mark is a simplified redraw of the warframe.market
logo, which belongs to warframe.market.

The polarity symbols (`polarity-*.svg`, drawn by `ic_polarity_*`) come from the Warframe wiki's
`*_Pol(xBlack).svg` files. The symbols are Digital Extremes' art.

## Background notifications

The site's push channel is Web Push, and Android can only wake a closed app through Firebase Cloud
Messaging, so using it would take a relay server between the two. The app doesn't have one. So by
default `SyncWorker` checks the inbox about every 5 minutes with the app closed, which needs no
ongoing notification. Android may stretch that gap unless the app has unrestricted battery use.

The Instant delivery setting adds `RealtimeService`, which keeps the websocket open in a foreground
service. Messages and auction bids then arrive right away, but Android requires a silent
notification while the app is closed. Without it, auction bid alerts only arrive while the app is open.

## Status with the app closed

The site shows you offline as soon as your presence connection closes, and gives a new connection a
status of its own (invisible, when tried) instead of the one you chose. So the app remembers the
status you pick, and the time its hold runs out, in `HeldStatus`. Each time the presence socket signs
in, `AccountRepository` waits out the site's opening report and sets the held status again. A status
changed elsewhere, such as on the website, becomes the held one.

With "Stay online when the app is closed" on (the default), an Online or In game hold also runs
`RealtimeService` while the app is closed, so the status stays visible until the hold ends. The
service starts as the app leaves the screen and stops when it returns. With unrestricted battery
use it runs as a plain background service, which Android allows for exempt apps, so nothing shows.
Without the exemption Android stops background services within about a minute, so it runs in the
foreground with a notification that shows the status and has buttons to switch it or go offline
(`StatusActionReceiver`). Instant delivery works the same way.

## Auction alerts

The site has no saved-search alerts, so `AuctionAlerts` keeps them in `auction_alerts.json` and
`SyncWorker` runs each search on every check. Results the alert has already seen are remembered, so
only new listings notify. The first check after saving takes the baseline silently. The search API
returns at most 100 results, so a broad search sorted by price only sees the 100 cheapest. Seller
status isn't a site filter either; `AuctionSearch.sellerStatuses` is applied to the results in the
app, and invisible sellers count as offline. Nor is price: `priceMin` and `priceMax` are checked
against what an auction goes for now (its buyout, or its top bid, else its start). With a price
range, a listing repriced into it notifies too, not only new listings.

Followed auctions are polled the same way and also update live over the socket while it's open.
A removed auction answers `400` with `{"error": {"auction": ["app.form.not_exist"]}}`.
