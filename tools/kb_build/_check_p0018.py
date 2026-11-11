# -*- coding: utf-8 -*-
import json, re, sys

def check(p, expected_page):
    lines = open(p, encoding='utf-8').read().splitlines()
    bad = 0
    total_chars = 0
    n = 0
    for i, l in enumerate(lines, 1):
        if not l.strip():
            continue
        n += 1
        try:
            o = json.loads(l)
        except Exception as e:
            print('JSON ERROR line', i, e); bad += 1; continue
        t = o.get('text', '')
        total_chars += len(t)
        if t.count('$') % 2 != 0:
            print('ODD $ line', i, t); bad += 1
        # backslash immediately followed by a digit in the logical text
        m = re.search(r'\\\d', t)
        if m:
            print('BS+DIGIT line', i, m.group(0)); bad += 1
        # backslash right before Chinese full-width punctuation
        m2 = re.search(r'\\[，。；：、（）「」！？——…]', t)
        if m2:
            print('BS+CN-PUNCT line', i, m2.group(0)); bad += 1
        # control chars (excluding none; \t\n not allowed inside text)
        m3 = re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', t)
        if m3:
            print('CTRL line', i, repr(m3.group(0))); bad += 1
        if o.get('page') != expected_page:
            print('PAGE line', i, o.get('page')); bad += 1
    print('file', p, 'records', n, 'bad', bad, 'chars', total_chars)
    return n, bad, total_chars

if __name__ == '__main__':
    for pg in (18, 19):
        check('knowledge-production/2027-53-fill/CHEMISTRY/p%04d.jsonl' % pg, pg)
