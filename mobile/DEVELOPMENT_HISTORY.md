# Douyin Pocket 개발 기록

작성일: 2026-10-05  
저장소: `MeeNGooK/HTML`
작업 브랜치: `codex/douyin-pocket`

## 목표 및 앱 흐름

Douyin 공유 텍스트를 통째로 붙여넣으면 URL만 찾아 공개 동영상 스트림을 추출하고, Android 다운로드 관리자로 저장하는 앱이다. 결과는 `Download/DouyinPocket`에 저장한다. 60fps 변형이 공개되어 있으면 우선 선택하고 그중 해상도가 가장 높은 것을 고른다. 없으면 공개된 최고 화질을 선택한다.

## Instagram 앱에서 가져온 구현 교훈

- URL 정규식으로 공유 텍스트의 첫 번째 주소를 무조건 받지 않는다. Douyin 도메인 허용 목록에 맞는 URL만 찾는다. 날짜, 캡션, 앱 코드가 함께 들어간 예시 텍스트로 회귀 검사를 둔다.
- 공유 단축 URL은 리디렉션을 직접 따라가고, 각 리디렉션마다 HTTPS와 Douyin 호스트를 검증한다.
- 페이지 구조 변경에 대비해 응답 요청과 JSON 해석 코드를 별도 resolver로 분리한다. 공개 JSON 우선으로 탐색하고, SSR 데이터가 없을 때는 숨겨진 Android WebView에서 영상 플레이어가 사용하는 MP4 주소를 읽는다.
- 영상 메타데이터 노드와 실제 재생 주소를 구분하며 poster 이미지를 영상 파일로 저장하지 않는다.
- CDN 서명 주소의 호스트 allowlist, 응답 크기 제한, 시간 제한, 최대 리디렉션 수를 적용한다. 사용자 로그인 쿠키나 비밀번호는 쓰지 않는다.
- Android SDK가 개발 PC에 없는 경우 GitHub Actions에서 Android SDK를 지정해 APK를 만들고 Release에 게시한다. APK와 SHA-256 파일도 함께 올린다.

## 구현

- Capacitor 8 + Vite 웹 UI와 네이티브 Android `DownloadManager`를 유지한다.
- 공유된 문자열 및 클립보드에서 `v.douyin.com`, `douyin.com`, `iesdouyin.com` URL을 선택한다.
- Douyin 공개 페이지에서 `RENDER_DATA` 또는 JSON script의 공개 aweme 정보를 찾고, 해당 ID의 `video.bit_rate` 및 `play_addr` 변형을 정렬한다.
- 별도 추출 서버, API 키, 앱 로그인 기능은 없다.
- Android 앱 ID는 `com.meengook.douyinpocket.app`, 저장 폴더는 `Download/DouyinPocket`이다. 첫 두 APK와 서명 호환이 안 되므로 새 ID로 설치하며, 이후 CI 빌드는 Action cache에 저장한 개인 테스트 키로 서명을 고정한다.

## 실제 접근 조사와 제한

2026-10-05에 사용자가 제공한 `https://v.douyin.com/0rxK1KgNtAA/` 링크는 `https://www.douyin.com/video/7682679999606357425`로 연결된다. 첫 릴리스는 최종 URL 경로를 `/video/{id}`와 `/note/{id}`로만 제한해 다른 Douyin 공유 경로를 공개 영상이 아닌 것으로 오판할 수 있었다. 1.0.1에서는 `/share/video/{id}`, `/share/note/{id}`, `/share/slides/{id}` 및 `modal_id`·`aweme_id`·`item_id` 쿼리 경로도 인식한다.

브라우저에서 제공 링크를 열어 공개 재생을 확인했다. 로그인 없이 9.4초 영상이 재생됐고 `video.currentSrc`의 MP4를 1,127,917 bytes로 다운로드했다. 확인된 비로그인 스트림은 576×1024였고 페이지는 HD 시청 시 로그인을 안내했다. 60fps 원본은 확인되지 않았다. 서버 렌더링 HTML과 공개 상세 API에는 재생 정보가 없어 resolver에 WebView 플레이어 스트림 조회를 추가했다. Android 앱에서 실제 저장하는 동작은 새 APK로 실기기 확인이 필요하다.

다른 지역·네트워크·시점에서 Douyin이 공개 페이지에 영상 정보를 포함할 수 있으므로 구현은 해당 공개 JSON 형태를 처리한다. 60fps가 게시물 원본이나 공개 응답에 없으면 앱이 60fps를 만들어내거나 프레임 보간하지 않으며, 공개된 최고 화질 변형을 선택한다.

## 검증 및 게시

- Node 링크/정책 테스트와 Vite 웹 빌드 통과.
- Android native lint 및 debug APK는 GitHub Actions workflow에서 수행한다.
- 개인 테스트용 debug 서명 APK이며 Android 10 이상을 지원한다. 고정 release 서명이나 Play Store 배포는 설정하지 않았다.
