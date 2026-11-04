"""Rebuild the reviewed ID corrections from a downloaded CBS 2023 datastore response.

Usage: python review_localities.py /path/to/cbs.json (requires pyproj at build time).
Source: https://data.gov.il/he/datasets/lamas/localities-in-israel/d47a54ff-87f0-44b3-b33a-f284c0c38e5a
A match requires review of both names and coordinates. Never select a nearest city
for GPS, never convert an entire country category, and never auto-add new IDs.
"""
import csv, gzip, json, math, sys
from pathlib import Path
from pyproj import Transformer
selected = {'282431':3640,'282993':3616,'283046':3779,'283991':3650,'284279':3652,
            '285101':3750,'8199386':3730,'8199420':3760,'11979646':3763,'12156437':3574}
source = 'https://data.gov.il/he/datasets/lamas/localities-in-israel/d47a54ff-87f0-44b3-b33a-f284c0c38e5a'
records = {r['סמל יישוב']:r for r in json.load(open(sys.argv[1]))['result']['records']}
transform = Transformer.from_crs(2039,4326,always_xy=True)
corrections = {}
audit = []
for line in gzip.open('app/src/main/assets/cities.tsv.gzip','rt'):
 f=line.rstrip('\n').split('\t')
 if f[2] != 'PS': continue
 if f[0] not in selected:
  audit.append([f[0],f[1],f[2],f[5],'Unchanged; not an identified Israeli locality'])
  continue
 record=records[selected[f[0]]]; coords=str(record['קואורדינטות'])
 lon,lat=transform.transform(int(coords[:6]),int(coords[6:]))
 km=math.hypot(float(f[3])-lat,(float(f[4])-lon)*math.cos(math.radians(lat)))*111.2
 assert km<2.0, (f[1],km)
 corrections[f[0]]={'name':f[1],'country':'IL','zone':'Asia/Jerusalem','inIsrael':True,
  'sourceLatitude':float(f[3]),'sourceLongitude':float(f[4]),'cbsId':selected[f[0]],
  'cbsName':record['שם יישוב'],'cbsEnglish':record['תעתיק'],'cbsLatitude':lat,'cbsLongitude':lon,
  'distanceKm':round(km,3),'source':source,'sourceYear':2023,
  'aliases':record['שם יישוב']+','+record['תעתיק']}
 audit.append([f[0],f[1],'IL','Asia/Jerusalem',f"Reviewed CBS {selected[f[0]]}: {record['שם יישוב']}; {km:.3f} km"])
assert len(corrections)==len(selected)
Path('app/src/main/assets/locality-corrections.json').write_text(json.dumps(corrections,ensure_ascii=False,indent=2)+'\n')
with open('docs/locality-audit.csv','w') as out:
 w=csv.writer(out);w.writerow(['geonames_id','name','display_country','civil_timezone','review']);w.writerows(audit)
print(len(audit),'PS source records audited;',len(corrections),'explicit corrections.')
