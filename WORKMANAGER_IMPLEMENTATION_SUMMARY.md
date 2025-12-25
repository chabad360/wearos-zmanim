# WorkManager Background Updates Implementation

## Overview
Implemented hourly background updates for zmanim data using Android's WorkManager. The tile no longer requires opening the app to get updated zmanim data.

## What Was Implemented

### 1. WorkManager Integration
- **ZmanimUpdateWorker**: A background worker that runs every hour
- **ZmanimApplication**: Application class that schedules the periodic work on app startup
- **Dependencies**: Added WorkManager and coroutines-play-services libraries

### 2. Update Logic
The worker performs the following checks every hour:
1. **Stale data check**: If cached data is from a different day, recalculates zmanim for today
2. **Location check**: If device has location permission, checks if location changed >100m
3. **Conditional update**: Only updates DataStore and tile if:
   - Data is stale (from yesterday), OR
   - Location changed significantly (>100m)

### 3. Code Organization
Created `ZmanimCalculator` utility class to avoid code duplication:
- Shared by MainViewModel, ZmanimUpdateWorker, and potentially the tile
- Single source of truth for zmanim calculation logic
- Easier to maintain and update

## Files Created

### ZmanimUpdateWorker.kt
```kotlin
Location: app/src/main/java/me/chabad360/zmanim/wearos/workers/ZmanimUpdateWorker.kt
```
- Runs as a periodic background task every hour
- Checks location permissions before attempting GPS
- Calculates distance between old and new location
- Recalculates zmanim when needed
- Persists updated data to DataStore
- Requests tile update after successful update

### ZmanimApplication.kt
```kotlin
Location: app/src/main/java/me/chabad360/zmanim/wearos/ZmanimApplication.kt
```
- Registers with AndroidManifest as application class
- Schedules periodic WorkManager task on app startup
- Uses constraints: battery not low
- Run interval: 1 hour with 15-minute flex period

### ZmanimCalculator.kt
```kotlin
Location: app/src/main/java/me/chabad360/zmanim/wearos/util/ZmanimCalculator.kt
```
- Utility class for calculating all zmanim
- Used by MainViewModel and ZmanimUpdateWorker
- Eliminates code duplication

## Files Modified

### AndroidManifest.xml
- Added `android:name=".ZmanimApplication"` to `<application>` tag

### build.gradle.kts (app)
- Added `implementation(libs.work.runtime.ktx)`
- Added `implementation(libs.kotlinx.coroutines.play.services)`

### libs.versions.toml
- Added `work = "2.10.0"`
- Added `coroutines = "1.10.1"`
- Added work-runtime-ktx library
- Added kotlinx-coroutines-play-services library

### MainViewModel.kt
- Updated to use shared `ZmanimCalculator.computeZmanim()`
- Removed duplicate zmanim calculation code

## How It Works

### On App Install/Launch
1. `ZmanimApplication.onCreate()` runs
2. Schedules periodic WorkManager task (if not already scheduled)
3. Worker will run every hour going forward

### Every Hour (Background)
1. WorkManager triggers `ZmanimUpdateWorker.doWork()`
2. Worker loads cached state from DataStore
3. Checks if data is stale (different day)
4. If has location permission, gets current location
5. Calculates distance from cached location
6. If stale OR location changed >100m:
   - Recalculates all zmanim
   - Saves to DataStore  
   - Requests tile update
7. Otherwise, does nothing (saves battery)

### Tile Updates
The tile automatically refreshes when:
- User glances at watch (normal tile refresh)
- Worker completes update (via `MainTileService.requestUpdate()`)
- App updates data (via `MainTileService.requestUpdate()`)

## Benefits

### 1. **No App Required**
- Tile updates automatically in background
- User never needs to open the app for fresh data
- Works even if app never opened

### 2. **Battery Efficient**
- Only runs when battery is not low
- Only updates if data actually changed
- Uses low-power location API
- 100m threshold prevents excessive recalculation

### 3. **Always Current**
- Zmanim recalculated daily automatically
- Location updates if user moves >100m
- Tile shows accurate data without user action

### 4. **Reliable**
- Survives device reboot (WorkManager persists tasks)
- Retries on failure
- Doesn't require exact timing

## Configuration

### Update Frequency
```kotlin
// In ZmanimApplication.kt
PeriodicWorkRequestBuilder<ZmanimUpdateWorker>(
    1, TimeUnit.HOURS,      // Run every hour
    15, TimeUnit.MINUTES    // Flex period (can run 45-60 min after last run)
)
```

### Location Threshold
```kotlin
// In ZmanimUpdateWorker.kt
if (distance > 100) {  // 100 meters
    // Update location
}
```

### Battery Constraint
```kotlin
// In ZmanimApplication.kt
Constraints.Builder()
    .setRequiresBatteryNotLow(true)  // Only run when battery OK
    .build()
```

## Testing

### Verify Worker is Scheduled
```kotlin
val workInfo = WorkManager.getInstance(context)
    .getWorkInfosForUniqueWork(ZmanimUpdateWorker.WORK_NAME)
    .get()
```

### Trigger Manual Update (Debug)
```kotlin
val request = OneTimeWorkRequestBuilder<ZmanimUpdateWorker>().build()
WorkManager.getInstance(context).enqueue(request)
```

### Check Logs
```
adb logcat | grep ZmanimUpdateWorker
```

Expected log output:
- `ZmanimUpdateWorker started`
- `Location distance: Xm`
- `Updating zmanim (stale: true/false, locationChanged: true/false)`
- `Zmanim updated successfully`

## Future Enhancements

### Potential Improvements
1. **Geofencing**: Only check location when entering/exiting region
2. **Smart scheduling**: Update more frequently near sunrise/sunset
3. **Network-based location**: Fallback if GPS unavailable
4. **User preferences**: Allow custom update interval
5. **Notification**: Alert on significant zman (e.g., upcoming mincha)

## Troubleshooting

### Worker Not Running
- Check battery saver mode isn't enabled
- Verify WorkManager is initialized
- Check for doze mode restrictions

### Tile Not Updating
- Verify `MainTileService.requestUpdate()` is called
- Check tile is added to watch face
- Ensure DataStore is being written

### Location Not Updating
- Verify location permissions granted
- Check GPS is enabled
- Ensure play-services-location dependency correct

## Code Locations

```
app/src/main/java/me/chabad360/zmanim/wearos/
├── ZmanimApplication.kt                    (NEW - App initialization)
├── workers/
│   └── ZmanimUpdateWorker.kt              (NEW - Background update logic)
├── util/
│   └── ZmanimCalculator.kt                (NEW - Shared zmanim calculation)
├── presentation/
│   └── MainViewModel.kt                    (MODIFIED - Uses shared calculator)
└── tile/
    └── MainTileService.kt                  (Unchanged - Auto-updates from DataStore)
```

## Summary

This implementation provides a robust, battery-efficient solution for keeping zmanim data current without user intervention. The tile will always display accurate times, automatically updating when the day changes or the user's location changes significantly.

Key design decisions:
- **Hourly updates**: Balances freshness with battery life
- **100m threshold**: Prevents unnecessary updates for small movements
- **Shared calculator**: Maintains consistency across app/worker/tile
- **WorkManager**: Reliable, system-optimized background execution
- **Constraints**: Respects device battery state

