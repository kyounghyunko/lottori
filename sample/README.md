# Lottori sample

**English** · [한국어](README.ko.md) · [日本語](README.ja.md)

The sample opens with **Paper flight**, a 5.2-second loop rendered from `paper-flight.json`. kyounghyunko created the animation using ChatGPT. [Paper flight JSON](../docs/assets/paper-flight.json) · [Android emulator (MP4)](../docs/assets/paper-flight-android.mp4) · [iOS simulator (MP4)](../docs/assets/paper-flight-ios.mp4)

The sample build bundles the JSON fixtures as `commonMain` Compose Resources. The shared Android and iOS screen opens the crane at `files/paper-flight.json`. Select **Moving circle** and tap **50%** to center the red circle in the preview. Use **Play** and **Pause** to control playback. Select **Trim path** to see the static fallback icon and the JSON path of the unsupported node. **Damaged JSON** and **ZIP** show distinct input errors. [Sample fixtures](../fixtures)

## Run on Android

Install JDK 21 and Android SDK 37 and start an Android emulator. Run these commands from the repository root, using the serial number of your emulator.

```sh
export ANDROID_SERIAL=emulator-5554
./gradlew :sample:androidApp:assembleDebug
"$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" install -r sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" shell am start -n com.ycgc.lottori.sample/.MainActivity
python3 sample/scripts/android-smoke.py "$ANDROID_SERIAL" sample/build/smoke/android
```

The last command taps the sample controls, checks the circle's center pixels, file diagnostics and fallback screen, and saves screenshots.

## Run on iOS

On an Apple Silicon Mac, install JDK 21 and Xcode with an iOS simulator runtime. Open the Xcode project, select the **LottoriSample** scheme and an iPhone simulator, then choose **Run**. The Xcode build phase prepares the Kotlin framework and the sample assets. [Xcode project](iosApp/LottoriSample.xcodeproj)

To run the UI tests with XCTest, set `IOS_UDID` to a simulator ID from `xcrun simctl list devices available`. Run this command from the repository root.

```sh
xcodebuild -project sample/iosApp/LottoriSample.xcodeproj -scheme LottoriSample \
  -destination "platform=iOS Simulator,id=$IOS_UDID" -parallel-testing-enabled NO \
  -derivedDataPath sample/build/xcode -resultBundlePath sample/build/smoke.xcresult test
```

The XCTest result bundle includes screenshots of the 50% frame and the fallback screen. Use a new result bundle path each time you run the tests. To change XcodeGen settings, edit `sample/iosApp/project.yml` and run `xcodegen generate` from its directory.

| Screen | Android emulator | iOS simulator |
| --- | --- | --- |
| Progress fixed at 50% | [Moving circle](../docs/screenshots/android/moving-050.png) | [Moving circle](../docs/screenshots/ios/moving-050.png) |
| Unsupported trim path | [Static fallback](../docs/screenshots/android/trim-fallback.png) | [Static fallback](../docs/screenshots/ios/trim-fallback.png) |
