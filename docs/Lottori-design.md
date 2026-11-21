# Lottori 설계안
Compose Multiplatform용 Lottie 2D 벡터 플레이어 · Android / iOS · 제안 단계

## 1. 이름과 제품 결정

프로젝트 이름은 **Lottori(로토리)**다. 저장소 표기는 `lottori`, Compose 진입점은 `LottoriAnimation`으로 정한다.

목표 사용자는 디자이너에게 받은 로딩·상태 아이콘을 Android와 iOS에 함께 배포하는 CMP 팀이다. 팀이 알고 싶은 것은 “이 파일을 넣으면 어떤 기능 때문에 깨지는가”, “제스처 진행률 50%에서 양쪽이 의도한 모양인가”이다. 첫 릴리스의 제품 단위는 플레이어, 파일 진단, 공개 프레임 테스트 세 가지다.

| 선택지 | 얻는 것 | 유지보수 비용과 권장 용도 |
|---|---|---|
| Compottie 채택 또는 기여 | 공통 Kotlin 엔진, expressions를 덜어낸 lite 모듈, 별도 dotLottie 모듈 | 앱에 즉시 재생을 도입하거나 기존 엔진에 진단을 더하고 싶을 때 유리 |
| 플랫폼 플레이어를 공통 API로 래핑 | 기존 Android/iOS 렌더러의 지원 기능 활용 | 플랫폼 뷰 통합, 재생 시점 동기화, 두 구현의 차이를 관리 |
| 자체 Kotlin 엔진 | 공통 코드에서 파싱 정책·프레임 평가·진단 경로를 통제 | 그래픽 사양과 회귀 테스트를 직접 유지. 지원 범위를 제한하는 조건으로 채택 |

Compottie도 외부 progress와 순수 Kotlin 렌더러를 제공한다. 이 프로젝트의 차별화 가설은 **명시적으로 한정한 기능 범위, 입력별 진단, 공개한 기준 프레임**이다. 용량·속도 우위는 측정 후 판단한다. [Compottie](https://github.com/alexzhirkevich/compottie)

## 2. Lottie 파일을 읽는 법

Lottie는 정해진 시간에 어떤 도형을 어디에 어떻게 그릴지 표현하는 JSON 형식이다. 이 설계의 기준 문서는 Lottie Animation Community 1.0.1이다. 배포 시에는 전체 사양 준수 대신 구현하고 테스트한 기능과 제약을 공개한다. [사양](https://lottie.github.io/lottie-spec/1.0.1/) · [공식 변경 기록](https://lottie.github.io/changelog/)

| 단위 | 필드 예 | 구현 의미 |
|---|---|---|
| Composition | `w, h, fr, ip, op` | 원본 좌표계, 초당 프레임 수, 재생 구간. 시간 길이는 `(op - ip) / fr`초 |
| 버전 | `ver, v` | 정수 ver는 대상 사양 버전, 기존 Bodymovin 파일의 문자열 v는 내보내기 도구 버전. 기능 검사는 실제 노드 기준 |
| Layer | `layers, ty, ind, parent, ks` | ty는 레이어 종류, ind는 부모 참조에 쓰이는 ID, ks는 레이어 변환. parent를 배열 인덱스로 해석하면 잘못된 객체가 움직인다 |
| Shape group | `shapes, it, ty` | 그룹(gr) 안에서 rc/el/sh는 윤곽, fl/st는 채우기·선, tr은 그룹 변환. 같은 ty라도 레이어와 도형에서 의미와 타입이 다르다 |
| Property | `a, k, t, s, h, i, o` | 고정값과 키프레임 배열을 구분한다. 키프레임의 t는 프레임 번호, s는 그 시점의 값, h=1은 다음 키까지 유지, i/o는 구간의 시간 보간 핸들 |
| Bézier path | `v, i, o, c` | 꼭짓점 배열, 각 꼭짓점에 상대적인 입·출력 핸들, 닫힘 여부. 여기의 i/o는 공간 좌표 |
| 재사용·합성 | `assets, refId, masksProperties, tt` | precomp는 별도 레이어 묶음을 참조한다. mask는 레이어에 걸리는 경로, matte는 다른 레이어의 알파·휘도로 가시 영역을 정한다 |

[Composition](https://lottie.github.io/lottie-spec/1.0.1/specs/composition/) · [Layers](https://lottie.github.io/lottie-spec/1.0.1/specs/layers/) · [Properties](https://lottie.github.io/lottie-spec/1.0.1/specs/properties/) · [Shapes](https://lottie.github.io/lottie-spec/1.0.1/specs/shapes/) · [Bodymovin 버전 필드](https://lottiefiles.github.io/lottie-docs/composition/)

**예시.** 다음은 좌우로 움직이는 원에서 레이어의 위치 속성인 `ks.p`만 뽑은 JSON이다. 컴포지션이 `fr=30, ip=10, op=40`이면 재생 시간은 1초다. 0.5초 뒤 평가할 프레임은 25이며 원의 중심은 (50, 20)이다. 마지막 키는 종료 경계의 보간 목표로 쓸 수 있다.

```json
{
  "a": 1,
  "k": [
    {
      "t": 10,
      "s": [20, 20],
      "o": { "x": [0, 0], "y": [0, 0] },
      "i": { "x": [1, 1], "y": [1, 1] }
    },
    { "t": 40, "s": [80, 20] }
  ]
}
```

**도형을 그리는 순서.** 예를 들어 한 그룹에 [원, 사각형, 채우기]가 있으면 채우기가 앞선 두 경로에 적용된다. 그룹 투명도 50%는 겹치는 도형을 먼저 합성한 결과에 한 번 적용한다. 각 도형을 따로 반투명하게 그리면 겹친 부분이 진해진다. 스타일과 도형의 대응 및 그룹 경계를 로딩 시에 결정해야 하는 이유다. [Shape Rendering Model](https://lottie.github.io/lottie-spec/1.0.1/specs/shapes/)

**dotLottie.** .lottie는 manifest와 하나 이상의 애니메이션을 담는 ZIP 컨테이너다. v2의 애니메이션은 a/에 있고, 테마와 상태 머신도 들어갈 수 있다. 컨테이너의 JSON 추출, 테마 적용, 상태 머신 실행은 각각 독립적인 지원 항목으로 표시한다. [dotLottie v2](https://dotlottie.io/spec/2.0/)

**입력과 버전 처리.** v0.1의 core 진입점은 `decodeJson(bytes)`다. 파일 이름은 오류 표시에만 쓸 수 있다. ZIP 시그니처가 들어오면 `UnsupportedContainer`를, 손상된 JSON이면 구문 오류를 반환한다. ZIP 시그니처만으로 dotLottie 여부를 확정할 수 없으므로 후속 리더는 manifest와 버전별 구조를 확인한다. `ver`가 없는 기존 Bodymovin 파일도 읽는다. 예를 들어 `"v": "5.12.2"`만 있는 파일은 실제 노드를 검사하고, `"ver": 10001`이면 사양 1.0.1로 해석한다. 생소한 대상 사양 버전은 진단에 남기며 미지원 기능 처리는 기존 Strict/Preview 정책을 따른다. [버전 지침](https://lottie.github.io/lottie-spec/1.0.1/specs/composition/)

**압축 지원의 우선순위.** v0.1에서는 동일한 벡터 JSON을 그대로 번들한 앱과 실험용으로 dotLottie v2에 패키징한 앱의 배포 크기를 비교한다. 이 에셋 실험에는 제품용 ZIP 리더가 필요 없다. 실제 팀에 들어오는 파일이 이미 .lottie이거나, 실험에서 유의미한 절감이 보이면 선택 모듈 `:lottori-dotlottie`를 우선한다. 첫 범위는 단일 벡터 애니메이션의 manifest 확인, JSON 추출, 기존 core 호출이다. 모듈 채택 판단에는 리더를 실제로 링크한 앱의 순절감과 첫 프레임 비용까지 포함한다. 이미지, 여러 애니메이션 선택, 테마, 상태 머신은 호환표에 각각 표시한다. [dotLottie v2](https://dotlottie.io/spec/2.0/)

## 3. v0.1 호환 범위

| 영역 | 첫 릴리스에 포함 | 후속 후보 / 발견 시 진단 |
|---|---|---|
| 입력 | 이름·확장자 없이 UTF-8 JSON 바이트 디코딩. 번들 리소스와 앱이 공급한 바이트가 같은 경로를 사용 | ZIP 입력은 미지원 컨테이너로 진단. 후속 후보는 dotLottie v2의 벡터 JSON 읽기, 이미지·폰트 로더, URL |
| 레이어 | 2D shape, null 부모, 표시 구간, 기본 시작 시점·배속 | precomp, solid, image, text, 3D, 비기본 st/sr, time remap |
| 변환 | anchor, position, scale, rotation, opacity, 중첩 그룹 | split position, skew, auto-orient, 공간 곡선 이동 |
| 도형 | 사각형(라운드 포함), 타원, 정적 Bézier path, 그룹 | path morph, 별, trim path, repeater |
| 스타일 | 단색 fill/stroke, fill rule, 기본 선 끝·이음, 그룹 투명도 | gradient, dash, blend modes, mask, matte |
| 시간 | 스칼라·2D 벡터에 고정값, hold·linear·시간 Bézier. 선형 이동과 속도 곡선을 함께 지원 | expressions, 시간 리매핑, 애니메이션 색상 |
| 제어 | 외부 progress, 1회·반복 재생, 일시정지·재개, 접근성 설명 | marker 구간 재생, 동적 속성 변경 |

우선순위는 단순 상태 아이콘이다. 예를 들어 디자이너가 선을 따라 체크 표시가 그려지는 효과를 trim path로 만들면 v0.1에서는 진단이 발생한다. 실제 채택 후보 파일에 이런 효과가 많으면 다음 버전의 첫 기능으로 trim path를 올린다.

## 4. 코드 구조와 실행 경로

플레이어와 JSON 디코더를 하나의 배포 모듈로 제공한다. Android/iOS는 동일한 Kotlin 평가기와 Compose 그리기 코드를 사용한다. Android Canvas, Core Animation, 플랫폼 뷰를 직접 감싸는 어댑터는 초기 설계에 필요하지 않다.

| 위치 | 책임 | 경계 |
|---|---|---|
| :lottori / commonMain / core 패키지 | kotlinx.serialization.json으로 입력 판독, 기능 검사·진단, 참조 해석, 정적 렌더 순서 계산, 키프레임·변환 평가 | Compose, 파일 시스템, 네트워크 독립. 외부에는 decode, composition, diagnostic만 노출 |
| :lottori / commonMain | 로딩 상태·취소, 재생 시계, Canvas/DrawScope 그리기, contentScale과 접근성 | 같은 모듈의 core 패키지를 사용. 플랫폼별 Path·그리기 객체와 가변 버퍼는 플레이어 인스턴스 소유 |
| :sample 및 테스트 | 동일한 asset을 Android·iOS에서 열고 progress를 고정하는 화면. 공통 수치 테스트와 플랫폼별 이미지 비교 | 샘플과 기준 이미지는 Maven 배포 대상에서 제외 |

**로딩 경로.** JSON을 한 번 파싱하고 bounds/fr/구간을 검증한다. 입력 위치를 기록하면서 기능을 검사하고, 레이어 ID·부모를 해석한다. 순환 참조 및 중복 ID로 해석이 불가능하면 실패한다. 그룹마다 지오메트리와 스타일의 대응 관계, 그리기 순서를 미리 만든다. 완성된 composition은 불변이며 여러 플레이어가 공유할 수 있다. v0.1은 호출 위치의 remember와 명시적 composition 재사용만 제공한다. 프로세스 전체 캐시는 벤치마크로 필요성을 확인한 뒤 도입한다.

**프레임 경로.** 재생 시계 또는 호출자가 지정한 progress에서 평가 프레임을 정한다. 활성 레이어를 고르고 각 속성의 키프레임 구간을 이분 탐색한다. 시간 Bézier는 경과 비율에 대응하는 x를 역산한 뒤 y로 값을 보간한다. 벡터는 축별 핸들을 지원한다. 부모와 자신의 변환을 합성한 다음, 미리 정한 순서로 경로와 스타일을 DrawScope에 전달한다. 뷰포트에는 원본 종횡비를 보존하는 Fit과 중앙 정렬을 기본 적용한다. 빌드 시점에 저장한 배열과 draw 시점에 변경하는 버퍼를 분리하면 두 플레이어가 서로의 진행 상태를 오염시키지 않는다.

**재생 계약.** 컴포지션과 레이어의 표시 범위는 [ip, op)이다. 중간 진행률은 소수 프레임을 보존한다. v0.1에서 단발 재생의 progress=1은 마지막 표시 가능한 소스 프레임인 max(ip, ceil(op)−1)에 고정하는 정책으로 정의한다. 그 프레임 이후에 값을 되감는 현상을 피하려고 단발 모드의 표시 프레임은 전체 구간에서 이 끝값으로 상한을 둔다. 반복은 전체 길이 (op−ip)/fr가 지나면 ip로 돌아간다. 시계는 누적 경과 시간으로 계산한다. 일시정지 중 경과 시간은 제외하고, composition이 화면에서 제거되면 프레임 대기를 취소한다. 정지 화면의 draw에는 프레임 루프가 필요 없다.

**진단 계약.** invalid JSON·유효하지 않은 fr·해석 불가능한 부모 참조는 실패다. 작성자 메타데이터 등 시각적 의미가 없는 속성은 허용한다. 알려진 미지원 시각 기능과 알 수 없는 도형 타입은 code, JSON path, 레이어 이름을 기록한다. 기본 Strict는 해당 파일을 실패로 처리해 앱이 지정한 정적 아이콘을 표시하게 한다. 명시적으로 선택한 Preview 모드는 진단을 유지하며 영향을 받는 레이어를 생략한다. matte처럼 다른 레이어에도 영향을 주면 연결된 레이어까지 생략하고 범위를 특정할 수 없으면 실패한다. 이 정책은 mask를 조용히 무시해 가려져야 할 부분이 노출되는 경우도 막는다.

```text
UnsupportedFeature  TrimPath  $.layers[2].shapes[0].it[3]  layer="checkmark"
```

## 5. 앱에서 보이는 API 제안

Lottori API의 호출 계약을 보여주는 예시다. 배포된 의존성이나 실행 가능한 SDK는 아직 없다. 파일 내용 또는 리소스 버전이 바뀌면 로딩 key도 바꾼다. progress는 0..1의 유한값을 입력 계약으로 두고, 유한하지만 범위를 벗어난 값은 양 끝으로 제한한다.

```kotlin
@Composable
fun SyncIcon(progress: State<Float>, modifier: Modifier = Modifier) {
    val result = rememberLottoriComposition(key = "sync-v1") {
        Res.readBytes("files/sync.json")
    }
    when (val state = result) {
        LottoriLoadState.Loading -> SyncPlaceholder(modifier)
        is LottoriLoadState.Failed -> StaticSyncIcon(modifier)
        is LottoriLoadState.Ready -> LottoriAnimation(
            composition = state.composition,
            progress = { progress.value },
            contentDescription = "동기화",
            modifier = modifier,
        )
    }
}
```

SyncPlaceholder와 StaticSyncIcon은 앱이 제공한다. 자동 재생 시에는 rememberLottoriPlayback(composition, iterations, isPlaying)이 반환하는 State<Float>를 같은 progress 자리에 전달한다. 입력 key 변경 시 이전 로딩을 취소하고 오래된 결과가 새 상태를 덮지 않게 한다. 디코딩은 백그라운드에서 수행한다. draw 안에서 progress()를 호출해 매 프레임 상위 컴포저블의 상태 읽기가 생기지 않게 한다. 콘텐츠 설명이 null이면 장식 요소로 취급한다. [Compose phases](https://developer.android.com/develop/ui/compose/phases)

## 6. 첫 기여에서 첫 릴리스까지

| PR | 결과물 | 통과 기준 |
|---|---|---|
| 1 | KMP 모듈·샘플, 자체 제작 JSON의 정적 원 하나, core decode → Compose Canvas | Android 에뮬레이터와 iOS 시뮬레이터에서 같은 위치·색의 원. 확장자 없는 동일 바이트도 성공. ZIP과 깨진 JSON은 서로 다른 진단을 내고 앱 fallback 표시 |
| 2 | 외부 progress와 scalar/vector 평가기 | 위 이동 예제의 f10, f25, f39가 x=20, 50, 78. hold 직전·직후, 같은 t, 축별 easing, ip≠0, 역순 seek 테스트 |
| 3 | 그룹·부모 변환, 정적 path, fill/stroke, 그룹 투명도 | 겹친 도형의 교차점 알파, 회전 중심, 레이어 겹침 순서, 닫힌 경로의 마지막 곡선이 기준 이미지와 일치 |
| 4 | 재생 컨트롤, 진단, 모드별 실패 처리 | 일시정지/재개, 단발 종료, 반복 경계, 빠른 파일 교체, trim path/matte 입력이 계약대로 처리 |
| 5 | 양 플랫폼 샘플·호환표·배포 후보 | clean 소비 앱에서 Android/iOS 빌드, 지원 행마다 fixture와 기준 프레임 링크, 패키지 및 공개 API 검사, 두 플랫폼의 배포 크기 기준값 기록 |

**정확도.** 자체 작성한 수치 예상값과 버전을 고정한 lottie-web 기준 이미지를 함께 쓴다. 기준 플레이어와 사양이 다를 때는 사양과 재현 파일을 이슈에 기록한다. 동일 viewport·배경·진행률로 렌더하고 채움 내부와 기하 좌표는 엄격히 검사한다. 안티앨리어싱이 다른 경계 픽셀은 양 플랫폼별 승인 기준 이미지와 허용 오차를 둔다. 실패한 CI는 원본·결과·차이 이미지를 남긴다. 기존 모바일 E2E에는 샘플 열기, progress 0.5 고정, 정적 fallback 확인을 연결한다.

**파일 크기와 앱 다운로드 크기.** 비교 단위는 바이트로 통일하고, 릴리스 결과에는 아래 세 값을 구분해 기록한다.

| 측정 대상 | 재현 방법 | 판단에 쓰는 값 |
|---|---|---|
| 에셋 자체 | 하나의 벡터 JSON을 원본과 dotLottie v2로 패키징한다. Rive 비교용 에셋이 있으면 같은 모션·길이·표시 크기로 제작하고 주요 프레임이 일치하는지 확인한다. 원본 크기와 동일 압축기·옵션으로 압축한 크기를 각각 기록한다. | 작품별 절대 차이와 비율. 외부의 단일 작품에서 나온 10배 차이를 목표치로 사용하지 않는다. |
| 런타임 추가분 | A는 기본 CMP 앱, B는 같은 앱에서 공개 재생 경로를 호출하는 빌드다. B에는 외부에서 바이트를 공급해 샘플 파일을 번들하지 않고 릴리스 최적화 뒤에도 재생 코드가 포함되도록 한다. | B−A. 동일한 Kotlin/CMP 버전, 최적화 설정, 대상 기기에서 측정한다. |
| 사용자 다운로드 증가분 | C는 B에 실제 에셋 세트를 번들한 빌드다. Android는 동일 device spec으로 `bundletool get-size total`, iOS는 동일 기기 variant의 App Thinning Size Report에서 compressed app size를 사용한다. | C−B는 에셋 증가분, C−A는 앱 전체 증가분. ZIP 리더를 추가하는 조합은 해당 리더를 링크한 B부터 다시 측정한다. |

[Android 배포 크기 측정](https://developer.android.com/tools/bundletool) · [iOS 앱 크기 보고서](https://developer.apple.com/documentation/xcode/reducing-your-app-s-size)

**재생 성능.** 같은 플랫폼·실기기·릴리스 설정에서 48dp 아이콘 1개와 동일 아이콘 10개를 각각 재생하고 정적 화면을 기준으로 둔다. 캐시가 비어 있을 때 입력 바이트 수신부터 첫 draw까지, 디코딩 시간, 재생 중 프레임 시간 p50/p95와 프레임별 할당을 구분해 기록한다. 후속 리더는 압축 해제 시간도 기록한다. Compottie lite와의 비교에는 동일 JSON을 쓴다. Rive 비교에는 같은 외형의 .riv와 해당 런타임을 사용하고 제작 방식과 런타임 버전을 함께 공개한다.

**배포·기여.** 코드 라이선스 제안은 Apache-2.0이다. 샘플과 기준 이미지의 출처·재배포 조건은 별도 기록한다. Maven Central 좌표에는 관리자가 소유한 namespace를 사용한다. README에 Kotlin/CMP·Android/iOS 검증 버전, 동작 예시, 지원표를 싣는다. 기여자가 기능 하나를 추가할 때는 최소 JSON, 예상 특정 프레임, Android/iOS 결과, 지원표 변경을 같은 PR에 넣는다.

**첫 마일스톤**은 이 문서의 이동 원을 Android/iOS의 progress=0.5에서 동일한 좌표에 그리고, trim path가 든 파일에서는 두 플랫폼이 같은 JSON 경로로 진단하는 것이다.
