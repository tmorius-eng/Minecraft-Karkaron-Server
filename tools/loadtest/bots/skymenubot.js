// The /menu sky window (MenuSky, docs/UI_HUB.md), one offline bot:
//   node skymenubot.js <srvctl.sh> [port]
// /menu seats the player on the sky stage with the hub page as text displays; looking at the «/shop» card and
// clicking opens the shop page; «Дараах ›» turns to page 2/2; the wheel turns back; Shift closes and puts the
// player back where they stood; /menu in combat range falls back to nothing breaking.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'S' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
let seatId = null; // mineflayer loses track of the vehicle: follow the passengers packet
b._client.on('set_passengers', (pk) => { if (b.entity && pk.passengers.includes(b.entity.id)) seatId = pk.entityId; });
const seat = () => b.entities[seatId];
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
// only the window's own text (the stage is far above the world; city holograms say /shop too)
const texts = () => Object.values(b.entities).filter(e => e.name === 'text_display' && e.position.y > 300).map(e => ({ e, t: JSON.stringify(e.metadata) }));
// a card's tooltip repeats its text: the card itself is the match with the fewest lines
const nl = (x) => (x.t.match(/\\n/g) || []).length;
const find = (s) => texts().filter(x => x.t.includes(s)).sort((a, c) => nl(a) - nl(c))[0];
// mineflayer's seat position drifts: the eye is taken from the window's geometry when it opened (2.78 under the title,
// 3.2 in front of the panel), moved by however far the seat moved since (WASD pan, wheel zoom)
// The seat's real position, from the server's own packets (mineflayer's entity copy drifts while it carries a player)
let seatRaw = null;
const onSeat = (pk) => { if (pk.entityId === seatId) seatRaw = { x: pk.x, y: pk.y, z: pk.z }; };
b._client.on('sync_entity_position', onSeat);
b._client.on('entity_teleport', onSeat);
let eye0 = null, seat0 = null;
function eyeNow() {
  const title = find('SÜLD');
  if (!eye0 && title) { eye0 = title.e.position.offset(0, -2.78, 0.08 - 3.2); seat0 = seatRaw ? { ...seatRaw } : null; }
  const d = seatRaw && seat0 ? { x: seatRaw.x - seat0.x, y: seatRaw.y - seat0.y, z: seatRaw.z - seat0.z } : { x: 0, y: 0, z: 0 };
  return eye0.offset(d.x, d.y, d.z);
}
async function clickOn(label) {
  const x = find(label);
  if (!x) return false;
  // the text's origin is a little under its middle line: aim at the card centre
  // mineflayer sends no rotation while riding: write the look packet like a real client does
  // the eye: straight over the seat (it pans in X/Y and zooms in Z); its height from the title (2.78 above the panel centre) plus the pan
  const eye = eyeNow(); const t = x.e.position;
  const dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
  const yaw = -Math.atan2(dx, dz) * 180 / Math.PI, pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI;
  b._client.write('look', { yaw, pitch, flags: { onGround: false, hasHorizontalCollision: false } });
  await sleep(500);
  b.swingArm('right');
  await sleep(1500);
  return true;
}

b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(1500);
  const start = b.entity.position.clone();
  b.chat('/menu'); await sleep(3000);
  check(!!b.vehicle && b.entity.position.y > 300, '/menu seats the player on the sky stage', 'vehicle ' + !!b.vehicle + ' y ' + b.entity.position.y.toFixed(0));
  check(!!find('КОМАНДЫН ӨРГӨӨ') && !!find('/shop') && !!find('Тоглогч'), 'the hub page is real text: title, cards and tabs', texts().length + ' text displays');
  check(!b.currentWindow, 'no chest window opens', String(!!b.currentWindow));
  eyeNow(); // the window's geometry as it opened
  await clickOn('/shop');
  check(!!find('ДЭЛГҮҮР · 1/2') && !!find('Дараах'), 'looking at «/shop» and clicking opens the shop page with a «Дараах ›» button', find('ДЭЛГҮҮР') ? 'shop' : 'no shop');
  await clickOn('Дараах');
  check(!!find('ДЭЛГҮҮР · 2/2') && !!find('Өмнөх'), '«Дараах ›» turns to page 2/2', find('2/2') ? '2/2' : 'still ' + (find('1/2') ? '1/2' : '?'));
  await clickOn('Өмнөх');
  check(!!find('ДЭЛГҮҮР · 1/2'), '«‹ Өмнөх» turns back to page 1/2', find('1/2') ? '1/2' : 'no');
  const x0 = seat().position.x;
  b._client.write('player_input', { inputs: { right: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(600);
  check(seat().position.x < x0 - 0.5, 'D pans the camera to the right', 'seat x ' + x0.toFixed(2) + ' -> ' + seat().position.x.toFixed(2));
  const z0 = seat().position.z;
  b.setQuickBarSlot((b.quickBarSlot + 8) % 9); await sleep(400); b.setQuickBarSlot((b.quickBarSlot + 8) % 9); await sleep(1200);
  const z1 = seat().position.z;
  check(z1 > z0 + 0.5, 'the wheel zooms in: the camera moves toward the window', 'seat z ' + z0.toFixed(2) + ' -> ' + z1.toFixed(2));
  if (process.env.DEBUG) { const f = find('Тоглогч'); console.log('tab', f && f.t.slice(0, 200), 'eye', eyeNow().toString(), 'at', f && f.e.position.toString()); }
  await clickOn('Тоглогч');
  if (process.env.DEBUG) console.log('after', texts().map(x => (x.t.match(/"text":"([^"]{0,30})/) || [])[1]).filter(Boolean).slice(0, 12).join(' | '));
  check(!!find('ТОГЛОГЧИЙН ПРОФАЙЛ'), 'zoomed in, the Тоглогч tab still opens the profile page in place', find('ПРОФАЙЛ') ? 'profile' : 'no');
  b._client.write('player_input', { inputs: { shift: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(2000);
  const back = b.entity.position;
  check(back.y < 300 && back.distanceTo(start) < 2 && !find('ПРОФАЙЛ'), 'Shift closes the window and the player stands where they were', 'dist ' + back.distanceTo(start).toFixed(1) + ' vehicle ' + !!b.vehicle);
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
