"""Regenerate typed adapters from the pinned KosherJava source artifact, never reflection at runtime."""
from pathlib import Path
import re, zipfile, json, argparse, os, csv
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, help='Path to zmanim-3.0.0-sources.jar')
args=parser.parse_args()
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))
source=args.source or next((cache/'caches/modules-2/files-2.1/com.kosherjava/zmanim/3.0.0').glob('*/*-sources.jar'),None)
if source is None:parser.error('Pass --source with the pinned source JAR. Regeneration is optional; the generated Kotlin is checked in.')
methods={}
bodies={}
with zipfile.ZipFile(source) as z:
 for cls in ['AstronomicalCalendar','ZmanimCalendar','ComprehensiveZmanimCalendar']:
  s=z.read(f'com/kosherjava/zmanim/{cls}.java').decode()
  for match in re.finditer(r'(?:public|protected|private)\s+(?:static\s+)?(?:synchronized\s+)?[\w<>]+\s+(get\w+)\s*\([^)]*\)\s*\{', s):
   i=match.end(); depth=1; end=i
   while depth and end<len(s):
    if s[end]=='{':depth+=1
    if s[end]=='}':depth-=1
    end+=1
   bodies.setdefault(match.group(1),[]).append(s[i:end-1])
  for typ,name in re.findall(r'public\s+(Instant|Duration)\s+(get\w+)\s*\(\s*\)',s): methods[name]=typ

def describe(name):
 if name in ('getSunriseBasedOnElevationSetting','getSunsetBasedOnElevationSetting'):
  return ('Sunrise' if 'Sunrise' in name else 'Sunset') + ' (profile elevation policy)'
 s=name[3:]
 replacements={'ShaahZmanis':'Temporal hour','SofZmanShma':'Latest Shema','SofZmanTfila':'Latest teffilah','SofZmanAchilasChametz':'Chametz eating deadline','SofZmanBiurChametz':'Chametz disposal deadline','Misheyakir':'Misheyakir','Alos':'Dawn','Tzais':'Nightfall','MinchaGedola':'Earliest Mincha','MinchaKetana':'Mincha ketanah','PlagHamincha':'Plag','ChatzosHayom':'Midday','ChatzosHalayla':'Midnight','Sunrise':'Sunrise','Sunset':'Sunset','BainHashmashos':'Twilight','TchilasZmanKidushLevana':'Earliest Kiddush Levanah','SofZmanKidushLevana':'Latest Kiddush Levanah','ZmanMolad':'Molad','FixedLocal':'Fixed local','SamuchLeMinchaKetana':'Before Mincha ketanah','Begin':'Morning','End':'Evening','GRA':'Vilna Gaon','MGA':'Magen Avraham','BaalHatanya':'Baal HaTanya','AteretTorah':'Ateret Torah','AhavatShalom':'Ahavat Shalom','Ahavat':'Ahavat','RT':'Rabbeinu Tam','Yereim':'Yereim','Geonim':'Geonim','GreaterThan':'Later than','TeshuvosVehanhagos':'Teshuvot VeHanhagot','BenIshChai':'Ben Ish Chai'}
 for a,b in sorted(replacements.items(), key=lambda x:-len(x[0])): s=s.replace(a,b+' ')
 s=re.sub(r'(\d+)Point(\d+)',r'\1.\2',s)
 s=re.sub(r'(\d+)point(\d+)',r'\1.\2',s)
 s=s.replace('Degrees','° below horizon').replace('MinutesZmanis',' temporal minutes').replace('Zmanis',' temporal minutes').replace('Minutes',' minutes').replace('Hours',' hours').replace('Days',' days')
 s=re.sub(r'([a-z])([A-Z])',r'\1 \2',s)
 s=re.sub(r'([a-z])(\d)',r'\1 \2',s)
 return ' '.join(s.split())
def group(n):
 if 'Shaah' in n or n=='getTemporalHour': return 'duration'
 if 'KidushLevana' in n or 'Molad' in n:return 'lunar'
 if 'Chametz' in n:return 'chametz'
 if 'Candle' in n:return 'candles'
 if 'SofZmanShma' in n:return 'shema'
 if 'SofZmanTfila' in n:return 'prayer'
 if n.startswith('getAlos'):return 'dawn'
 if 'Misheyakir' in n:return 'misheyakir'
 if 'MinchaGedola' in n:return 'mincha-gedolah'
 if 'MinchaKetana' in n or 'SamuchLeMincha' in n:return 'mincha-ketanah'
 if 'Plag' in n:return 'plag'
 if 'Tzais' in n:return 'nightfall'
 if 'Sunrise' in n:return 'sunrise'
 if 'Sunset' in n:return 'sunset'
 if 'Chatzos' in n or 'SunTransit' in n or 'Midnight' in n:return 'chatzos'
 return 'twilight'
ids={'getSeaLevelSunrise':'sea-level-sunrise','getSeaLevelSunset':'sea-level-sunset'}
def ident(n):return ids.get(n,re.sub(r'([a-z0-9])([A-Z])',r'\1-\2',n[3:]).lower())
def sensitive(name, visited=None):
 if name in ('getSunrise','getSunset','getSunriseBasedOnElevationSetting','getSunsetBasedOnElevationSetting'):return True
 visited=set() if visited is None else visited
 if name in visited:return False
 visited.add(name)
 return any(sensitive(call,visited.copy()) for body in bodies.get(name,[]) for call in re.findall(r'\b(get\w+)\(',body))

def half_days(name, visited=None):
 visited=set() if visited is None else visited
 if name in visited:return False
 visited.add(name)
 for body in bodies.get(name,[]):
  if re.search(r'get(?:SofZmanShma|SofZmanTfila|MinchaGedola|MinchaKetana|PlagHamincha)\([^;]+,\s*true\s*\)',body):return True
  if any(half_days(call,visited.copy()) for call in re.findall(r'\b(get\w+)\(\s*\)',body)):return True
 return False

def boundaries(name, visited=None):
 visited=set() if visited is None else visited
 if name in visited:return ''
 visited.add(name)
 found=[]
 for body in bodies.get(name,[]):
  for call in re.findall(r'\b(get(?:Sunrise\w*|Sunset\w*|SeaLevelSunrise|SeaLevelSunset|Alos\w*|Tzais\w*|FixedLocalChatzosHayom|ChatzosHayom))\(\s*\)', body):
   if call not in found:found.append(call)
 if len(found)>=2:return 'Day: ' + ' → '.join(describe(x) for x in found[:2])
 for body in bodies.get(name,[]):
  for call in re.findall(r'\b(get\w+)\(\s*\)',body):
   inherited=boundaries(call,visited.copy())
   if inherited:return inherited
 return ''
lines=['// Generated by tools/generate_catalog.py from KosherJava 3.0.0. Labels require owner review.','@file:Suppress("DEPRECATION") // The complete pinned catalog intentionally exposes these explicit choices.','package com.example.zmanim.domain','','import com.kosherjava.zmanim.ComprehensiveZmanimCalendar','','object MethodCatalog {','    val methods: List<ZmanMethod> = listOf(']
review=[]
for name,typ in methods.items():
 label=describe(name)
 boundary=boundaries(name)
 if boundary and group(name) in ('shema','prayer','mincha-gedolah','mincha-ketanah','plag','duration'):label+=' · '+boundary
 if name=='getSeaLevelSunrise':label='Sunrise · sea level'
 if name=='getSeaLevelSunset':label='Sunset · sea level'
 review.append([ident(name),group(name),label,typ,'Ateret Torah sunset offset' if 'AteretTorah' in name else 'Candle-lighting offset' if name=='getCandleLighting' else '',sensitive(name),half_days(name),'Pending owner review'])
 lines.append(f'        ZmanMethod({json.dumps(ident(name))}, {json.dumps(group(name))}, {json.dumps(label)}, {str(typ=="Duration").lower()}, '+('true' if 'AteretTorah' in name else 'false')+', '+('true' if name=='getCandleLighting' else 'false')+', '+str(sensitive(name)).lower()+', '+str(half_days(name)).lower()+f') {{ c -> c.{name}() }},')
lines+=['    )','    val byId = methods.associateBy { it.id }','}']
Path('app/src/main/java/com/example/zmanim/domain/MethodCatalog.kt').write_text('\n'.join(lines)+'\n')
with Path('docs/method-catalog.csv').open('w',newline='') as f:
 writer=csv.writer(f);writer.writerow(['id','group','label','result','required_parameter','uses_elevation','supports_profile_half_days','review']);writer.writerows(review)
print(f'Generated {len(methods)} typed calculation adapters and owner-review catalog.')
