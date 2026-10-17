# EngHub (영어 스터디, 저장소 이름: LexiFlow)

사전 · 단어 암기 · 예문 퀴즈 · 스피킹 연습을 한 앱에서 하는 Android 앱입니다.
[SpeakFlow](https://github.com/hdlee73/SpeakFlow)와 [sajeon-app](https://github.com/hdlee73/sajeon-app)을 통합했습니다.

## 기능
| 탭 | 내용 |
|---|---|
| 📖 사전 | 오프라인 사전 + 온라인 사전 검색, 단어 저장, 정렬, 엑셀 내보내기, 클립보드 단어 자동 검색 |
| 🃏 암기 | 저장 단어 플래시카드. ‘알아요’는 박스를 올리고(3단계 = 외움), ‘모르겠어요’는 처음 단계로 되돌리며 같은 라운드에 다시 보여 줍니다 |
| ✏️ 퀴즈 | 저장 단어 예문에서 단어를 빈칸 처리(어형 변화 포함), 한국어 해석을 힌트로 4지선다. 오답은 저장 단어에서 우선 고르고, 부족하면 기본 단어로 채웁니다 |
| 📰 리딩 | 매일 중급 영어 글 3편. 단어를 누르면 사전 탭에서 검색, 전체 번역(온라인) 지원. 글은 `app/src/main/assets/reading_articles.txt`에 있고 끝에 추가하면 됩니다(3의 배수 편 유지) |
| 🎤 스피킹 | 문장을 듣고 따라 말하는 연습. ‘저장 단어 예문 학습’으로 저장 단어의 예문을 스피킹 데이터셋으로 만들어 바로 연습 |

## 빌드
- Android Studio 또는 `./gradlew assembleDebug` (JDK 17)
- 오프라인 사전 DB는 저장소에 포함되지 않고 `python tools/build_learning_data.py`로 생성합니다(CI가 자동 실행). 이 단계 없이 빌드하면 오프라인 사전 없이 온라인 검색만 동작합니다.
- 단위 테스트: `./gradlew testDebugUnitTest`, 데이터 도구 테스트: `python -m unittest discover -s tools -p 'test_*.py'`
- 온라인 음성(문서→MP3)은 Microsoft Edge 읽어주기 서비스를 씁니다. 클라이언트 토큰은 소스에 넣지 않고 빌드 속성 `edgeTtsToken`(또는 `EDGE_TTS_TOKEN` 환경 변수/CI 시크릿)으로 받습니다. 값이 없으면 온라인 음성만 실패하고 나머지 기능은 그대로 동작합니다.

## 릴리스
`main`에 푸시하면 GitHub Actions가 테스트 후 **release** APK를 빌드해 `v<versionName>` 릴리스로 올립니다 (`app/build.gradle.kts`의 `versionName`).

### 서명 키
서명 정보는 소스에 넣지 않고 빌드 속성/환경 변수로만 받습니다 (`engHubKeystorePath`, `engHubStorePassword`, `engHubKeyAlias`, `engHubKeyPassword` 또는 대응하는 `ENGLISH_STUDY_*` 환경 변수). 값이 없으면 release 빌드는 서명 없이 만들어지고 debug 빌드는 표준 디버그 키를 쓰므로 로컬 개발은 막히지 않습니다.

> **보안 TODO — 반드시 처리하세요.** 예전에 커밋된 `signing/english-study.keystore`(별칭 `androiddebugkey`, 비밀번호 `android`)는 이미 공개된 키이므로 **노출된 것으로 간주하고 교체(rotate)** 해야 합니다. 새 release 키를 만들어 저장소 **밖**에 보관하고, 위 시크릿을 설정한 뒤, 예전 파일을 git 기록에서 제거하세요(`git filter-repo`/BFG). 이 파일은 이제 `.gitignore`에 포함되어 있어 앞으로 커밋되지 않습니다. (이 작업 환경에서는 키를 교체하거나 기록을 정리할 수 없어 TODO로만 남깁니다.)

## 데이터 출처
[CREDITS.md](CREDITS.md), `app/src/main/assets/DATA_SOURCES.txt` 참고.
