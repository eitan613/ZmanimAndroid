"""Build finite Oraysa references and validate every weekday against its official PDF.

Usage: python3 tools/prepare_oraysa.py /path/to/oraysa-official.pdf
Source: https://dev.oraysa.org/_files/ugd/4323da_75e996c05d904bfca2643c55d3a1e82b.pdf
The publisher's Yevamot calendar includes 2025-11-23 through 2026-11-07.
Requires Poppler pdftotext; no downloaded book text or page images are packaged.
Run prepare_learning_supplements.py first, then this script, then prepare_learning.mjs.
"""
import datetime as dt
import hashlib,json,re,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
source=Path(sys.argv[1])
bbox=subprocess.check_output(['pdftotext','-bbox-layout',str(source),'-'])
ns={'h':'http://www.w3.org/1999/xhtml'}
months={name:i+1 for i,name in enumerate(['JANUARY','FEBRUARY','MARCH','APRIL','MAY','JUNE','JULY','AUGUST','SEPTEMBER','OCTOBER','NOVEMBER','DECEMBER'])}
letters=dict(zip('אבגדהוזחטיכלמנסעפצקרשת',[1,2,3,4,5,6,7,8,9,10,20,30,40,50,60,70,80,90,100,200,300,400]))
def gematria(value):return sum(letters.get(c,0) for c in value)
def hebrew(n):
 result=''
 for value,letter in [(100,'ק'),(90,'צ'),(80,'פ'),(70,'ע'),(60,'ס'),(50,'נ'),(40,'מ'),(30,'ל'),(20,'כ'),(10,'י'),(9,'ט'),(8,'ח'),(7,'ז'),(6,'ו'),(5,'ה'),(4,'ד'),(3,'ג'),(2,'ב'),(1,'א')]:
  while n>=value:result+=letter;n-=value
 return result.replace('יה','טו').replace('יו','טז')
# Explicit published boundary: Chagigah ends at 27a, Yevamot at 122b.
readings=[('Chagigah','חגיגה',26,'a'),('Chagigah','חגיגה',26,'b'),('Chagigah','חגיגה',27,'a')]
readings += [('Yevamot','יבמות',n,side) for n in range(2,123) for side in ['a','b']]
readings += [('Ketubot','כתובות',n,side) for n in range(2,5) for side in ['a','b']]
start=dt.date(2025,11,23);end=dt.date(2026,11,7)
new={};cursor=0;date=start
while date<=end:
 if date.weekday() not in [4,5]:new[date]=readings[cursor];cursor+=1
 date+=dt.timedelta(days=1)
assert new[dt.date(2025,11,26)]==('Yevamot','יבמות',2,'a')
assert new[dt.date(2026,10,29)]==('Yevamot','יבמות',122,'b')
assert new[dt.date(2026,11,1)]==('Ketubot','כתובות',2,'a')
# Validate all 250 weekday readings against the PDF's positioned date/reference cells.
verified={}
for page in ET.fromstring(bbox).findall('.//h:page',ns)[1:7]:
 words=[(w.text,float(w.get('xMin')),float(w.get('yMin'))) for w in page.findall('.//h:word',ns)]
 for month,x,y in words:
  if month not in months:continue
  days=[int(t) for t,xx,yy in words if t.isdigit() and x<xx<x+60 and abs(yy-y)<1]
  if len(days)!=1:continue
  candidates=[d for d in new if d.month==months[month] and d.day==days[0]]
  refs=[t for t,xx,yy in words if x-60<xx<x-5 and abs(yy-(y-5))<3 and re.fullmatch(r'[.:][א-ת]+',t)]
  if not refs:continue # Friday/Shabbat contain ranges, not a new amud.
  assert len(candidates)==1,(month,days,refs)
  date=candidates[0];assert len(refs)==1,(date,refs)
  expected=new[date]
  assert (gematria(refs[0]),'a' if refs[0][0]=='.' else 'b')==expected[2:],(date,refs,expected)
  verified[date]=expected
assert set(verified)==set(new),(len(verified),len(new),set(new)-set(verified))
def render(ref,he=False):
 en,heb,n,side=ref
 return f'{heb} {hebrew(n)} ע״{"א" if side=="a" else "ב"}' if he else f'{en} {n}{side}'
rows=[];date=start
while date<=end:
 if date in new:
  previous=max((d for d in new if d<date),default=None)
  review=new[previous] if previous else ('Chagigah','חגיגה',25,'b')
  en=f'{render(new[date])} · Review {render(review)}'
  he=f'{render(new[date],True)} · חזרה {render(review,True)}'
 else:
  sunday=date-dt.timedelta(days=(date.weekday()+1)%7)
  offsets=[0,1] if date.weekday()==4 else [2,3,4]
  refs=[new[sunday+dt.timedelta(days=i)] for i in offsets]
  en='Review '+', '.join(render(r) for r in refs)
  he='חזרה '+', '.join(render(r,True) for r in refs)
 rows.append('\t'.join([date.isoformat(),'oraysa',en,he]));date+=dt.timedelta(days=1)
path=Path('tools/learning-supplements.tsv')
existing=[r for r in path.read_text().splitlines() if r.split('\t')[1]!='oraysa']
path.write_text('\n'.join(sorted(existing+rows))+'\n')
meta=Path('tools/learning-supplement-metadata.json');data=json.loads(meta.read_text())
data['providers']=[p for p in data['providers'] if p['id']!='oraysa']+[{'id':'oraysa','en':'Oraysa · Amud V’chazara','he':'אורייתא · עמוד וחזרה','cycle':'Finite published Yevamot calendar with bordering Chagigah/Ketubot readings. New amud Sunday–Thursday, previous-amud review; Friday reviews Sunday/Monday, Shabbat Tuesday–Thursday. Includes holidays as printed; no extrapolation after source coverage.','validFrom':start.isoformat(),'validThrough':end.isoformat(),'source':'Oraysa official printed calendar','version':'Yevamot 2025–2026 · snapshot 2026-11-04','sources':[{'url':'https://dev.oraysa.org/_files/ugd/4323da_75e996c05d904bfca2643c55d3a1e82b.pdf','sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'records':len(rows),'validatedWeekdays':len(verified)}]}]
meta.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print('Oraysa:',len(rows),'days;',len(verified),'weekday readings validated against official PDF')
