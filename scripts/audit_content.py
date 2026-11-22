#!/usr/bin/env python3
"""Independent exact-rational math audit and inventory integrity checks (no eval)."""
import ast,json,hashlib,re,sys,unicodedata
from fractions import Fraction
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def exact(node):
    if isinstance(node,ast.Expression):return exact(node.body)
    if isinstance(node,ast.Constant) and type(node.value) in (int,float):return Fraction(ast.get_source_segment(EXPRESSION,node))
    if isinstance(node,ast.BinOp):
        a,b=exact(node.left),exact(node.right)
        if isinstance(node.op,ast.Add):return a+b
        if isinstance(node.op,ast.Sub):return a-b
        if isinstance(node.op,ast.Mult):return a*b
        if isinstance(node.op,ast.Div):return a/b
    raise ValueError(ast.dump(node))
counts={};seen=set();details={};numeric=0;logic=0
for subject in ('math','chinese','english','poetry','riddle','science','ai'):
    values=json.loads((ROOT/'content'/f'{subject}.json').read_text());counts[subject]=len(values);assert len(values)>=300
    for x in values:
        key=(subject,re.sub(r'\s+','',unicodedata.normalize('NFKC',x['prompt'])))
        assert key not in seen,key;seen.add(key)
        assert len(set(x['choices']))==4 and x['choices'][0]==x['answer']
        if subject=='math':
            EXPRESSION=x['expression'];expected=exact(ast.parse(EXPRESSION,mode='eval'));actual=Fraction(x['answer']);assert actual==expected,x
            assert len({Fraction(c) for c in x['choices']})==4,x
            numeric+=1
        if subject=='riddle':
            nums=[int(n) for n in re.findall(r'\d+',x['prompt'])];topic=x['topic'];expected=None
            if topic=='间隔推理':expected=nums[0]//nums[1]+1
            elif topic=='环形推理':expected=nums[0]
            elif topic=='敲钟间隔':expected=Fraction(nums[1],nums[0]-1)*(nums[2]-1)
            elif topic=='楼层推理':expected=nums[1]-nums[0]
            elif topic=='最坏情况·异色':expected=max(nums)+1
            elif topic=='抽屉推理':expected=(nums[0]+nums[1]-1)//nums[1]
            elif topic=='配对计数':expected=nums[0]*(nums[0]-1)//2
            elif topic=='并行推理':expected=nums[2]
            elif topic=='规律·等差':expected=nums[-1]+nums[0]
            elif topic=='规律·倍增':expected=nums[-1]*nums[0]
            elif topic=='空间想象':expected=6*nums[0]-2*(nums[0]-1)
            elif topic=='折叠与切口':expected=2*(nums[1]-1)+1
            elif topic=='图形计数':expected=4+3*(nums[0]-1)
            elif topic=='组合推理':expected=nums[0]*(nums[0]-1)//2
            if expected is not None:
                assert Fraction(x['answer'])==expected,(x,expected);logic+=1
        assert not any(t in x['prompt'] for t in ('这条题目不采用','TODO','待补充'))
    details[subject]={'count':len(values),'topics':len(set(v['topic'] for v in values)),'maxPromptChars':max(len(v['prompt']) for v in values),'maxChoiceChars':max(len(c) for v in values for c in v['choices'])}
report={'version':'0.9.0','total':sum(counts.values()),'exactMathChecksPassed':numeric,'logicInstancesChecked':logic,'details':details,'scope':'结构和数值独立校验；不等同于教材专家逐题审定。语文、英语、科普和唐诗另经来源与语义抽查。'}
if '--json' in sys.argv:print(json.dumps(report,ensure_ascii=False,indent=2))
else:print(f'PASS: {sum(counts.values())} unique prompts; {numeric} math expressions and numeric option uniqueness verified')
