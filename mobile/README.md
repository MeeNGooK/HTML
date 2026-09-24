# Insta Pocket

인스타그램 게시물의 사진과 동영상을 휴대폰에 저장하는 Android 앱입니다. 한국어 UI, 링크 붙여넣기, Android 공유 메뉴, 여러 항목 선택, 백그라운드 다운로드, 진행 상태, 저장 파일 열기를 제공합니다.

## APK 받기

1. [GitHub Actions](https://github.com/MeeNGooK/HTML/actions/workflows/insta-pocket-apk.yml)에서 성공한 최신 실행을 엽니다.
2. Artifacts의 **InstaPocket-APK**를 내려받아 압축을 풉니다. GitHub 로그인이 필요할 수 있습니다.
3. Android 10 이상 기기에서 `InstaPocket-debug.apk`를 설치합니다. 파일을 여는 앱의 '알 수 없는 앱 설치' 허용이 필요할 수 있습니다.

개인 테스트용 debug 서명 APK입니다. Play Store 배포용이 아닙니다. CI 실행마다 debug 키가 달라질 수 있어 이전 버전과 서명이 다르면 기존 앱 삭제 후 설치해야 합니다. 삭제하면 앱의 로그인 세션과 저장 목록이 지워집니다. 배포·업데이트용 고정 release 서명 키는 별도 설정해야 합니다.

## 사용법

1. 인스타그램에서 게시물 또는 릴스의 링크를 복사해 앱에 붙여넣습니다. 공유 대상에서 **Insta Pocket**을 선택해도 됩니다.
2. **게시물 열고 미디어 찾기**를 누릅니다. 인스타그램 페이지에서 필요한 경우 직접 로그인합니다.
3. 사진 여러 장은 옆으로 넘기고, 동영상은 재생합니다. 페이지에서 로드된 미디어가 수집됩니다.
4. 아래의 **미디어 선택하러 가기**를 누르고 저장할 항목을 선택합니다.
5. **선택한 항목 저장하기**를 누릅니다. 파일은 `Download/InstaPocket`에 저장됩니다. 내 포켓에서 완료 상태를 확인하고 파일을 열 수 있습니다.

## 지원 범위와 한계

- `/p/`, `/reel/`, `/reels/`, `/tv/` 게시물 링크를 지원합니다. 프로필, 스토리, 라이브, `/share/` 단축 링크는 지원하지 않습니다.
- 공개 게시물도 인스타그램이 로그인을 요구할 수 있습니다. 계정·네트워크·지역에 따라 WebView 로그인이 제한될 수 있습니다.
- 게시물에 포함된 JSON의 원본 미디어 정보를 우선 읽고, 없으면 게시물 DOM의 사진·영상 주소를 읽습니다. 화면에 로드하지 않은 캐러셀 항목은 누락될 수 있습니다. DOM 대체 경로는 화면에 제공된 화질입니다.
- `blob:` 기반 스트리밍에 원본 URL이 제공되지 않으면 다운로드하지 않습니다. 영상 썸네일을 영상으로 속여 저장하지 않습니다. 비디오/오디오 스트림 병합이나 DRM 처리는 하지 않습니다.
- 인스타그램 페이지 구조 변경, 만료된 CDN 링크, 접근 제한에 따라 추출·다운로드가 실패할 수 있습니다. 실패하면 게시물을 다시 열어 새 링크를 수집하세요.
- 실제 계정 로그인, Instagram 콘텐츠 다운로드, 기기별 파일 열기는 실기기에서 추가 확인이 필요합니다. 자동 테스트 통과는 모든 게시물 다운로드 성공을 보장하지 않습니다.

## 개인정보

별도 서버, 분석 SDK, API 키가 없습니다. 로그인은 Instagram의 HTTPS 페이지에서 이루어지고 WebView 쿠키는 기기에 남습니다. 앱은 비밀번호를 읽거나 로그인 쿠키를 다운로드 CDN에 전달하지 않습니다. 설정에서 로그인 세션을 지울 수 있습니다. 저장 기록은 기기에만 보관합니다. Android DownloadManager에 파일 전송을 맡기며 광범위한 저장소 권한을 요구하지 않습니다. 본인 소유이거나 저장 허락을 받은 콘텐츠에 사용하세요. Instagram/Meta와 관련 없는 독립 앱입니다.

## 개발

Flutter SDK와 Android SDK가 없는 개발 컴퓨터를 기준으로 Node.js + Capacitor를 사용했습니다. Android 빌드는 GitHub Actions에서 수행합니다. 기존 저장소의 웹 파일은 유지합니다.

```sh
cd mobile
npm ci
npm test
npm run dev
# Android 프로젝트 동기화
npm run sync
# Java 21, Android SDK 36이 있는 환경에서
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Node.js 22 이상, Capacitor 8, Java 21, Android SDK 36, 최소 Android 10(API 29). 웹 미리보기에는 네이티브 다운로드 기능이 없고 Android에서 동작합니다.

워크플로: `.github/workflows/insta-pocket-apk.yml`. `master`에 mobile/워크플로 변경을 push하면 테스트·lint·APK 빌드를 자동 실행합니다. Actions 화면에서 수동 실행도 가능합니다. APK와 SHA-256 체크섬은 30일 동안 아티팩트로 보관합니다.

테스트는 악성 URL·잘못된 링크 거부, 여러 장/영상 JSON 추출, DOM fallback, blob 영상 오인 방지, 저장 기록 복원, 네이티브 URL 정책을 검증합니다.
