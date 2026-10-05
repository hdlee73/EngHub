# LexiFlow (영어 스터디)

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

## 릴리스
`main`에 푸시하면 GitHub Actions가 테스트 후 APK를 빌드해 `v<versionName>` 릴리스로 올립니다 (`app/build.gradle.kts`의 `versionName`).
APK는 `signing/english-study.keystore`(디버그 키, 비밀번호 `android`)로 서명됩니다. 공개 저장소에 포함된 키이므로 **업데이트 호환용**일 뿐 신뢰 보증 수단이 아닙니다. 별도 키를 쓰려면 저장소 시크릿 `ENGLISH_STUDY_KEYSTORE_BASE64`를 설정하세요.

## 데이터 출처
[CREDITS.md](CREDITS.md), `app/src/main/assets/DATA_SOURCES.txt` 참고.
