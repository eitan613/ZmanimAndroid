"""Extract factual Oz V'hadar references from Kollel Iyun Hadaf's published calendars.

Download calendaryeruoz11heb-cycle.htm and calendaryeruoz12heb-cycle.htm from
https://www.dafyomi.co.il/calendars/ as learning-oz11.html/learning-oz12.html.
Run: python3 tools/prepare_learning_supplements.py /path/to/download-directory
This is a finite source table, never an alias for Vilna or Schottenstein.
"""
import datetime as dt
from html import unescape
import hashlib,json,re,sys
from pathlib import Path
months={'ינו':1,'פבר':2,'מרץ':3,'אפר':4,'מאי':5,'יונ':6,'יול':7,'אוג':8,'ספט':9,'אוק':10,'נוב':11,'דצמ':12}
books=dict(zip(['ברכות','פאה','דמאי','כלאים','שביעית','תרומות','מעשרות','מעשר שני','חלה','ערלה','ביכורים','שבת','עירובין','פסחים','שקלים','יומא','סוכה','ביצה','ראש השנה','תענית','מגילה','חגיגה','מועד קטן','יבמות','כתובות','נדרים','נזיר','סוטה','גיטין','קדושין','בבא קמא','בבא מציעא','בבא בתרא','סנהדרין','מכות','שבועות','עבודה זרה','הוריות','נדה'],['Berakhot','Peah','Demai','Kilayim','Sheviit','Terumot','Maasrot','Maaser Sheni','Challah','Orlah','Bikkurim','Shabbat','Eruvin','Pesachim','Shekalim','Yoma','Sukkah','Beitzah','Rosh Hashanah','Taanit','Megillah','Chagigah','Moed Katan','Yevamot','Ketubot','Nedarim','Nazir','Sotah','Gittin','Kiddushin','Bava Kamma','Bava Metzia','Bava Batra','Sanhedrin','Makkot','Shevuot','Avodah Zarah','Horayot','Niddah']))
letters=dict(zip('אבגדהוזחטיכלמנסעפצקרשת',[1,2,3,4,5,6,7,8,9,10,20,30,40,50,60,70,80,90,100,200,300,400]))
def number(word):return sum(letters.get(c,0) for c in word)
rows=[];sources=[];cycles=[]
for cycle in [11,12]:
 raw=(Path(sys.argv[1])/f'learning-oz{cycle}.html').read_bytes()
 text=raw.decode('cp1255');assert f'מחזור {cycle}' in text and 'עוז והדר' in text
 extracted=[]
 for row in re.findall(r'<tr[^>]*>(.*?)</tr>',text,re.S):
  values=[' '.join(unescape(re.sub('<[^>]+>',' ',x)).split()) for x in re.findall(r'<td[^>]*>(.*?)</td>',row,re.S)]
  if len(values)!=4:continue
  day,month,year=values[0].split();date=dt.date(int(year),months[month],int(day))
  book,daf=values[3].rsplit(' ',1);assert book in books and number(daf)>0
  extracted.append([date.isoformat(),'yerushalmi-oz',f'{books[book]} {number(daf)}',values[3]])
 assert len(extracted)==2094
 for a,b in zip(extracted,extracted[1:]):assert dt.date.fromisoformat(b[0])-dt.date.fromisoformat(a[0])==dt.timedelta(days=1)
 assert extracted[0][2]=='Berakhot 1' and extracted[-1][2]=='Niddah 11'
 cycles.append(extracted);rows+=extracted
 sources.append({'url':f'https://www.dafyomi.co.il/calendars/calendaryeruoz{cycle}heb-cycle.htm','sha256':hashlib.sha256(raw).hexdigest(),'cycle':cycle,'records':len(extracted)})
assert [r[2] for r in cycles[0]]==[r[2] for r in cycles[1]]
assert dt.date.fromisoformat(cycles[1][0][0])-dt.date.fromisoformat(cycles[0][-1][0])==dt.timedelta(days=1)
provider={'id':'yerushalmi-oz','schedule':'oz-vhadar-published-cycles-11-12','en':'Yerushalmi · Oz V’hadar','he':'ירושלמי · עוז והדר',
 'cycle':'Published Oz V’hadar pagination, cycles 11–12; 2,094 consecutive civil days per published cycle; no holiday skips in these source tables. Distinct from Vilna and Schottenstein.',
 'validFrom':rows[0][0],'validThrough':rows[-1][0], 'source':'Kollel Iyun Hadaf / dafyomi.co.il', 'version':'Published cycles 11–12 · snapshot 2026-11-04','sources':sources}
Path('tools/learning-supplements.tsv').write_text('\n'.join('\t'.join(r) for r in rows)+'\n')
Path('tools/learning-supplement-metadata.json').write_text(json.dumps({'providers':[provider]},ensure_ascii=False,indent=2)+'\n')
print(len(rows),'Oz V’hadar references;',provider['validFrom'],'through',provider['validThrough'])
for row in rows:
 if row[0] in ['2022-11-14','2026-11-04','2028-08-07','2028-08-08']:print(row)
