# DefectCamera (decam) — 프로젝트 요약

## 개요
크롬 faucet(수전) 표면 불량 검사용 안드로이드 현미경 카메라 앱.
카메라2 API 기반 수동 ISO/SS/EV/초점 제어, 촬영 후 주석(화살표/원/사각형/펜) 도구로 불량 부위 표시, 갤러리 자동 저장.

## 기술 스택
- Kotlin + Jetpack Compose
- Camera2 API (CameraX 사용하지 않음 — 이유: CameraX는 런타임 ISO/SS 수동 제어 불가)
- MinSdk 28, TargetSdk 34
- Compose BOM 2023.10.01, Kotlin 1.9.20, AGP 8.2.0
- Coil (이미지 로딩)
- `material-icons-extended` (도형 아이콘 등)

## 핵심 아키텍처

### 카메라 제어 (Camera2Controller)
- `rebuildPreview()`: `CaptureRequest.Builder`를 매번 새로 생성 → 이전 설정(stale key) 충돌 방지
- 수동 ISO: `CONTROL_AE_MODE_OFF` + `SENSOR_SENSITIVITY` 직접 설정
- 수동 SS: `SENSOR_EXPOSURE_TIME` (나노초 단위)
- 수동 초점: `LENS_FOCUS_DISTANCE` + AF 모드 토글 (AF/MF)
- `SessionConfiguration(SESSION_REGULAR)` 사용 (API 28+, deprecated `createCaptureSession` 대체)
- 디지털 줌: `SCALER_CROP_REGION`으로 제어

### UI 구조
- `CameraScreen` → TextureView + 카메라 컨트롤 + 셔터
- `ControlBar` → EV 슬라이더, AF/MF 토글, ISO 슬라이더, SS 프리셋, 셔터 버튼, 갤러리 버튼
- `AnnotationScreen` → 캡처 후 주석 편집 화면
- `MainActivity` → 네비게이션 (Camera ↔ Annotation)

### 주석 시스템 (AnnotationScreen)
- **비트맵 오버레이**: `PorterDuff.Mode.CLEAR`로 픽셀 단위 지우개
- **실행 취소**: 전체 비트맵 히스토리 (최대 20개)
- 4가지 도형: 화살표, 원, 사각형, 펜 + 선 굵기 (2~30px)
- 8가지 색상 프리셋
- 스케치 On/Off 토글 (Visibility 아이콘)

### 제스처 처리 (AnnotationScreen)
- **핀치 줌**: `awaitEachGesture` 기반, 두 손가락 거리 변화로 zoom (1x~10x, 0.1단위)
- **팬**: `showAnnotations=false`면 한 손가락 드래그 = 이미지 이동
- **그리기**: `showAnnotations=true`면 한 손가락 드래그 = 도형 그리기
- 좌표 변환 없음 (raw 화면 좌표 사용) → 확대 시 떨림/부정확 문제 존재

## 구현 시 어려웠던 점

1. **Camera2 수동 ISO/SS**: CameraX는 수동 ISO 불가 → Camera2로 전환. `CaptureRequest` 키 충돌(stale key) 문제로 `rebuildPreview()` 도입
2. **핀치 줌 좌표 정합성**: `graphicsLayer`의 `transformOrigin`(center) 고려한 좌표 변환 시도했으나 오히려 부정확 → raw 좌표로 회귀. 떨림 현상 미해결
3. **스티커 시스템**: 완전히 제거함. 텍스트 스티커 위치/크기/회전 제스처 처리와 드로잉 제스처 충돌로 오류 다수 발생
4. **갤러리 저장**: MediaStore API 29+ `IS_PENDING` 패턴 적용, `ACTION_VIEW` 대신 `CATEGORY_APP_GALLERY`로 갤러리 메인 화면 열기
5. **멀티 터치 + 드로잉**: 동시에 핀치(두 손가락)와 드로잉(한 손가락) 구분을 위해 `pinching` 플래그로 상태 관리

## 알려진 이슈
- **핀치 줌 떨림**: 확대/축소 시 이미지가 부드럽지 않음. `graphicsLayer` 변환과 포인터 좌표 간 차이 때문. 좌표 변환을 적용하면 드로잉 위치가 더 틀어짐.
- **줌/팬 관련**: `graphicsLayer`의 `transformOrigin`이 center(0.5)라서, 확대 시 줌 중심이 손가락 위치와 일치하지 않음. offset 계산식은 있지만 완벽하지 않음.
- **드로잉 좌표**: 확대 상태에서 드로잉하면 손가락 위치와 다른 곳에 그려짐 (좌표 변환 미적용 상태이므로)
- **성능**: 히스토리 전체 비트맵 저장 (OOM 위험, 20개 제한)

## 커밋 기록 (요약)
1. `04f96db` Initial commit — 전체 앱 구조, Camera2, UI, 주석 시스템

## 외부 의존성
```kotlin
// Compose BOM 2023.10.01
implementation platform("androidx.compose:compose-bom:2023.10.01")
// Camera2 (직접 구현, CameraX 아님)
// material-icons-extended (도형 아이콘)
// coil-compose 2.5.0
// exifinterface 1.3.6
```

## 재현 방법
1. 안드로이드 스튜디오에서 `D:\lee\vibe1\camera1` 열기
2. Gradle Sync
3. 에뮬레이터(Pixel 34 API 34+) 또는 실기기에서 실행
4. `./gradlew assembleDebug`로 APK 빌드
