# -*- coding: utf-8 -*-
import io
p = r'knowledge-production/2027-53-transcripts/MATH/【2027】53高中知识清单高中数学必修+选择性必修/range_0477_0490.jsonl'
t = io.open(p, encoding='utf-8').read()
pats = [
    'm=1',
    'g(x)_{\\min}',
    '\\frac{1-\\ln x}{x^2}',
    'h(x)=\\frac{\\ln x}{x}',
    '(0,e) 上单调递增',
    '\\left(\\frac{1}{e},+\\infty\\right)$ 上单调递增',
    '由(1)知',
    '得$0<x<1$',
]
for pat in pats:
    print(pat, '->', t.count(pat))
