# Loop

An Android GIF search app using the GIPHY API.

Kotlin · Jetpack Compose · MVVM · Hilt · Retrofit · Coroutines · Coil

## Run

Open in Android Studio with Android SDK 37. Gradle provisions the configured JDK 25. Minimum device version: Android 10.

Create a [GIPHY API key](https://developers.giphy.com/dashboard/) and add it to your `local.properties`:

```local.properties
GIPHY_API_KEY=your_key_here
```

Alternatively, set the `GIPHY_API_KEY` environment variable. Rebuild after changing the key, then run the `app` configuration.

The build fails with a setup message if the key is missing or blank. `API_BASE_URL` in `local.properties` optionally overrides `https://api.giphy.com/v1/`.

## Features

- Debounced search, trending GIFs, and category shortcuts.
- Offline indication follows validated internet connectivity, including after returning from the background. Category failures can be retried without reloading the GIF feed.
- Failed visible GIF images retry once when connectivity returns; successful images and the grid are preserved. Manual image retry remains available if recovery fails.
- Animated masonry grid with pagination, loading states, and retry. GIF decoding follows display size; off-screen and background animations pause.
- GIF details with a wrapping title, sharing, and link copying.
- Up to six similar GIFs found through title-based search, excluding the current GIF and duplicates. Relevance depends on GIPHY's search results; loading, empty, and retry states are included.
- Opening a similar GIF replaces the current details screen. Back returns to search with the query and scroll position preserved.
- Light/dark themes, portrait/landscape support, and Compose previews.
- While typing in landscape, the search field and category chips stay available and the GIF grid is hidden until the keyboard closes.

## Structure

`app` hosts navigation and API configuration. Its `AppConfigModule` provides the API key and base URL from the app's generated `BuildConfig`. `core:data` provides OkHttp, Retrofit, the GIPHY API service, and repository bindings. `feature:search` and `feature:details` contain screens and ViewModels. `core` holds data, domain, navigation, and shared UI. Screen-specific composables stay in their screen files.

HTTP logging is enabled only in debug builds, with API keys redacted. Release builds use R8 minification and resource shrinking.

App R8 rules retain crash-retracing information; `core:data` supplies Gson DTO reflection rules. Retrofit includes its own consumer rules. The release APK is unsigned until a signing configuration is supplied locally.

The root `.gitignore` excludes build outputs, local SDK/API-key configuration, signing credentials, IDE files, caches, and local design assets. Keep the Gradle wrapper, source and test files, build configuration, and app resources in the repository.

## Checks and release

```sh
./gradlew detekt testDebugUnitTest lintDebug
./gradlew :app:connectedDebugAndroidTest :feature:search:connectedDebugAndroidTest :feature:details:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```
