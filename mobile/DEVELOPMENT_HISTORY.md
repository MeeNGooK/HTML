# Insta Pocket 개발·오류 해결 기록

작성일: 2026-10-05  
저장소: [MeeNGooK/HTML](https://github.com/MeeNGooK/HTML)  
앱 위치: `mobile/`

## 1. 최초 목표

공개 Instagram 사진·동영상 게시물의 링크를 복사해 앱에 붙여넣으면 파일을 휴대폰에 저장하는 간단한 Android 앱을 만드는 것이 목표였다.

요구한 사용자 흐름은 다음 한 줄로 정리된다.

> Instagram 링크 복사 → 앱에 붙여넣기 → 다운로드

Flutter를 우선 검토했지만 개발 PC에 Flutter, Android SDK, Android Studio, Java 개발환경이 없었다. 대신 설치되어 있던 Node.js를 이용해 Capacitor 웹 UI와 Java 네이티브 Android 코드를 결합하고, Android 컴파일은 GitHub Actions에서 수행하는 구조를 선택했다.

## 2. 개발환경과 저장소 준비

처음 확인한 로컬 환경은 다음과 같았다.

- Node.js와 npm 사용 가능
- Git 사용 가능
- Flutter와 Dart 없음
- Android SDK와 Android Studio 없음
- Java 개발환경 없음
- GitHub 저장소 읽기는 가능했지만 최초 등록 계정 `JBMoon00`에는 push 권한이 없었음

Git Credential Manager에서 `MeeNGooK` 계정으로 다시 인증한 뒤 `MeeNGooK/HTML` 저장소에 대한 `push`, `workflow`, 관리자 권한을 확인했다. 저장소를 `insta-pocket` 폴더로 복제하고 기존 루트 파일은 건드리지 않은 채 `mobile/` 아래에 앱을 만들었다.

## 3. 초기 구현

초기 앱은 다음 구성으로 만들었다.

- Capacitor 8 기반 Android 앱
- Vite 기반 한국어 모바일 UI
- Instagram 링크 형식 검증
- Android 공유 메뉴에서 텍스트 링크 받기
- Android `DownloadManager`를 이용한 백그라운드 저장
- 저장 위치 `Download/InstaPocket`
- 다운로드 진행률, 실패 상태, 저장 기록과 파일 열기
- 별도 서버, 분석 SDK, API 키 없음
- Android 10 이상 지원

악성 호스트 위장, 사용자 정보가 포함된 URL, 비 HTTPS 미디어 주소 등을 차단하는 URL 정책도 추가했다.

초기 커밋:

- `731d308` — 앱 소스와 APK 빌드 워크플로 추가
- `659bb3c` — GitHub Actions의 Android SDK 패키지 지정

## 4. 첫 번째 설계 오류: 게시물을 앱 안에서 직접 열도록 구현

### 문제

처음에는 앱 내부 WebView에서 Instagram 게시물을 열고 사용자가 사진을 넘기거나 영상을 재생한 뒤, 화면에 로드된 미디어 URL을 수집하는 방식으로 구현했다.

이 방식에는 다음 문제가 있었다.

- 사용자가 원한 한 단계 다운로드 흐름과 달랐다.
- 공개 게시물에도 로그인 화면이나 수동 조작이 등장할 수 있었다.
- 캐러셀을 직접 넘기고 영상을 재생해야 했다.
- WebView DOM 구조 변경에 지나치게 의존했다.
- `blob:` 스트리밍 영상은 원본 URL을 얻을 수 없었다.

### 사용자 피드백

원래 취지는 공개 게시물에서 로그인 없이 다음과 같이 동작하는 앱이었다.

> 링크 복사 → 앱 이용 → 다운로드

### 수정

WebView와 수동 미디어 수집 화면을 삭제하고 다음 구조로 변경했다.

1. 링크에서 게시물 shortcode 추출
2. Instagram 공개 페이지를 익명 요청
3. 공개 JSON 또는 embed 응답에서 해당 게시물의 원본 미디어 URL 추출
4. 추출된 모든 파일을 Android `DownloadManager`에 등록
5. 내 포켓 화면에서 진행 상태 표시

비공개 게시물, 로그인 전용 콘텐츠, 스토리는 지원하지 않도록 명확히 제한했다.

관련 커밋:

- `710f5ab` — WebView 기반 흐름을 익명 링크 다운로드 방식으로 교체

## 5. 첫 GitHub Actions 오류: 존재하지 않는 Android SDK 패키지

### 증상

첫 APK 워크플로가 `android-actions/setup-android` 단계에서 실패했다.

로그의 핵심 내용:

```text
Warning: Failed to find package 'tools'
sdkmanager failed with exit code 1
```

### 원인

설정 액션의 기본 패키지 목록에 현재 Android SDK 저장소에서 더 이상 제공하지 않는 구형 `tools` 패키지가 포함되어 있었다.

### 해결

워크플로에서 필요한 SDK를 직접 지정했다.

```yaml
packages: 'platform-tools platforms;android-36 build-tools;36.0.0'
```

이후 JavaScript 테스트, 웹 빌드, Android 단위 테스트, lint, `assembleDebug`가 정상 실행됐다.

관련 커밋:

- `659bb3c` — 지원되는 Android SDK 패키지를 명시

## 6. 두 번째 오류: 공개 게시물에서 다운로드 주소를 찾지 못함

### 증상

APK 설치는 성공했지만 공개 게시물 링크를 넣어도 다음 오류가 발생했다.

```text
게시물의 다운로드 주소를 찾지 못했어요.
```

처음에는 Instagram의 익명 접근 제한으로 판단했으나, 실제 공개 게시물 응답을 조사한 결과 추출기 구현에도 문제가 있었다.

### 조사에 사용한 실제 게시물

호날두 계정의 공개 영상 shortcode `DXeh-kYiIge`를 사용했다.

```text
https://www.instagram.com/p/DXeh-kYiIge/embed/captioned/
```

공개 embed 응답에는 실제 `video_url`이 포함되어 있었고, 추출한 CDN 주소는 다음 결과를 반환했다.

- HTTP 상태: `206 Partial Content`
- Content-Type: `video/mp4`
- CDN 호스트: `scontent-*.cdninstagram.com`

즉 Instagram이 원본 영상을 제공하고 있었지만 앱이 응답 구조를 잘못 해석하고 있었다.

## 7. 원인 1: Android Chrome User-Agent에 다른 응답 제공

### 발견

동일한 공개 embed URL에 요청해도 User-Agent에 따라 응답이 달랐다.

- 완전한 Android Chrome User-Agent: 미디어 데이터가 없는 Instagram 앱 셸 반환
- 단순한 표준 User-Agent: 공개 embed와 원본 미디어 JSON 반환

### 해결

익명 공개 게시물 조회용 User-Agent를 다음처럼 최소화했다.

```text
Mozilla/5.0
```

이 변경으로 Android 기기에서도 공개 embed 응답을 받을 수 있게 했다.

## 8. 원인 2: 미디어 JSON이 JavaScript 래퍼와 문자열 안에 중첩됨

### 실제 응답 구조

Instagram 공개 embed는 단순한 `<script type="application/json">`만 사용하지 않았다. 현재 응답은 대략 다음 구조였다.

```javascript
requireLazy([...], function (...) {
  var s = ...;
  s.handle({
    "...": "...",
    "contextJSON": "{\"context\": {...}, \"gql_data\": {...}}"
  });
});
```

원본 게시물 정보는 다음 두 겹 안에 있었다.

1. `s.handle({...})` JavaScript 래퍼 내부의 JSON 객체
2. 그 객체의 `contextJSON` 필드에 문자열로 다시 인코딩된 JSON

기존 파서는 JSON 전용 script만 읽었기 때문에 이 데이터를 전혀 보지 못했다.

### 해결

페이지 JavaScript를 실행하지 않고 다음 방식으로 안전하게 파싱했다.

1. script에서 `s.handle(` 위치 탐색
2. 문자열 내부의 괄호는 무시하면서 `{`와 `}`의 균형 검사
3. 완결된 JSON 객체만 추출
4. `contextJSON`을 다시 JSON으로 파싱
5. 요청한 shortcode와 일치하는 게시물만 선택
6. `video_url`, `video_versions`, `display_url`, 캐러셀 항목 추출

`eval`이나 WebView JavaScript 실행은 사용하지 않는다.

관련 커밋:

- `404623c` — 문자열로 감싸진 공개 embed JSON 처리
- `447a71b` — 현재 Instagram embed 구조와 User-Agent 처리 수정

## 9. 원인 3: 메타데이터 노드를 실제 영상으로 오인

`contextJSON` 안에는 먼저 다음과 같은 메타데이터 노드가 등장했다.

```json
{
  "type": "GraphVideo",
  "shortcode": "DXeh-kYiIge",
  "copyright_blocked": false
}
```

이 노드에는 실제 `video_url`이 없다. 기존 파서는 shortcode와 `GraphVideo` 타입만 보고 이를 영상 항목으로 처리한 뒤, URL이 없다는 이유로 전체 결과를 불완전 상태로 만들었다. 뒤쪽에 정상 `video_url`이 있어도 최종 결과가 폐기됐다.

수정 후에는 다음 필드 중 하나가 실제로 존재할 때만 미디어 노드로 처리한다.

- `video_url`
- `video_versions`
- `display_url`
- `display_src`
- `image_versions2`
- `carousel_media`
- `edge_sidecar_to_children`

메타데이터 노드는 계속 순회하되 다운로드 항목으로 판정하지 않는다.

## 10. 안전장치

현재 추출기에는 다음 검사가 적용되어 있다.

- HTTPS Instagram 게시물 링크만 허용
- `/p/`, `/reel/`, `/reels/`, `/tv/` 경로만 허용
- Instagram 외부 리디렉션 차단
- 미디어 호스트를 `cdninstagram.com`, `fbcdn.net`, `instagram.com` 계열로 제한
- 응답 최대 8 MiB 제한
- 전체 처리 제한시간 45초
- 리디렉션 횟수 제한
- JSON 순회 깊이·노드 수·항목 수 제한
- 추천 게시물의 미디어 제외
- 비공개 계정 감지
- 저작권 차단 및 비공개 처리된 미디어 거부
- 영상 URL이 없을 때 포스터 이미지를 영상 대신 저장하지 않음
- 캐러셀 일부만 찾았을 때 부분 성공으로 표시하지 않음
- 사용자 로그인 쿠키와 비밀번호를 사용하지 않음

## 11. 테스트 과정에서 발생한 컴파일 오류

현재 embed 구조를 재현하는 Java 단위 테스트를 추가한 뒤 CI 컴파일이 한 번 실패했다.

```text
error: unreported exception JSONException; must be caught or declared to be thrown
```

테스트에서 `JSONObject.put()`이 발생시킬 수 있는 checked exception을 선언하지 않은 것이 원인이었다. 해당 테스트 메서드에 `throws Exception`을 추가해 해결했다.

관련 커밋:

- `4d83af5` — 네이티브 embed 파서 테스트 컴파일 수정

## 12. 최종 검증 결과

버전 1.2에서 다음을 확인했다.

- 링크 정규화 JavaScript 테스트 통과
- 위장 Instagram 도메인 및 비 HTTPS 주소 차단 테스트 통과
- 공개 JSON 미디어 추출 테스트 통과
- 여러 장 게시물의 최고 해상도 선택 테스트 통과
- 중첩 `contextJSON` 파싱 테스트 통과
- `s.handle({...})` 래퍼 파싱 테스트 통과
- 영상 썸네일 오인 방지 테스트 통과
- 불완전 캐러셀 거부 테스트 통과
- Android lint 통과
- Android debug APK 빌드 통과
- 실제 호날두 공개 게시물에서 원본 `video_url` 추출 확인
- 추출한 CDN URL에서 `video/mp4` 응답 확인

성공한 최종 소스 커밋:

```text
4d83af58ea1aec0b44ccfa9da538385116c2d36d
```

APK 릴리스:

- [Insta Pocket build `insta-pocket-7`](https://github.com/MeeNGooK/HTML/releases/tag/insta-pocket-7)
- [InstaPocket-debug.apk](https://github.com/MeeNGooK/HTML/releases/download/insta-pocket-7/InstaPocket-debug.apk)

## 13. 현재 동작

1. Instagram 앱에서 공개 게시물 링크 복사
2. Insta Pocket 실행
3. 링크 붙여넣기
4. **다운로드** 선택
5. 공개 페이지와 embed 응답에서 원본 URL 자동 추출
6. 모든 사진·동영상을 `Download/InstaPocket`에 저장
7. **내 포켓**에서 진행 상태 확인 및 완료 파일 열기

Instagram 공유 메뉴에서 Insta Pocket을 선택해 링크를 전달할 수도 있다.

## 14. 남아 있는 한계

Instagram의 비공식 공개 웹 응답을 해석하는 방식이므로 다음 상황에서는 실패할 수 있다.

- Instagram이 embed 또는 JSON 구조를 변경한 경우
- 익명 접근 요청 횟수 제한에 걸린 경우
- 국가, IP 또는 네트워크별 접근 제한이 적용된 경우
- 게시물이 삭제되거나 연령·지역·저작권 제한 상태인 경우
- 비공개 계정 게시물인 경우
- 원본 영상 URL을 공개 응답에 포함하지 않는 경우

이 앱은 로그인 우회, 비공개 게시물 접근, DRM 해제 기능을 제공하지 않는다. 현재 구조가 바뀌면 `PublicPostResolver.java`의 요청 경로와 `PublicMediaParser.java`의 응답 파서를 우선 점검해야 한다.

## 15. 주요 파일

- `mobile/src/main.js` — 사용자 화면과 링크 다운로드 흐름
- `mobile/src/model.js` — 링크·미디어 URL 검증과 저장 상태 모델
- `mobile/android/app/src/main/java/com/meengook/instapocket/PublicPostResolver.java` — 익명 공개 게시물 요청
- `mobile/android/app/src/main/java/com/meengook/instapocket/PublicMediaParser.java` — Instagram 응답 파싱
- `mobile/android/app/src/main/java/com/meengook/instapocket/PocketMediaPlugin.java` — Capacitor와 Android 다운로드 연결
- `mobile/android/app/src/test/java/com/meengook/instapocket/PublicMediaParserTest.java` — 네이티브 파서 회귀 테스트
- `.github/workflows/insta-pocket-apk.yml` — 테스트, lint, APK 빌드와 GitHub Release 게시

## 16. 커밋 흐름

| 커밋 | 내용 |
|---|---|
| `731d308` | 최초 앱, 네이티브 다운로드 기능, APK 워크플로 추가 |
| `659bb3c` | GitHub Actions Android SDK 패키지 오류 수정 |
| `710f5ab` | WebView·로그인 중심 흐름을 익명 링크 다운로드로 교체 |
| `e68b6e4` | 성공한 APK를 GitHub Release에도 게시하도록 변경 |
| `404623c` | 문자열로 중첩된 공개 embed JSON 파싱 |
| `447a71b` | 실제 Instagram embed 래퍼와 User-Agent 문제 수정 |
| `4d83af5` | 네이티브 테스트 컴파일 오류 수정 및 최종 성공 빌드 |
