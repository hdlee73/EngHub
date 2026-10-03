"""Direct Korean-Wiktionary English headwords, supplemented by NIKL reverse entries."""
import json
import re
import sqlite3
import subprocess
from pathlib import Path
from english_wiktionary_translations import add_missing, load_translations

URL = "https://kaikki.org/kowiktionary/%EC%98%81%EC%96%B4/kaikki.org-dictionary-%EC%98%81%EC%96%B4.jsonl"
FINAL_SENSES = 5
POS = {"noun": "명사", "verb": "동사", "adj": "형용사", "adv": "부사", "prep": "전치사",
       "conj": "접속사", "pron": "대명사", "article": "관사", "det": "한정사",
       "intj": "감탄사", "phrase": "숙어", "num": "수사", "name": "고유명사",
       "prefix": "접두사", "suffix": "접미사", "abbrev": "약어", "proverb": "속담"}

def clean_gloss(gloss):
    """Drop English explanations that Korean Wiktionary glosses carry in () or [] and 부록 links."""
    text = gloss
    for _ in range(5):  # nested parentheses: remove the innermost English-only ones first
        before = text
        text = re.sub(r"\s*\[[^\[\]가-힣]*[A-Za-z][^\[\]가-힣]*\]", "", text)
        text = re.sub(r"\s*\((?:[^()가-힣]*[A-Za-z][^()가-힣]*)\)", "", text)
        if text == before:
            break
    text = re.sub(r"\s*\(부록:[^)]*\)", "", text)
    return re.sub(r"\s+", " ", text).strip()

def pos_of(line):
    """Normalized Korean part of speech of a "[품사] ..." sense, or '' when it has none."""
    match = re.match(r"\s*(?:\d+\.\s*)?\[([^\]]+)\]", line)
    label = match.group(1) if match else ""
    for key in ("대명사", "명사", "동사", "형용사"):
        if key in label:
            return key
    if "관형사" in label:  # NIKL determiners are adjectives in English
        return "형용사"
    return label.strip()

def merge_meanings(direct, nikl_text, max_direct=4, max_nikl=2):
    """Korean-Wiktionary senses first. Wiktionary only keeps senses that have a Korean gloss, so a
    whole part of speech can vanish (palm, date and match lost their noun senses, and the NIKL
    entry used to be overwritten). NIKL senses (already ranked by learner level) are added only
    for parts of speech the Wiktionary senses do not cover, which avoids padding well-covered
    verbs like run or set with loosely related Korean verbs."""
    merged = list(direct[:max_direct])
    present = {pos_of(line) for line in merged if pos_of(line)}
    extras, heads = [], set()
    for line in (nikl_text or "").splitlines():
        body = re.sub(r"^\d+\.\s*", "", line).strip()
        pos = pos_of(body)
        match = re.search(r"\]\s*([^:\]]+):|^([^:\[\]]+):", body)
        head = next((g.strip() for g in match.groups() if g), "") if match else ""
        if not head or head in heads:
            continue
        if merged and (not pos or pos in present):
            continue
        extras.append(body)
        heads.add(head)
        if len(extras) == max_nikl:
            break
    return merged + extras, bool(extras)

def direct_records(lines):
    records = {}
    for line in lines:
        entry = json.loads(line)
        if entry.get("lang_code") != "en":
            continue
        word = " ".join(entry.get("word", "").lower().split())
        if not re.fullmatch(r"[a-z][a-z '\-]*", word):
            continue
        pos = POS.get(entry.get("pos"), "")
        if not pos:
            categories = [c.get("name", "") for c in entry.get("categories", []) if isinstance(c, dict)]
            labels = [v for v in POS.values() if "영어 " + v in categories]
            if len(labels) == 1:
                pos = labels[0]
        for sense in entry.get("senses", []):
            gloss = "; ".join(clean_gloss(g) for g in sense.get("glosses", [])
                              if isinstance(g, str) and re.search("[가-힣]", g))
            if not re.search("[가-힣]", gloss):
                continue
            if not gloss:
                continue
            value = ("[" + pos + "] " if pos else "") + gloss
            values = records.setdefault(word, [])
            if value not in values:
                values.append(value)
    return records

def add_direct_dictionary(root, destination):
    path = Path(root) / ".learning_data/kowiktionary-english.jsonl"
    path.parent.mkdir(exist_ok=True)
    if not path.exists():
        temporary = path.with_suffix(".part")
        subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--max-time", "120",
                        URL, "--output", str(temporary)], check=True)
        # Validate the entire JSONL before replacing a known-good cache.
        with temporary.open(encoding="utf8") as source:
            records = direct_records(source)
        temporary.replace(path)
    else:
        with path.open(encoding="utf8") as source:
            records = direct_records(source)
    translations = load_translations(root)
    assert len(records) > 10000, "Incomplete Korean-Wiktionary English data"
    assert "preliminary" in records and any("예비" in g for g in records["preliminary"])
    expected = ("initial", "important", "ordinary", "simple", "prepare", "eat", "go",
                "basic", "evidence", "appropriate", "consequence", "significant", "effective",
                "available", "possible", "particular", "general", "specific", "immediate",
                "primary", "improve", "increase", "decrease", "result", "research")
    assert all(word in records for word in expected), "Missing ordinary English headword"
    with sqlite3.connect(destination) as db:
        db.execute("ALTER TABLE words ADD COLUMN source TEXT DEFAULT 'NIKL'")
        new = merged_count = translated_count = 0
        # Headwords that only the English Wiktionary translates are added too.
        for word in translations:
            records.setdefault(word, [])
        for word, senses in records.items():
            old = db.execute("SELECT meaning_ko FROM words WHERE word=?", (word,)).fetchone()
            new += old is None
            # Direct English -> Korean senses lead; NIKL senses fill in what they lack.
            chosen, merged = merge_meanings(senses, old[0] if old else "")
            merged_count += merged
            # English-Wiktionary translations only complete thin entries (fewer than four senses, at most
            # two added): for well-covered words their rare senses ("one" = a one-dollar bill) are noise.
            extra = translations.get(word, [])
            if extra and len(chosen) < 4:
                grown = add_missing(chosen, extra, " ".join(chosen), min(FINAL_SENSES, len(chosen) + 2))
                if len(grown) > len(chosen):
                    translated_count += 1
                    chosen, merged = grown, True
            chosen = chosen[:FINAL_SENSES]
            korean = "\n".join(str(i + 1) + ". " + s for i, s in enumerate(chosen))
            db.execute("INSERT OR REPLACE INTO words(word,meaning_ko,meaning_en,ipa,source) VALUES(?,?,'','',?)",
                       (word, korean, "MERGED" if merged else "KOWIKTIONARY"))
        total = db.execute("SELECT count(*) FROM words").fetchone()[0]
        # The primary key handles exact lookup; FTS4 handles partial multi-word input.
        db.execute("DROP TABLE IF EXISTS words_fts")
        db.execute("CREATE VIRTUAL TABLE words_fts USING fts4(word)")
        db.execute("INSERT INTO words_fts(docid, word) SELECT rowid, word FROM words")
        indexed = db.execute("SELECT count(*) FROM words_fts").fetchone()[0]
        assert indexed == total, "Incomplete FTS phrase index"
    print("Direct Korean-Wiktionary headwords:", len(records), "new headwords:", new,
          "merged with NIKL:", merged_count, "with English-Wiktionary translations:", translated_count, "total:", total)
