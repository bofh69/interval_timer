# Simple Interval Timer

A native Android app for creating and running interval workouts.

The countdown stays on one line and resizes when pause controls appear, then
expands again on resume. Beeps use the media volume and audio route, including
Bluetooth headsets. A silent stream starts two seconds before the first countdown
beep and stays active through the final beep to reduce headset wake-up delays.
Intervals started or resumed close to a beep have only the remaining time to warm
up the headset; effectiveness depends on the headset.

## Build

Install JDK 17 and the Android SDK, then run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The installable debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions runs the unit tests and build on pushes and pull requests, and
attaches the APK to a GitHub release for any pushed tag.

## License

Licensed under the GNU General Public License, version 3. See [LICENSE](LICENSE).
