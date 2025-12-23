## Plan: Wear Zmanim Experience

Wear OS app centers on a shared `ZmanimRepository` that bridges Compass last-known city location, KosherKotlin zmanim calculations, and device-only DataStore-backed user preferences so the watch face, tile, and complication all render consistent, user-filtered data. Compose-powered UI shows current location, handles manual refresh via overscroll/tap, supports rotary scrolling, and keeps tiles/complications synchronized as zmanim advance.

### Steps
1. Expand `app/build.gradle.kts` to add Compass, Horologist utilities (navigation, tile, complication helpers), DataStore, and KosherKotlin dependencies plus configure Kotlin/Compose compiler flags for Wear rotary input.
2. Implement data layer (`app/src/main/java/.../data/`) with Compass-powered `LocationDataSource` (last-known, city-level accuracy, persisted locally), KosherKotlin-backed `ZmanimCalculator`, `UserPreferencesDataStore` (device-only), and a coroutine-based `ZmanimRepository` exposing flows for current city, filtered zmanim, and the next upcoming user-enabled zman.
3. Add domain models/use cases (`domain/`) describing `Zman`, location freshness metadata, and operations like `ObserveZmanim`, `ObserveNextZman`, plus commands to request a fresh location when UI signals overscroll/tap, ensuring repository caches timestamps to flag stale positions and expose reminders for UI.
4. Build presentation layer in `MainActivity`, `MainViewModel`, and composables (`ui/`) to render location header (tap-to-refresh + overscroll gesture), rotary-scrollable list of preferred zmanim via `ScalingLazyColumn`, edit screen for toggling visibility, and state handling for stale location banners plus Horologist scroll + scaling layout support.
5. Refactor `MainTileService` and `MainComplicationService` to consume repository flows so they only display user-selected zmanim, automatically advance to the next zman after one passes, and schedule updates via Horologist helpers/`ComplicationRequestUpdateRequester` using repository-provided next-event timestamps.

### Further Considerations
1. Location refresh feedback: render a spinner inline where the location normally appears whenever overscroll or tap triggers a fetch, hiding it as soon as Compass reports success/failure.
2. No timestamp watermark: rely on users to notice inaccurate data rather than surfacing last-fix times, keeping the header visually clean.
3. Refresh failure UX: when Compass cannot update (e.g., airplane mode), show a toast explaining that the app is falling back to the previous location while leaving the last-known data visible.
