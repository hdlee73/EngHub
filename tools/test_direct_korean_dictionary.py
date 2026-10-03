import json
import unittest
from build_learning_data import select_senses
from direct_korean_dictionary import clean_gloss, direct_records, merge_meanings, pos_of

class DirectKoreanTests(unittest.TestCase):
    def test_native_gloss_pos_and_exact_headword(self):
        rows = [{"word": "preliminary", "lang_code": "en", "pos": "adj",
                 "senses": [{"glosses": ["예비의, 준비의."]}]}]
        self.assertEqual({"preliminary": ["[형용사] 예비의, 준비의."]},
                         direct_records(map(json.dumps, rows)))

    def test_no_reverse_substring_or_fake_korean(self):
        rows = [{"word": "preliminary research", "lang_code": "en", "pos": "noun",
                 "senses": [{"glosses": ["예비 조사"]}]},
                {"word": "example", "lang_code": "en", "pos": "noun",
                 "senses": [{"glosses": ["English gloss only", "ㄱㄴ"]}]},
                {"word": "important", "lang_code": "fr", "pos": "adj",
                 "senses": [{"glosses": ["잘못된 언어"]}]}]
        result = direct_records(map(json.dumps, rows))
        self.assertEqual({"preliminary research": ["[명사] 예비 조사"]}, result)

    def test_unknown_pos_not_guessed_from_ambiguous_categories(self):
        rows = [{"word": "necessary", "lang_code": "en", "pos": "unknown",
                 "categories": [{"name": "영어 명사"}, {"name": "영어 형용사"}],
                 "senses": [{"glosses": ["필요한"]}]}]
        self.assertEqual({"necessary": ["필요한"]}, direct_records(map(json.dumps, rows)))

class CleaningAndMergeTests(unittest.TestCase):
    def test_english_explanations_are_removed_but_korean_notes_stay(self):
        self.assertEqual("(자동사, 사람이 주어) 앉다, 착석하다.",
                         clean_gloss("(자동사, 사람이 주어) 앉다, 착석하다.( To be in a position (especially the upper legs) supported by an object. )"))
        self.assertEqual("가다, 나가다.", clean_gloss("가다, 나가다. (To move from one place to another.)"))
        self.assertEqual("사과나무에서 자라는 열매", clean_gloss("사과나무 (apple tree)에서 자라는 열매"))
        self.assertEqual("먹다.", clean_gloss("먹다. (부록: 영어 불규칙 동사) [To take in food]"))
        self.assertEqual("(불가산) 빛, 불빛", clean_gloss("(불가산) 빛, 불빛"))

    def test_gloss_that_is_only_english_is_dropped_from_records(self):
        rows = [{"word": "go", "lang_code": "en", "pos": "verb",
                 "senses": [{"glosses": ["(To move.)"]}, {"glosses": ["가다 (To move.)"]}]}]
        self.assertEqual({"go": ["[동사] 가다"]}, direct_records(map(json.dumps, rows)))

    def test_pos_is_normalized(self):
        self.assertEqual("명사", pos_of("1. [의존 명사] 권: 단위"))
        self.assertEqual("동사", pos_of("[보조 동사] 있다"))
        self.assertEqual("대명사", pos_of("[대명사] 나"))
        self.assertEqual("형용사", pos_of("[관형사] 어떤"))
        self.assertEqual("", pos_of("가다"))

    def test_nikl_fills_a_missing_part_of_speech_only(self):
        nikl = "1. [명사] 손바닥: 손의 안쪽.\n2. [명사] 손뼉: 손 안쪽.\n3. [명사] 손안: 손의 안쪽.\n4. [동사] 쥐다: 손에 잡다."
        merged, changed = merge_meanings(["[동사] 손 안에 감추다."], nikl)
        self.assertTrue(changed)
        self.assertEqual(["[동사] 손 안에 감추다.", "[명사] 손바닥: 손의 안쪽.", "[명사] 손뼉: 손 안쪽."], merged)

    def test_covered_parts_are_not_padded_with_loose_nikl_senses(self):
        nikl = "1. [동사] 내다: 신문에 싣다.\n2. [동사] 흐르다: 액체가 흐르다."
        direct = ["[동사] 달리다. 뛰다.", "[동사] 달리게 하다."]
        self.assertEqual((direct, False), merge_meanings(direct, nikl))

    def test_nikl_only_words_are_not_touched_and_direct_is_capped(self):
        self.assertEqual((["[명사] 책."], False), merge_meanings(["[명사] 책."], ""))
        self.assertEqual(4, len(merge_meanings([f"[명사] 뜻{i}" for i in range(9)], "")[0]))

class SelectSensesTests(unittest.TestCase):
    def test_learner_level_beats_alphabetical_order_and_heads_are_unique(self):
        # (text, english, level rank, head) in NIKL file order, which is sorted by Korean spelling.
        senses = [("[동사] 게재하다: 싣다", "", 2, "게재하다"), ("[명사] 구보: 달림", "", 3, "구보"),
                  ("[동사] 달리다: 빨리 가다", "", 0, "달리다"), ("[동사] 달리다: 모자라다", "", 0, "달리다"),
                  ("[동사] 뛰다: 빨리 움직이다", "", 1, "뛰다")]
        chosen = select_senses(senses, limit=3)
        self.assertEqual(["달리다", "뛰다", "게재하다"], [item[3] for item in chosen])
        self.assertEqual("[동사] 달리다: 빨리 가다", chosen[0][0])

if __name__ == "__main__":
    unittest.main()
