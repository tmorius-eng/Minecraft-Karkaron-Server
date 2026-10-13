// Мэргэжил in the wild (docs/MASTERY_AND_PROFESSIONS.md), one offline bot A in creative (no mob hunts it, so no hit
// breaks a gather by chance):
//   node professionbot.js <srvctl.sh> [port]
// A tier-I ore vein stands at its place once A is near; a right click gathers one iron ore at level 1 and earns the
// Дархан 10 points; the vein is then spent; walking away breaks a gather; a tier-II vein refuses a level-1 Дархан and
// gives at 13; a herb patch gives ганга; in a herder's pen a horse gives horsehair and a sheep wool (the sheep is then
// shorn); /mergejil refine turns ore into the smith's iron; a SÜLD wool block is never placed; the points stand after a
// relog; the pen stands where it was built after a server restart (the bot restarts it). The node lines come from the
// staff /suld gather list (read in the server log).
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const nameA = 'Pf' + Math.floor(Math.random() * 90000 + 10000);
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const LOG = (() => { const t = fs.readFileSync(ctl, 'utf8').match(/SRV=(\S+)/); return t ? path.join(t[1], 'logs', 'latest.log') : null; })();
const logLines = () => { const a = fs.readFileSync(LOG, 'utf8').split('\n'); a.pop(); return a; };
const ask = async (command, re, wait = 1500) => { const n = logLines().length; cmd(command); await sleep(wait); return logLines().slice(n).filter(l => re.test(l)); };
const parse = (l) => {
  const m = l.match(/node (\S+) (vein|herb|pen) T(\d) (-?\d+) (\S+) (-?\d+) (\S+) (\d+)m/);
  return m && { id: m[1], kind: m[2], tier: +m[3], x: +m[4], y: m[5] === '?' ? null : +m[5], z: +m[6], state: m[7], d: +m[8] };
};
const list = async (n) => (await ask(`suld gather list ${nameA} ${n}`, /\bnode \S+ (vein|herb|pen) /)).map(parse).filter(Boolean);
const crafts = async () => (await ask(`suld profession ${nameA}`, /crafts /))[0] || '';

function join(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  b.chatLog = [];
  b.on('messagestr', (m) => b.chatLog.push(m));
  return new Promise((r, j) => { b.once('spawn', () => r(b)); setTimeout(() => j(new Error('no spawn ' + name)), 60000); });
}
async function pickClass(b) {
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
}
const count = (b, item) => b.inventory.items().filter(i => i.name === item).reduce((s, i) => s + i.count, 0);

// go to a node and wait until the keeper has placed it there (its live spot is listed)
async function visit(a, node, want = 'ready') {
  cmd(`suld gather tp ${nameA} ${node.id}`);
  let seen = null;
  for (let i = 0; i < 10; i++) {
    await sleep(2000);
    seen = (await list(400)).find(n => n.id === node.id);
    if (seen && seen.state === 'nowhere') return seen; // no ground for it this run
    if (seen && seen.y !== null && (want === null || seen.state === want || seen.state === 'built')) break;
  }
  if (seen && seen.y !== null) { cmd(`suld gather tp ${nameA} ${node.id}`); await sleep(2500); } // beside its live spot
  return seen;
}
async function gatherAt(a, entity) {
  if (!entity) return;
  await a.lookAt(entity.position.offset(0, 0.5, 0), true);
  a.activateEntity(entity);
}
const boxNear = (a, n) => a.nearestEntity(e => e.name === 'interaction' && n && n.y !== null && e.position.distanceTo(new Vec3(n.x + 0.5, n.y, n.z + 0.5)) < 1.5);

(async () => {
  let a = await join(nameA);
  await sleep(2500);
  await pickClass(a);
  await sleep(1500);
  cmd(`gamemode creative ${nameA}`); await sleep(500);
  const plan = await list(400);
  const veins1 = plan.filter(n => n.kind === 'vein' && n.tier === 1), veins2 = plan.filter(n => n.kind === 'vein' && n.tier === 2);
  const herbs1 = plan.filter(n => n.kind === 'herb' && n.tier === 1), pens = plan.filter(n => n.kind === 'pen' && n.tier === 1);
  check(veins1.length > 0 && veins2.length > 0 && herbs1.length > 0 && pens.length > 0, 'the wild holds veins, herb patches and pens',
    plan.length + ' places listed: ' + veins1.length + ' tier-I veins, ' + veins2.length + ' tier-II, ' + herbs1.length + ' tier-I herbs, ' + pens.length + ' tier-I pens');

  // --- a tier-I vein
  let v = await visit(a, veins1[0]);
  check(!!v && v.state === 'ready' && !!boxNear(a, v), 'a vein stands at its place once a player is near', JSON.stringify(v));
  const ore0 = count(a, 'raw_iron');
  await gatherAt(a, boxNear(a, v)); await sleep(2800);
  const ore1 = count(a, 'raw_iron');
  let c = await crafts();
  check(ore1 === ore0 + 1 && /TOMRIIN_DARKHAN=1\/10\b/.test(c), 'a right click gathers one iron ore at level 1 and 10 points', 'raw iron ' + ore0 + ' -> ' + ore1 + ' · ' + c);
  await gatherAt(a, boxNear(a, v)); await sleep(2800);
  v = (await list(400)).find(n => n.id === veins1[0].id);
  check(count(a, 'raw_iron') === ore1 && v && v.state === 'spent', 'a gathered vein is spent (for everyone, 5 minutes)', 'raw iron ' + count(a, 'raw_iron') + ', state ' + (v && v.state));
  cmd('suld gather respawn'); await sleep(2500);
  await gatherAt(a, boxNear(a, v)); await sleep(500);
  a.setControlState('forward', true); await sleep(900); a.setControlState('forward', false); await sleep(2000);
  check(count(a, 'raw_iron') === ore1, 'walking away breaks a gather', 'raw iron ' + count(a, 'raw_iron'));

  // --- the tier gate: a tier-II vein at Дархан 1, then at 13
  let v2 = await visit(a, veins2[0]);
  let k = a.chatLog.length;
  const ore2 = count(a, 'raw_iron');
  await gatherAt(a, boxNear(a, v2)); await sleep(2800);
  const refused = a.chatLog.slice(k).some(l => l.includes('13-р түвшин хэрэгтэй'));
  check(refused && count(a, 'raw_iron') === ore2, 'a tier-II vein refuses a level-1 Дархан, naming level 13',
    (a.chatLog.slice(k).find(l => l.includes('түвшин')) || 'no message') + ' · raw iron ' + count(a, 'raw_iron'));
  cmd(`suld profession ${nameA} TOMRIIN_DARKHAN 13`); await sleep(1000);
  await gatherAt(a, boxNear(a, v2)); await sleep(2800);
  c = await crafts();
  check(count(a, 'raw_iron') === ore2 + 1 && /TOMRIIN_DARKHAN=13\/20\b/.test(c), 'at 13 it gives (20 points for tier II)', 'raw iron ' + count(a, 'raw_iron') + ' · ' + c);

  // --- a herb patch
  const h = await visit(a, herbs1[0]);
  const g0 = count(a, 'allium');
  await gatherAt(a, boxNear(a, h)); await sleep(2800);
  c = await crafts();
  check(count(a, 'allium') === g0 + 1 && /EMIIN_BARIACH=1\/10\b/.test(c), 'a herb patch gives ганга to the Бариач', 'allium ' + g0 + ' -> ' + count(a, 'allium') + ' · ' + c);

  // --- a herder's pen: a horse, a sheep (the nearest pen whose ground holds a yard: one with none is skipped)
  let pen = null;
  for (const p of pens.slice(0, 4)) {
    pen = await visit(a, p, null);
    if (pen && pen.state === 'built') break;
  }
  let horse = null, sheep = null;
  for (let i = 0; i < 8 && !(horse && sheep); i++) {
    await sleep(1500);
    horse = a.nearestEntity(e => e.name === 'horse' && e.position.distanceTo(a.entity.position) < 6);
    sheep = a.nearestEntity(e => e.name === 'sheep' && e.position.distanceTo(a.entity.position) < 6);
  }
  check(!!pen && pen.state === 'built' && !!horse && !!sheep, 'a pen is built with its horses and sheep', JSON.stringify(pen) + ' · horse ' + !!horse + ', sheep ' + !!sheep);
  const s0 = count(a, 'string'), w0 = count(a, 'white_wool');
  await gatherAt(a, horse); await sleep(2800);
  await gatherAt(a, sheep); await sleep(2800);
  c = await crafts();
  const shorn = sheep && sheep.metadata ? (Number(sheep.metadata[17]) & 0x10) !== 0 : false;
  check(count(a, 'string') === s0 + 1 && count(a, 'white_wool') === w0 + 1 && /ADUUCHIN=1\/10\b/.test(c) && /ESGIICH=1\/10\b/.test(c),
    'a horse gives horsehair to the Адуучин and a sheep wool to the Эсгийч', 'string ' + count(a, 'string') + ', wool ' + count(a, 'white_wool') + ' · ' + c);
  check(shorn, 'the sheep stands shorn until it is back', 'metadata[17] ' + (sheep && sheep.metadata ? sheep.metadata[17] : 'none'));

  // --- refining: ore into the smith's iron
  cmd(`itemsadmin give ${nameA} item.tumriin_khudar`); await sleep(1500);
  const p0 = count(a, 'raw_iron'), q0 = count(a, 'iron_nugget');
  a.chat('/mergejil refine tumur 1'); await sleep(2000); // once: a stack is used in part
  check(p0 >= 4 && count(a, 'raw_iron') === p0 - 3 && count(a, 'iron_nugget') === q0 + 2, '/mergejil refine once takes three ores from a stack and gives two shards',
    'raw iron ' + p0 + ' -> ' + count(a, 'raw_iron') + ', shards ' + q0 + ' -> ' + count(a, 'iron_nugget'));
  const r0 = count(a, 'raw_iron'), n0 = count(a, 'iron_nugget');
  a.chat('/mergejil refine tumur all'); await sleep(2000);
  const times = Math.floor(r0 / 3);
  check(count(a, 'raw_iron') === r0 - 3 * times && count(a, 'iron_nugget') === n0 + 2 * times, '/mergejil refine: three ores make two iron shards',
    'raw iron ' + r0 + ' -> ' + count(a, 'raw_iron') + ', shards ' + n0 + ' -> ' + count(a, 'iron_nugget'));

  // --- a SÜLD wool block is never placed
  const wool = a.inventory.items().find(i => i.name === 'white_wool');
  let placed = false;
  if (wool) {
    await a.equip(wool, 'hand').catch(() => {});
    const below = a.blockAt(a.entity.position.offset(2, -1, 0));
    if (below) await a.placeBlock(below, new Vec3(0, 1, 0)).then(() => { placed = true; }).catch(() => {});
    await sleep(800);
    const above = a.blockAt(a.entity.position.offset(2, 0, 0));
    placed = placed || (above && above.name === 'white_wool');
  }
  check(!!wool && !placed && count(a, 'white_wool') === w0 + 1, 'a SÜLD wool block is never placed', 'placed ' + placed + ', wool ' + count(a, 'white_wool'));

  // --- the points stand after a relog
  const beforeQuit = await crafts();
  a.quit(); await sleep(3000);
  a = await join(nameA); await sleep(4000);
  const after = await crafts();
  const strip = (s) => (s.match(/(TOMRIIN_DARKHAN|EMIIN_BARIACH|ADUUCHIN|ESGIICH)=\d+\/\d+/g) || []).join(' ');
  check(strip(after) !== '' && strip(after) === strip(beforeQuit) && !/loading/.test(after), 'the crafts stand after a relog', strip(beforeQuit) + ' -> ' + strip(after));

  // --- a built pen stands where it was after a restart (professions.yml): never forgotten, never built again
  a.quit(); await sleep(2000);
  execFileSync(ctl, ['stop']); execFileSync(ctl, ['start']);
  a = await join(nameA); await sleep(4000);
  const again = pen && (await list(40)).find(n => n.id === pen.id);
  const rebuilt = pen ? logLines().filter(l => l.includes('[crafts] built ' + pen.id + ' ')).length : -1;
  check(!!again && again.state === 'built' && again.x === pen.x && again.y === pen.y && again.z === pen.z && rebuilt === 0,
    'a built pen stands where it was after a restart', JSON.stringify(pen && { x: pen.x, y: pen.y, z: pen.z }) + ' -> ' + JSON.stringify(again) + ' · built again ' + rebuilt + 'x');

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  a.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
