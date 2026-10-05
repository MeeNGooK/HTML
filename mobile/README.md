# Douyin Pocket

Douyin 공유 텍스트를 그대로 붙여넣으면 앱이 그 안에서 Douyin URL만 골라 공개 영상 페이지를 열고, 공개된 재생 스트림 중 가장 높은 화질을 저장합니다. 60fps 스트림이 있으면 60fps 후보를 우선합니다.

## Android APK

GitHub Actions의 [Douyin Pocket APK workflow](https://github.com/MeeNGooK/HTML/actions/workflows/douyin-pocket-apk.yml) 실행 결과에서 `DouyinPocket-APK`를 내려받거나 [Releases](https://github.com/MeeNGooK/HTML/releases)에서 최신 APK를 받으세요. Android 10 이상에서 설치할 수 있습니다. 개인 테스트용 서명 APK입니다.

1.0 및 1.0.1 APK는 매 빌드마다 다른 임시 키로 서명되어 서로 업데이트할 수 없습니다. 이번 앱은 별도 ID `com.meengook.douyinpocket.app`으로 설치되므로 기존 설치와 충돌하지 않습니다. 새 버전을 확인한 뒤 이전 Douyin Pocket을 삭제할 수 있습니다. 이후 CI 빌드는 같은 테스트 서명 키를 재사용합니다.

## 사용법

1. Douyin에서 **공유 → 링크 복사**를 누릅니다.
2. Douyin Pocket에 공유 문구 전체를 붙여넣고 **영상 찾기**를 누릅니다.
3. 다운로드는 `Download/DouyinPocket`에 저장됩니다.

앱은 메시지에서 `v.douyin.com`, `douyin.com`, `iesdouyin.com` 링크만 선택합니다. 그 외 문구와 날짜·코드는 무시합니다. 공유 메뉴에서 Douyin Pocket을 직접 선택할 수도 있습니다.

## 지원 및 제한

- 공개 Douyin 동영상 공유 링크를 처리합니다. 사진 게시물, 계정 전용 콘텐츠, 삭제 영상은 지원하지 않습니다.
- 공개 페이지의 서버 렌더링 데이터가 없을 때는 Android WebView에서 실제 영상 플레이어가 고른 스트림을 읽습니다. 60fps 원본이 공개 데이터에 있으면 우선 선택합니다. 재생 가능한 스트림은 지역, 앱 버전, 게시물 설정에 따라 다르며, HD/60fps 원본이 항상 제공되는 것은 아닙니다.
- Douyin 페이지의 비공식 공개 응답 구조에 의존하므로 구조 변경, 접근 제한, CDN 차단 시 실패할 수 있습니다. 로그인이나 외부 추출 서버는 사용하지 않습니다.
- 본인 소유이거나 저장 허락을 받은 콘텐츠에 사용하세요. Douyin과 관련 없는 독립 앱입니다.

## 빌드

Node.js 22, Java 21, Android SDK 36 환경에서 `npm ci`, `npm run sync`, `cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug` 순서로 빌드합니다. GitHub Actions는 APK와 SHA-256 체크섬을 생성하고 GitHub Release에 게시합니다.
