# Douyin Pocket 개발 기록

작성일: 2026-10-05  
저장소: `MeeNGooK/HTML`
작업 브랜치: `codex/douyin-pocket`

## 목표 및 앱 흐름

Douyin 공유 텍스트를 통째로 붙여넣으면 URL만 찾아 공개 동영상 스트림을 추출하고, Android 다운로드 관리자로 저장하는 앱이다. 결과는 `Download/DouyinPocket`에 저장한다. 60fps 변형이 공개되어 있으면 우선 선택하고 그중 해상도가 가장 높은 것을 고른다. 없으면 공개된 최고 화질을 선택한다.

## Instagram 앱에서 가져온 구현 교훈

- URL 정규식으로 공유 텍스트의 첫 번째 주소를 무조건 받지 않는다. Douyin 도메인 허용 목록에 맞는 URL만 찾는다. 날짜, 캡션, 앱 코드가 함께 들어간 예시 텍스트로 회귀 검사를 둔다.
- 공유 단축 URL은 리디렉션을 직접 따라가고, 각 리디렉션마다 HTTPS와 Douyin 호스트를 검증한다.
- 페이지 구조 변경에 대비해 응답 요청과 JSON 해석 코드를 별도 resolver로 분리한다. JavaScript를 실행하거나 코드 평가를 하지 않고, bounded JSON 탐색으로 해당 aweme ID에 맞는 영상만 선택한다.
- 영상 메타데이터 노드와 실제 재생 주소를 구분하며 poster 이미지를 영상 파일로 저장하지 않는다.
- CDN 서명 주소의 호스트 allowlist, 응답 크기 제한, 시간 제한, 최대 리디렉션 수를 적용한다. 사용자 로그인 쿠키나 비밀번호는 쓰지 않는다.
- Android SDK가 개발 PC에 없는 경우 GitHub Actions에서 Android SDK를 지정해 APK를 만들고 Release에 게시한다. APK와 SHA-256 파일도 함께 올린다.

## 구현

- Capacitor 8 + Vite 웹 UI와 네이티브 Android `DownloadManager`를 유지한다.
- 공유된 문자열 및 클립보드에서 `v.douyin.com`, `douyin.com`, `iesdouyin.com` URL을 선택한다.
- Douyin 공개 페이지에서 `RENDER_DATA` 또는 JSON script의 공개 aweme 정보를 찾고, 해당 ID의 `video.bit_rate` 및 `play_addr` 변형을 정렬한다.
- 별도 추출 서버, API 키, 로그인 기능, 페이지 WebView 수동 조작은 없다.
- Android 앱 ID는 `com.meengook.douyinpocket`, 저장 폴더는 `Download/DouyinPocket`이다.

## 실제 접근 조사와 제한

2026-10-05에 사용자가 제공한 `https://v.douyin.com/0rxK1KgNtAA/` 링크를 비로그인 HTTP 요청했다. 링크는 `https://www.douyin.com/video/7682679999606357425`로 연결되지만, 현재 공개 페이지 응답은 JS 로더 셸이며 `aweme_id`, `RENDER_DATA`, `play_addr`, `bit_rate`를 포함하지 않았다. 두 공개 상세 API 경로도 HTTP 200 빈 응답을 반환했다. 그러므로 현재 이 링크에서 HD/60fps 원본 추출·실제 기기 저장 성공은 검증되지 않았다. 앱은 응답에서 원본을 찾을 수 없을 때 명시적인 실패 메시지를 표시한다. 페이지 공개 스키마 또는 비로그인 접근이 바뀌면 `PublicPostResolver.java`와 `UrlPolicy.java`를 먼저 확인한다.

다른 지역·네트워크·시점에서 Douyin이 공개 페이지에 영상 정보를 포함할 수 있으므로 구현은 해당 공개 JSON 형태를 처리한다. 60fps가 게시물 원본이나 공개 응답에 없으면 앱이 60fps를 만들어내거나 프레임 보간하지 않으며, 공개된 최고 화질 변형을 선택한다.

## 검증 및 게시

- Node 링크/정책 테스트와 Vite 웹 빌드 통과.
- Android native lint 및 debug APK는 GitHub Actions workflow에서 수행한다.
- 개인 테스트용 debug 서명 APK이며 Android 10 이상을 지원한다. 고정 release 서명이나 Play Store 배포는 설정하지 않았다.
