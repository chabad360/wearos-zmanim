# Zmanim Persistence Implementation Summary

## Overview
Successfully implemented JSON DataStore persistence for the Zmanim app, adapted to work with the new lazy computation architecture where `ZmanDisplay` is already serializable.

## What Was Changed

### 1. Added Kotlin Serialization Plugin
- Updated `gradle/libs.versions.toml` to include `kotlin-serialization` plugin
- Updated `app/build.gradle.kts` to apply the serialization plugin

### 2. Created Data Models (`presentation/data/PersistentModels.kt`)
- **`SerializableLocation`**: Stores location data (name, lat, lon, altitude, timezone)
- **`PersistentState`**: Root state object with versioning support
  - Stores location, zmanDisplays list (already serializable), disabledIds, and lastComputedMillis
  - Version field enables future schema migrations

### 3. Created DataStore Manager (`presentation/data/DataStoreManager.kt`)
- **`PersistentStateSerializer`**: JSON serializer for PersistentState
- **`DataStoreManager`**: Manages all persistence operations
  - `stateFlow`: Flow of current persistent state
  - `saveState()`: Save complete app state
  - `updateDisabledIds()`: Update only disabled IDs (for toggle operations)
  - `isStateStale()`: Check if cached data is from a different day
  - Handles version compatibility and migration

### 4. Updated MainViewModel
- Added `DataStoreManager` instance
- **`init` block**: Loads cached state on startup and restores UI
- **`restoreFromCache()`**: Reconstructs UI state from persisted data
  - Recreates GeoLocation from coordinates
  - Regenerates ComplexZmanimCalendar and property map
  - Uses cached ZmanDisplay list directly (no conversion needed!)
- **`persistState()`**: Saves state after computing zmanim
- **`updateForGeo()`**: Now persists state after successful computation
- **`toggleZmanVisiblity()`**: Now persists disabled IDs changes

## Key Architecture Benefits

1. **Lazy Computation**: The `Map<String, () -> Zman.DateBased>` is regenerated from location on restore, property references remain fresh
2. **Direct Serialization**: `ZmanDisplay` is already `@Serializable`, no conversion needed
3. **Offline Support**: Full location data is stored, enabling offline recomputation
4. **Fast Startup**: Cached zmanim display immediately while checking staleness
5. **Daily Expiration**: Automatically detects when zmanim need recomputation (different day)
6. **Versioned Storage**: Migration-safe for future schema changes

## Data Flow

### On App Start
1. Load `PersistentState` from DataStore
2. Check if stale (different day)
3. Restore UI with cached `ZmanDisplay` list
4. Recreate `ComplexZmanimCalendar` from cached location
5. Generate property reference map
6. User sees instant cached data (marked stale if needed)

### On Location Refresh
1. Get new location from GPS
2. Compute fresh zmanim
3. Update UI state
4. Persist new state (location + zmanDisplays + timestamp)

### On Toggle Zman Visibility
1. Update disabledIds in UI state
2. Persist just the disabledIds (partial update)

## Files Modified/Created
- ✅ `gradle/libs.versions.toml` - Added serialization plugin
- ✅ `app/build.gradle.kts` - Applied serialization plugin  
- ✅ `presentation/data/PersistentModels.kt` - NEW
- ✅ `presentation/data/DataStoreManager.kt` - NEW
- ✅ `presentation/MainViewModel.kt` - Added persistence integration

## Testing Recommendations
1. Test first launch (no cache)
2. Test app restart (restore from cache)
3. Test day change detection
4. Test offline mode with cached location
5. Test toggle persistence across app restarts

