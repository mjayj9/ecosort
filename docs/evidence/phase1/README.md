# 1단계 실제 검증 자료

현재 V2 증거는 이 폴더에 있다. 상위 폴더의 수치 오염도/RECYCLE 결과는 이전 버전 이력이다.
성공뿐 아니라 cloud-initial-failure.json과 app-needs-photo.png도 보존했다.
cloud-state-flow.json과 local-state-flow.json은 실제 NIM 호출을 포함한다. 사용자 답변 변경은 상태 분기 테스트 입력이며 이미지 정답 데이터가 아니다.
verification-summary.json에 검사 범위, 테스트 수, APK와 사진 해시를 기록했다.

공개 실사진: [Empty Plastic Bottle](https://commons.wikimedia.org/wiki/File:Empty_Plastic_Bottle.jpg), Echendu Tracy, 2025-11-27, CC0 1.0. 공개 960px 썸네일을 테스트에 사용했다. 사진이나 응답을 APK에 넣은 Mock 구현이 아니다.

app-question.png: 실제 서버의 내용물 질문. app-offline.png: 오프라인에서도 사진/답변 보존. app-preparation.png: 복구·재시도 뒤 실제 내용물 준비 안내. app-evidence.png: 사용자 확인 우선과 실제 모델/기록. app-reset.png와 app-reset.json: 새 사진의 상태 초기화.
