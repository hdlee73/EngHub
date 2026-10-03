"""Build exact English headword index from NIKL equivalents, plus bilingual examples.

Dictionary adaptation: NIKL Korean Basic Dictionary, CC BY-SA 2.0 KR.
Source mirror: binjang/NIKL-dictionary-parser, pinned January 2024 export.
Never index English definition text as if it were an English headword.
"""
from direct_korean_dictionary import add_direct_dictionary
import concurrent.futures
import json
import re
import sqlite3
import subprocess
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PIN = '52ffffdc0a6fd8d00d7dce45cffcc0930ebbdaea'
FILES = [f'{i}_5000_20240101.json' for i in range(1, 11)] + ['11_1960_20240101.json']
# NIKL marks headwords learners meet early; the source files are sorted by Korean spelling, so
# cutting at the first few senses would keep ㄱ-words and drop common ones (달리다 for "run").
LEVEL_RANK = {'초급': 0, '중급': 1, '고급': 2}
MAX_NIKL_SENSES = 5

def as_list(value):
    return value if isinstance(value, list) else [value] if value else []

def features(value):
    return {item.get('att'): item.get('val', '') for item in as_list(value)}

def download(name):
    path = ROOT / '.learning_data' / name
    path.parent.mkdir(exist_ok=True)
    for attempt in range(3):
        try:
            if not path.exists():
                url = f'https://raw.githubusercontent.com/binjang/NIKL-dictionary-parser/{PIN}/2024_01/{name}?full={attempt}'
                temporary = path.with_suffix('.part')
                urllib.request.urlretrieve(url, temporary)
                with temporary.open(encoding='utf8') as source:
                    json.load(source)
                temporary.replace(path)
            else:
                with path.open(encoding='utf8') as source:
                    json.load(source)
            return path
        except (ValueError, OSError):
            path.unlink(missing_ok=True)
            if attempt == 2:
                raise
            time.sleep(1)

def select_senses(senses, limit=MAX_NIKL_SENSES):
    """Most learner-relevant senses first, one per Korean headword, original order breaking ties."""
    ranked = sorted(enumerate(senses), key=lambda pair: (pair[1][2], pair[0]))
    chosen, heads = [], set()
    for _, item in ranked:
        if item[3] in heads:
            continue
        heads.add(item[3])
        chosen.append(item)
        if len(chosen) == limit:
            break
    return chosen

def build_dictionary(paths, destination):
    records = {}
    for path in paths:
        with path.open(encoding='utf8') as source:
            entries = json.load(source)['LexicalResource']['Lexicon']['LexicalEntry']
        for entry in entries:
            lemmas = as_list(entry.get('Lemma'))
            head = next((features(lemma.get('feat')).get('writtenForm', '') for lemma in lemmas), '')
            entry_features = features(entry.get('feat'))
            pos = entry_features.get('partOfSpeech', '').strip()
            rank = LEVEL_RANK.get(entry_features.get('vocabularyLevel', '').strip(), 3)
            for sense in as_list(entry.get('Sense')):
                korean = features(sense.get('feat')).get('definition', '').strip()
                if not head or not korean:
                    continue
                for equivalent in as_list(sense.get('Equivalent')):
                    f = features(equivalent.get('feat'))
                    if f.get('language') != '영어':
                        continue
                    english = f.get('definition', '').strip()
                    for lemma in re.split(r'[,;]', f.get('lemma', '')):
                        lemma = ' '.join(lemma.lower().split())
                        if not re.fullmatch(r"[a-z][a-z '\-]*", lemma):
                            continue
                        # Exact translated headwords only; no synonym inference.
                        item = (f'[{pos}] {head}: {korean}' if pos else f'{head}: {korean}', english, rank, head)
                        if all(item[:2] != known[:2] for known in records.setdefault(lemma, [])):
                            records[lemma].append(item)
        del entries
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.unlink(missing_ok=True)
    with sqlite3.connect(destination) as db:
        db.execute('CREATE TABLE words(word TEXT PRIMARY KEY COLLATE NOCASE, meaning_ko TEXT, meaning_en TEXT, ipa TEXT)')
        for word, senses in records.items():
            selected = select_senses(senses)
            ko = '\n'.join(f'{i+1}. {s[0]}' for i,s in enumerate(selected))
            en = '\n'.join(f'{i+1}. {s[1]}' for i,s in enumerate(selected) if s[1])
            db.execute('INSERT INTO words VALUES(?,?,?,?)', (word, ko, en, ''))
    assert len(records) > 10000, f'Incomplete dictionary: {len(records)}'
    assert 'spontaneous' in records
    assert all('자적:' not in item[0] for item in records['spontaneous'])
    assert any('달리다' in item[0] for item in select_senses(records['run'])), 'run must keep 달리다'
    assert any('손바닥' in item[0] for item in select_senses(records['palm']))
    print(f'Built {len(records)} exact English headwords from NIKL equivalents')

def build_examples(destination):
    archive = ROOT / '.learning_data' / 'kor-eng.zip'
    if not archive.exists():
        subprocess.run(['curl', '--fail', '--location', '--retry', '3', '--max-time', '60',
                        'https://www.manythings.org/anki/kor-eng.zip', '--output', str(archive)], check=True)
    with zipfile.ZipFile(archive) as z:
        data = z.read('kor.txt').decode('utf8')
    destination.unlink(missing_ok=True)
    with sqlite3.connect(destination) as db:
        db.execute('CREATE TABLE examples(id INTEGER PRIMARY KEY, english TEXT, korean TEXT, credit TEXT)')
        db.execute('CREATE TABLE tokens(token TEXT, example_id INTEGER)')
        for line in data.splitlines():
            row = line.split('\t')
            if len(row) < 3:
                continue
            en, ko, credit = row[:3]
            if not any('가' <= ch <= '힣' for ch in ko) or len(en) > 220:
                continue
            eid = db.execute('INSERT INTO examples(english,korean,credit) VALUES(?,?,?)', (en,ko,credit)).lastrowid
            db.executemany('INSERT INTO tokens VALUES(?,?)', [(w,eid) for w in set(re.findall(r"[a-z]+(?:'[a-z]+)?", en.lower()))])
        db.execute('CREATE INDEX token_index ON tokens(token)')
        count = db.execute('SELECT count(*) FROM examples').fetchone()[0]
    assert count > 1000
    print(f'Built {count} attributed human bilingual sentence pairs')

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        paths = list(executor.map(download, FILES))
    assets = ROOT / 'app/src/main/assets'
    build_dictionary(paths, assets / 'word_dictionary.sqlite')
    add_direct_dictionary(ROOT, assets / 'word_dictionary.sqlite')
    build_examples(assets / 'bilingual_examples.sqlite')
