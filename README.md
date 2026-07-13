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
- Android 홈 위젯, 클라우드 동기화, 다크 테마는 후속 범위입니다.
