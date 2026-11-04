/** npm ci --ignore-scripts --prefix /tmp/zmanim-learning (using the pinned lockfile), then:
 * node tools/prepare_learning.mjs /tmp/zmanim-learning/node_modules
 * Build-time only; Android reads versioned gzip tables offline.
 */
import {pathToFileURL} from 'node:url';
import {readFileSync, writeFileSync} from 'node:fs';
import {gzipSync} from 'node:zlib';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const modules = resolve(process.argv[2] || '/tmp/zmanim-learning/node_modules');
const lib = '@hebcal/learning';
const pkg = JSON.parse(readFileSync(`${modules}/${lib}/package.json`));
assert.equal(pkg.version, '6.13.1');
await import(pathToFileURL(`${modules}/${lib}/dist/esm/index.js`));
const {DailyLearning} = await import(pathToFileURL(`${modules}/@hebcal/core/dist/esm/index.js`));
const {HDate} = await import(pathToFileURL(`${modules}/@hebcal/hdate/dist/esm/index.js`));
const specs = [
 ['bavli','dafYomi','Daf Yomi · Bavli','דף יומי · בבלי','Standard Bavli cycle; 2711 days in modern cycles'],
 ['yerushalmi-vilna','yerushalmi-vilna','Yerushalmi · Vilna','ירושלמי · וילנא','Vilna edition; skips Yom Kippur and observed Tisha B’Av; not Oz V’hadar or Meshulav'],
 ['mishna','mishnaYomi','Mishna Yomi · two mishnayot','משנה יומית · שתי משניות','Two mishnayot per day; cycle anchor 1947-05-20'],
 ['perek','perekYomi','Mishna · Perek Yomi','משנה · פרק יומי','One Mishna chapter per day'],
 ['nach','nachYomi','Nach Yomi','נ״ך יומי','One chapter per day; standard Nach Yomi cycle'],
 ['rambam-one','rambam1','Rambam · one chapter','רמב״ם · פרק אחד','Chabad one-chapter cycle, 1017 days; begins with introductory readings'],
 ['rambam-three','rambam3','Rambam · three chapters','רמב״ם · שלושה פרקים','Chabad three-chapter cycle; introductory readings and special combined chapters'],
 ['dirshu-amud','dirshuAmudYomi','Amud Yomi · Dirshu','עמוד יומי · דרשו','Dirshu Bavli amud cycle beginning 2023-10-16; distinct from Oraysa'],
 ['chafetz-chaim','chofetzChaim','Chafetz Chaim','חפץ חיים','Three cycles per Hebrew year beginning Tishrei, Shevat and Sivan, with ordinary/leap-year divisions from Hebcal'],
 ['tanya','tanyaYomi','Tanya · Chitas','תניא · חת״ת','Hayom Yom/Chitas; begins 19 Kislev; Sefaria paragraph-start references; ordinary/leap-year tables'],
 ['dirshu-halacha','dirshuDafHalacha','Daf HaYomi B’Halacha · Dirshu','דף היומי בהלכה · דרשו','Third cycle transcribed from Dirshu schedules; Sunday–Thursday study, Friday/Shabbat review; ends 2027-08-31'],
];
function reading(schedule, date, lang = 'en') {
 const event = DailyLearning.lookup(schedule, new HDate(date));
 if (!event) return '';
 // Store references only, never extracts of book text.
 return ((event.renderBrief ? event.renderBrief(lang) : event.render(lang)) + (schedule === 'tanyaYomi' && event.reading.verse !== undefined ? ` · ${lang === 'he' ? 'מתחיל בפסקה' : 'starts at paragraph'} ${event.reading.verse}` : '')).replaceAll('\t',' ').replaceAll('\n',' ');
}
assert.match(reading('dafYomi', new Date(2020,0,5)), /Bera[ck]hot 2/);
assert.match(reading('dirshuAmudYomi', new Date(2023,9,16)), /Bera[ck]hot 2/);
assert.equal(reading('yerushalmi-vilna', new HDate(10,7,5787)), ''); // Yom Kippur
assert.equal(reading('dirshuDafHalacha', new Date(2027,8,1)), '');
const start = new Date(2020,0,1,12), end = new Date(2041,0,1,12);
const rows = [];
const ranges = new Map();
for(let date = new Date(start); date < end; date.setDate(date.getDate()+1)) {
 const iso = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
 for(const [id,schedule] of specs) {
  let en = reading(schedule,date), he = reading(schedule,date,'he');
  if (!en) continue;
  ranges.set(id, [ranges.get(id)?.[0] ?? iso,iso]);
  rows.push([iso,id,en,he].join('\t'));
 }
}
const supplement = JSON.parse(readFileSync('tools/learning-supplement-metadata.json'));
const supplementRows = readFileSync('tools/learning-supplements.tsv','utf8').trim().split('\n');
assert.equal(supplementRows.length, 4538);
for (const row of supplementRows) {
 const fields = row.split('\t');
 assert.equal(fields.length, 4);
 assert.ok(supplement.providers.some(provider => provider.id === fields[1]));
 rows.push(row);
}
rows.sort(); // The Android reader scans by civil date.
const metadata = {version:pkg.version,source:'https://github.com/hebcal/hebcal-learning',license:pkg.license,
 civilRollover:true,start:'2020-01-01',endExclusive:'2041-01-01',
 providers:[...specs.map(([id,schedule,en,he,cycle])=>({id,schedule,en,he,cycle,validFrom:ranges.get(id)?.[0],validThrough:ranges.get(id)?.[1]})), ...supplement.providers]};
writeFileSync('app/src/main/assets/learning.tsv.gzip',gzipSync(rows.join('\n'),{mtime:0}));
writeFileSync('app/src/main/assets/learning-metadata.json',JSON.stringify(metadata,null,2)+'\n');
const sampleDates = ['2020-01-05','2023-10-16','2024-04-08','2026-11-04','2027-08-31','2027-09-01'];
writeFileSync('docs/learning-fixtures.tsv',rows.filter(row=>sampleDates.some(date=>row.startsWith(date+'\t'))).join('\n')+'\n');
console.log(`Generated ${rows.length} references from ${pkg.name} ${pkg.version}, Oz V’hadar cycles 11–12 and the official Oraysa Yevamot calendar`);
