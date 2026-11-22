# Lottori sample

[English](README.md) · **한국어** · [日本語](README.ja.md)

샘플 앱을 열면 `paper-flight.json`으로 그린 **Paper flight**가 5.2초 주기로 반복 재생됩니다. kyounghyunko가 ChatGPT를 사용해 이 애니메이션을 만들었습니다. [종이학 예제 JSON](../docs/assets/paper-flight.json) · [Android 에뮬레이터 (MP4)](../docs/assets/paper-flight-android.mp4) · [iOS 시뮬레이터 (MP4)](../docs/assets/paper-flight-ios.mp4)

샘플을 빌드하면 [예제 JSON](../fixtures)이 `commonMain`의 Compose Resources에 포함됩니다. Android와 iOS의 공통 화면은 `files/paper-flight.json`에서 종이학을 읽습니다. **Moving circle**에서 **50%**를 누르면 빨간 원이 미리보기 중앙에 나타납니다. **Play**와 **Pause**는 프레임 시계를 제어합니다. **Trim path**를 선택하면 앱에서 제공하는 정적 아이콘과 미지원 노드의 JSON 경로가 표시됩니다. **Damaged JSON**과 **ZIP**은 서로 다른 입력 오류를 보여줍니다.

## Android에서 실행

JDK 21, Android SDK 37 및 실행 중인 Android 에뮬레이터가 필요합니다. 저장소 루트에서 실행하세요. 일련번호는 실행 중인 에뮬레이터에 맞춥니다.

```sh
export ANDROID_SERIAL=emulator-5554
./gradlew :sample:androidApp:assembleDebug
"$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" install -r sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" shell am start -n com.ycgc.lottori.sample/.MainActivity
python3 sample/scripts/android-smoke.py "$ANDROID_SERIAL" sample/build/smoke/android
```

마지막 명령은 샘플의 버튼을 눌러 원의 중심 픽셀, 파일별 진단, 정적 아이콘 화면을 검사하고 스크린샷을 저장합니다.

## iOS에서 실행

Apple Silicon Mac에 JDK 21 및 iOS 시뮬레이터 런타임을 포함한 Xcode를 설치하세요. [Xcode 프로젝트](iosApp/LottoriSample.xcodeproj)를 열고 **LottoriSample** scheme과 iPhone 시뮬레이터를 선택한 다음 Run을 실행하세요. Xcode 빌드 단계가 Kotlin 프레임워크와 예제 에셋을 준비합니다.

같은 앱을 XCTest로 검증하려면 저장소 루트에서 실행하세요. `IOS_UDID`에는 `xcrun simctl list devices available`에 표시된 시뮬레이터 ID를 사용하세요.

```sh
xcodebuild -project sample/iosApp/LottoriSample.xcodeproj -scheme LottoriSample \
  -destination "platform=iOS Simulator,id=$IOS_UDID" -parallel-testing-enabled NO \
  -derivedDataPath sample/build/xcode -resultBundlePath sample/build/smoke.xcresult test
```

XCTest 결과에는 진행률 50%와 미지원 파일의 스크린샷이 첨부됩니다. 다시 실행할 때는 새 결과 번들 경로를 지정하세요. XcodeGen 프로젝트 설정을 수정할 때는 `sample/iosApp/project.yml`을 변경하고 해당 디렉터리에서 `xcodegen generate`를 실행하세요.

| Screen | Android emulator | iOS simulator |
| --- | --- | --- |
| Progress fixed at 50% | [Moving circle](../docs/screenshots/android/moving-050.png) | [Moving circle](../docs/screenshots/ios/moving-050.png) |
| Unsupported trim path | [Static fallback](../docs/screenshots/android/trim-fallback.png) | [Static fallback](../docs/screenshots/ios/trim-fallback.png) |
