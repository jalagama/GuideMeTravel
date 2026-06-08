# Google Play: Background Location Justification

Use this text when submitting GuideMe Travel for Play Store review (Sensitive permissions → Background location).

## Feature description

GuideMe Travel is an offline audio tour guide. During an **active trip**, the app uses background location to detect when the user enters a geofence around the next planned attraction and automatically plays a pre-downloaded audio guide — hands-free, like a personal tour guide.

## Why background location is required

- Users walk or drive between attractions with the phone in pocket
- Foreground-only location would stop guides when the map is not visible
- Audio must trigger **before arrival** (~400 m radius) while the app is in background

## When background location is used

- **Only** after user taps "Start trip" on a downloaded offline itinerary
- **Only** while the foreground service notification "Trip in progress" is visible
- **Stopped** when user completes the trip or force-stops the service

## User consent flow

1. Onboarding explains automatic audio guides
2. Privacy consent screen (GDPR/CCPA/DPDP) with explicit background location checkbox
3. Runtime permission request before starting trip
4. Optional battery optimization exemption prompt for OEM reliability

## Data handling

- Location is processed on-device for geofencing
- We do not continuously upload live location to servers during the trip
- Trip routes are generated once online before travel

## Video demo for reviewers

Record a 30–60 second video showing:

1. Create trip → download offline pack
2. Start trip → lock screen / switch apps
3. Approach attraction → audio plays automatically
4. Complete trip → background service stops
