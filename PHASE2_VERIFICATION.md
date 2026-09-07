# EcoSort 2단계 검증 기록

2026-09-07 · Android 1.3 / V2 서버 · `phase2/local-carton-tracking`

## 실행한 검사

| 검사 | 직접 확인한 결과 |
|---|---|
| Debug | assembleDebug 성공, 새 에뮬레이터에 설치·실행, versionCode 4/versionName 1.3 |
| Release | assembleRelease 성공, R8/lintVital 경로 통과, unsigned APK. Play Store 미배포 |
| Android 단위 검사 | 9개 suite, 25개 검사, 실패·오류·건너뜀 0 |
| 서버 | 98개 통과, 실패 0. JSON·인증·타임아웃·SSE·사용량 제한·상태 전이 등 기존 회귀 |
| 실제 Android OpenCV | instrumentation 1개 검사 통과. 그 안에서 합성 18개·원본 6장·다른 병 1장·빈 화면을 처리 |
| 합성 영상 | 면 6개 × 각도 -25/0/25도에서 18/18 면 일치. 기울임·크기·위치 변형 포함 |
| 등록 원본 | 제공된 IMG_1554~1559를 줄인 사진 6/6 면 일치. 독립 평가 자료 아님 |
| 음성 사례 | 다른 투명 병 사진, 빈 화면에서 등록 포장을 찾지 않음 |
| 추적 상태 | 3회 일치, 면 변경, 시간 간격, 위치 점프, 유실 초기화 단위 검사 통과 |
| 실제 UI | 이동·부분 가림에서 점 표시, 대상 숨김에서 점·사각형 제거, 복원 후 재획득 |
| 실제 사진 연결 | Photo Picker → 앞면 인식(255개 기하 검증점 관찰) → 기존 상태 확인 화면에 사진 전달 |
| 카메라 | 검증 기기 카메라 사용 안 함, 앱 CAMERA granted=false 확인 |
| 실제 Firebase | 현재 키로 익명 로그인·App Check·서버 호출 성공, 기존 인증 흐름 유지 |

최종 화면의 어두운 테마에서 글자 대비가 낮던 문제를 Surface 적용으로 수정하고 Debug·Release를 다시 빌드했다. 네이티브 인식기 최적화 후 동일 검사도 다시 통과했다.

## 성능 측정

`docs/evidence/phase2/native-initial.json`은 초기 측정, `native-results.json`은 직전 검출 면 우선 검사와 ORB 특징점 수 조정 후 측정이다. 최적화 후 기록된 25개 입력의 중앙값 226ms, 최소 54ms, 최대 446ms다. 합성 같은 면 반복은 다수 54~126ms에 처리했지만 표시 면 일부는 전체 탐색이 필요했다.

서로 다른 시점의 에뮬레이터 부하가 통제되지 않았으므로 정밀한 속도 향상 비율을 주장하지 않는다. 이 수치는 모델 정확도, 실기기 처리 속도, 30fps 카메라 지원을 뜻하지 않는다.

## 실제 NVIDIA 호출: 실패도 포함

제공된 앞면과 표시 면을 축소해 실제 Firebase Auth/App Check를 거쳐 배포된 NVIDIA 서버로 보냈다. 테스트 입력은 purpose=PREVIEW, useState=UNUSED다. **UNUSED는 직접 지정한 상황이며 사진이 개봉 상태를 증명했다는 뜻이 아니다.**

| 요청 | 결과 |
|---|---|
| 앞면 최초 | HTTP 503 / UNAVAILABLE, 6,388ms. 안내 문구를 포함한 오류 응답 |
| 앞면 수동 재시도 | HTTP 200, 16,035ms. 음료팩·재질 추가 확인 |
| 표시 면 | HTTP 200, 61,799ms. 음료팩·재질 추가 확인 |
| 멸균팩 확인 후 재판정 | 두 경우 PREVIEW, 638/440ms. NVIDIA 재호출 없이 서버 규칙 적용 |
| 내부 오염·즉시 배출 판정 | 위 성공 결과의 contaminationScore와 decision은 null |
| 시험 계정 정리 | 실패·성공 회차 모두 본인 임시 계정 삭제 HTTP 200/success=true |

현재 공급자는 `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning`이다. Kimi K3의 성공을 새로 확인한 것은 아니다. 원문 결과는 `cloud-first-attempt.json`, `cloud-retry.json`에 있다. 이번의 실제 오류는 서버 응답으로 검사했다. 실제 오류 화면의 모든 종류를 수동 재현했다고 주장하지 않는다.

재실행용 `functions/scripts/live-carton-check.js`는 실제 비용이 드는 외부 요청이다. 등록된 Debug 기기와 현재 로컬 Firebase 설정이 필요하다. 충분히 축소한 JPEG 경로를 전달한다. 로그에는 인증 값이나 이미지 본문을 출력하지 않는다.

```powershell
$env:ECOSORT_DEVICE = 'emulator-5556'
node functions/scripts/live-carton-check.js result.json front.jpg nutrition.jpg
```

## 설치 환경에서 발생한 문제

처음 기존 `emulator-5554`에 connectedDebugAndroidTest를 실행했을 때 저장 공간 부족으로 설치가 실패했다. 이 실패 과정에서 테스트 도구가 기존 앱을 제거했다. 다른 사용자 앱·데이터를 지우지 않고 작업 폴더에 별도 `EcoSort_Phase2` AVD를 만들어 `emulator-5556`에서 검증했다. 새 기기 부팅 완료 전의 첫 설치도 실패하여 부팅 완료를 확인한 뒤 설치했다.

최종 APK는 `emulator-5556`에 설치했다. `emulator-5554`에 최신 앱을 설치했다고 주장하지 않는다. OpenCV 여러 ABI를 포함한 universal Debug APK는 약 175.4MB, Release unsigned는 약 151.7MB다. 배포 크기 최적화는 이번 범위에서 하지 않았다.

## 보안 확인 범위

- 현재 Firebase 키·프로젝트는 유지. 과거 키 삭제의 deleteTime과 현재 키 active를 확인했다.
- `app/google-services.json`은 로컬에 보존하고 Git 추적에서 제외했다. 예제에는 실제 값이 없다.
- HEAD 및 최종 staged 파일의 알려진 키 패턴 검사를 수행했다. NVIDIA·Google 키, OAuth·GitHub 토큰, 개인키 패턴을 검사한다.
- Debug/Release APK에서 NVIDIA 키·GitHub/OAuth 토큰·알 수 없는 Google 키 패턴과 NVIDIA 직접 호출 주소를 찾지 못했다. Firebase 공개 클라이언트 식별자만 허용했다.
- Debug `classes10.dex`에는 `BEGIN PRIVATE KEY`라는 단독 파서 상수가 있었다. NUL로 끝나는 문자열이며 전체 PEM 키 블록은 0개다. 캐시의 gRPC `io/grpc/util/CertificateUtils.class`에 동일 상수가 있음을 확인했다. 실제 개인키 발견으로 집계하지 않았다.
- 과거 커밋에는 설정 문자열이 남는다. 현재 키 경고는 폐기 완료로 처리하지 않았다. 모든 Git 기록이 비밀 검사 0건이라고 말하지 않는다.
- 패턴 검사는 모든 형태의 비밀 유출을 수학적으로 보장하지 않는다. Auth/App Check와 기존 서버 권한은 유지했다. 이번 단계에 Firebase Functions 재배포는 없었다.

## 평가 한계와 사람이 확인할 사항

등록용 사진과 같은 자료의 변형이므로 18/18과 6/6은 **기능 검사 결과**다. 다른 물품 사진 한 장은 충분한 오인식률 평가가 아니다. 조명·배경·거리·초점·찌그러짐이 다른 새 사진으로 독립 검증해야 한다. 일반 물체 인식 모델 학습, 오염도 측정, 멸균 상태 인증, 복제 포장 구별, 실제 카메라 스트림은 구현·검증하지 않았다.

발표 기기에서 APK 설치와 글자 가독성, 실제 새 사진 인식, 인터넷/App Check, 서버 응답 지연을 확인한다. 지역 배출 기준과 팀원의 활동 기록은 사람이 사실대로 보완한다. 실패·대기 화면을 포함해 시연하고 외부 AI 응답이 즉시 나온다고 약속하지 않는다.

제품 사진 6장은 사용자가 제공했다. 음성 사례 병 사진은 [Wikimedia Commons: Empty Plastic Bottle](https://commons.wikimedia.org/wiki/File:Empty_Plastic_Bottle.jpg), Echendu Tracy, CC0 1.0이며 기존 1단계 자료를 재사용했다.

## 증거 파일

`docs/evidence/phase2/`의 native-initial/results, cloud-first-attempt/retry, key-remediation, apk-audit, checks.json 및 tracking/lost/real-photo/handoff PNG. 영상은 제공된 `EcoSort-phase2-demo.mp4`에 저장했다. 움직임 실험의 실제 앱 녹화이며 카메라 촬영 영상이나 독립 실물 평가가 아니다.
