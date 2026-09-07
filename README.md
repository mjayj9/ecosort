# EcoSort · 에코소트

사진으로 물품을 관찰하고 **사용자가 확인한 상태에 따라 배출 준비를 안내하는 Android MVP**입니다. 사진 분석은 실제 Firebase와 NVIDIA NIM을 사용합니다.

현재 모델은 **Nemotron 3 Nano Omni**입니다. Kimi K3 설정은 유지하지만 이 환경의 성공은 확인하지 못했습니다. 독자 학습 모델이나 실시간 영상 인식 기능은 아직 없습니다.

사진 촬영/선택 → JPEG 축소·압축 → 목적·사용 상태 선택 → 실제 Firebase Auth/App Check → 서버 AI 관찰 → 내용물·잔여물·재질 확인 → 출처 있는 한국어 준비 안내. 답변 수정은 저장한 관찰을 서버에서 재판정하며 NVIDIA를 다시 호출하지 않습니다.

미개봉 제품에 현재 세척이 필요하다고 단정하지 않습니다. 오염도 점수와 AI 자체 확률을 화면에서 제거했습니다. 현재 안내는 지역 수거 가능 여부를 확정하지 않는 **공통 준비 안내**입니다. 모르는 상태나 애매한 사진은 보류 또는 재촬영으로 안내합니다.

- [실행·키 설정·3분 시연·사업계획서 수정](COMPETITION_UPDATE.md)
- [실제 성공·실패와 검증 한계](VERIFICATION_REPORT.md)
- [승인된 ①~⑧ 계획](docs/PHASE1_PLAN.md)
- [커밋별 복구 방법](docs/PHASE1_CHECKPOINTS.md)
- [안내 규칙과 공식 출처](docs/GUIDANCE_RULES.md)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
cd functions
npm.cmd test
```

클라우드 앱 1.2(versionCode 3)과 V2 서버를 함께 사용해야 합니다. 구버전 APK는 업데이트 안내를 받습니다. 새 Debug 기기는 앱 실행 후 `npm.cmd run register:device -- 기기ID`로 App Check 등록이 필요합니다. 현재 등록된 기기의 실제 익명 로그인을 시연 경로로 사용합니다. Play Store는 업데이트하지 않았습니다.

NVIDIA 키는 서버 환경변수/Firebase Secret만 사용합니다. 사진·원문 응답은 Firestore에 저장하지 않습니다. 서버는 재판정을 위해 정규화한 관찰·사용자 답변·결과·제한 메타데이터를 저장합니다. 포인트·쿠폰·단지 순위·광고는 향후 계획입니다.

2단계부터 실제 Firebase 설정은 Git에서 제외합니다. 새 체크아웃에서는 루트 `configure-firebase.ps1 -Source "다운로드한 google-services.json 경로"`를 먼저 실행하세요. 현재 키는 유지했고, 과거 프로젝트 노출 키만 폐기했습니다. 기존 커밋의 소스는 복구 가능하지만 과거 키는 복구해 사용하지 마세요.
