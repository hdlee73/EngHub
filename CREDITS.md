# Data credits

The bundled offline dictionary (`word_dictionary.sqlite`) and example corpus are built in CI by `tools/build_learning_data.py` from these sources. Each record keeps the license of its source; the combined file does not relicense them.

- **Korean Wiktionary (한국어 위키낱말사전) English entries** via Kaikki.org / Wiktextract — CC BY-SA 4.0 — https://ko.wiktionary.org/ · https://kaikki.org/kowiktionary/
- **National Institute of Korean Language, Korean Basic Dictionary (국립국어원 한국어기초사전)**, English equivalents reverse-indexed to Korean headwords — CC BY-SA 2.0 KR — https://krdict.korean.go.kr/ (mirror: binjang/NIKL-dictionary-parser, commit `52ffffdc0a6fd8d00d7dce45cffcc0930ebbdaea`)
- **Tatoeba** English–Korean sentence pairs, provided by ManyThings.org/anki (Charles Kelly) — CC BY 2.0 FR — https://www.manythings.org/anki/ ; contributor credits are kept per sentence and shown in the app and in the Excel “출처” sheet.

Online data:

- English definitions, examples and Korean lexical translations: [FreeDictionaryAPI.com](https://freedictionaryapi.com/) (English Wiktionary content, CC BY-SA 4.0), with [dictionaryapi.dev](https://dictionaryapi.dev/) as a fallback.
- Automatic fallback for Korean meanings and example translations (labeled as automatic translation in the app): Google Translate (`translate.googleapis.com`, unofficial `gtx` client).
- Spelling suggestions when nothing is found: [Datamuse API](https://www.datamuse.com/api/).

Korean meanings are never machine-translated. Full details: `app/src/main/assets/DATA_SOURCES.txt`.

- English Wiktionary Korean translations (via Kaikki.org English dump), CC BY-SA 4.0 — https://en.wiktionary.org

# Code origin

This app merges the code of [SpeakFlow](https://github.com/hdlee73/SpeakFlow) (speaking practice) and [sajeon-app](https://github.com/hdlee73/sajeon-app) (dictionary and word list), both by the same author, and adds the flashcard and quiz features.

The reading texts in `app/src/main/assets/reading_articles.txt` were written for this app as original learning material (feature-style explainers, not real news reports). Korean translations shown in the reading tab are produced on demand by Google Translate (automatic translation).
