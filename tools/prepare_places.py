"""Build offline city and timezone assets with provenance. Run only when refreshing datasets."""
import urllib.request, zipfile, io, json, struct, gzip
from pathlib import Path
out=Path('app/src/main/assets')
out.mkdir(parents=True, exist_ok=True)
url='https://download.geonames.org/export/dump/cities5000.zip'
print('Downloading GeoNames cities5000', flush=True)
with zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(url, timeout=90).read())) as z:
 lines=z.read('cities5000.txt').decode().splitlines()
 cities=[]
 for line in lines:
  f=line.split('\t')
  aliases=','.join(dict.fromkeys([f[1],f[2]]+f[3].split(',')))
  cities.append('\t'.join([f[0],f[1],f[8],f[4],f[5],f[17],f[15],aliases]))
 with gzip.open(out/'cities.tsv.gzip','wt',encoding='utf8') as f:f.write('\n'.join(cities))
print('Cities:',len(cities),flush=True)
req=urllib.request.Request('https://api.github.com/repos/evansiroky/timezone-boundary-builder/releases/latest',headers={'User-Agent':'Zmanim-offline-data-builder'})
release=json.load(urllib.request.urlopen(req,timeout=60))
asset=next(x for x in release['assets'] if x['name']=='timezones-with-oceans.geojson.zip')
print('Downloading timezone boundaries',release['tag_name'],flush=True)
with zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(asset['browser_download_url'],timeout=180).read())) as z:
 data=json.loads(z.read(z.namelist()[0]))
polygons=[]
for feature in data['features']:
 zone=feature['properties']['tzid']
 g=feature['geometry']
 for rings in ([g['coordinates']] if g['type']=='Polygon' else g['coordinates']):
  polygons.append((zone,rings))
index=[]
offset=0
with gzip.open(out/'timezone-points.bin.gzip','wb') as f:
 for zone,rings in polygons:
  outer=rings[0]
  bounds=[min(p[0] for p in outer),min(p[1] for p in outer),max(p[0] for p in outer),max(p[1] for p in outer)]
  entries=[]
  for ring in rings:
   entries.append([offset,len(ring)])
   for x,y,*_ in ring:f.write(struct.pack('>ff',x,y))
   offset+=len(ring)*8
  index.append({'zone':zone,'bounds':bounds,'rings':entries})
(out/'timezone-index.json').write_text(json.dumps(index,separators=(',',':')))
(out/'place-data.json').write_text(json.dumps({'cities':{'source':url,'license':'CC BY 4.0','records':len(cities)},'timezones':{'source':asset['browser_download_url'],'version':release['tag_name'],'license':'ODbL 1.0','polygons':len(polygons)}},indent=2))
print('Timezone polygons:',len(polygons),flush=True)
