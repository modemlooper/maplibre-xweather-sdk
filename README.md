# Xweather MapLibre SDK

Add [Xweather](https://www.xweather.com/) weather radar, satellite, and other
raster map layers to a [MapLibre Native](https://github.com/maplibre/maplibre-native)
Android map — without touching raw `RasterSource`/`RasterLayer`/tile-URL code
yourself.

The SDK wraps Xweather's [Raster Maps API](https://www.xweather.com/docs/maps/getting-started/map-tiles)
(a standard XYZ tile scheme) and exposes typed layer constants, opacity/order
control, a simple frame-swap animator, and required attribution handling.

## Requirements

- Android `minSdk 23`+
- An Xweather account with an API `client_id` / `client_secret` — sign up at
  [xweather.com](https://www.xweather.com/)
- Your app already depends on MapLibre Android and has a working `MapView`

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
    implementation("com.github.modemlooper:maplibre-xweather-sdk:<tag>")

    // MapLibre Android itself — the SDK depends on this transitively, but
    // pin it explicitly if you need a specific version.
    implementation("org.maplibre.gl:android-sdk:13.4.1")
}
```

Replace `<tag>` with the [latest release tag](https://github.com/modemlooper/maplibre-xweather-sdk/releases).

## Usage

### 1. Configure your credentials

```kotlin
val config = XweatherConfig(
    clientId = "YOUR_CLIENT_ID",
    clientSecret = "YOUR_CLIENT_SECRET",
)
```

Keep credentials out of source control — load them from `BuildConfig`,
`local.properties`, or your own secrets management. The SDK never stores or
bundles credentials itself.

### 2. Attach the controller to your map's style

```kotlin
mapLibreMap.getStyle { style ->
    val xweatherMap = XweatherMapController(mapView, style, config)

    xweatherMap.addLayer(XweatherLayer.Radar.Standard)
}
```

`XweatherMapController` wraps a MapLibre `Style` you already own — it adds a
raster `Source`/`Layer` pair per weather layer and injects the required
Xweather attribution automatically, so you don't need to add it yourself. It
also takes your `MapView` so `animator()` (below) can tell when a layer's
tiles have actually finished downloading, not just when they were requested.

### 3. Add, remove, and reorder layers

```kotlin
// Add a layer with custom opacity
xweatherMap.addLayer(XweatherLayer.Satellite.GeoColor, opacity = 0.8f)

// Adjust opacity later
xweatherMap.setOpacity(XweatherLayer.Satellite.GeoColor, 0.5f)

// Remove a layer
xweatherMap.removeLayer(XweatherLayer.Radar.Standard)

// Re-stack layers bottom-to-top
xweatherMap.setLayerOrder(
    listOf(XweatherLayer.Satellite.GeoColor, XweatherLayer.Radar.Standard),
)
```

### 4. Available layers (v0.1)

`XweatherLayer` is a sealed class grouped by category. Currently modeled:

| Category | Constants |
|---|---|
| Radar | `Radar.Standard`, `Radar.Global`, `Radar.Forecast` |
| Satellite | `Satellite.Infrared`, `Satellite.GeoColor`, `Satellite.Visible`, `Satellite.InfraredColor`, `Satellite.WaterVapor`, `Satellite.Forecast` |
| Temperature | `Temperature.Surface`, `Temperature.SurfaceText`, `Temperature.Forecast`, `Temperature.ForecastHigh`, `Temperature.ForecastLow` |
| Precipitation | `Precipitation.Accumulated`, `Precipitation.ForecastHourly`, `Precipitation.ForecastAccumulated` |
| Wind | `Wind.Speed`, `Wind.Gusts`, `Wind.Direction` |
| Alerts | `Alerts` |
| Base maps | `BaseMap.Flat`, `BaseMap.FlatDark`, `BaseMap.Terrain`, `BaseMap.BlueMarble` |
| Admin/boundaries | `Admin.Boundaries`, `Admin.Cities`, `Admin.States`, `Admin.Counties` |

Xweather's full catalog has 100+ layer codes; for anything not yet modeled as
a typed constant, use the escape hatch:

```kotlin
xweatherMap.addLayer(XweatherLayer.Custom("some-layer-code"))
```

### Adding/removing/toggling layers by name

If the layer to show is only known at runtime (e.g. driven by app config or
user input) rather than a compile-time constant, `addLayer`/`removeLayer`/
`setOpacity` also accept a raw layer code `String` directly — no need to wrap
it in `XweatherLayer.Custom` yourself:

```kotlin
xweatherMap.addLayer("radar", opacity = 0.8f)

// Turn it off later
xweatherMap.removeLayer("radar")

// Check whether it's currently on
xweatherMap.hasLayer("radar")
```

### 5. Animate layers (shared timeline)

Xweather's raster tiles have no built-in timeline, so `XweatherMapController.timeline`
pre-loads one source/layer per time offset per registered layer and toggles
opacity between them. This is a single shared clock — every layer registered
with it animates together, in sync, driven by one `play()`/`stop()`/`goTo()`
(mirroring `mapController.timeline` in Xweather's MapsGL Apple SDK), rather
than each layer running its own independent loop:

```kotlin
val timeline = xweatherMap.timeline
timeline.configure(frameCount = 12, frameIntervalMinutes = 15)
timeline.addLayer(XweatherLayer.Radar.Standard)
timeline.play()
```

Call `.stop()` to pause on the current frame, or `.goTo(fraction)` to scrub
to an arbitrary 0f..1f position across the configured range.

Registering a layer only *starts* its frames' tile downloads — it doesn't
wait for them to finish. Use `onLoadStart`/`onLoadComplete` to defer enabling
playback (e.g. disabling a play button, or showing/hiding a loading spinner)
until every registered layer's frames actually have pixels, so you don't
play through frames that pop in mid-loop:

```kotlin
timeline.onLoadStart = { spinner.isVisible = true }
timeline.onLoadComplete = {
    spinner.isVisible = false
    timeline.play()
}
timeline.addLayer(XweatherLayer.Radar.Standard)
```

Add more layers later (e.g. from a layer-toggle menu) with `timeline.addLayer(...)` /
`timeline.removeLayer(...)` — they join the same running clock instead of
starting their own.

### Attribution

`XweatherMapController` sets the required attribution string
(`XweatherAttribution.HTML` — "Powered by Vaisala Xweather", linked to
xweather.com) on every source it creates, so it shows up automatically
wherever your map surfaces source attribution. You don't need to add this
yourself.

## WebView-based MapsGL integration (`xweather-webview`)

The module above wraps Xweather's **Raster Maps API** — static XYZ tile
imagery, not Xweather's vector-rendered **MapsGL** engine (the same engine
that powers Xweather's iOS/web SDKs: GPU-rendered weather layers,
particle-animated wind, click-to-query, legends). MapsGL's actual rendering
engine is closed-source, so it can't be ported to Android directly — but
Xweather ships it as a JavaScript SDK, and `xweather-webview` embeds that
real JS SDK (MapLibre GL JS + `aerisweather.mapsgl.js`) inside a `WebView`,
bridged to Kotlin. This gets you MapsGL feature parity with iOS/web without
reimplementing any GPU rendering code.

```kotlin
val config = XweatherWebConfig(
    clientId = "YOUR_CLIENT_ID",
    clientSecret = "YOUR_CLIENT_SECRET",
)
val xweatherWeb = XweatherWebMapController(webView, config)

xweatherWeb.onLoad = {
    xweatherWeb.addLayer("radar")
}

xweatherWeb.timeline.play()
```

`XweatherWebMapController`/`XweatherWebTimeline` mirror the shape of
`XweatherMapController`/`XweatherTimeline` above (`addLayer`/`removeLayer`/
`setOpacity`/`timeline.play()`/`stop()`/`goTo()`), but every call is
forwarded into the JS SDK via `WebView.evaluateJavascript`, and JS-side
events (load, load-start/complete, timeline advance, map clicks) come back
through a `@JavascriptInterface` bridge. Calls made before `onLoad` fires are
reported via `onError` rather than applied — wait for `onLoad` before adding
layers.

The `sample-app` module's "MapsGL Web Demo" button (`WebMapActivity`) is a
minimal working example of this module.

## Sample app

The `sample-app` module in this repo is a minimal end-to-end consumer of the
SDK. To run it:

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
