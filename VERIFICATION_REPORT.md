# EcoSort 검증 기록 · 2026-09-06

## 직접 확인한 결과

| 검사 | 결과와 범위 |
|---|---|
| 실제 핵심 경로 | Android 사진 선택 → 실제 Firebase Auth/App Check → 클라우드 Callable → NVIDIA NIM → 결과 표시 성공 |
| 실제 모델 | Nemotron 3 Nano Omni. 사용자 승인으로 변경. Kimi K3는 반복 시간 초과로 성공 미확인 |
| 클라우드 사진 3종 | 최종 서버에서 RECYCLE 25,847ms / GENERAL_WASTE 27,084ms / 빈 사진 UNKNOWN 13,339ms. 모두 HTTP 200, 모델·scanId·9개 필드 검증 |
| 클라우드 보호 4종 | App Check 누락/로그인 누락 401, 잘못된 JPEG/클라이언트 모델 지정 400 |
| 실제 앱 결과 | 정상 결과와 실제 모델명·기록 ID, 빈 사진 판단 보류·재촬영 화면 확인 |
| Debug 빌드 | 성공, 클라우드 패키지 com.aistudio.ecosort.kxmpzq 설치 확인 |
| Release 빌드 | R8 축소·lintVital 통과, unsigned APK. 설치·Play Store 업데이트하지 않음 |
| Android 테스트 | 12/12 통과: 결과 계약 5, 이미지 2, Compose 결과 카드 2, 기존 3 |
| 서버 테스트 | 71/71 통과: 계약·JSON·인증·상태별 오류·SSE·제한된 재시도·공통 마감 시간 |
| 이전 로컬 통합 | Callable HTTP 6개, Firestore 8개 통과. Firestore 통합의 NIM 응답은 테스트 내 대체 |
| 이전 실기 오류 | 카메라 촬영/복귀, 오프라인/복구, 키 미설정, 실제 K3 시간 초과와 같은 사진 재시도 확인 |
| 저장 필드 | 실제 앱 scan 문서는 confidence, createdAt, decision, model, uid만 저장. 사진·키 비저장 확인 |
| 비밀 패턴 검사 | 현재 작업 파일·Git blob 133개·클라우드 APK에서 NVIDIA/개인키 등 알려진 패턴 탐지 0. APK의 정확히 일치하는 Firebase 공개 클라이언트 식별자만 제외 |
| 실제 경로 Mock | AiVisionRepository/Scanner/index/analysis에 고정 성공 판독 없음. NIM URL은 서버 파일에만 존재 |
| diff 검사 | 에이전트 수정 파일의 git diff --check 통과. 기존 사용자 gradle.properties·Firebase 첨부 파일은 그대로 보존 |
| 계정 삭제 | 실제 탈퇴 HTTP 200·success=true, 탈퇴 후 계정 조회 HTTP 400. Auth/App Check 누락은 각각 401로 차단. 전용 실행 계정 확인 |

클라우드 핵심 시나리오 7개는 마지막 검증에서 외부 수동 재요청 없이 통과했다. 서버는 공급자 스트림 일시 오류에만 1회 재시도한다. 이전 호출에서는 공급자 error 이벤트 때문에 실패하거나 수동 재시도가 필요했으며 해당 실패도 기록했다. 모든 호출이 항상 성공한다고 주장하지 않는다.

## 원인과 수정

1. 첨부 google-services (1).json은 프로젝트의 설정과 동일했다. 설정 파일만으로 서버가 생기는 것은 아니었다. Cloud Functions API 비활성, 분석 주소 404, 기본 Firestore DB 부재를 확인했다.
2. 실제 익명 Auth, 별도 기본 DB(us-central1), 규칙, Functions와 NVIDIA Secret을 설정했다. 기존 이름 있는 DB 5개는 수정하지 않았다.
3. 실제 NIM 연결에서 K3 반복 시간 초과를 재현했다. Python requests와 공식 예시 이미지로도 성공하지 못했다. 같은 키의 Nemotron 실제 응답을 검증해 사용자 승인으로 변경했다.
4. NVIDIA의 HTTP 200 SSE에 error 이벤트가 섞이는 현상을 확인했다. 원문 없이 고정 진단 코드만 기록해 원인을 구분했다. 이 오류만 서버에서 최대 1회 재시도하며 105초 전체 마감 시간을 공유한다.
5. 첫 Cloud Build의 소스 버킷 경합과 빌드 계정 권한 부족을 확인했다. 구체적 IAM 승인을 받고 빌드 역할을 보완하고 별도 ecosort-runtime 계정을 사용했다. 초기 빌드 계정에 붙었던 NVIDIA Secret 읽기 권한은 회수했다.
6. Debug는 등록된 기기의 App Check debug provider, Release는 Play Integrity 소스로 분리했다. 앱에 비밀 디버그 토큰을 내장하지 않는다.

## 보안과 검증 한계

- NVIDIA 키는 사용자 숨김 입력 → 서버 프로세스 환경변수 → Firebase Secret으로 전달했고, 요청 본문/응답 본문 로그를 차단했다. 키 값은 코드·APK·Git·문서에 넣지 않았다. 패턴 검사는 모든 가능한 비밀 부재의 수학적 증명은 아니다.
- **별도 보안 확인 사항:** Firebase Auth 설정 조회 중 hashConfig.signerKey가 도구 출력에 포함된 실수가 있었다. 해당 값을 파일·코드·APK에 복사하지 않았고 이후 조회는 필드 제한을 적용했다. NVIDIA 키 노출과는 별개다. 프로젝트 소유자는 대화 공유 범위를 확인하고 해당 Firebase Auth 설정의 대응 필요성을 확인해야 한다.
- 실제 사진은 기존 프로젝트 자료 2장과 검증용 회색 빈 이미지다. 재질의 정답·사진 출처·권리와 배출 기준을 검증한 데이터셋이 아니다. 판독 결과의 정확성을 보장하지 않는다. 특히 접시 재질 등은 실제 표시를 확인해야 한다.
- 실제로 세척 후 재활용해야 하는 용기의 WASH_THEN_RECYCLE 분기는 자동 테스트로 검증했으며, 정답을 아는 실물 사진으로 추가 확인이 필요하다.
- Google 계정 로그인은 코드를 유지하고 공개 인증서 지문을 등록했으나 실제 계정 선택 성공은 미검증이다. 시연은 실제 Firebase 익명 로그인을 사용한다.
- Release Play Integrity, 실제 휴대전화 카메라 환경, Play Console 출시/심사, 쿠폰 계약·매출·환경 성과는 확인하지 않았다.
- 이전 전체 lintDebug는 오류 0/경고 58이었다. 이번 변경 뒤에는 Debug/Release 빌드와 unit/Compose 및 Release lintVital을 실행했다. 전체 lintDebug를 반복 실행한 것으로 주장하지 않는다.
- 테스트 서버 Node 24와 배포 Node 22 차이가 있었다. 실제 배포 Node 22에서 사진 왕복을 검증했다.
- Debug APK는 새 기기마다 App Check 등록이 필요하다. 현재 emulator-5554는 등록을 완료했다. 인터넷이 끊기면 AI 판독할 수 없다.

## 재현 자료

- 자동 테스트: functions/test/, app/src/test/java/com/example/
- 실제 클라우드: docs/evidence/cloud-live-results.json
- 수정 전 공급자 실패: docs/evidence/cloud-initial-validation-failure.json
- 화면: docs/evidence/app-cloud-result.png, app-cloud-model.png, app-cloud-unknown.png
- DB 필드 확인: docs/evidence/cloud-storage-check.json
- 실행·NVIDIA Secret 설정·모델 변경·발표: COMPETITION_UPDATE.md
- 계정 삭제: docs/evidence/cloud-account-deletion.json, cloud-account-only-results.json.
- 계정 삭제 함수의 HTTP 진입 권한은 사용자 별도 승인 후 설정했고, 내부 Auth/App Check/본인 uid 제한을 실제 검증했다.

현재 클라우드 APK SHA-256: A31CC47F982EA7AE2690527985AD861AC3B03B0145470CA58C5990AED17D9466.
