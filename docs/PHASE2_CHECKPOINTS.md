# 2단계 번호별 커밋과 복구

원격: https://github.com/mjayj9/ecosort

브랜치: `phase2/local-carton-tracking`. 기존 `origin`과 두 저장소의 main은 변경하지 않는다.

| 구분 | 커밋 | 내용 |
|---|---|---|
| 시작 기준 | `7b8268f` | 1단계 완성 버전 보존 |
| ① | `cea55ef` | 실제 설정 추적 제거·이전 키만 폐기·hook/CI 검사 |
| ② | `7ea8242` | 제공 사진 6면 보정 자료·OpenCV 의존성 |
| ③ | `1d107f1` | ORB/RANSAC 인식기·추적 상태·실제 Android 검사 |
| ④ | `f2c3045` | 카메라 없는 조작 화면·원본 사진 연결·반복 인식 최적화 |
| ⑤ | 이 문서를 포함하는 `test(phase2-05)` 커밋 | 최종 검증 기록·발표/실행 문서·증거 |

```powershell
git log --oneline -6
git log -1 --format='%h %s' --grep='phase2-05'
```

현재 작업을 지우지 않고 별도 폴더에서 기준을 확인한다. 이미 존재하는 폴더 대신 새 경로를 선택한다.

```powershell
cd C:\Users\admin\StudioProjects\ecosort_1
git worktree add --detach ..\ecosort-phase2-check f2c3045
```

각 체크포인트는 개발 이력이다. ②·③만으로 완성 UI가 제공되는 것은 아니다. 다른 폴더에서는 Android SDK 설정과 현재 Firebase 로컬 설정을 준비해야 한다. ①의 보안 변경을 취소해 키를 다시 추적하지 않는다. 과거 키를 복원·재사용하지 않는다.

개별 변경을 취소하려면 의존성을 검토한 뒤 `git revert <커밋>`으로 취소 이력을 남긴다. 이번 작업은 `reset --hard`, 과거 기록 재작성, 강제 푸시를 하지 않았다. 과거 소스 복구와 Firebase 외부 상태 복구는 별개다. 삭제된 키나 Secret, 계정·데이터는 Git이 복원하지 않는다.

현재 키·프로젝트는 사용자 지시에 따라 그대로 유지했다. 현재 키의 역사상 노출 기록도 삭제 완료로 표현하지 않는다. 세부 내용은 `docs/KEY_REMEDIATION.md`를 본다.
