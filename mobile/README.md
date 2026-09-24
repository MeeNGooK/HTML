# Insta Pocket

**링크 복사 → 앱에 붙여넣기 → 다운로드.** 공개 Instagram 게시물의 사진·동영상을 로그인 없이 자동 추출해 저장하는 Android 앱입니다. 여러 장짜리 게시물은 원본 정보를 모두 찾았을 때 전체 항목을 저장합니다. Instagram 공유 메뉴에서 이 앱으로 링크를 전달할 수도 있습니다.

## APK

[GitHub Actions의 최신 성공 실행](https://github.com/MeeNGooK/HTML/actions/workflows/insta-pocket-apk.yml) → Artifacts → **InstaPocket-APK**를 내려받고 압축을 풀어 `InstaPocket-debug.apk`를 Android 10 이상 기기에 설치합니다. GitHub 로그인이 필요할 수 있습니다. Android에서 파일을 여는 앱의 '알 수 없는 앱 설치' 허용이 필요할 수 있습니다.

개인 테스트용 debug APK입니다. CI마다 debug 서명 키가 달라질 수 있어 이전 버전과 서명이 다르면 앱을 삭제하고 다시 설치해야 합니다. 삭제 시 앱 저장 기록이 사라집니다. 고정 release 서명과 Play Store 배포는 별도 설정이 필요합니다.

## 사용법

1. 공개 사진 게시물 또는 릴스에서 링크를 복사합니다.
2. Insta Pocket에 붙여넣고 **다운로드**를 누릅니다.
3. 자동 추출 후 저장이 시작되면 **내 포켓**에서 진행 상태를 확인합니다.

저장 위치: `Download/InstaPocket`. 완료된 항목을 누르면 파일을 엽니다. Android DownloadManager를 사용하므로 파일 전송 시작 후에는 앱을 닫아도 다운로드가 이어집니다. 주소 분석 중에는 앱을 열어 두세요.

## 지원 범위

- `/p/`, `/reel/`, `/reels/`, `/tv/` 게시물 링크. 프로필·스토리·라이브·`/share/` 단축 링크·비공개 게시물은 지원하지 않습니다.
- 로그인 UI, 사용자 세션 쿠키 입력, 게시물 수동 탐색·재생은 없습니다.
- 기기에서 Instagram의 공개 페이지·비로그인 공개 JSON·공개 임베드 응답을 조회합니다. 해당 게시물의 원본 미디어 정보만 해석하며 추천 게시물은 제외합니다.
- `og:image`만 있는 응답을 사진이라고 추측하지 않습니다. 영상 썸네일을 동영상 대신 저장하거나, 일부만 추출한 캐러셀을 전체 성공으로 표시하지 않습니다.
- 공개 게시물이라고 항상 비로그인 접근이 허용되는 것은 아닙니다. 요청 빈도·IP·지역 제한, 삭제, 페이지 형식 변경, 스트리밍 원본 주소 미제공이면 오류를 표시합니다.
- 개발 PC에서 시험한 공개 릴스는 비로그인 응답에 원본 영상 URL을 제공하지 않아 실제 다운로드 성공을 확인하지 못했습니다. 자동 테스트 및 APK 빌드 성공과 실서비스 다운로드 성공은 구분해야 합니다. Android 실기기에서 사용할 게시물 링크로 추가 검증이 필요합니다.

## 개인정보

외부 추출 서버·분석 SDK·API 키가 없습니다. 비밀번호나 사용자 로그인 쿠키를 사용하지 않습니다. 한 번의 추출 작업 안에서만 익명 세션 쿠키를 메모리에 유지하고 작업 후 폐기합니다. Instagram과 CDN에는 게시물 및 파일 요청이 전달됩니다. 저장 기록은 기기에만 보관하며 설정에서 기록만 지울 수 있습니다. 본인 소유 또는 저장 허락을 받은 콘텐츠에 사용하세요. Instagram/Meta와 관련 없는 독립 앱입니다.

## 개발·빌드

로컬에 Flutter와 Android SDK가 없어 Node.js + Capacitor + Java로 구현했습니다. 기존 저장소 루트 파일은 유지합니다.

```sh
cd mobile
npm ci
npm test
npm run dev
npm run sync
# Java 21 + Android SDK 36 환경에서:
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Node 22 이상, Capacitor 8, Java 21, SDK 36, 최소 Android 10(API 29). 웹 미리보기는 화면·링크 검증용이며 네이티브 다운로드는 Android에서 동작합니다.

워크플로 `.github/workflows/insta-pocket-apk.yml`은 `master`의 mobile/워크플로 변경 및 수동 실행에서 JS 테스트, 네이티브 단위 테스트, lint, APK 생성을 수행합니다. APK·SHA-256 체크섬은 30일 동안 아티팩트로 보관합니다.

공개 요청 경로와 데이터 필드 참고: [yt-dlp Instagram extractor](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/instagram.py). 네이티브 추출기는 자체 구현이며 해당 도구를 실행하지 않습니다. Instagram의 공개 JSON 문서 ID와 스키마는 변경될 수 있으므로 `PublicPostResolver`와 `PublicMediaParser`에 분리했습니다.
