# Background Location Update Implementation

## Problem Statement
The application previously had the following issues:
1. **Slow startup**: App waited for GPS update to complete before showing data
2. **Fallback to Jerusalem on GPS failure**: If GPS failed or was rate-limited, it always fell back to Jerusalem instead of using cached data
3. **Lost cached data**: Even when valid cached location/zmanim data existed, it was immediately overwritten with Jerusalem fallback

## Solution

### Changes Made

#### 1. MainViewModel.kt
- **Removed immediate Jerusalem fallback**: The `refreshLocation()` method no longer immediately calls `updateForGeo(fallbackGeo())` at the start, which was overwriting cached data
- **Low-priority GPS requests**: Changed GPS priority from `PRIORITY_BALANCED_POWER_ACCURACY` to `PRIORITY_LOW_POWER` to make location refresh truly background/non-blocking
- **Smart fallback logic**: Only falls back to Jerusalem if:
  - GPS fails AND no cached location exists
  - Otherwise, keeps existing cached data and shows an error message
- **Location change detection**: Added `shouldUpdateLocation()` method that:
  - Uses Haversine formula to calculate distance between old and new location
  - Only updates if location changed by more than 100 meters
  - Prevents unnecessary recalculations when user hasn't moved
- **Cached data loading**: Added `cachedDataLoaded` flag to track when cached data is loaded
- **Improved error handling**: Better error messages distinguish between "no location available" and "using cached data"

#### 2. MainActivity.kt
- **Async location refresh**: Added `lifecycleScope.launch` with 500ms delay before calling `refreshLocation()`
- **Non-blocking startup**: UI shows immediately with cached data, GPS refresh happens in background
- **Added required imports**: Added `kotlinx.coroutines.delay` and `kotlinx.coroutines.launch`

### Behavior Flow

#### On App Launch (with cached data):
1. App starts, ViewModel `init` block loads cached state from DataStore
2. UI shows immediately with cached data (zmanim, location, etc.)
3. After 500ms delay, background GPS check starts
4. If location changed >100m, update silently in background
5. If location unchanged, just clear the refreshing state
6. If GPS fails, keep showing cached data with error indicator

#### On App Launch (no cached data):
1. App starts, ViewModel finds no cached state
2. UI shows empty/default state
3. After 500ms delay, GPS check starts
4. If GPS succeeds, update with new location
5. If GPS fails, fall back to Jerusalem (since no cached data exists)

#### On GPS Failure with Cached Data:
- Keeps showing cached data
- Shows error: "Unable to update location, using cached data"
- User can still see their zmanim from last successful update

### Benefits
1. **Fast startup**: App shows data immediately without waiting for GPS
2. **Reliable data**: Never loses cached data due to temporary GPS issues
3. **Battery efficient**: Uses low-power GPS mode for background refresh
4. **Reduced API calls**: Only recalculates when location actually changed (>100m)
5. **Better UX**: Users always see something useful, not a loading screen

### Configuration
- **Distance threshold**: 100 meters (can be adjusted in `shouldUpdateLocation()`)
- **GPS priority**: `Priority.PRIORITY_LOW_POWER` (can be changed if needed)
- **Startup delay**: 500ms (gives UI time to render before GPS starts)

### Testing Considerations
- Test with airplane mode to verify cached data persistence
- Test location changes to verify 100m threshold works
- Test first launch (no cache) to verify Jerusalem fallback
- Test rapid app launches to verify no race conditions

