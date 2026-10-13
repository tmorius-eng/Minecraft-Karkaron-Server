// Class change through «Ангийн Академи» (docs/CLASS_CHANGE.md), one offline bot on the dev server:
//   node classchangebot.js <srvctl.sh> [port]
// Pick Баатар; /class opens the academy; while new the change to Мэргэн is free and swaps the soulbound gear (bow in,
// sword out); back to Баатар the same sword comes back; at level 20 a change costs 5000 coins, then waits 6 h; a
// «Хувь Төөргийн Тамга» skips the wait.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'C' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const chat = [];
b.on('messagestr', m => chat.push(m));
let seatId = null;
b._client.on('set_passengers', (pk) => { if (b.entity && pk.passengers.includes(b.entity.id)) seatId = pk.entityId; });
const seat = () => b.entities[seatId];
const texts = () => Object.values(b.entities).filter(e => e.name === 'text_display' && e.position.y > 300).map(e => ({ e, t: JSON.stringify(e.metadata) }));
// the hover tooltip repeats a card's name with its whole lore: take the text with the fewest lines (the card)
const lines = (x) => (x.t.match(/\\n/g) || []).length;
const find = (...all) => texts().filter(x => all.every(s => x.t.includes(s))).sort((a, c) => lines(a) - lines(c))[0];
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
const items = () => b.inventory.items().map(i => i.name);
async function clickOn(...label) {
  const x = find(...label), title = find('SÜLD');
  if (!x || !title) return false;
  // the eye from the window itself (mineflayer mis-tracks the moving seat): MenuSky.DIST0 = 3.2 from the panel, the
  // title 2.78 above the panel centre, text 0.08 in front of the panel
  const eye = title.e.position.offset(0, -2.78, 0.08 - 3.2);
  const t = x.e.position, dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
  b._client.write('look', { yaw: -Math.atan2(dx, dz) * 180 / Math.PI, pitch: -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI, flags: { onGround: false, hasHorizontalCollision: false } });
  await sleep(500); b.swingArm('right'); await sleep(1800);
  return true;
}
async function changeTo(cls) {
  b.chat('/class'); await sleep(3500);
  await clickOn(cls, 'Үүрэг'); await sleep(500);
  return find('Батлах');
}
async function close() { b._client.write('player_input', { inputs: { shift: true } }); await sleep(600); b._client.write('player_input', { inputs: {} }); await sleep(1500); }

b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2000);
  check(items().some(n => n.includes('sword')), 'Баатар starts with the class sword', items().join(','));

  b.chat('/class'); await sleep(3500);
  check(!!find('АНГИЙН АКАДЕМИ') && !!find('Мэргэн', 'Үүрэг') && !!find('Бөө', 'Үүрэг'), '/class opens «Ангийн Академи» with every class', texts().length + ' texts');
  await clickOn('Мэргэн', 'Үүрэг');
  check(!!find('АНГИ СОЛИХ') && !!find('Батлах'), 'a class card opens the confirm page (free while new)', find('Үнэгүй') ? 'free' : 'no free line');
  let k = chat.length;
  await clickOn('Батлах'); await sleep(1500);
  check(said(k, 'Мэргэн анги боллоо'), 'confirming changes the class', chat.slice(k).join(' | ').slice(0, 120));
  await close();
  check(items().includes('bow') && !items().some(n => n.includes('sword')), 'the soulbound gear swapped: the bow in, the Баатар sword out', items().join(','));

  if (await changeTo('Баатар')) { k = chat.length; await clickOn('Батлах'); await sleep(1500); }
  await close();
  check(said(k, 'Баатар анги боллоо') && items().some(n => n.includes('sword')) && !items().includes('bow'), 'back to Баатар: the sword comes back, the bow leaves', items().join(','));

  cmd(`suld exp ${name} 6000000`); await sleep(2000);
  cmd(`suld coins ${name} -100000000`); await sleep(800); // level rewards paid coins: start from none
  b.chat('/class'); await sleep(3500);
  await clickOn('Мэргэн', 'Үүрэг');
  check(!!find('Зоос хүрэлцэхгүй'), 'after the grace a change needs coins (level x 250)', find('хүрэлцэхгүй') ? 'no coins' : 'no refusal shown');
  await close();
  cmd(`suld coins ${name} 100000`); await sleep(1000);
  if (await changeTo('Мэргэн')) { k = chat.length; await clickOn('Батлах'); await sleep(1500); }
  await close();
  check(said(k, 'зоос төллөө'), 'with coins it changes and charges', chat.slice(k).join(' | ').slice(0, 140));
  b.chat('/class'); await sleep(3500);
  await clickOn('Бөө', 'Үүрэг');
  check(!!find('хүлээлт дуусаагүй'), 'a second change within 6 hours is refused', find('хүлээлт') ? 'cooldown' : 'not refused');
  await close();
  cmd(`suld classseal ${name} 1`); await sleep(1000);
  if (await changeTo('Бөө')) { k = chat.length; await clickOn('Батлах'); await sleep(1500); }
  await close();
  check(said(k, 'Тамга зарцууллаа'), 'a «Хувь Төөргийн Тамга» skips the wait', chat.slice(k).join(' | ').slice(0, 140));

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
