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

### 5. Animate a layer (frame loop)

Xweather's raster tiles have no built-in timeline, so `XweatherAnimator`
pre-loads one source/layer per time offset and toggles opacity between them:

```kotlin
xweatherMap.animator(XweatherLayer.Radar.Standard)
    .loadFrames(count = 6, intervalMinutes = 10)
    .play()
```

Call `.stop()` to pause on the current frame, or pass explicit offset
strings (e.g. `"-30m"`, `"current"`) via `loadFrames(offsets: List<String>)`
for full control.

`loadFrames` only *starts* each frame's tile download — it doesn't wait for
them to finish. Pass `onFramesReady` to defer enabling playback (e.g.
disabling a play button, or hiding a loading spinner) until every frame
actually has pixels, so you don't play through frames that pop in mid-loop:

```kotlin
xweatherMap.animator(XweatherLayer.Radar.Standard)
    .loadFrames(count = 6, intervalMinutes = 10, onFramesReady = { playButton.isEnabled = true })
```

### Attribution

`XweatherMapController` sets the required attribution string
(`XweatherAttribution.HTML` — "Powered by Vaisala Xweather", linked to
xweather.com) on every source it creates, so it shows up automatically
wherever your map surfaces source attribution. You don't need to add this
yourself.

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
