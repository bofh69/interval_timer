# Simple Interval Timer

A native Android app for creating and running interval workouts.

## Build

Install JDK 17 and the Android SDK, then run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The installable debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions runs the unit tests and build on pushes and pull requests, and
attaches the APK to a GitHub release for any pushed tag.
