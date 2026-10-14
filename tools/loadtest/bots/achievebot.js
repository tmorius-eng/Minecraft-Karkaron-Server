// Achievements and the season's pass «Хуралдайн Зам» (docs/ACHIEVEMENTS_AND_PASS.md), one offline bot A:
//   node achievebot.js <srvctl.sh> [port]
// A fresh player has nothing; a first dungeon clear unlocks «Агуйн анхны ялалт» (120 + 150 points); thirteen clears
// unlock «Арван гурван хүрээний тулаан» with its title; a region found in the wild gives 100; deeds stop giving points
// at the daily cap (2 500); tier 5 makes the free track's first reward claimable and /pass claim takes it; the heroic
// track's rewards wait until staff open it; /achievements and /pass open their windows; after a relog everything stands
// and the backfill gives no points again. Deeds are recorded with the staff /suld achievement (the same path the game
// takes); the state is read from its line in the server log.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const nameA = 'Ac' + Math.floor(Math.random() * 90000 + 10000);
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const LOG = (() => { const t = fs.readFileSync(ctl, 'utf8').match(/SRV=(\S+)/); return t ? path.join(t[1], 'logs', 'latest.log') : null; })();
const logLines = () => { const a = fs.readFileSync(LOG, 'utf8').split('\n'); a.pop(); return a; };
const ask = async (command, re, wait = 1500) => { const n = logLines().length; cmd(command); await sleep(wait); return logLines().slice(n).filter(l => re.test(l)); };
const state = async () => {
  const l = (await ask(`suld achievement ${nameA}`, /deeds \S+/))[0] || '';
  const m = l.match(/unlocked=(\d+) \[([^\]]*)\] season=(\S+) points=(\d+) tier=(\d+) heroic=(\w+) claimable=(\d+)/);
  return m ? { line: l, unlocked: +m[1], ids: m[2] ? m[2].split(',') : [], season: m[3], points: +m[4], tier: +m[5], heroic: m[6] === 'true', claimable: +m[7], loading: /\(loading\)/.test(l) }
    : { line: l, unlocked: -1, ids: [], points: -1, tier: -1, heroic: false, claimable: -1 };
};
const deed = async (d, subject, n) => { cmd(`suld achievement ${nameA} deed ${d} ${subject} ${n}`); await sleep(400); };

function join(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  b.chatLog = [];
  b.on('messagestr', (m) => b.chatLog.push(m));
  b.windows = [];
  b.on('windowOpen', (w) => b.windows.push(typeof w.title === 'string' ? w.title : JSON.stringify(w.title)));
  return new Promise((r, j) => { b.once('spawn', () => r(b)); setTimeout(() => j(new Error('no spawn ' + name)), 60000); });
}
async function pickClass(b) {
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
}
const said = (b, from, re) => b.chatLog.slice(from).some(l => re.test(l));

(async () => {
  let a = await join(nameA);
  await pickClass(a);
  await sleep(3000);
  let s = await state();
  check(s.unlocked === 0 && s.points === 0 && !s.loading, 'a fresh player has no achievement and no points', s.line.slice(s.line.indexOf('deeds')));

  // --- a first clear: the achievement, its points and the clear's
  let k = a.chatLog.length;
  await deed('DUNGEON_CLEAR', 'dungeon.khasar_den', 1); await sleep(800);
  s = await state();
  check(s.ids.includes('ach.battle.first_clear') && s.points === 270 && said(a, k, /Амжилт: Агуйн анхны ялалт/),
    'a first clear unlocks «Агуйн анхны ялалт» (120 for the clear + 150)', 'points ' + s.points + ' · ' + s.ids.join(','));

  // --- thirteen clears: the title it gives
  k = a.chatLog.length;
  for (let i = 0; i < 12; i++) await deed('DUNGEON_CLEAR', 'dungeon.khasar_den', 1);
  await sleep(1000);
  s = await state();
  check(s.ids.includes('ach.battle.thirteen_kurens') && said(a, k, /Хүрээний Цэрэг/) && s.points === 270 + 12 * 120 + 400,
    'thirteen clears unlock «Арван гурван хүрээний тулаан» and grant its title', 'points ' + s.points + ' · title ' + said(a, k, /Хүрээний Цэрэг/));
  check(said(a, k, /Хуралдайн Зам: 1-р шат/) && said(a, k, /Хуралдайн Зам: 2-р шат/), 'each tier reached is announced', a.chatLog.slice(k).filter(l => /шат/.test(l)).join(' | ').slice(0, 160));

  // --- a real deed: a region found in the wild (100 points); the backfill of a fresh player gave none
  const p0 = s.points;
  cmd(`gamemode creative ${nameA}`); await sleep(300); // no fall where the teleport lands
  cmd(`tp ${nameA} 700 140 -46`); await sleep(5000);
  cmd(`gamemode survival ${nameA}`); await sleep(500);
  s = await state();
  check(s.points === p0 + 100, 'a region found in the wild gives 100 points', p0 + ' -> ' + s.points);

  // --- the daily cap: deeds stop giving points at 2 500 a day (achievements' own points are not capped)
  for (let i = 0; i < 20; i++) await deed('MOB_KILL', 'mob.deeremchin', 50); // 100 a record before the cap
  await sleep(1000);
  s = await state();
  const fromDeeds = 120 + 12 * 120 + 100;
  check(s.points === p0 + 100 + (2500 - fromDeeds), 'deeds give at most 2 500 points a day', 'points ' + s.points + ' (expected ' + (p0 + 100 + 2500 - fromDeeds) + ')');

  // --- tier 5: the free track's first reward; the heroic one waits
  const need = 5000 - s.points;
  cmd(`suld achievement ${nameA} points ${need}`); await sleep(800);
  s = await state();
  check(s.tier === 5 && s.claimable === 1 && !s.heroic, 'at tier 5 one free reward is claimable, the heroic ones are closed', 'tier ' + s.tier + ', claimable ' + s.claimable);
  k = a.chatLog.length;
  a.chat('/pass claim'); await sleep(1500);
  s = await state();
  check(s.claimable === 0 && said(a, k, /Хуралдайн Зам 5-р шат: Аянчин/), '/pass claim takes the free reward (a title)', a.chatLog.slice(k).join(' | ').slice(0, 160));
  a.chat('/pass claim'); await sleep(1000);
  check(said(a, k, /Авах шагнал алга/), 'a reward is claimed once', 'second claim: ' + a.chatLog.slice(-1)[0]);

  // --- staff open the heroic track: its tier 1 and 5 rewards
  k = a.chatLog.length;
  cmd(`suld achievement ${nameA} heroic`); await sleep(1200);
  s = await state();
  check(s.heroic && s.claimable === 2 && said(a, k, /Баатарлаг зам нээгдлээ/), 'the heroic track opens: two rewards wait', 'heroic ' + s.heroic + ', claimable ' + s.claimable);
  a.chat('/pass claim'); await sleep(1500);
  s = await state();
  check(s.claimable === 0 && said(a, k, /1-р шат: Баатарлаг Аянчин/) && said(a, k, /5-р шат: Алтан Үг/), 'both heroic rewards are claimed', a.chatLog.slice(k).filter(l => /шат:/.test(l)).join(' | ').slice(0, 160));

  // --- the windows
  a.windows = [];
  a.chat('/achievements'); await sleep(1500);
  const achWin = a.windows.find(t => /Амжилт/.test(t));
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  await sleep(500);
  a.chat('/pass'); await sleep(1500);
  const passWin = a.windows.find(t => /Хуралдайн Зам/.test(t));
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  check(!!achWin && !!passWin, '/achievements and /pass open their windows', (achWin || 'none').slice(0, 60) + ' · ' + (passWin || 'none').slice(0, 60));

  // --- a relog: everything stands, the backfill gives nothing again
  const before = await state();
  a.quit(); await sleep(3000);
  a = await join(nameA); await sleep(5000);
  const after = await state();
  check(after.unlocked === before.unlocked && after.points === before.points && after.heroic && after.claimable === 0,
    'after a relog the unlocks, points, heroic track and claims stand', JSON.stringify({ u: before.unlocked, p: before.points }) + ' -> ' + JSON.stringify({ u: after.unlocked, p: after.points, h: after.heroic, c: after.claimable }));

  cmd(`suld achievement ${nameA} reset`); await sleep(1000);
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  a.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
