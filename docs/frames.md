# 프레임 정확도 검증

모든 입력 JSON은 이 저장소에서 작성했다. 뷰포트는 100 × 40, 배경은 투명, 논리 픽셀과 출력 픽셀의 비율은 1이다. 프레임 계산은 `min(ip + progress × (op − ip), max(ip, ceil(op) − 1))`을 사용한다. 유한한 진행률을 먼저 0..1로 제한한다.

## 기준 이미지

| 입력 | 독립 기준 | 모바일 결과에서 확인할 점 |
| --- | --- | --- |
| [정적 원](../fixtures/static-circle.json) | [프레임 10](../fixtures/frames/web/static-circle-000.png) | 같은 위치와 색 |
| [이동 원](../fixtures/moving-circle.json) | [시작](../fixtures/frames/web/moving-circle-000.png) · [중간](../fixtures/frames/web/moving-circle-050.png) · [끝](../fixtures/frames/web/moving-circle-100.png) | 중심 x가 20, 50, 78이며 y는 20 |
| [그룹·경로·스타일](../fixtures/render-order.json) | [프레임 25](../fixtures/frames/web/render-order-050.png) | 원과 둥근 사각형의 겹침, 부모 변환, 마지막 곡선, 채우기 구멍과 선 |
| [홀드·시간 보간](../fixtures/timing.json) | [홀드 직전](../fixtures/frames/web/timing-025.png) · [축별 보간](../fixtures/frames/web/timing-050.png) · [끝](../fixtures/frames/web/timing-100.png) | 진행률을 고정했을 때 위치·회전 |

`fixtures/frames/web`의 PNG는 lottie-web **5.13.0**의 SVG 렌더러와 Chrome **157.0.8103.79**의 소프트웨어 렌더링에서 생성했다. GoToAndStop에는 컴포지션 시작점에 상대적인 프레임을 전달한다. 모든 PNG는 직접 제작한 fixture에서 생성했다. 재배포 조건은 저장소의 [Apache-2.0 라이선스](../LICENSE)와 같다. lottie-web(MIT)은 기준 이미지 생성에만 사용한다.

```sh
npm ci --prefix tools/reference --ignore-scripts
npm --prefix tools/reference run frames
python3 fixtures/frames/generate.py
```

Chrome 실행 경로를 직접 지정하려면 `CHROME_BIN` 환경변수를 설정한다. Python 스크립트는 원의 방정식을 픽셀당 16 × 16점에서 평가해 별도의 [수치 기준 이미지](../fixtures/frames/moving-circle-050.png)를 만든다.

## 비교와 실패 분석

공통 테스트가 같은 바이트와 진행률에서 Compose 픽셀을 읽는다. 각 픽셀의 알파와 알파를 곱한 RGB 값을 0..255로 비교한다. 주변 3 × 3 픽셀이 같은 색인 영역의 채널별 허용 오차는 3이다. 독립 기준의 경계 오차는 41, 같은 플랫폼에서 승인한 기준과 비교할 때의 경계 오차는 8이다. 검은색·투명 배경의 RGB 차이를 색상 오차로 세지 않는다. [비교 코드](../lottori/src/commonTest/kotlin/com/ycgc/lottori/GoldenFrameTest.kt)

각 플랫폼에서 독립 기준 검사를 통과한 후 캡처한 9장의 프레임을 [Android](../fixtures/frames/android/)와 [iOS](../fixtures/frames/ios/)에 기록한다. 기준 갱신은 테스트 실행과 별개다. `-PlottoriCaptureFrames=true`는 픽셀을 출력하고 일반 검사도 실행한다. Android의 출력은 해당 에뮬레이터의 logcat에서, JVM과 iOS의 출력은 Gradle 테스트 XML에서 읽는다. 다음 명령은 실패 레코드나 캡처를 PNG로 복원한다.

```sh
java tools/frames/FrameArtifacts.java build/frame-differences \
    lottori/build/test-results lottori/build/outputs/androidTest-results
```

각 차이 디렉터리에는 원본 `expected.png`, 관측값 `actual.png`, 채널 오차를 빨강으로 표시한 `diff.png`가 들어간다. CI는 실패 시 이 세 파일과 원래 테스트 보고서를 아티팩트로 보관한다.

## 기준 렌더러에서 발견한 그룹 투명도 차이

`render-order.json`의 (35, 26)은 불투명 도형 두 개가 겹치는 반투명 그룹 안에 있다. 그룹 불투명도 55%를 합성 결과에 한 번 적용하면 알파는 140이다. 같은 버전의 lottie-web Canvas 렌더러는 해당 픽셀에 알파 203을 기록했다. SVG 렌더러와 Lottori iOS 결과는 모두 140이었다. 이 재현 파일의 독립 기준은 SVG 결과를 사용한다. [Lottie 1.0.1 도형 렌더링 규칙](https://lottie.github.io/lottie-spec/1.0.1/specs/shapes/)
