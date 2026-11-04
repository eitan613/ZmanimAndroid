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
docs={}
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
  for m in re.finditer(r'/\*\*((?:(?!\*/).)*)\*/\s*(?:@[^\n]+\s*)*public\s+(?:Instant|Duration)\s+(get\w+)\s*\(\s*\)', s, re.S):
   doc=m.group(1).split('@return')[0].split('@see')[0].split('@deprecated')[0]
   doc=re.sub(r'\{@(?:link|code)\s+([^}]+)\}', lambda x: x.group(1).split(' ',1)[-1].split('#')[-1], doc)
   doc=re.sub(r'<[^>]*>', ' ', doc)
   doc=re.sub(r'\s*\*\s*', ' ', doc)
   doc=' '.join(doc.split())
   docs[m.group(2)]=doc
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
def hebrew_label(name, label):
 terms={'Temporal hour':'שעה זמנית','Temporal Hour':'שעה זמנית','Latest Shema':'סוף זמן קריאת שמע','Latest teffilah':'סוף זמן תפילה','Earliest Mincha':'מנחה גדולה','Mincha ketanah':'מנחה קטנה','Dawn':'עלות השחר','Nightfall':'צאת הכוכבים','Misheyakir':'משיכיר','Sunrise':'הנץ החמה','Sunset':'שקיעה','Midday':'חצות היום','Midnight':'חצות הלילה','Plag':'פלג המנחה','Twilight':'בין השמשות','Candle Lighting':'הדלקת נרות','Chametz eating deadline':'סוף זמן אכילת חמץ','Chametz disposal deadline':'סוף זמן ביעור חמץ','Earliest Kiddush Levanah':'תחילת זמן קידוש לבנה','Latest Kiddush Levanah':'סוף זמן קידוש לבנה','Molad':'מולד','Vilna Gaon':'הגר״א','Magen Avraham':'מגן אברהם','Baal HaTanya':'בעל התניא','Baal Ha Tanya':'בעל התניא','Geonim':'גאונים','Ateret Torah':'עטרת תורה','Ahavat Shalom':'אהבת שלום','Rabbeinu Tam':'רבנו תם','Yereim':'יראים','Ben Ish Chai':'בן איש חי','Teshuvot VeHanhagot':'תשובות והנהגות','Teshuvot Ve Hanhagot':'תשובות והנהגות','Sun Transit':'חצות שמש','Solar Midnight':'חצות שמש בלילה'}
 result=label.split(' · Day:')[0].replace(' (profile elevation policy)','')
 for en,he in sorted(terms.items(),key=lambda x:-len(x[0])):result=re.sub(re.escape(en),he,result,flags=re.I)
 return result.replace('below horizon','below the horizon')

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

def explicit_elevation(name, visited=None):
 if name in ('getSunriseBasedOnElevationSetting','getSunsetBasedOnElevationSetting','getSeaLevelSunrise','getSeaLevelSunset'):return False
 if name in ('getSunrise','getSunset'):return True
 visited=set() if visited is None else visited
 if name in visited:return False
 visited.add(name)
 return any(explicit_elevation(call,visited.copy()) for body in bodies.get(name,[]) for call in re.findall(r'\b(get\w+)\(',body))

def half_days(name, visited=None):
 visited=set() if visited is None else visited
 if name in visited:return False
 visited.add(name)
 for body in bodies.get(name,[]):
  if re.search(r'get(?:SofZmanShma|SofZmanTfila|MinchaGedola|MinchaKetana|SamuchLeMinchaKetana|PlagHamincha)\([^;]+,\s*true\s*\)',body):return True
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
 if name == 'getMinchaGedola72Minutes':return 'Day: Dawn 72 minutes → Nightfall 72 minutes'
 if name == 'getMinchaGedola16Point1Degrees':return 'Day: Dawn 16.1° below horizon → Nightfall 16.1° below horizon'
 if name == 'getPolarPlagHaminchaBenIshChai':return 'Day: Polar sunrise at azimuth 90° → Polar sunset at azimuth 270°'
 if name == 'getPolarPlagHaminchaTeshuvosVehanhagos':return 'Day: Previous polar day boundary → Current polar day boundary'
 if len(found)>=2:return 'Day: ' + ' → '.join(describe(x) for x in found[:2])
 for body in bodies.get(name,[]):
  for call in re.findall(r'\b(get\w+)\(\s*\)',body):
   inherited=boundaries(call,visited.copy())
   if inherited:return inherited
 return ''

def explanation(name, label):
 body=' '.join(bodies.get(name,[]))
 special={
 'getSunrise':'The upper edge of the sun rises above the ideal horizon, adjusted for the saved location height. Requires a known, non-negative height, even when the profile elevation switch is off.',
 'getSunset':'The upper edge of the sun sets below the ideal horizon, adjusted for the saved location height. Requires a known, non-negative height, even when the profile elevation switch is off.',
 'getSeaLevelSunrise':'Sunrise calculated at sea level. Ignores the saved location height and the profile elevation switch.',
 'getSeaLevelSunset':'Sunset calculated at sea level. Ignores the saved location height and the profile elevation switch. Also supplies the app’s sunset date boundary.',
 'getSunTransit':'Astronomical solar noon: the sun crosses the local meridian. Independent of the profile’s midpoint-versus-solar-noon choice.',
 'getSolarMidnight':'Astronomical lower-meridian transit at the end of the selected day. It can fall after civil midnight on the following date.',
 'getChatzosHayom':'Uses astronomical solar noon when the profile enables it; otherwise uses the midpoint of sea-level sunrise and sunset, with the pinned library’s astronomical fallback when needed.',
 'getChatzosHalayla':'Midnight at the end of the selected day. Uses astronomical midnight when enabled; otherwise the midpoint of sea-level sunset and the following sunrise, with astronomical fallback if needed.',
 'getChatzosHayomAsHalfDay':'The midpoint of sea-level sunrise and sunset, independently of the profile’s astronomical-noon switch.',
 'getFixedLocalChatzosHayom':'Local mean noon from longitude: four clock minutes per degree, expressed in the actual civil timezone including daylight saving. Does not depend on sunrise or sunset.',
 'getSofZmanShma3HoursBeforeChatzos':'Three fixed clock hours before the profile’s chatzos. These are regular 60-minute hours, not temporal hours.',
 'getSofZmanTfila2HoursBeforeChatzos':'Two fixed clock hours before the profile’s chatzos. These are regular 60-minute hours, not temporal hours.',
 'getMinchaGedola30Minutes':'Thirty fixed clock minutes after the profile’s chatzos; not half a temporal hour.',
 'getMinchaGedolaGRAFixedLocalChatzos30Minutes':'Thirty fixed clock minutes after longitude-based fixed local noon, independent of sunrise and sunset.',
 'getMinchaGedolaGRAGreaterThan30':'The later of the GRA mincha gedolah result and thirty fixed clock minutes after the profile’s chatzos.',
 'getMinchaGedolaAhavatShalom':'The later of thirty fixed minutes after chatzos and half a temporal hour after chatzos. This temporal hour is one twelfth of dawn at 16.1° below the horizon to nightfall at 3.7°. The profile half-day switch does not change this formula.',
 'getMinchaKetanaAhavatShalom':'Two and a half temporal hours before nightfall at 3.8° below the horizon; the temporal hour is one twelfth of dawn at 16.1° to that nightfall.',
 'getPlagAhavatShalom':'One and a quarter temporal hours before nightfall at 3.8° below the horizon; the temporal hour is one twelfth of dawn at 16.1° to that nightfall.',
 'getBainHashmashosRT2Stars':'Adds five eighteenths of the interval from dawn at 19.8° below the horizon to sunrise to the evening sunset. Sunrise and sunset follow the profile elevation policy.',
 'getBainHashmashosRT13Point5MinutesBefore7Point083Degrees':'Thirteen and a half fixed clock minutes before evening solar depression of 7.083°.',
 'getCandleLighting':'Before-sunset candle lighting subtracts your explicit fixed-minute offset from sea-level sunset. Applicable holiday transitions instead use your explicitly chosen nightfall calculation. Friday evenings retain the before-sunset rule.',
 'getTzaisAteretTorah':'Adds the explicitly configured Ateret Torah fixed-minute offset to sunset under the profile elevation policy. The offset also defines the end boundary of the Ateret Torah temporal-day family.',
 'getAlosBaalHatanya':'Morning solar depression of 16.9° below the geometric horizon. This is a solar angle, not a fixed 72-minute offset.',
 'getTzaisBaalHatanya':'Evening solar depression of 6° below the geometric horizon. This is a solar angle, not a fixed-minute offset.',
 'getPolarSunriseBenIshChai':'When ordinary sunrise is absent, uses the sun’s due-east azimuth of 90°. Does not apply on dates with ordinary sunrise.',
 'getPolarSunsetBenIshChai':'When ordinary sunset is absent, uses the sun’s due-west azimuth of 270°. Does not apply on dates with ordinary sunset.',
 'getPolarStartOfDayTeshuvosVehanhagos':'A polar-only day boundary. Uses solar midnight during continuous daylight, or solar noon during continuous darkness with no 16.1° dawn. Other dates have no result for this method.',
 'getPolarPlagHaminchaTeshuvosVehanhagos':'Uses 10.75 temporal hours of the 24-hour interval ending at the polar day boundary. The library’s supported half-day policy can affect the calculation.',
 'getSofZmanKidushLevanaBetweenMoldos':'Half a mean lunar month after the molad: 14 days, 18 hours, 22 minutes and 1⅔ seconds. Shown only on the local date of that instant.',
 'getSofZmanKidushLevana15Days':'Fifteen elapsed 24-hour days after the molad. Shown only on the local date of that instant.',
 'getTchilasZmanKidushLevana3Days':'Three elapsed 24-hour days after the molad. Shown only on the local date of that instant.',
 'getTchilasZmanKidushLevana7Days':'Seven elapsed 24-hour days after the molad. Shown only on the local date of that instant.',
 'getZmanMolad':'The traditional calculated mean lunar conjunction, expressed as an exact instant in this location’s timezone, on its applicable local date.',
 }
 if name in special:return special[name]
 half=re.search(r'getHalfDayBasedZman\([^;]+,\s*([0-9.]+)\s*\)',body)
 if half and 'FixedLocalChatzos' in name:
  return f'{half.group(1)} sixths of the specified half-day after its start. This method explicitly uses fixed local noon and does not follow the profile’s optional half-day switch. '+boundaries(name)
 g=group(name)
 if g in ('shema','prayer','mincha-gedolah','mincha-ketanah','plag','duration','chametz'):
  amount={'shema':'3','prayer':'4','mincha-gedolah':'6.5','mincha-ketanah':'9.5','plag':'10.75','chametz':'4' if 'Achilas' in name else '5'}
  definition='One twelfth of the specified day interval; a duration rather than a clock time.' if g=='duration' else f'{"9" if "Samuch" in name else amount[g]} temporal hours after the specified day begins. A full-day temporal hour is one twelfth of that interval.'
  if g=='chametz':definition+=' Applies only on 14 Nisan (Erev Pesach).'
  if 'BaalHatanya' in name:definition+=' Baal HaTanya boundaries use the sun at 1.583° below the sea-level horizon, not the location height.'
  if half_days(name):definition+=' When enabled, the library’s supported half-day policy uses six morning/afternoon hours around the profile chatzos.'
  return definition+' '+boundaries(name)
 if 'Yereim' in name:
  angle=re.search(r'([\d.]+)°',label)
  if angle:return f'Evening sun {angle.group(1)}° above the geometric horizon, before sunset. This Yereim angle is above, not below, the horizon.'
  minutes=re.search(r'([\d.]+) minutes',label)
  return f'{minutes.group(1)} fixed clock minutes before sunset under the profile elevation policy.'
 angle=re.search(r'([\d.]+)°',label)
 if angle:return ('Morning' if g in ('dawn','misheyakir') else 'Evening')+f' sun {angle.group(1)}° below the geometric horizon. A solar angle, not a fixed-minute interval; the time may be unavailable at high latitudes.'
 minutes=re.search(r'([\d.]+) (temporal )?minutes',label)
 if minutes:
  side='before sunrise' if g=='dawn' else 'after sunset'
  return f'{minutes.group(1)} '+('temporal minutes (one sixtieth of the sunrise-to-sunset temporal hour)' if minutes.group(2) else 'fixed clock minutes')+f' {side}. Sunrise/sunset follow the profile elevation policy.'
 if 'Twilight' in name:
  depression=6 if 'Civil' in name else 12 if 'Nautical' in name else 18
  return ('Morning' if 'Begin' in name else 'Evening')+f' solar depression of {depression}° below the geometric horizon.'
 raise ValueError('Every exposed method requires an explicit explanation: '+name)

lines=['// Generated by tools/generate_catalog.py from KosherJava 3.0.0. Labels require owner review.','@file:Suppress("DEPRECATION") // The complete pinned catalog intentionally exposes these explicit choices.','package com.example.zmanim.domain','','import com.kosherjava.zmanim.ComprehensiveZmanimCalendar','','object MethodCatalog {','    val methods: List<ZmanMethod> = listOf(']
review=[]
for name,typ in methods.items():
 label=describe(name)
 if 'Yereim' in name and 'Degrees' in name:label=label.replace('below horizon','above horizon')
 if name in ('getSunrise','getSunset'):label += ' · elevation adjusted'
 boundary=boundaries(name)
 if boundary and group(name) in ('shema','prayer','mincha-gedolah','mincha-ketanah','plag','duration'):label+=' · '+boundary
 if name=='getSeaLevelSunrise':label='Sunrise · sea level'
 if name=='getSeaLevelSunset':label='Sunset · sea level'
 review.append([ident(name),group(name),label,typ,'Ateret Torah sunset offset' if 'AteretTorah' in name else 'Candle-lighting offset' if name=='getCandleLighting' else '',sensitive(name),half_days(name),'Pending owner review'])
 lines.append(f'        ZmanMethod({json.dumps(ident(name))}, {json.dumps(group(name))}, {json.dumps(label)}, {str(typ=="Duration").lower()}, '+('true' if 'AteretTorah' in name else 'false')+', '+('true' if name=='getCandleLighting' else 'false')+', '+str(sensitive(name)).lower()+', '+str(half_days(name)).lower()+f', explicitElevation = {str(explicit_elevation(name)).lower()}, sourceMethod = {json.dumps(name)}, hebrewLabel = {json.dumps(hebrew_label(name, label), ensure_ascii=False)}, definition = {json.dumps(explanation(name,label),ensure_ascii=False)}, sourceDefinition = {json.dumps(docs.get(name, describe(name)), ensure_ascii=False)}) {{ c -> c.{name}() }},')
lines+=['    )','    val byId = methods.associateBy { it.id }','}']
Path('app/src/main/java/com/example/zmanim/domain/MethodCatalog.kt').write_text('\n'.join(lines)+'\n')
with Path('docs/method-catalog.csv').open('w',newline='') as f:
 writer=csv.writer(f);writer.writerow(['id','group','label','result','required_parameter','uses_elevation','supports_profile_half_days','review']);writer.writerows(review)
print(f'Generated {len(methods)} typed calculation adapters and owner-review catalog.')
