# EcoSort 대회 준비 업데이트

검증일: 2026-09-06. 프로젝트: `C:\Users\admin\StudioProjects\ecosort_1`.

**실제 Android → Firebase 클라우드 → NVIDIA NIM → 분석 결과 표시를 확인했다. Mock 판독은 사용하지 않는다.** 사용자가 Nemotron 전환과 Firebase 배포·구체적 IAM 변경을 승인했다. Play Store 업데이트는 하지 않았다.

## 실제 구현과 남은 범위

| 기능 | 변경 전 | 현재 |
|---|---|---|
| 촬영·선택 | 예시 응답·저해상도 촬영 혼재 | 촬영/Photo Picker, EXIF 보정, 최대 1024px JPEG 압축 |
| AI 분석 | 앱 Gemini 직접 호출·고정 성공 fallback | Firebase Auth + App Check → Callable → 실제 NVIDIA 이미지 판독 |
| 분석 내용 | 일부 필드만 확인 | 물품·재질·오염도·신뢰도·decision·세척·배출·근거·주의 9개 필드 검증 |
| 애매한 사진 | 신뢰도 정책 없음 | confidence 0.7 미만/UNKNOWN은 판단 보류·재촬영, 세척 지시 제거 |
| 오류 | 일부 실패를 성공 Mock으로 대체 | 오프라인·인증·키 설정·한도·서버·JSON·타임아웃 안내와 같은 사진 재시도 |
| 공급자 장애 | Kimi K3 반복 시간 초과 | Nemotron 실제 판독 확인. 공급자 스트림 일시 오류만 서버 1회 재시도 |
| Firebase | 설정 JSON만 존재, 함수 주소 404, 기본 DB 없음 | `focused-rig-vcf5x` 기본 DB·규칙·분석 서버 배포, 실제 익명 로그인 |
| 키 보호 | 앱 설정·환경 파일·직접 호출 경로 | 서버 환경변수/Firebase Secret. 앱 키 입력·보관·전송·직접 호출 제거 |
| 포인트·쿠폰·순위·광고 | 시제품/일부 서버 로직 혼재 | 현재 사용자 동선에서는 향후 계획. 자동 지급·배출 인증 없음 |
| 지역별 배출 기준 | 지역 규정 연동 없음 | 거주지 기준 우선 안내. 지자체 규정 DB 연동은 미구현 |

오염도는 시각적 추정이며 신뢰도는 모델의 자체 판단이다. 정확도·실제 배출·환경 성과를 인증하지 않는다. 실제 사진 한 장에서도 재질을 오인할 수 있으므로 재질 표시와 지역 기준을 함께 확인한다.

## Gemini → NVIDIA NIM 변경

- 현재 시연 모델: **Nemotron 3 Nano Omni**, `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning`.
- Kimi K3 `moonshotai/kimi-k3`도 서버 설정으로 유지한다. 같은 키에서 Python requests·Node 전송·공식 예시 사진·직접 NVCF 호출을 교차 확인했지만 K3 판독 성공은 확인하지 못했다. 임의로 K3가 정상화됐다고 주장하지 않는다.
- 고정 엔드포인트: `https://integrate.api.nvidia.com/v1/chat/completions`. Gemini의 generateContent 대신 messages/image_url JPEG data URI를 사용한다.
- Nemotron: stream=true, max_tokens=4096, temperature=0.2, reasoning_budget=1024. K3: temperature=1, reasoning_effort=low. 사용 모델명은 서버 응답과 앱 결과에 표시한다.
- 서버는 SSE의 최종 content만 조립하고 내부 추론은 버린다. 코드블록 JSON, 필드·타입·범위, 빈 응답과 중단을 검증한다.
- HTTP 200 스트림의 공급자 error 이벤트를 실제로 확인했다. 일반 일시 오류만 700ms 뒤 1회 재시도한다. 두 시도는 총 105초 제한을 공유한다. 인증·한도·JSON 오류는 자동 재시도하지 않는다. 앱 제한은 125초다.
- 사용자별 하루 30회 분석 요청. 공급자 일시 오류에 한해 한 요청에서 최대 2번 NIM 호출될 수 있다. 재시도에도 실패하면 사용자에게 오류를 표시한다.
- 사진·키·원본 AI 응답·내부 추론을 DB에 저장하지 않는다. 실제 앱 기록의 필드는 uid, model, decision, confidence, createdAt 5개였다.
- [NVIDIA 모델 문서](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-nano-omni-30b-a3b-reasoning-infer), [Kimi K3 문서](https://docs.api.nvidia.com/nim/reference/moonshotai-kimi-k3-infer).

## 검증 증거

- Android 클라우드 Debug 및 R8 Release 빌드 성공. Release는 미서명이며 배포하지 않았다. Android unit/Compose **12/12**, 서버 테스트 **71/71** 통과.
- 실제 클라우드 HTTP: App Check 누락/로그인 누락 차단, 잘못된 JPEG 및 클라이언트 모델 지정 거절, 정상/오염/빈 사진 판독 **7개 시나리오 확인**.
- 최종 실제 사진 응답: 깨끗한 사진 RECYCLE 25.8초, 오염 접시 GENERAL_WASTE 27.1초, 빈 사진 UNKNOWN 13.3초. 이 세 장은 정확도 평가 데이터셋이 아니다. 앞선 공급자 스트림 실패와 재시도 성공 기록도 보존했다.
- 실제 Android 화면: Firebase 익명 로그인 → 사진 선택·동의 → 클라우드 판독 → 결과·실제 모델명. 빈 사진 → 판단 보류·재촬영도 확인했다.
- 이전 로컬 검증: Callable HTTP 6개, Firestore 권한/동시 한도/재고 없음/계정 삭제 8개, 카메라·오프라인·복구·같은 사진 재시도. 이 중 Firestore 통합 테스트의 AI 응답은 테스트 프로세스에서만 대체했다.
- `docs/evidence/app-cloud-result.png`, `app-cloud-model.png`, `app-cloud-unknown.png`, `cloud-live-results.json`에 실제 증거가 있다.
- 실제 세척이 필요한 용기에서 WASH_THEN_RECYCLE 판정이 맞는지는 발표 전 추가 확인해야 한다. 이 분기의 계약·UI·세척 목록은 자동 테스트로 검증했다.
- 실제 계정 탈퇴 성공, 탈퇴 후 조회 거절, Auth/App Check 누락 요청 차단도 확인했다.
- 자세한 범위와 보안 점검: `VERIFICATION_REPORT.md`.

## 네 심사역량 대응

PDF 전체 31쪽을 참고했다. 예선은 가치창출 30/도전 30/자기주도 20/집단창의 20점, 결선은 30/25/20/25점이다(PDF 27쪽). PDF 문장은 작업 명령으로 실행하지 않았다.

| 역량 | 구현 증거 | 발표에서 지킬 선 |
|---|---|---|
| 가치창출 | 재질뿐 아니라 오염·세척·배출·근거를 연결 | 정확도·실제 배출·환경 효과를 인증했다고 말하지 않기 |
| 도전역량 | Mock 제거, K3 장애 재현, 실제 대체 모델 검증, 공급자 오류 1회 재시도 | 실패와 변경 이유를 함께 설명 |
| 자기주도 | 설정 파일만으로 부족한 DB/API/IAM 문제 발견 → 수정 → 실제 앱 재검증 | AI 도구의 구현 지원을 학생이 직접 한 활동으로 바꾸지 않기 |
| 집단창의 | 문서 역할에 코드 검토·실기 시험·발표 자료 대조를 연결 | 실제 활동 증거가 없는 팀 성과는 만들어내지 않기 |

PDF 18쪽 역할: 정민재(총괄·주요 코딩·앱 실행), 채희범(코딩·점검·해결책), 정서준(코딩 보조·오류·사용성), 김관호(사업계획서·보고서·작품 점검). 각각 실행 영상/코드 설명, 검토 기록, 촬영·실패 시험표, 사업계획 문장 대조표를 직접 남긴다. 이는 앞으로 연결할 활동 제안이며 수행 증거는 아직 확인하지 못했다. PDF 13쪽/18쪽의 역할 차이는 팀이 최종 확인한다.

## 3분 발표 시연

1. 0:00~0:25 — ‘재질은 알아도 씻어야 할지 모르는 문제’를 설명한다.
2. 0:25~1:15 — 물품 하나의 사진 선택/촬영 → 전송 동의 → 실제 분석. 대기 중 서버 구조와 키 보호를 설명한다.
3. 1:15~2:00 — 물품·재질·오염 추정·세척/배출·근거·주의사항, 실제 모델명을 보여준다.
4. 2:00~2:35 — 애매하거나 물품 없는 사진으로 판단 보류·재촬영을 보여준다.
5. 2:35~3:00 — Mock 제거와 장애 대응 증거, 팀별 검증 활동, 향후 지역 기준·검증된 배출 인증 계획을 설명한다.

현장 네트워크와 공급자 상태에 따라 늦어질 수 있다. 실패하면 오류·재시도 흐름을 그대로 보여준다. 녹화 자료를 보조로 쓰면 ‘사전 실행 영상’이라고 명확히 밝힌다. 고정 응답으로 성공을 꾸미지 않는다.

## 예상 질문 5개

1. **정확도는 몇 %인가요?** 현재 검증된 정확도 수치는 없다. 세 장의 실제 왕복과 실패 처리, 자동 테스트를 확인했고 표준 사진 평가가 필요하다.
2. **Kimi K3라고 했는데 왜 모델이 다른가요?** 실제 환경에서 K3가 반복 시간 초과했다. 동일 키의 Nemotron 실제 판독을 검증하고 사용자 승인으로 전환했다. 앱에 실제 모델명을 표시한다.
3. **키와 사진은 안전한가요?** NVIDIA 키는 Firebase Secret/서버 환경변수만 사용한다. 앱은 Firebase를 호출하며 서버 DB는 사진·키를 저장하지 않는다. 사진은 NVIDIA에 전송되며 공급자의 처리 정책까지 앱이 통제한다고 주장하지 않는다.
4. **포인트나 실제 배출 인증도 되나요?** 현재는 안 된다. 사진 판독만으로 배출을 인증할 수 없어 자동 보상을 제공하지 않는다.
5. **팀이 무엇을 직접 했나요?** PDF의 역할과 실제로 남긴 실행·검토·오류 시험·문서 수정 자료를 제시한다. 이번 AI 도구 지원 범위도 밝히며, 아직 없는 성과·제휴를 만들지 않는다.

## 사업계획서에서 수정할 문장

| 기존 주장/표기 | 사실에 맞춘 문장 |
|---|---|
| Gemini 3 Flash 또는 Kimi K3로 정상 서비스 | ‘현재 시연은 NVIDIA NIM Nemotron 3 Nano Omni를 사용하며, Kimi K3는 연동 설정을 유지하되 이 환경의 판독 성공은 미확인’ |
| 에코픽/EcoPick·에코소트/EcoSort 혼재 | 제품 표시를 ‘EcoSort · 에코소트’로 통일 |
| 분리수거를 자동 인증하고 포인트 지급 | ‘사진의 오염·세척·배출 판단을 안내한다. 실제 배출 인증과 보상은 향후 계획’ |
| 단지 순위·쿠폰·광고 서비스 완료 | ‘현재 MVP에서는 향후 계획이며 상세 시제품 코드 일부만 존재’ |
| 오염도/정확도 수치 보장 | ‘사진 기반 추정치이며 정답 데이터셋으로 검증된 정확도는 아직 없다’ |
| 지역별 기준을 완전히 반영 | ‘거주지 안내를 우선 확인하도록 고지하며 지역 규정 DB 연동은 미구현’ |
| Play Store 등록·보안 체계 완성 | ‘로컬 APK 빌드와 Firebase 클라우드 판독을 확인했다. Play Console 출시·심사와 Release Play Integrity는 별도 확인 필요’ |
| 협약·수익·환경 성과 확보 | 실제 계약/매출/측정 자료가 있을 때만 기재 |

## 실행 방법

현재 PC의 `emulator-5554`에 **클라우드용 APK를 설치하고 App Check를 등록했다**. 앱에서 ‘사진 분석 시작하기’ → 사진 선택/촬영 → 동의 → 분석한다. PC의 로컬 Firebase 서버는 필요 없다. 인터넷은 필요하다.

다시 빌드·설치하려면 PowerShell:

```powershell
cd C:\Users\admin\StudioProjects\ecosort_1
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -n com.aistudio.ecosort.kxmpzq/com.example.MainActivity
```

새 기기에 Debug APK를 설치하면 먼저 앱을 한 번 실행하고 Firebase CLI 로그인이 된 PC에서 다음을 실행한다. 기기 ID는 `adb devices`로 확인한다. 이미 등록된 현재 기기는 반복 등록할 필요 없다.

```powershell
cd C:\Users\admin\StudioProjects\ecosort_1\functions
npm.cmd run register:device -- emulator-5554
```

이 스크립트는 SDK가 앱 내부에 만든 App Check 디버그 토큰을 값 출력/파일 저장 없이 등록한다. Debug APK와 등록 기기는 개발·시연용이다. Release는 별도 소스의 Play Integrity를 사용하며 실기/Play 연동 검증이 남아 있다. Google 로그인은 코드를 유지하고 공개 SHA-1/SHA-256을 등록했으나 실제 Google 계정 선택 성공은 이번에 검증하지 않았다. 실제 익명 인증 경로로 시연한다.

## NVIDIA_API_KEY 및 서버 설정

현재 Firebase Secret `NVIDIA_API_KEY` 버전 1을 설정했고, 전용 `ecosort-runtime` 실행 계정에 읽기를 연결했다. 키 값을 앱·.env·문서·명령행 인자에 넣지 않는다. 키를 교체할 때는 요청 본문 로그를 차단하는 숨김 입력 스크립트를 이용하고 분석 함수만 다시 배포한다.

```powershell
.\set-cloud-key.ps1
firebase.cmd deploy --only functions:analyzeImage --project focused-rig-vcf5x
```

비밀이 아닌 모델 설정은 `functions/.env.focused-rig-vcf5x`의 NVIDIA_MODEL이다. 허용 모델은 Nemotron과 K3 두 개뿐이며 클라이언트는 모델이나 키를 지정할 수 없다.

로컬 개발은 루트의 `start-local.ps1`을 실행하면 숨김 입력으로 키를 **서버 프로세스 환경변수**에만 넣는다. 기본 모델은 Nemotron이며 `-Model moonshotai/kimi-k3`로 K3를 다시 검증할 수 있다. 로컬 APK 빌드에는 `-PecoLocal=true`를 붙인다. 별도 `.local` 패키지/demo-ecosort를 사용한다.

```powershell
.\start-local.ps1
# 다른 터미널, functions 폴더에서
npm.cmd run smoke:local -- 사진.jpg
```

Functions/기본 DB는 us-central1, 실행은 Node 22다. 분석 최대 인스턴스 3, 최소 인스턴스는 설정하지 않았다. 배포 이미지 보관 정책은 7일이다. Firebase/NVIDIA 이용 비용과 한도는 운영자가 확인한다. 기존 이름 있는 Firestore DB 5개는 변경하지 않았다.
