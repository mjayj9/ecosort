# 키 노출 조치

2026-09-07 GitHub 경고를 값 출력 없이 확인했다. 두 키는 Firebase Android client 설정의 Google API Key이며 Generative Language API는 허용 목록에 없었다. 현재 키는 사용자 지시로 유지했다. 새 키나 새 프로젝트를 만들지 않았다.

과거 studio-4335756557-ed258 프로젝트의 노출 키 하나만 사용자 승인으로 폐기했고 deleteTime을 확인했다. 현재 focused-rig-vcf5x 키는 active 상태를 확인했다.

실제 app/google-services.json은 Git 추적에서 제거하고 로컬 파일은 보존했다. 커밋 hook과 GitHub CI는 Firebase 키를 포함한 알려진 키 패턴을 예외 없이 차단한다. 새 체크아웃은 configure-firebase.ps1로 현재 Firebase 설정을 로컬에 설치한다.

기존 커밋에는 예전 설정 문자열이 남아 있다. 복구 커밋 해시 보존을 위해 기록을 강제 재작성하지 않았다. 삭제된 과거 키는 재사용하지 않는다. 현재 Firebase 클라이언트 키는 APK에 포함되는 공개 식별자이며 숨김 자체가 데이터 보호 수단은 아니다. Auth/App Check/서버 권한과 API 제한을 유지한다. 현재 키의 기존 GitHub 경고를 폐기 완료로 표시하지 않는다.

근거: https://firebase.google.com/docs/projects/api-keys
