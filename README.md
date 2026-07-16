# DayBird

드래그 시간 블록, 7일 버드뷰, 일정 기반·포모도로 집중 타이머를 제공하는 로컬 우선 플래너입니다. 같은 React UI를 GitHub Pages PWA와 Capacitor Android APK로 배포합니다.

## 로컬 실행

```powershell
npm.cmd install
npm.cmd run dev
```

`http://127.0.0.1:5500`을 엽니다. 5500 포트가 사용 중이면 `npm.cmd run dev -- --port 5501`을 사용합니다.

## 검증

```powershell
npm.cmd run verify
npm.cmd run android:debug
```

빌드와 Android 패키징은 일반 터미널에서 실행합니다.

## GitHub Pages와 APK

1. 저장소 Settings → Pages → Source에서 **GitHub Actions**를 선택합니다.
2. `npm.cmd run signing:setup`으로 release keystore를 한 번 생성하고 GitHub Secrets에 등록합니다.
3. 생성된 `.android-signing` 폴더를 별도의 안전한 장소에 반드시 백업합니다. 이 폴더에는 다음 Actions secrets의 원본이 들어 있습니다.
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
4. `main`에 push하면 검증, 서명 APK 생성, PWA 배포가 순서대로 실행됩니다.

배포 주소는 `https://aretenald2018-sys.github.io/daybird/`, APK는 `/daybird/downloads/daybird.apk`입니다.

## 데이터와 플랫폼 차이

- 일정과 설정은 기기의 IndexedDB에만 저장됩니다. 설정에서 JSON 백업·복원을 사용할 수 있습니다.
- PWA는 열린 동안 타이머 완료를 감지합니다.
- APK는 로컬 알림, 정시 알람 설정, 햅틱을 추가로 사용합니다.
- APK에는 일정, 타임라인, 집중 타이머와 종합 대시보드 홈 위젯이 포함됩니다. 종합 대시보드는 최소 4×5 크기를 권장하며, 눌러서 DayBird를 열 수 있습니다.

## 종합 대시보드 데이터 연결

Budget의 설정 화면에서 **DayBird 연결**을 누르면 1회용 연결 링크가 열립니다. DayBird는 링크를 교환해 기기 전용 Firebase 계정으로 로그인하고, 권한이 제한된 대시보드 문서만 실시간 구독합니다. 새 데이터 알림은 FCM으로 받고, 누락에 대비해 15분 주기 WorkManager와 기기 재부팅 복구도 함께 사용합니다.

대시보드는 기본 4×6, 최소 4×5 크기를 지원합니다. 4×5에서는 전용 압축 레이아웃으로 같은 핵심 지표를 표시합니다. 스냅샷 계약은 `schemaVersion: 1`이며 리비전이 역행하거나 필수 영역/가중치 검증에 실패한 데이터는 캐시에 저장하지 않습니다.

```json
{
  "schemaVersion": 1,
  "revision": 42,
  "score": 87,
  "domains": {
    "food": { "score": 80 }, "health": { "score": 85 },
    "running": { "score": 90 }, "spending": { "score": 78 }, "wine": { "score": 88 }
  },
  "weights": { "food": 25, "health": 25, "running": 20, "spending": 20, "wine": 10 },
  "nutrition": { "progress": 80, "actualKcal": 1840, "targetKcal": 2300 },
  "workouts": [{ "label": "하체", "value": "110kg × 8", "status": "달성" }],
  "running": { "paceChangePct": 2.8, "cadenceChangePct": 1.6, "trend": [2, 4, 3, 5, 6, 5, 8] },
  "spending": { "savings": 12400, "monthSpent": 312000, "weeklyChangePct": -6.2 },
  "wine": { "name": "Chianti Classico Riserva 2021", "rating": 4.1 }
}
```
