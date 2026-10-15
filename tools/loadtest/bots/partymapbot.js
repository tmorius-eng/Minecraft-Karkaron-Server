// A party on the map (docs/WORLD_MAP.md): two offline bots, A and B, in one party:
//   node partymapbot.js <srvctl.sh> [port]
// A's corner minimap shows B as a light blue arrow (map colour 17, high shade = byte 70) where B stands, and an arrow on
// the rim when B is far; the atlas shows B's name at B's place and moves it when B moves; nothing of B shows once B has
// left the party. The bots float in the air with slow falling (the terrain under them does not matter: the bot counts
// the light blue pixels against the picture before the party).
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const tag = Math.floor(Math.random() * 9000 + 1000);
const nameA = 'Pa' + tag, nameB = 'Pb' + tag;
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const MARK = [16 * 4 + 2, 31 * 4 + 3, 30 * 4 + 0, 28 * 4 + 3];
const MATE = 17 * 4 + 2;
// Minimap: the view is 120 x 98 pixels at (4, 4), one block a pixel, the player in the middle
const VX = 4, VY = 4, VW = 120, VH = 98, CX = VX + VW / 2, CY = VY + VH / 2;

function join(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  b.minimap = null; // the latest picture of this bot's own minimap (the map whose first row carries the mark)
  b._client.on('map', (pk) => {
    if (!pk.data || pk.columns !== 128 || pk.x !== 0 || pk.y !== 0) return;
    if (MARK.every((v, i) => pk.data[i] === v)) b.minimap = Array.from(pk.data);
  });
  return new Promise((r, j) => { b.once('spawn', () => r(b)); setTimeout(() => j(new Error('no spawn ' + name)), 60000); });
}
async function pickClass(b) {
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
}
const count = (img, x0, y0, x1, y1) => {
  let n = 0;
  if (!img) return -1;
  for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (img[y * 128 + x] === MATE) n++;
  return n;
};
const near = (img) => count(img, CX - 6, CY - 27, CX + 7, CY - 13); // around a mate 20 blocks north
const eastRim = (img) => count(img, VX + VW - 12, VY, VX + VW, VY + VH);
const labels = (b, name) => Object.values(b.entities).filter(e => e.name === 'text_display' && JSON.stringify(e.metadata).includes(name));
const texts = (b) => Object.values(b.entities).filter(e => e.name === 'text_display').map(e => (JSON.stringify(e.metadata).match(/"text":\{"type":"string","value":"([^"]*)"/) || [])[1]).filter(Boolean);

(async () => {
  const a = await join(nameA);
  const b = await join(nameB);
  await sleep(3000);
  await Promise.all([pickClass(a), pickClass(b)]);
  await sleep(2500);
  // both in the air over the same spot of steppe, B 20 blocks north of A, slow falling
  const x = Math.floor(a.entity.position.x) + 400, z = Math.floor(a.entity.position.z) + 400;
  for (const n of [nameA, nameB]) cmd(`effect give ${n} slow_falling 120 0 true`);
  cmd(`tp ${nameA} ${x} 220 ${z} 0 0`);
  cmd(`tp ${nameB} ${x} 220 ${z - 20} 0 0`);
  await sleep(12000); // the 10 s refresh draws A's minimap with B standing there (still no party)
  const before = near(a.minimap);
  check(before >= 0, 'A has a corner minimap', 'light blue pixels near B before the party: ' + before);
  // the party
  a.chat(`/party invite ${nameB}`); await sleep(1500);
  b.chat('/party accept'); await sleep(1500);
  cmd(`tp ${nameB} ${x} 220 ${z - 20} 90 0`); // B turns: the mates' key changes, A's minimap redraws
  await sleep(2500);
  const after = near(a.minimap);
  check(after >= before + 4, 'in a party, A\'s minimap shows B as a light blue arrow where B is', before + ' -> ' + after + ' light blue pixels');
  // B far east: an arrow on A's east rim
  cmd(`tp ${nameB} ${x + 2000} 220 ${z} 0 0`); await sleep(2500);
  check(eastRim(a.minimap) >= 3 && near(a.minimap) <= before, 'B 2 000 blocks east: an arrow on the east rim', 'rim ' + eastRim(a.minimap) + ', near ' + near(a.minimap));
  // the atlas: B's name where B stands, and it moves with B
  cmd(`tp ${nameB} ${x + 200} 220 ${z} 0 0`); await sleep(1500); // on the wall at the opening zoom (640 blocks across)
  a.chat('/map');
  // the first atlas after a start sets up its 25 map views: wait for the stage rather than a fixed time
  for (let i = 0; i < 24 && labels(a, nameB).length === 0; i++) await sleep(500);
  const l1 = labels(a, nameB);
  const p1 = l1.length ? l1[0].position.clone() : null;
  check(!!a.vehicle && l1.length === 1, 'the atlas shows B\'s name', 'seated ' + !!a.vehicle + ', labels ' + l1.length + (l1.length ? '' : ' of ' + JSON.stringify(texts(a)).slice(0, 300)));
  cmd(`tp ${nameB} ${x + 200} 220 ${z - 160} 0 0`);
  const movedBy = () => { const l = labels(a, nameB); return p1 && l.length === 1 ? l[0].position.distanceTo(p1) : 0; };
  for (let i = 0; i < 12 && movedBy() <= 0.02; i++) await sleep(500); // redrawn within a second of the move
  const l2 = labels(a, nameB);
  const moved = movedBy();
  check(l2.length === 1 && moved > 0.02, 'B\'s marker moves on the atlas when B moves', 'label moved ' + moved.toFixed(3) + ' blocks on the wall');
  // B leaves: gone from the atlas and the minimap
  b.chat('/party leave'); await sleep(2500);
  check(labels(a, nameB).length === 0, 'after B leaves the party the atlas shows no B', labels(a, nameB).length + ' label(s)');
  a._client.write('player_input', { inputs: { shift: true } }); await sleep(800); a._client.write('player_input', { inputs: {} }); await sleep(2500);
  cmd(`tp ${nameB} ${x} 220 ${z - 20} 0 0`); await sleep(1000);
  cmd(`tp ${nameA} ${x} 220 ${z} 90 0`); await sleep(2500); // A turns: its minimap redraws
  check(near(a.minimap) <= before, 'and A\'s minimap no longer shows B', 'near ' + near(a.minimap) + ' (before the party ' + before + ')');
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  a.quit(); b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
