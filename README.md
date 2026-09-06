# EcoSort · 에코소트

**실제 Android → Firebase 클라우드 → NVIDIA NIM 이미지 판독을 확인한 MVP. Mock 판독은 없다.**

현재 모델은 사용자 승인으로 전환한 **Nemotron 3 Nano Omni**다. Kimi K3는 설정을 유지하지만 이 환경에서 판독 성공을 확인하지 못했다. 앱은 실제 모델명을 결과에 표시한다.

촬영/사진 선택 → 축소·JPEG 압축 → Firebase Auth/App Check → 서버 NIM 호출 → 9개 필드 검증 → 오염·세척·배출·근거·주의사항. 낮은 신뢰도/빈 사진은 재촬영으로 안내한다. 공급자 스트림 일시 오류만 총 105초 안에서 1회 재시도한다.

NVIDIA 키는 서버 환경변수/Firebase Secret만 사용한다. 사진·키를 Firestore에 저장하지 않는다. 포인트·쿠폰·단지 순위·광고는 향후 계획이다.

- [실행·기기 등록·키 설정·3분 발표](COMPETITION_UPDATE.md)
- [빌드·실제 판독·제한 사항](VERIFICATION_REPORT.md)
- 클라우드 Debug 빌드: `gradlew.bat :app:assembleDebug`
- 새 Debug 기기 등록: functions 폴더에서 `npm.cmd run register:device -- 기기ID`
- 로컬 개발: `start-local.ps1`, APK는 `-PecoLocal=true`
- 서버 테스트: functions 폴더에서 `npm.cmd test`
- Android 테스트: `gradlew.bat :app:testDebugUnitTest`

현재 PC 에뮬레이터에는 클라우드 APK와 App Check 등록이 준비되어 있다. 앱에서 ‘사진 분석 시작하기’를 누르면 실제 익명 인증으로 이용한다. 인터넷이 필요하며 로컬 서버는 필요 없다. Play Store는 업데이트하지 않았다.
