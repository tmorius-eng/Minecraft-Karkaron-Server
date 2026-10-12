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
const name = 'S' + Math.floor(Math.random() * 900 + 100);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
// only the window's own text (the stage is far above the world; city holograms say /shop too)
const texts = () => Object.values(b.entities).filter(e => e.name === 'text_display' && e.position.y > 300).map(e => ({ e, t: JSON.stringify(e.metadata) }));
const find = (s) => texts().find(x => x.t.includes(s));
async function clickOn(label) {
  const x = find(label);
  if (!x) return false;
  // the text's origin is a little under its middle line: aim at the card centre
  // mineflayer sends no rotation while riding: write the look packet like a real client does
  // the eye from the window itself: the title sits 2.78 above the eye, 5.8 blocks ahead (MenuSky.DIST)
  const title = find('SÜLD');
  const eye = title.e.position.offset(0, -2.78, -5.8), t = x.e.position;
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
  await clickOn('/shop');
  check(!!find('ДЭЛГҮҮР · 1/2') && !!find('Дараах'), 'looking at «/shop» and clicking opens the shop page with a «Дараах ›» button', find('ДЭЛГҮҮР') ? 'shop' : 'no shop');
  await clickOn('Дараах');
  check(!!find('ДЭЛГҮҮР · 2/2') && !!find('Өмнөх'), '«Дараах ›» turns to page 2/2', find('2/2') ? '2/2' : 'still ' + (find('1/2') ? '1/2' : '?'));
  b.setQuickBarSlot((b.quickBarSlot + 8) % 9); await sleep(1200);
  check(!!find('ДЭЛГҮҮР · 1/2'), 'the hotbar wheel turns back to page 1/2', find('1/2') ? '1/2' : 'no');
  await clickOn('Тоглогч');
  check(!!find('ТОГЛОГЧИЙН ПРОФАЙЛ'), 'the Тоглогч tab opens the profile page in place', find('ПРОФАЙЛ') ? 'profile' : 'no');
  b._client.write('player_input', { inputs: { shift: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(2000);
  const back = b.entity.position;
  check(back.y < 300 && back.distanceTo(start) < 2 && !find('ПРОФАЙЛ'), 'Shift closes the window and the player stands where they were', 'dist ' + back.distanceTo(start).toFixed(1) + ' vehicle ' + !!b.vehicle);
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
