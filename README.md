# Xweather MapsGL Android (WebView)

Add [Xweather](https://www.xweather.com/) [MapsGL](https://www.xweather.com/products/mapsgl)
— the same vector-rendered, GPU-powered weather engine Xweather ships for
iOS/web (radar, satellite, wind particles, forecast animation, click-to-query,
legends) — to an Android app.

MapsGL's rendering engine is closed-source and only published as compiled
binaries for iOS/Android-via-OpenGL; there's no way to reimplement it
natively from scratch. Xweather does publish it as a real JavaScript SDK,
though, so this module embeds that JS SDK (MapLibre GL JS +
`aerisweather.mapsgl.js`) inside a `WebView`, bridged to Kotlin — full MapsGL
feature parity with iOS/web, no GPU rendering code of our own.

## Requirements

- Android `minSdk 23`+
- An Xweather account with an API `client_id` / `client_secret` — sign up at
  [xweather.com](https://www.xweather.com/)
- Your app can host a `WebView` (internet access; the module's manifest
  declares `INTERNET` so consuming apps don't need to add it themselves)

## Installation

Add the JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.modemlooper:xweather-webview:<tag>")
}
```

Replace `<tag>` with the [latest release tag](https://github.com/modemlooper/maplibre-xweather-sdk/releases).

## Usage

### 1. Add a `WebView` to your layout

```xml
<WebView
    android:id="@+id/webMapView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 2. Configure your credentials and attach the controller

```kotlin
val config = XweatherWebConfig(
    clientId = "YOUR_CLIENT_ID",
    clientSecret = "YOUR_CLIENT_SECRET",
)
val xweatherWeb = XweatherWebMapController(findViewById(R.id.webMapView), config)
```

Keep credentials out of source control — load them from `BuildConfig`,
`local.properties`, or your own secrets management. The SDK never stores or
bundles credentials itself.

`XweatherWebMapController` owns the `WebView`'s settings, loads the bundled
map page (MapLibre GL JS + the real MapsGL JS SDK), and drives it via a JS
bridge — you never write JS or touch `evaluateJavascript` yourself.

### 3. Add layers once the controller has loaded

```kotlin
xweatherWeb.onLoad = {
    xweatherWeb.addLayer("radar")
}
```

Calls made before `onLoad` fires are reported via `onError` instead of
applied, so always gate `addLayer`/`removeLayer`/`setOpacity` behind it.

```kotlin
xweatherWeb.addLayer("satellite-geocolor", opacity = 0.8f)
xweatherWeb.setOpacity("satellite-geocolor", 0.5f)
xweatherWeb.removeLayer("radar")
xweatherWeb.hasLayer("radar") { exists -> /* ... */ }
```

Layer codes match Xweather's [Weather Layers](https://www.xweather.com/docs/mapsgl/weather-layers)
reference (e.g. `radar`, `satellite-geocolor`, `wind-particles`, `alerts`).

### 4. Animate layers (shared timeline)

`XweatherWebMapController.timeline` forwards to the JS SDK's own
`controller.timeline`, which natively animates every added weather layer in
lockstep — one `play()`/`stop()`/`goTo()` controls all of them together:

```kotlin
val timeline = xweatherWeb.timeline
timeline.play()
```

Call `.stop()` to pause on the current frame, or `.goTo(fraction)` to scrub
to an arbitrary 0f..1f position across the configured range.

```kotlin
timeline.onLoadStart = { spinner.isVisible = true }
timeline.onLoadComplete = { spinner.isVisible = false }
timeline.onAdvance = { /* timeline.position moved */ }
timeline.onRangeChange = { /* timeline.startHour/endHour/nowFraction known */ }
```

### Map clicks / errors

```kotlin
xweatherWeb.onMapClick = { lat, lon, featureJson -> /* ... */ }
xweatherWeb.onError = { message -> Log.e("MapsGL", message) }
```

`onError` also surfaces JS-side console errors and unhandled promise
rejections from the SDK bundle, not just thrown exceptions — check it (or
`chrome://inspect`, enabled automatically for debuggable builds) first when
something isn't rendering.

## Sample app

The `sample-app` module in this repo (`WebMapActivity`) is a minimal
end-to-end consumer: a full-screen `WebView` map with a `TimelineController`
(play/pause + scrubber ruler) docked on top. To run it:

1. Add your Xweather credentials to the root `local.properties` (gitignored,
   never committed):
   ```properties
   xweather.clientId=YOUR_CLIENT_ID
   xweather.clientSecret=YOUR_CLIENT_SECRET
   ```
   Alternatively, set the `XWEATHER_CLIENT_ID` / `XWEATHER_CLIENT_SECRET`
   environment variables. The sample app's `build.gradle.kts` injects
   whichever is set into `R.string.xweather_client_id`/`xweather_client_secret`
   at build time via `resValue`.
2. `./gradlew :sample-app:installDebug`
