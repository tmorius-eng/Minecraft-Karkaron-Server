// «Сүлдийн Атлас» (/map, docs/WORLD_MAP.md), one offline bot:
//   node atlasbot.js <srvctl.sh> [port]
// /map seats the player on the sky stage before a wall of 25 map frames (glow item frames holding the pooled maps);
// the server sends every map's pixels at once, drawn from the real world (spawn is generated: most frames have land);
// the title and the places' labels are text displays; D pans (new pixels arrive), the wheel zooms (new pixels), the
// «Агуй» tab leaves only the dungeons' labels (after Space puts the first-time guide away); Shift closes and puts the
// player back where they stood. The regions are named only zoomed out: none at 1 block a pixel (where it opens), all
// eight at the whole world.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'M' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });

// the layout of AtlasSky (blocks on the wall): the camera is DIST in front of the wall's middle (at the default 70° fit),
// EYE_UP above it
const DIST = 5.25, EYE_UP = -0.29, TITLE_V = 391 / 128, TAB_U = 406 / 128, TAB_STEP = 58 / 128, FILTERS = 7;
const maps = new Map(); // map id -> { packets, nonZero }
b._client.on('map', (pk) => {
  if (!pk.data || pk.columns !== 128) return;
  const m = maps.get(pk.itemDamage) || { packets: 0, nonZero: 0 };
  m.packets++;
  m.nonZero = pk.data.reduce((n, v) => n + (v !== 0 ? 1 : 0), 0);
  maps.set(pk.itemDamage, m);
});
const packetsNow = () => [...maps.values()].reduce((n, m) => n + m.packets, 0);
const high = (e) => e.position.y > 400;
const texts = () => Object.values(b.entities).filter(e => e.name === 'text_display' && high(e)).map(e => ({ e, t: JSON.stringify(e.metadata) }));
const find = (s) => texts().find(x => x.t.includes(s));
// a region's name may be written in two lines: its line break reads as a space here
const flat = (t) => t.replace(/\\+n/g, ' ');
const frames = () => Object.values(b.entities).filter(e => e.name === 'glow_item_frame' && high(e));

function lookAt(eye, t) {
  const dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
  const yaw = -Math.atan2(dx, dz) * 180 / Math.PI, pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI;
  b._client.write('look', { yaw, pitch, flags: { onGround: false, hasHorizontalCollision: false } });
}

b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(1500);
  const start = b.entity.position.clone();
  b.chat('/map'); await sleep(4000);
  // mineflayer's own position drifts while it rides: the stage's height is read from the frames around the seat
  const fr = frames();
  check(!!b.vehicle && fr.length > 0, '/map seats the player on the atlas stage, high over the world', 'vehicle ' + !!b.vehicle
    + ' frames at y ' + (fr.length ? fr[0].position.y.toFixed(0) : '-'));
  check(fr.length === 25, 'a wall of 25 map frames', fr.length + ' glow item frames');
  const ids = [...maps.keys()];
  const withLand = [...maps.values()].filter(m => m.nonZero > 0).length;
  check(ids.length >= 25, 'every frame\'s map is sent', ids.length + ' maps, ' + packetsNow() + ' packets');
  check(withLand >= 12, 'the maps show real land around the spawn', withLand + ' of ' + ids.length + ' maps have pixels');
  // a new player's first atlases open with the guide (AtlasSky HELP_AUTO); Space puts it away, and until then the
  // first click only closes it
  const guide = !!find('ЗААВАР');
  b._client.write('player_input', { inputs: { jump: true } }); await sleep(300); b._client.write('player_input', { inputs: {} }); await sleep(1200);
  check(guide && !find('ЗААВАР'), 'a new player\'s atlas opens with the guide and Space puts it away', 'guide ' + guide + ' -> ' + !!find('ЗААВАР'));
  const title = find('"СҮЛДИЙН АТЛАС"');
  check(!!title, 'the title is real text', texts().length + ' text displays');
  check(!!find('Хархорум'), 'the city is on the map with its label', find('Хархорум') ? 'label' : 'none');
  if (!title) { b.quit(); process.exit(1); }
  // the wall's middle and the eye, from the title's position (AtlasSky: text at plane + (-u, v, -0.06))
  const plane = title.e.position.offset(0, -TITLE_V, 0.06);
  const eye = plane.offset(0, EYE_UP, -DIST);
  // D pans east: new pixels arrive
  let p0 = packetsNow();
  b._client.write('player_input', { inputs: { right: true } }); await sleep(400); b._client.write('player_input', { inputs: {} }); await sleep(2500);
  check(packetsNow() > p0, 'D pans the map: new pixels arrive', (packetsNow() - p0) + ' map packets');
  const closeUp = texts().filter(x => /"(ХЭРЛЭНГИЙН ТАЛ|ГОВЬ|ХАНГАЙ|АЛТАЙ)"/.test(flat(x.t))).length;
  check(closeUp === 0, 'close up (the atlas opens at 1 block a pixel) no region is named', closeUp + ' region name(s)');
  // the wheel zooms: new pixels arrive
  p0 = packetsNow();
  lookAt(eye, plane); await sleep(300);
  b.setQuickBarSlot((b.quickBarSlot + 1) % 9); await sleep(2500);
  check(packetsNow() > p0, 'the wheel zooms: new pixels arrive', (packetsNow() - p0) + ' map packets');
  // the regions' names lie across their lands from 8 blocks a pixel out (none closer); at the whole world all eight
  // (at 8 blocks a pixel the panned view leaves some of them off the wall, so the whole world is the check)
  // the atlas opens at zoom 0 and the first notch went to 1: three more reach the whole world (zoom 4)
  for (let i = 0; i < 3; i++) { b.setQuickBarSlot((b.quickBarSlot + 1) % 9); await sleep(2500); }
  const all = ['ХЭРЛЭНГИЙН ТАЛ', 'ГОВЬ', 'ХАНГАЙ', 'АЛТАЙ', 'ХЭНТИЙН НУРУУ', 'ЗҮҮНГАРЫН ГОВЬ', 'ОТГОНТЭНГЭРИЙН ОРГИЛ', 'ХӨВСГӨЛИЙН ТАЙГА'];
  const named = () => all.filter(n => texts().some(x => flat(x.t).includes('"' + n + '"'))).length;
  check(named() === 8, 'at the whole world the eight regions are named', named() + ' of 8');
  // and wholly on the wall, none over another (AtlasSky.REGION_METRICS: 7 and 10 font pixels at 0.62, 1/40 block a
  // pixel; a name longer than 12 letters in two lines): boxes round each name's text display, in the wall's plane
  const boxes = [];
  for (const n of all) {
    const x = texts().find(q => flat(q.t).includes('"' + n + '"'));
    if (!x) continue;
    const lines = n.length > 12 && n.includes(' ') ? n.split(' ') : [n];
    const hw = Math.max(...lines.map(l => l.length)) * 7 * 0.025 * 0.62 / 2, hh = lines.length * 10 * 0.025 * 0.62 / 2;
    const u = -(x.e.position.x - plane.x), v = x.e.position.y - plane.y;
    boxes.push({ n, u0: u - hw, u1: u + hw, v0: v - hh, v1: v + hh });
  }
  const offWall = boxes.filter(q => q.u0 < -2.5 || q.u1 > 2.5 || q.v0 < -2.5 || q.v1 > 2.5).map(q => q.n);
  const meet = [];
  for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
    const a = boxes[i], c = boxes[j];
    if (a.u0 < c.u1 && c.u0 < a.u1 && a.v0 < c.v1 && c.v0 < a.v1) meet.push(a.n + ' / ' + c.n);
  }
  check(boxes.length === 8 && offWall.length === 0 && meet.length === 0, 'at the whole world each name is wholly on the wall and none covers another',
    'off the wall: ' + (offWall.join(', ') || 'none') + ' · meeting: ' + (meet.join(', ') || 'none'));
  // the «Агуй» tab (the third on the left) leaves only the dungeons
  const cityBefore = !!find('Хархорум');
  const v = (FILTERS - 1) * TAB_STEP / 2 - 2 * TAB_STEP;
  lookAt(eye, plane.offset(TAB_U, v, -0.02)); await sleep(500);
  b.swingArm('right'); await sleep(1500);
  const labels = texts().map(x => x.t);
  const dungeons = labels.filter(t => t.includes('Агуй') || t.includes('Булш') || t.includes('Үүр') || t.includes('Оргил') || t.includes('Гүн')).length;
  check(cityBefore && !find('Хархорум') && dungeons > 0, 'the «Агуй» tab shows only the dungeons', 'city label ' + !!find('Хархорум') + ', dungeon-like labels ' + dungeons);
  b._client.write('player_input', { inputs: { shift: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(2000);
  const back = b.entity.position;
  check(back.y < 400 && back.distanceTo(start) < 2 && frames().length === 0, 'Shift closes the atlas and the player stands where they were',
    'dist ' + back.distanceTo(start).toFixed(1) + ' frames left ' + frames().length);
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
