# 이동 원의 배포 크기

같은 이동 원을 같은 재생 코드에 추가했을 때의 크기를 비교한다. 크기 단위는 바이트다. 입력은 [moving-circle.json](../fixtures/moving-circle.json) 한 개다. [기준 프레임](../fixtures/frames/web/moving-circle-050.png)은 진행률 50%를 나타낸다.

## 에셋

[패키징 스크립트](../tools/size/assets.py)는 원본 바이트를 그대로 `a/circle.json`에 넣고 v2 manifest를 추가한다. ZIP 타임스탬프와 DEFLATE 레벨 9를 고정한다. 출력에서 원본 JSON을 다시 꺼내 바이트 단위로 대조한다.

| 파일 | 원본 바이트 | 동일한 zlib 레벨 9로 다시 압축한 바이트 |
| --- | ---: | ---: |
| JSON | 789 | 285 |
| dotLottie v2 | 566 | 470 |
| ZIP − JSON | −223 (−28.3%) | +185 (+64.9%) |

이 파일은 ZIP 자체로는 223바이트 작지만, 두 파일에 같은 외부 압축을 적용하면 JSON이 185바이트 작다. 포맷 전체나 다른 작품의 압축률은 이 결과로 판단할 수 없다.

```sh
python3 tools/size/assets.py fixtures/moving-circle.json build/size/input.lottie
```

## Android 릴리스

A는 화면에 Compose 컨테이너만 표시한다. B는 앱에 공급한 바이트를 공개 로더에서 디코딩해 재생하는 코드를 링크한다. C와 Z는 B의 바이너리 구성에 각각 JSON과 dotLottie 하나를 추가한다. Z에는 제품용 ZIP 리더가 없다. Z로 구하는 값은 에셋을 배포했을 때의 크기다.

[측정 스크립트](../tools/size/android.py)는 R8 최적화를 적용한 네 AAB에서 실제 에셋의 이름과 바이트를 검사한다. 최적화된 DEX에 디코더의 진단 코드가 B·C·Z에서만 남는지도 확인한다. A와 B에는 JSON·ZIP 에셋이 없어야 한다. B·C·Z의 DEX를 SHA-256으로 비교해 앱 코드가 동일함을 확인한다. 같은 [기기 사양](../tools/size/device.json)으로 APK 세트를 만들고 `bundletool get-size total`의 MIN과 MAX가 같은지 검사한다.

```sh
python3 tools/size/android.py /path/to/bundletool-all-1.18.3.jar build/size/android
```

JDK 21, Gradle 9.5.0, AGP 9.1.1, Kotlin 2.4.10, Compose 1.12.0, compileSdk 37, minSdk 23, targetSdk 36. 측정 기기 사양은 Android 36, arm64-v8a, 420 dpi, en-US다. 이 명령은 기기에 앱을 설치하지 않는다. AAB, APK 세트, 각 CSV, 결과 JSON은 지정한 출력 디렉터리에 남는다.

고정 기기 사양에서 얻은 다운로드 추정값은 다음과 같다. 각 모드 한 번의 릴리스 빌드에서 APK 세트를 생성했다. [측정 원본](../tools/size/results/android.json)

| 모드 | 다운로드 바이트 | A 대비 | B 대비 |
| --- | ---: | ---: | ---: |
| A 기본 앱 | 554,741 | 0 | |
| B 외부 바이트 재생 | 608,425 | +53,684 | 0 |
| C JSON 번들 | 608,756 | +54,015 | +331 |
| Z dotLottie 번들 | 608,950 | +54,209 | +525 |

이 기기 사양과 단일 에셋에서는 dotLottie 번들이 JSON 번들보다 194바이트 컸다. 앱의 기반 코드가 달라지거나 애니메이션의 수·내용이 바뀌면 R8과 압축 결과도 달라진다.

## iOS 릴리스

[측정 스크립트](../tools/size/ios.py)는 같은 Xcode 프로젝트의 Release 설정으로 arm64 기기용 A·B·C·Z를 아카이브한다. 각 앱의 `Fixtures`를 검사한다. 서명 팀과 등록된 프로파일이 있는 Mac에서는 같은 기종의 압축 다운로드 크기를 Xcode의 App Thinning Size Report에서 읽을 수 있다.

```sh
python3 tools/size/ios.py build/size/ios --team YOUR_TEAM_ID --variant iPhone18,1
```

각 `*-export`의 `App Thinning Size Report.txt`에서 해당 기종의 **App size / compressed** 항목을 비교한다. B−A는 런타임, C−B는 번들한 JSON, C−A는 전체 증가분이다. Z−C는 같은 앱에서 에셋 컨테이너를 바꾼 차이다. Xcode가 표시한 단위와 반올림 정밀도를 보존한다.

서명 없는 기기용 실행 파일과 프레임워크의 로컬 크기는 다음 명령으로 확인한다.

```sh
python3 tools/size/ios.py build/size/ios-local --unsigned
```

Xcode 26.6(17F113)에서 arm64 Release 아카이브를 만들고 같은 기종으로 내보내기를 시도했다. `xcodebuild -exportArchive`는 `No Team Found in Archive`로 종료했고, 키체인의 유효한 서명 ID는 0개였다. 따라서 iOS의 기기별 압축 다운로드 크기는 미측정이다. 기기 아카이브의 로컬 파일 바이트와 앱 다운로드 바이트는 별개의 값이다.

서명 없는 4개 arm64 기기 아카이브에서 확인한 로컬 파일 크기는 다음과 같다. `앱 전체`는 `.app` 내부 일반 파일의 바이트 합이다. 아카이브의 dSYM과 파일시스템 블록 반올림은 제외한다. [측정 원본](../tools/size/results/ios-local.json)

| 모드 | 앱 전체 | 메인 실행 파일 | Kotlin 프레임워크 실행 파일 |
| --- | ---: | ---: | ---: |
| A 기본 앱 | 26,127,079 | 75,088 | 26,043,680 |
| B 외부 바이트 재생 | 27,279,191 | 75,088 | 27,195,792 |
| C JSON 번들 | 27,279,980 | 75,088 | 27,195,792 |
| Z dotLottie 번들 | 27,279,757 | 75,088 | 27,195,792 |

B·C·Z의 `LottoriSampleKit` SHA-256은 모두 `e52c006ae0215ffc5f151af50a10e0fa1298beadcc7de22c6bbf1263c2bc8acf`였다. B−A는 로컬 파일 1,152,112바이트다. B에 JSON을 넣으면 789바이트, ZIP을 넣으면 566바이트가 추가되었다.

## 측정 시점

측정은 소스 스냅샷을 별도 디렉터리에 복사해 수행했다. [핑거프린트 스크립트](../tools/size/fingerprint.py)는 플레이어와 디코더의 공통 코드와 A·B 화면에 대해 파일 경로와 내용의 SHA-256을 구한다. Android 측정값은 `097562b408bcec6f8424a52da98a77abdba049535affd87a72e52eb2596ab485`, iOS 측정값은 `4b4ffe9c03786c4cad1de22c1d979fcabb25f1351b3c7f131df980a2d526f179` 시점이다. iOS 스냅샷은 문자열 파서 보조 함수의 가시성을 `internal`로 변경하기 전이다. 두 측정 모두 프레임 시계가 역행할 때의 보정 추가 전이다. 현재 체크아웃의 값은 위 명령으로 다시 측정할 수 있다.
