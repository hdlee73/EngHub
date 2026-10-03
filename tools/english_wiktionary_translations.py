"""Korean translations from the English Wiktionary (kaikki.org English dump).

The English Wiktionary lists Korean translations per sense ("to cheat": 속이다, 바가지를 씌우다), which
fills in senses that the Korean Wiktionary and the NIKL reverse index leave out."""
import json
import re
import subprocess
from pathlib import Path

URL = "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl"
POS = {"noun": "명사", "verb": "동사", "adj": "형용사", "adv": "부사", "prep": "전치사",
       "conj": "접속사", "pron": "대명사", "intj": "감탄사", "phrase": "숙어", "num": "수사"}
MAX_WORDS_PER_SENSE = 3
MAX_SENSES_PER_ENTRY = 4

def clean_word(word):
    word = re.sub(r"\s*[\[(][^\])]*[\])]\s*", " ", word or "")
    word = re.sub(r"\s+", " ", word).strip()
    return word if re.search("[가-힣]", word) and len(word) <= 20 and not re.search("[A-Za-z]", word) else ""

def translation_records(lines):
    """{headword: ["[동사] 속이다, 바가지를 씌우다", ...]} from English-Wiktionary JSON lines."""
    records = {}
    for line in lines:
        if '"ko"' not in line:  # cheap pre-filter: most entries have no Korean translation
            continue
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if entry.get("lang_code") != "en":
            continue
        word = " ".join(entry.get("word", "").lower().split())
        if not re.fullmatch(r"[a-z][a-z '\-]*", word):
            continue
        pos = POS.get(entry.get("pos"), "")
        groups = {}
        for item in entry.get("translations", []):
            if not isinstance(item, dict) or (item.get("lang_code") or item.get("code")) != "ko":
                continue
            korean = clean_word(item.get("word"))
            if korean:
                words = groups.setdefault((item.get("sense") or "").strip().lower(), [])
                if korean not in words and len(words) < MAX_WORDS_PER_SENSE:
                    words.append(korean)
        for words in groups.values():
            senses = records.setdefault(word, [])
            value = ("[" + pos + "] " if pos else "") + ", ".join(words)
            if value not in senses and len(senses) < MAX_SENSES_PER_ENTRY:
                senses.append(value)
    return records

def add_missing(senses, extra_senses, known_text, limit):
    """Append translation senses whose Korean words are not already covered, up to [limit] senses."""
    result = list(senses)
    known = known_text
    for sense in extra_senses:
        if len(result) >= limit:
            break
        body = re.sub(r"^\[[^\]]*\]\s*", "", sense)
        fresh = [w.strip() for w in body.split(",") if w.strip() and w.strip() not in known]
        if not fresh:
            continue
        label = re.match(r"^\[[^\]]*\]\s*", sense)
        result.append((label.group(0) if label else "") + ", ".join(fresh))
        known += " " + " ".join(fresh)
    return result

def load_translations(root):
    """Download and parse the dump; returns {} (and the build continues) when it is unavailable."""
    path = Path(root) / ".learning_data/english-wiktionary-ko.jsonl"
    path.parent.mkdir(exist_ok=True)
    try:
        if not path.exists():
            temporary = path.with_suffix(".part")
            with temporary.open("w", encoding="utf8") as out:
                curl = subprocess.Popen(["curl", "--fail", "--location", "--retry", "3", "--silent", URL],
                                        stdout=subprocess.PIPE)
                for raw in curl.stdout:
                    line = raw.decode("utf8", "replace")
                    if '"ko"' in line:
                        out.write(line)
                print("::notice::English-Wiktionary dump read; curl exit", curl.wait())
                if curl.returncode != 0:
                    raise RuntimeError("download failed: curl exit " + str(curl.returncode))
            temporary.replace(path)
        with path.open(encoding="utf8") as source:
            records = translation_records(source)
        print("::notice::English-Wiktionary Korean translations: %d headwords, cache %d bytes" % (len(records), path.stat().st_size))
        assert len(records) > 1000, "Too few English-Wiktionary Korean translations: " + str(len(records))
        return records
    except Exception as error:  # the dictionary still builds from the other sources
        print("::warning::English-Wiktionary translations unavailable:", error)
        return {}
