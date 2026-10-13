// «Дайчдын Дасгалын Талбай» (docs/TRAINING_GROUNDS.md), one offline bot:  node trainingbot.js <srvctl.sh> [port]
// A brand-new player opens the training grounds from the class window («⚔ Туршиж үзэх») before choosing a class: in the
// yard as Баатар 60 with the T6 kit; hits on the Warden show numbers and the Warden stays; a spell is explained in
// chat; a passive is explained when it fires; other commands and dropping are refused; switching to Мэргэн B gives the
// bow; leaving gives the bag and the spot back and the class window again; the database never saw level 60. Then the
// player chooses the class tried last (Мэргэн) for real: hitting a Warden without training is refused with a hint; training as Бөө from
// /dasgal and leaving gives the Мэргэн things back.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'D' + Math.floor(Math.random() * 90000 + 10000);
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
// the big Warden (config training.x/z + 0.5; the shipped centre is the owner's spot of 2026-10-13)
const CENTRE = { x: Number(process.env.TRAIN_X || -29.5), z: Number(process.env.TRAIN_Z || -78.5) };
const dist = (e) => Math.hypot(e.position.x - CENTRE.x, e.position.z - CENTRE.z);

const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = [];
b.on('messagestr', m => chat.push(m));
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
let dialog = null;
b._client.on('show_dialog', (pk) => { dialog = pk; });
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
const lastWith = (k, s) => chat.slice(k).filter(l => l.includes(s)).pop() || '';
const items = () => b.inventory.slots.map((s, i) => s ? i + ':' + s.name : null).filter(Boolean);
const bag = () => b.inventory.items().map(i => i.name).sort().join(',');
const flatSlotsInv = () => b.inventory.slots.filter(Boolean).map(s => s.name).sort().join(',');
const wardens = () => Object.values(b.entities).filter(e => e.name === 'warden');
const centreWarden = () => wardens().sort((a, c) => Math.hypot(a.position.x - CENTRE.x, a.position.z - CENTRE.z) - Math.hypot(c.position.x - CENTRE.x, c.position.z - CENTRE.z))[0];
// a damage number: a text display the server shows this player only, spawned right at the centre Warden; its text ends
// in the number (the yard's own labels near it, the five class names, end in a letter)
// a component's plain text: its own text first, then its children (NBT keeps the keys in any order)
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; if (Array.isArray(c)) return c.map(flat).join('');
  if (typeof c !== 'object') return '';
  if (c.type && 'value' in c && !('text' in c)) return flat(c.value);
  let s = '';
  for (const k of ['text', '']) if (k in c) s += typeof c[k] === 'string' ? c[k] : flat(c[k]);
  if ('extra' in c) s += flat(c.extra);
  return s; };
const numbers = [];
b.on('entitySpawn', (e) => {
  if (e.name !== 'text_display' || Math.hypot(e.position.x - CENTRE.x, e.position.z - CENTRE.z) > 3) return;
  setTimeout(() => { const t = flat(e.metadata && e.metadata[23]); if (/\d$/.test(t.trim())) numbers.push(t.trim()); }, 200);
});
const R = () => { b.activateItem(); setTimeout(() => b.deactivateItem(), 60); }, L = () => b.swingArm('right');
async function combo(keys) { for (const c of keys) { (c === 'R' ? R : L)(); await sleep(260); } }
async function hitCentre(times, gap = 650) {
  const w = centreWarden();
  if (!w) return false;
  cmd(`tp ${name} ${CENTRE.x} ${Math.round(w.position.y)} ${CENTRE.z + 2.6} 180 0`); await sleep(1200);
  for (let i = 0; i < times; i++) {
    const t = centreWarden();
    if (!t) break;
    await b.lookAt(t.position.offset(0, 2.2, 0), true);
    b.attack(t); await sleep(gap);
  }
  return true;
}

b.once('spawn', async () => {
  try {
    await sleep(3500);
    for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
    check(!!b.currentWindow, 'a new player gets the class window', b.currentWindow ? 'open' : 'none');
    const origin = b.entity.position.clone();
    const before = flatSlotsInv();
    // Баатар card → the confirm page → «⚔ Туршиж үзэх»
    await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(900);
    const tryBtn = b.currentWindow && b.currentWindow.slots[22];
    check(!!tryBtn && tryBtn.name === 'netherite_sword', 'the confirm page offers «⚔ Туршиж үзэх»', tryBtn ? tryBtn.name : 'none');
    let k = chat.length;
    await b.clickWindow(22, 0, 0).catch(() => {}); await sleep(4500);
    const inYard = Math.hypot(b.entity.position.x - CENTRE.x, b.entity.position.z - CENTRE.z) < 22;
    check(said(k, '— Дасгал: Баатар · 60-р түвшин · Build A —') && inYard, 'the try button puts the player in the yard as Баатар 60, build A',
      b.entity.position.toString() + ' :: ' + lastWith(k, 'Дасгал'));
    const slots = b.inventory.slots;
    const armour = [5, 6, 7, 8].filter(i => slots[i]).length;
    check(!!slots[36] && armour === 4 && slots[44] && slots[44].name === 'barrier' && slots[43] && slots[43].name === 'writable_book',
      'the T6 kit: the class weapon, four armour pieces, the menu book and the leave barrier', items().join(' '));
    const entered = k;
    check(said(k, 'Keystone:') && said(k, 'Идэвхгүй:') && said(k, 'Ид шид:'), 'the build is explained on entry (keystone, spells, passives)',
      chat.slice(k).filter(l => l.length > 4).slice(1, 4).join(' | ').slice(0, 220));
    await sleep(2500);
    const tab = b.players[name] && b.players[name].displayName ? JSON.stringify(b.players[name].displayName) : '';
    check(tab.includes('дасгал'), 'TAB marks the trainee «⚔ дасгал» (no mastery mark)', tab.slice(0, 160));
    // the yard's shape (owner, 2026-10-13): the big Warden on the spot, the five class weapons floating still in an arc
    // 5 blocks north of it, five small Wardens close together on the owner's second spot (+10.93, -5.85), one walking
    // an 8-block circle round the big one
    const big = centreWarden();
    const G = { x: CENTRE.x + 10.93, z: CENTRE.z - 5.85 };
    const group = wardens().filter(e => Math.hypot(e.position.x - G.x, e.position.z - G.z) < 2.1);
    const walker = wardens().find(e => Math.abs(dist(e) - 8.0) < 0.5);
    const weapons = Object.values(b.entities).filter(e => e.name === 'item_display' && Math.abs(dist(e) - 5.0) < 0.3 && e.position.z < CENTRE.z + 0.3);
    const lift = big ? weapons.map(e => +(e.position.y - big.position.y).toFixed(2)) : [];
    const was = weapons.map(e => e.position.clone()), walkerWas = walker ? walker.position.clone() : null;
    await sleep(2000);
    const still = weapons.filter((e, i) => e.position.distanceTo(was[i]) < 0.05).length;
    check(!!big && dist(big) < 0.3 && group.length === 5 && weapons.length === 5 && lift.every(h => Math.abs(h - 1.5) < 0.6) && still === 5
      && !!walker && walker.position.distanceTo(walkerWas) > 1.5,
      'the yard: the big Warden on its spot, five weapons floating still in an arc north of it, the group of five on its spot, one walking round',
      `big ${big ? dist(big).toFixed(2) : '-'} · group ${group.length} · weapons ${weapons.length} at +${lift.join('/')} still ${still} · walker ${walker ? dist(walker).toFixed(1) + ' moved ' + walker.position.distanceTo(walkerWas).toFixed(1) : '-'}`);
    // a floating weapon is its class's station: a click opens the class page
    cmd(`tp ${name} ${CENTRE.x} ${big ? Math.round(big.position.y) : 70} ${CENTRE.z - 2.6} 0 0`); await sleep(1200);
    dialog = null;
    const boxes = Object.values(b.entities).filter(e => e.name === 'interaction' && Math.abs(dist(e) - 5.0) < 0.3);
    const box = boxes.sort((a, c) => a.position.distanceTo(b.entity.position) - c.position.distanceTo(b.entity.position))[0];
    if (box) { await b.lookAt(box.position.offset(0, 1.3, 0), true); b.activateEntity(box); }
    await sleep(1500);
    const page = JSON.stringify((dialog && dialog.dialog) || '');
    check(boxes.length === 5 && !!dialog && page.includes('Build A'), 'a click on a floating weapon opens its class page', `boxes ${boxes.length}, dialog ${dialog ? page.length + ' chars' : 'none'}`);
    // the Warden: hits show numbers, it never dies, it never moves
    const w0 = centreWarden();
    const at0 = w0 ? w0.position.clone() : null;
    check(!!w0 && wardens().length >= 7, 'the training Wardens stand in the yard (centre, group of five, walking one)', 'wardens ' + wardens().length);
    numbers.length = 0;
    k = chat.length;
    await hitCentre(10);
    await sleep(1200);
    const w1 = centreWarden();
    check(numbers.length > 0, 'hits on the Warden show damage numbers', numbers.slice(0, 4).join(' | ').slice(0, 200));
    check(!!w1 && w1.id === (w0 && w0.id) && at0 && w1.position.distanceTo(at0) < 0.1, 'the Warden takes every hit and stays where it is (∞, no AI)',
      w1 ? w1.position.toString() : 'gone');
    // a spell: R-L-R Тэнгэрийн Цавчилт, explained after it lands
    k = chat.length; numbers.length = 0;
    const t = centreWarden(); if (t) await b.lookAt(t.position.offset(0, 2.2, 0), true);
    await combo('RLR'); await sleep(3500);
    check(said(k, 'Тэнгэрийн Цавчилт III (R-L-R): Урд талын бүх дайснаа цавчина.') && said(k, 'хохирол'), 'a spell is explained in chat after it lands',
      lastWith(k, 'Тэнгэрийн Цавчилт').slice(0, 220));
    check(numbers.some(n => /^Тэнгэрийн Цавчилт [\d ]+$/.test(n)), "the spell's number over the Warden carries its name", numbers.filter(n => /[А-я]/.test(n)).slice(0, 2).join(' | '));
    // passives: explained in chat the first time each fires (it may have been during the hits above)
    for (let i = 0; i < 4 && !said(entered, '⚡'); i++) await hitCentre(8, 600);
    check(said(entered, '⚡') && said(entered, 'Энд '), 'a passive is explained the first time it fires, with how often it fired here',
      lastWith(entered, '⚡').slice(0, 220));
    // guards
    k = chat.length; b.chat('/spawn'); await sleep(1000);
    check(said(k, 'Дасгалын үеэр зөвхөн /dasgal'), 'other commands are refused while training', chat.slice(k).join(' | ').slice(0, 120));
    const weapon = b.inventory.slots[36];
    if (weapon) { b.setQuickBarSlot(0); await sleep(300); await b.tossStack(weapon).catch(() => {}); await sleep(1200); }
    check(!!b.inventory.slots[36], 'the kit cannot be dropped', items().join(' '));
    // switch class and build
    k = chat.length; b.chat('/dasgal mergen b'); await sleep(3500);
    check(said(k, '— Дасгал: Мэргэн · 60-р түвшин · Build B —') && b.inventory.items().some(i => i.name === 'bow'), 'switching gives Мэргэн B and the bow',
      lastWith(k, 'Дасгал') + ' :: ' + bag());
    // the bow shoots at the Warden (right click, no arrows)
    numbers.length = 0;
    const bow = b.inventory.items().find(i => i.name === 'bow');
    if (bow) { await b.equip(bow, 'hand').catch(() => {}); await sleep(500); }
    { const cw = centreWarden(); cmd(`tp ${name} ${CENTRE.x} ${cw ? Math.round(cw.position.y) : 70} ${CENTRE.z + 9} 180 0`); } await sleep(1500);
    for (let i = 0; i < 4; i++) { const tw = centreWarden(); if (tw) await b.lookAt(tw.position.offset(0, 2.5, 0), true); R(); await sleep(1200); }
    await sleep(1000);
    check(numbers.length > 0, 'Мэргэн arrows hit the Warden with numbers', numbers.slice(0, 3).join(' | '));
    // leaving: the bag, the spot, the class window
    k = chat.length; b.chat('/dasgal leave'); await sleep(3000);
    check(said(k, 'Дасгал дууслаа.') && flatSlotsInv() === before, 'leaving takes the kit and gives the bag back as it was', '[' + flatSlotsInv() + '] vs [' + before + ']');
    check(b.entity.position.distanceTo(origin) < 3, 'leaving puts the player back where they stood', b.entity.position.distanceTo(origin).toFixed(1));
    await sleep(1500);
    const win = b.currentWindow;
    check(!!win && win.slots[13] && win.slots[22], 'a player without a class gets the class window again, on the class just tried', win ? (win.slots[13] || {}).name : 'none');
    const row = psql(`select coalesce(class_id,'-'), level, currency from suld_profiles where name='${name}'`);
    check(/^-\|1\|/.test(row), 'the database never saw the training character (no class, level 1)', row);
    // choose for real the class the window reopened on (Мэргэн, tried last), then: a Warden without training refuses the hit
    if (b.currentWindow) { await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(2500); }
    if (b.currentWindow) b.closeWindow(b.currentWindow);
    await sleep(1500);
    const realBag = bag();
    k = chat.length; numbers.length = 0;
    await hitCentre(2);
    await sleep(800);
    check(said(k, 'Дасгал эхлүүлэх') && numbers.length === 0, 'a player not training cannot hit a Warden and is told how to start',
      chat.slice(k).join(' | ').slice(0, 120));
    // a classed player trains as Бөө and comes back to Мэргэн
    k = chat.length; b.chat('/dasgal boo'); await sleep(4000);
    check(said(k, '— Дасгал: Бөө · 60-р түвшин · Build A —'), '/dasgal boo trains as Бөө', lastWith(k, 'Дасгал'));
    k = chat.length; b.chat('/dasgal leave'); await sleep(3000);
    check(said(k, 'Мэргэн, 1-р түвшин') && bag() === realBag, 'leaving gives Мэргэн and its things back', lastWith(k, 'буцлаа') + ' :: ' + bag());
    const row2 = psql(`select coalesce(class_id,'-'), level from suld_profiles where name='${name}'`);
    check(row2 === 'mergen|1', 'the database has the real Мэргэн at level 1', row2);
  } catch (e) {
    check(false, 'no exception', String(e && e.stack || e).slice(0, 300));
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
