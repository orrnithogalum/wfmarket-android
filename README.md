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
