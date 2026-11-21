# 에뮬레이터·시뮬레이터 재생 측정

동일한 [789바이트 이동 원](../fixtures/moving-circle.json)을 매 실행마다 새 프로세스에서 디코딩한다. 측정 화면은 한 아이콘을 48dp(48pt) 영역에 맞춘다. 정적 화면은 진행률을 0에 고정한다. 재생 화면은 1개 또는 10개 인스턴스에 같은 composition을 공급하고, 매 프레임 외부 진행률로 1초 주기의 애니메이션을 그린다.

**측정 구간.** 입력 파일을 읽은 직후부터 디코더가 반환할 때까지가 decode 시간이다. 첫 draw는 같은 시작 시점부터 마지막 아이콘의 첫 `drawContent`가 반환할 때까지다. 프로세스 시작과 파일 I/O는 이 두 시간에 포함되지 않는다. 앱은 24회 프레임 콜백을 기다린 후 120개 콜백 간격을 기록한다. 정적 화면에서도 콜백은 측정기가 요청한다.

재생 프레임 간격은 마지막 아이콘을 실제로 그려 반환한 두 시각의 차이다. draw 시간은 같은 프레임에서 모든 아이콘의 `drawContent`에 걸린 경과 시간의 합이다. GPU 완료, 화면 표시까지의 시간, 전체 앱의 CPU 실행 시간은 별도 측정 항목이다. Android의 할당량은 구간 양 끝에서 읽은 ART의 프로세스 전체 `art.gc.bytes-allocated` 차이를 그린 프레임 수로 나눈다. 프레임이 없는 정적 화면은 나눗셈을 하지 않는다. iOS 기록에 이 ART 지표는 없다.

## Android 16 Pixel 10 AVD 결과

릴리스 APK를 Apple M4(10코어)의 arm64 에뮬레이터에서 실행했다. 같은 호스트에서 Gradle과 Kotlin/Native 빌드도 진행됐다. 표는 시나리오별 새 프로세스 5회의 중앙값이다. 대괄호에는 그 5회 중 최솟값과 최댓값을 표시했다. 프레임 시간은 각 실행에서 먼저 백분위를 구했다. [전체 측정값](../tools/perf/results/android.json.gz)

| 아이콘 | decode (ms) | 첫 draw (ms) | draw 완료 간격 p50 / p95 (ms) | 아이콘 draw 합 p50 / p95 (ms) | ART 할당 B/렌더 프레임 |
| --- | --- | --- | --- | --- | --- |
| 정적 | 9.72 [2.08, 212.92] | 61.09 [9.90, 392.14] | 추가 draw 0회 | 해당 없음 | 해당 없음 |
| 1개 | 12.12 [2.13, 32.21] | 31.44 [7.67, 51.68] | 24.94 / 55.00 [29.74, 123.35] | 0.15 / 1.24 | 3,277 [3,277, 3,824] |
| 10개 | 6.23 [2.98, 33.43] | 47.47 [28.20, 165.25] | 26.91 / 76.29 [29.02, 152.00] | 0.51 / 7.87 | 19,115 [18,842, 20,754] |

재생한 프로세스마다 측정 구간에서 119~120회 렌더했다. 정적 화면은 처음 한 번 그린 뒤 같은 구간에서 다시 그리지 않았다. 호스트 부하에 따라 결과가 크게 흔들렸다. 제품 기기의 프레임 예산이나 다른 플레이어와의 성능 차이를 이 값으로 판단할 수 없다. 에뮬레이터가 전달한 중복 또는 역순 VSYNC 시각도 원자료에 포함했다. 표의 draw 간격은 독립적인 단조 시계를 사용한다.

## iOS 26.5 iPhone 17e 시뮬레이터 결과

arm64 시뮬레이터에서 같은 Kotlin 코드와 순서로 측정했다. [전체 측정값](../tools/perf/results/ios.json.gz)

| 아이콘 | decode (ms) | 첫 draw (ms) | draw 완료 간격 p50 / p95 (ms) | 아이콘 draw 합 p50 / p95 (ms) |
| --- | --- | --- | --- | --- |
| 정적 | 0.20 [0.19, 2.20] | 1.75 [1.40, 14.01] | 추가 draw 0회 | 해당 없음 |
| 1개 | 0.20 [0.19, 0.22] | 1.51 [1.45, 1.90] | 16.67 / 16.77 [16.73, 16.85] | 0.06 / 0.11 |
| 10개 | 0.20 [0.19, 0.40] | 1.97 [1.53, 3.06] | 16.67 / 16.91 [16.74, 16.92] | 0.15 / 0.31 |

두 플랫폼의 시간은 서로 다른 백엔드, 호스트 부하, 가상 기기 조건에서 얻었다.

### iOS 네이티브 힙 할당

같은 릴리스 앱에서 `MallocStackLoggingNoCompact=1`을 켜고 시나리오마다 새 프로세스 3개를 실행했다. 앱은 120개 콜백 간격의 시작과 끝에서 주소와 크기를 식별할 수 있는 메모리를 한 번씩 할당한다. 실행기는 `malloc_history -allEvents -noContent`의 시간순 기록에서 두 표식 **사이의** `ALLOC` 바이트와 건수를 합산한다. 해제된 메모리의 할당도 포함한다. [실행별 할당량과 렌더 프레임 수](../tools/perf/results/ios-allocations.json)

아래 값은 3회의 중앙값이다. 마지막 열은 매 실행의 구간 총량을 실제 렌더 프레임 수로 나눈 평균이며 대괄호는 세 평균의 최솟값과 최댓값이다.

| 아이콘 | 렌더 프레임 수 | 구간 할당 건수 | 구간 할당 바이트 | B/렌더 프레임 |
| --- | ---: | ---: | ---: | ---: |
| 정적 | 0 | 57,900 | 37,597,178 | 해당 없음 |
| 1개 | 120 | 60,024 | 38,613,706 | 321,781 [313,332, 340,281] |
| 10개 | 120 | 71,821 | 42,757,834 | 356,315 [354,281, 359,187] |

이 값에는 UIKit, Compose, Skia와 측정기를 포함한 프로세스 전체의 네이티브 힙 요청이 들어간다. Kotlin/Native가 이미 확보한 페이지 안에서 할당한 개별 객체와 VM 할당 이벤트는 집계 대상에서 빠진다. 정적 화면에서도 프레임 콜백이 실행되므로 그 구간의 힙 사용량을 함께 표시했다. 스택 기록에 드는 비용 때문에 위의 재생 시간은 이 프로파일링과 별도로 측정했다.

## Compottie lite와 같은 입력으로 비교

두 플레이어를 한 Release 앱에 링크했다. 비교 빌드에서 Gradle이 선택한 Compose Multiplatform Foundation 버전은 양 플랫폼 모두 1.12.1이다. 같은 789바이트 JSON, 48dp(48pt) 뷰포트, 외부 진행률과 측정 코드를 사용했다. Lottori는 `decodeJson(bytes)`와 `LottoriAnimation`을 호출한다. Compottie lite 2.3.3은 UTF-8 변환부터 `LottieComposition.parse` 반환까지를 디코딩 시간으로 잡고, `rememberLottiePainter`를 중앙 정렬과 Fit으로 그린다. 측정기는 painter와 같은 그리기 레이어에서 draw 시작과 끝을 기록한다. [측정 앱 코드](../sample/src/perf/kotlin/com/ycgc/lottori/sample/PerfAnimation.kt) · [Compottie lite 패키지](https://central.sonatype.com/artifact/io.github.alexzhirkevich/compottie-lite/2.3.3)

플랫폼마다 정적 화면, 아이콘 1개, 10개를 각 엔진으로 5회씩 실행했다. 아래는 실행별 값의 중앙값이며 대괄호는 최솟값과 최댓값이다. 프레임 간격과 draw 시간은 실행마다 p50과 p95를 먼저 구했다. 두 값이 있는 열의 대괄호는 p95의 범위다. 할당량은 측정 구간에서 프로세스 전체에 할당한 바이트를 그린 프레임 수로 나눈 값이다.

시간 측정과 별도로 정적 원 1개와 진행률 50%에 고정한 원 10개를 화면에서 확인했다. 두 플레이어 사이의 중간 프레임 원 중심 차이는 양 플랫폼 모두 0.2픽셀 이내였다. Android 화면을 캡처할 때는 원 위에 겹치는 상태 표시줄 시계를 잠시 숨겼다.

### iPhone 17e 시뮬레이터

| 아이콘 | 플레이어 | 디코딩 (ms) | 첫 draw (ms) | draw 간격 p50 / p95 (ms) | 아이콘 draw 합 p50 / p95 (ms) | 네이티브 힙 B/프레임 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 정적 | Lottori | 0.19 [0.18–2.68] | 1.55 [1.28–62.60] | — | — | — |
| 정적 | Compottie lite | 0.52 [0.45–22.08] | 1.43 [1.33–34.00] | — | — | — |
| 1 | Lottori | 0.19 [0.18–0.35] | 1.45 [1.39–1.91] | 16.67 / 16.84 [16.76–16.88] | 0.12 / 0.15 [0.09–0.15] | 317,337 [312,560–322,657] |
| 1 | Compottie lite | 0.50 [0.45–0.66] | 1.56 [1.34–2.28] | 16.67 / 16.86 [16.80–16.92] | 0.18 / 0.20 [0.15–0.22] | 317,337 [315,565–322,000] |
| 10 | Lottori | 0.18 [0.18–0.22] | 1.46 [1.40–1.50] | 16.66 / 17.00 [16.91–17.02] | 0.31 / 0.37 [0.34–0.40] | 356,317 [347,819–358,573] |
| 10 | Compottie lite | 0.47 [0.45–0.58] | 1.66 [1.60–1.95] | 16.67 / 17.04 [16.95–17.09] | 0.55 / 0.67 [0.66–0.69] | 371,610 [365,989–377,135] |

아이콘 10개의 draw 시간 p95는 각각 0.37ms와 0.67ms였다. 화면을 다시 그리는 간격의 p95는 17.00ms와 17.04ms로 실행별 범위가 겹쳤다. 정적 화면의 추가 draw는 두 플레이어 모두 0회였다. [시간 측정 30회](../tools/perf/results/compare-ios.json.gz) · [네이티브 할당 측정 30회](../tools/perf/results/compare-ios-allocations.json)

### Pixel 10 에뮬레이터

| 아이콘 | 플레이어 | 디코딩 (ms) | 첫 draw (ms) | draw 간격 p50 / p95 (ms) | 아이콘 draw 합 p50 / p95 (ms) | ART B/프레임 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 정적 | Lottori | 0.98 [0.71–6.80] | 28.02 [23.29–40.76] | — | — | — |
| 정적 | Compottie lite | 20.09 [9.76–67.34] | 62.21 [51.17–105.48] | — | — | — |
| 1 | Lottori | 1.56 [0.56–3.30] | 22.62 [13.41–53.45] | 16.68 / 23.25 [20.10–30.98] | 0.08 / 0.25 [0.17–0.85] | 3,550 [3,550–3,550] |
| 1 | Compottie lite | 3.35 [2.89–63.92] | 33.90 [16.51–129.83] | 16.67 / 22.76 [20.08–27.59] | 0.13 / 0.89 [0.41–3.92] | 2,185 [2,185–2,185] |
| 10 | Lottori | 0.75 [0.54–4.85] | 33.98 [16.66–42.85] | 16.67 / 25.96 [21.99–36.01] | 0.16 / 2.56 [1.19–5.57] | 18,842 [18,842–18,842] |
| 10 | Compottie lite | 24.57 [16.06–45.94] | 58.90 [33.00–87.59] | 16.73 / 28.38 [25.48–30.57] | 0.26 / 7.24 [3.28–9.22] | 6,282 [6,282–6,555] |

아이콘 10개의 draw 간격 p95는 Lottori 25.96ms, Compottie lite 28.38ms로 실행별 범위가 겹쳤다. 이 입력에서 ART 할당은 Lottori 18,842B/프레임, Compottie lite 6,282B/프레임이었다. Pixel 10 AVD는 2코어, RAM 2,048MiB, SwiftShader 설정으로 단독 실행했다. 이 비교의 범위는 한 애니메이션과 두 가상 기기다. 실기기 성능과 Rive 런타임은 미측정이다. [Android 측정 30회](../tools/perf/results/compare-android.json.gz)

## 재현

Android SDK, JDK 21, Xcode를 사용한다. Android 실행기는 에뮬레이터 일련번호와 QEMU 시스템 속성을 모두 확인한다. 측정용 앱 ID는 `com.ycgc.lottori.sample.perf`다.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew -PlottoriSampleMode=perf :sample:androidApp:assembleRelease
python3 tools/perf/run.py android emulator-5580 \
  --install sample/androidApp/build/outputs/apk/release/androidApp-release.apk \
  --output build/perf/android.json
```

iOS 대상에는 부팅한 시뮬레이터 UUID를 지정한다. 빌드는 Release 구성의 arm64 시뮬레이터 바이너리를 생성한다.

```sh
ORG_GRADLE_PROJECT_lottoriSampleMode=perf xcodebuild \
  -project sample/iosApp/LottoriSample.xcodeproj -scheme LottoriSample \
  -configuration Release -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/perf/derived CODE_SIGNING_ALLOWED=NO ARCHS=arm64 \
  PRODUCT_BUNDLE_IDENTIFIER=com.ycgc.lottori.sample.perf build
python3 tools/perf/run.py ios "$SIMULATOR_UDID" \
  --install build/perf/derived/Build/Products/Release-iphonesimulator/LottoriSample.app \
  --output build/perf/ios.json
python3 tools/perf/ios_allocations.py "$SIMULATOR_UDID" \
  --output build/perf/ios-allocations.json
```

시간 측정은 시나리오당 5회, iOS 힙 할당 측정은 3회가 기본값이다. 실행기는 시나리오 순서를 매 라운드 회전하고, 재생 중 실제 draw가 발생했는지 확인한다. 시간 측정 JSON에는 모든 개별 프레임 간격과 draw 소요 시간이 들어간다. `--rounds 1`은 실행 경로만 확인한다.

두 플레이어를 비교하려면 위와 같은 `perf` 빌드를 설치한 뒤 다음 명령을 사용한다. Compottie lite 2.3.3을 포함하는 Android 측정 앱의 최소 API는 24다. 각 라운드에서 같은 아이콘 수로 두 플레이어를 연달아 실행하고, 라운드마다 먼저 실행할 플레이어를 바꾼다. 실행기는 시나리오 사이에 앱 프로세스를 종료해 디코딩을 처음부터 측정한다. 재생 시나리오가 끝나면 진행률을 50%로 고정하므로 다음 실행 전까지 화면에서 원의 위치를 확인할 수 있다. iOS 힙 할당은 시간 측정을 마친 뒤 별도로 수집한다.

```sh
python3 tools/perf/run.py android "$ANDROID_SERIAL" --compare --output build/perf/compare/android.json
python3 tools/perf/run.py ios "$SIMULATOR_UDID" --compare --output build/perf/compare/ios.json
python3 tools/perf/ios_allocations.py "$SIMULATOR_UDID" --compare --rounds 5 \
  --output build/perf/compare/ios-allocations.json
```
