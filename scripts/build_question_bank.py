#!/usr/bin/env python3
"""Validate authored items and produce the Android/Java offline resource and inventory report."""
import hashlib,json,unicodedata,re
from collections import Counter
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SUBJECTS=('math','chinese','english','poetry','riddle','science','ai')
def canonical(s):return re.sub(r'\s+','',unicodedata.normalize('NFKC',s))
def build(check=False):
    rows=[];counts={};seen=set();ids=set();coverage={}
    for subject in SUBJECTS:
        items=json.loads((ROOT/'content'/f'{subject}.json').read_text())
        for x in items:
            assert x['subject']==subject
            assert x['id'] not in ids,x['id'];ids.add(x['id'])
            key=subject+':'+canonical(x['prompt']);assert key not in seen,key;seen.add(key)
            assert len(x['choices'])==4 and len({canonical(s) for s in x['choices']})==4,x
            assert x['answer']==x['choices'][0] and x['explanation'] and x['reference'] and x['topic'],x
            assert 1<=x['minGrade']<=x['maxGrade']<=6 and x['semester'] in (0,1,2) and x['difficulty'] in (1,2,3)
            fields=[x['id'],subject,*[str(x[k]) for k in ('minGrade','maxGrade','semester','difficulty')],x['prompt'],*x['choices'],x['explanation'],x['reference'],x['topic']]
            assert len(fields)==14 and all('\t' not in s and '\n' not in s and '\r' not in s for s in fields),x['id']
            rows.append('\t'.join(fields))
        counts[subject]=len(items);assert len(items)>=300,(subject,len(items))
        coverage[subject]={}
        for grade in range(1,7):
            for term in range(3):
                for difficulty in range(1,4):
                    n=sum(x['minGrade']<=grade<=x['maxGrade'] and (term==0 or x['semester']==0 or x['semester']<=term) and x['difficulty']<=difficulty for x in items)
                    assert n>0,(subject,grade,term,difficulty)
                    coverage[subject][f'{grade}-{term}-{difficulty}']=n
    text='# id\tsubject\tminGrade\tmaxGrade\tsemester\tdifficulty\tprompt\tanswer\tdistractor1\tdistractor2\tdistractor3\texplanation\treference\ttopic\n'+'\n'.join(rows)+'\n'
    target=ROOT/'core/src/main/resources/questionbank/questions.tsv'
    if check:assert target.read_text()==text,'Generated resource is stale; run scripts/build_question_bank.py'
    else:target.write_text(text)
    report={'version':'0.9.0','counts':counts,'total':sum(counts.values()),'uniquePromptCount':len(seen),'resourceSha256':hashlib.sha256(text.encode()).hexdigest(),'eligibleCounts':coverage,'countPolicy':'同一类别题干规范化去重，不将选项重排或重复题计为新题；数量为每类总量，不是每个年级300题。'}
    reportText=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
    if check:assert (ROOT/'content/inventory.json').read_text()==reportText
    else:(ROOT/'content/inventory.json').write_text(reportText)
    print(json.dumps({'counts':counts,'total':sum(counts.values()),'sha256':report['resourceSha256']},ensure_ascii=False))
if __name__=='__main__':
    import sys
    build('--check' in sys.argv)
