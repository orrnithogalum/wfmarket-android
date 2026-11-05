<div align="center">

<img src="docs/icon.png" alt="Warframe Market icon" width="96">

# Warframe Market

A native Android client for [warframe.market](https://warframe.market), built with Jetpack Compose
and Material 3 Expressive. Browse and post orders, trade rivens with per-stat grades, chat with
traders and keep your status up, all from your phone.

[![Latest release](https://img.shields.io/github/v/release/orrnithogalum/wfmarket-android?label=release&color=4666ab)](https://github.com/orrnithogalum/wfmarket-android/releases/latest)
[![CI](https://github.com/orrnithogalum/wfmarket-android/actions/workflows/ci.yml/badge.svg)](https://github.com/orrnithogalum/wfmarket-android/actions/workflows/ci.yml)
[![License: AGPL-3.0](https://img.shields.io/github/license/orrnithogalum/wfmarket-android?color=4666ab)](LICENSE)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)

</div>

<p align="center">
  <img src="docs/screenshots/market.png" alt="Market home" width="24%">
  <img src="docs/screenshots/statistics.png" alt="Item price statistics" width="24%">
  <img src="docs/screenshots/auctions.png" alt="Riven auctions with grades" width="24%">
  <img src="docs/screenshots/auction.png" alt="An auction with each stat's roll" width="24%">
</p>

> [!NOTE]
> Warframe Market is an unofficial client. It isn't affiliated with warframe.market or Digital Extremes.

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Download](#download)
- [Updates](#updates)
- [Architecture](#architecture)
- [Building](#building)
- [Releasing](#releasing)
- [Privacy](#privacy)
- [License](#license)

---

## Features

**Market**

- Search all 3,800+ tradeable items, or browse them by category
- Item pages with every order, filtered by whether the trader is in game, online or offline
- 48-hour and 90-day price history: candles, Donchian channel, median and moving averages
- Relic drop chances, set contents and ducat values
- Post and edit your own orders, mark them sold, or remove them

**Auctions**

- Rivens, lich weapons and sister weapons, with the site's search filters plus a price range and seller status
- A grade from S to F beside each riven stat, and a bar on the auction's page showing where in its range it rolled
- Hold a riven to see it at a glance, with *Find similar* for pricing it
- Warframe's own weekly riven trade figures for every weapon, kept week after week
- Saved searches that notify you of new listings, and followed auctions that notify you of new bids
- Create and edit auctions, with stats graded as you type

**Trading**

- Trade chat, with replies and *Mark as read* straight from the notification
- Swipe actions in the inbox, chosen per direction
- Set your status to In game, Online or Invisible for as long as you choose, and keep it while the app is closed
- The Ducanator: the best ducats for your platinum
- Player profiles with reviews, trade history and achievements

**The app**

- Material 3 Expressive, with your wallpaper's colors or its own pastel blue, and an AMOLED black option
- warframe.market links open in the app, and launcher shortcuts open your messages, search auctions, start one or set you In game

<p align="center">
  <img src="docs/screenshots/riven-prices.png" alt="Riven prices" width="24%">
  <img src="docs/screenshots/updates.png" alt="An update downloading in Settings" width="24%">
</p>

---

## Requirements

- Android 12 or newer
- A warframe.market account for anything beyond browsing. Signing in uses your site email and
  password. Accounts that sign in with Steam, Xbox or Discord need an email and password added on
  the website first.

---

## Download

Download `wfmarket-<version>.apk` from the [latest release](https://github.com/orrnithogalum/wfmarket-android/releases/latest)
and open it on your phone. Android asks once to allow installs from your browser or file manager.

Each release also has a `.sha256` file to check the download against:

```bash
sha256sum -c wfmarket-<version>.apk.sha256
```

> [!TIP]
> Google Play Protect may warn about an app it hasn't seen before, since Warframe Market isn't on
> Google Play. Every release is built from this repository's source by the
> [release workflow](.github/workflows/release.yml), and you can build it yourself instead.

---

## Updates

Warframe Market checks this repository for a new release when you open it (at most every few hours)
and about twice a day in the background. A new version downloads on its own and waits at the top of
Settings, with its release notes, until you tap **Install**. Checking and downloading are both
switches under **Settings › Updates**, which also has a button to check now.

Before installing, the app checks that the download matches the checksum GitHub records for it,
and that it's signed with the same key as the app you have. The first time, Android asks you to
allow Warframe Market to install apps; after that, updates may install without asking again.
Android closes the app to replace it, and a notification takes you back in.

---

## Architecture

```mermaid
flowchart LR
    subgraph app["app (Android)"]
        UI["Compose screens"] --> Repos["Repositories"]
        Repos --> Local[("DataStore, encrypted session,<br>catalogue cache")]
        Background["WorkManager checks,<br>foreground socket"] --> Repos
        Updater["Updater"]
    end
    subgraph api["api (plain Kotlin)"]
        Http["WfmApi: HTTP, v1 and v2"]
        Sockets["WfmSockets: chat, presence"]
    end
    Repos --> Http & Sockets
    Http & Sockets --> Site[("warframe.market")]
    Repos --> Warframe[("Warframe's public data")]
    Updater --> GitHub[("GitHub Releases")]
```

Screens talk only to repositories, and all knowledge of the site's API lives in `api`.

| Module | Contents |
| --- | --- |
| `api` | The warframe.market client: HTTP API, websockets, models and readable errors |
| `app/data` | Repositories, settings, the encrypted session and the on-disk catalogue cache |
| `app/notifications` | The background connection, the periodic check and notification actions |
| `app/update` | Updates from this repository's releases |
| `app/ui` | Compose screens, charts, theme, motion and navigation |

| | |
| --- | --- |
| Language | Kotlin 2.4, coroutines and Flow |
| UI | Jetpack Compose, Material 3 Expressive, Navigation Compose |
| Networking | OkHttp 5 (HTTP and websockets), kotlinx.serialization |
| Images | Coil 3 |
| Storage | DataStore, Android Keystore |
| Background | WorkManager, a foreground service |
| Testing | JUnit, contract tests against the live API |
| CI/CD | GitHub Actions, Dependabot |

How the app uses the site's two API generations, keeps your status with the app closed, grades
rivens and updates itself is written up in [docs/internals.md](docs/internals.md).

---

## Building

You need JDK 17 and the Android SDK.

```bash
git clone https://github.com/orrnithogalum/wfmarket-android.git
cd wfmarket-android
./gradlew :app:installDebug      # or :app:assembleDebug for just the APK
```

The debug build installs beside a release one, as *Warframe Market debug* with a green icon, so the
two can be compared. A release build without the release key is signed with your debug key instead.

```bash
./gradlew :api:test :app:testDebugUnitTest   # unit tests, no network needed
./gradlew :app:lintDebug                     # lint
./gradlew :api:test -Plive                   # plus the contract tests against the live site
```

Set `WFM_EMAIL` and `WFM_PASSWORD` to include the signed-in contract tests. They remove whatever
they create.

---

## Releasing

Development happens on `master`, and `main` follows the latest release.

1. Bump `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts) and commit.
2. Tag that commit and push the tag. The tag's message becomes the release notes. Without one,
   they're built from the [Conventional Commits](https://www.conventionalcommits.org) since the
   last release.

   ```bash
   git tag -a v0.1.0 -m "What changed, in Markdown"
   git push origin v0.1.0
   ```

3. The [release workflow](.github/workflows/release.yml) tests, builds, signs and verifies the APK,
   then attaches it to a **draft** release. Publishing the draft is what makes the app offer it.

Signing reads the keystore from four repository secrets, listed at the top of the workflow. To sign
release builds locally, put the same values in `~/.gradle/gradle.properties`:

```properties
wfmarket.signing.storeFile=/path/to/release.jks
wfmarket.signing.storePassword=…
wfmarket.signing.keyAlias=…
wfmarket.signing.keyPassword=…
```

Alongside releases, [CI](.github/workflows/ci.yml) lints, tests and builds every push and pull
request, a [weekly job](.github/workflows/contract.yml) runs the contract tests against the live
site to catch API changes early, and Dependabot proposes dependency updates monthly.

---

## Privacy

Warframe Market talks to warframe.market, to Warframe's public data for riven figures and the
current Prime Resurgence, and to GitHub for updates. There are no analytics, ads or crash
reporters. Your password goes only to warframe.market and isn't stored. The session token it
returns is encrypted with a key that stays in the Android Keystore.

---

## License

Warframe Market is free software, licensed under the [GNU Affero General Public License v3.0](LICENSE).

Warframe, its art and its item names belong to Digital Extremes. The app's icon is a simplified
redraw of the warframe.market logo, which belongs to warframe.market, and its polarity symbols are
Digital Extremes' art.
