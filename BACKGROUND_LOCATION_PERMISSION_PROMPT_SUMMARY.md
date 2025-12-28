# Background Location Permission Prompt Implementation

## Overview
This implementation adds a user-friendly prompt to request background location access after the user has granted foreground location permissions. This allows the app to automatically update the user's location throughout the day for accurate zmanim calculations.

## Changes Made

### 1. MainViewModel.kt
**Added:**
- `showBackgroundLocationPrompt` flag in `UiState` to track when to show the permission prompt
- `shouldRequestBackgroundLocation(context: Context): Boolean` - Checks if background permission should be requested (returns true if foreground is granted but background is not)
- `showBackgroundLocationPrompt()` - Shows the permission prompt dialog
- `dismissBackgroundLocationPrompt()` - Dismisses the permission prompt dialog

### 2. MainActivity.kt
**Added:**
- `backgroundLocationPermissionLauncher` - Activity result launcher for requesting background location permission
- `requestBackgroundLocationPermission()` - Public method to trigger the permission request
- Updated `permissionLauncher` callback to check for background location permission after foreground is granted
- Updated `ensureLocationPermission()` to check for background location permission after app startup

**Flow:**
1. App starts and checks for foreground location permission
2. If foreground permission exists, the app waits 1 second then checks if background permission is needed
3. If background permission is not granted, shows the prompt dialog
4. User can choose "Allow" or "Not Now"

### 3. Navigation.kt
**Updated:**
- Added `activity: MainActivity` parameter to pass activity reference to screens for permission handling

### 4. ZmanimScreen.kt
**Added:**
- `activity: MainActivity?` parameter to the composable
- `BackgroundLocationDialog` composable - A Material3 AlertDialog that prompts users with:
  - Location icon
  - Clear title: "Background Location"
  - Explanation text: "Allow background location access to automatically update your location throughout the day?"
  - "Allow" button - Triggers the permission request
  - "Not Now" button - Dismisses the dialog
- Logic to show the dialog when `state.showBackgroundLocationPrompt` is true

### 5. location_on_24px.xml (New File)
**Created:**
- Material Design location icon drawable for the permission dialog

## User Experience Flow

1. **First Launch:**
   - User opens the app
   - App requests foreground location permissions (FINE or COARSE)
   - After user grants foreground permission, wait 1 second
   - Show background location permission prompt dialog

2. **Dialog Interaction:**
   - **User Taps "Allow"**: System permission dialog opens for background location
   - **User Taps "Not Now"**: Dialog dismisses, can be shown again later

3. **Background Updates:**
   - If user grants background location permission, the `ZmanimUpdateWorker` can update location in the background
   - Location updates happen automatically when the app is not in the foreground
   - Zmanim are recalculated if location changes by more than 100m

## Technical Details

### Permission Levels
- **Foreground Location** (already implemented): `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- **Background Location** (now with prompt): `ACCESS_BACKGROUND_LOCATION`

### Timing
- 500ms delay after app startup before refreshing location
- Additional 1000ms delay before showing background permission prompt
- This ensures UI is ready and doesn't overwhelm the user

### Android Requirements
- Background location permission can only be requested after foreground permission is granted
- On Android 10+ (API 29+), background location requires explicit user consent
- The permission dialog is shown as a non-blocking Material3 AlertDialog

## Benefits

1. **User-Friendly**: Clear explanation of why background location is needed
2. **Non-Intrusive**: Shows after a delay, not immediately on app launch
3. **Optional**: Users can deny and still use the app with manual refresh
4. **Automatic Updates**: With background permission, location updates throughout the day without user intervention
5. **Battery Efficient**: Uses low power location priority and only updates when location changes significantly (>100m)

## Files Modified
- `/app/src/main/java/me/chabad360/zmanim/wearos/presentation/MainViewModel.kt`
- `/app/src/main/java/me/chabad360/zmanim/wearos/presentation/MainActivity.kt`
- `/app/src/main/java/me/chabad360/zmanim/wearos/presentation/navigation/Navigation.kt`
- `/app/src/main/java/me/chabad360/zmanim/wearos/presentation/ui/ZmanimScreen.kt`

## Files Created
- `/app/src/main/res/drawable/location_on_24px.xml`

## Testing Recommendations

1. Test on Android 10+ devices where background location is restricted
2. Verify the dialog appears 1 second after granting foreground permission
3. Test "Allow" flow - should show system permission dialog
4. Test "Not Now" flow - should dismiss without requesting
5. Verify background location updates work after granting permission
6. Test that app still works if user denies background permission

