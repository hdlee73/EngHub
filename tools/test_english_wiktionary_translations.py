import json
import unittest
from english_wiktionary_translations import add_missing, clean_word, translation_records

def entry(word, pos, translations, lang="en"):
    return json.dumps({"word": word, "lang_code": lang, "pos": pos, "translations": translations}, ensure_ascii=False)

class TranslationTests(unittest.TestCase):
    def test_korean_translations_are_grouped_by_sense(self):
        line = entry("rip off", "verb", [
            {"lang_code": "ko", "word": "바가지를 씌우다", "sense": "to cheat"},
            {"lang_code": "ko", "word": "속이다", "sense": "to cheat"},
            {"lang_code": "ko", "word": "훔치다", "sense": "to steal"},
            {"lang_code": "ja", "word": "騙す", "sense": "to cheat"}])
        self.assertEqual({"rip off": ["[동사] 바가지를 씌우다, 속이다", "[동사] 훔치다"]}, translation_records([line]))

    def test_other_languages_and_non_korean_text_are_ignored(self):
        lines = [entry("palm", "noun", [{"lang_code": "ko", "word": "palm", "sense": "x"}]),
                 entry("야자", "noun", [{"lang_code": "ko", "word": "야자나무"}], lang="ko"),
                 entry("Palm", "noun", [{"lang_code": "ko", "word": "야자"}]),
                 "not json but has \"ko\""]
        self.assertEqual({"palm": ["[명사] 야자"]}, translation_records(lines))

    def test_clean_word_removes_notes_and_rejects_long_or_latin(self):
        self.assertEqual("야자나무", clean_word("야자나무 (식물)"))
        self.assertEqual("", clean_word("야자나무 palm"))
        self.assertEqual("", clean_word("가" * 21))

    def test_add_missing_keeps_only_new_words_and_respects_the_limit(self):
        have = ["[명사] 손바닥", "[동사] 손 안에 감추다"]
        got = add_missing(have, ["[명사] 손바닥, 야자나무", "[동사] 감추다"], " ".join(have), 5)
        self.assertEqual(have + ["[명사] 야자나무"], got)
        self.assertEqual(have, add_missing(have, ["[명사] 야자나무"], " ".join(have), 2))

if __name__ == "__main__":
    unittest.main()
