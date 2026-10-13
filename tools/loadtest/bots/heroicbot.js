// «Тэнгэрийн Гэсгээлт Агуй», heroic keys on the dev server (docs/HEROIC_DUNGEONS.md):
//   node heroicbot.js <srvctl.sh> <logfile> [port]
// The gates (level 60, one normal clear, the open key), a timed +1 run (level-60 hall mobs, the clock on the HUD, the
// next keys open, «Эртний Хас Чулуу» paid, the keys window), a +3 run with «Цагаан Зуд» forced by the staff option (the
// blizzard freezes a player without a fire), a failed run (keys stay), and the jade spent at the smith on a level-60
// rare weapon's affixes. The waves and bosses are killed from the console; the bot stands in the hall, present and up.
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the last element after the final newline is '' (or a half-written line): never count it, or slice(n) skips a line
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.error((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' :: ' + detail : '')); };
const grab = (from, re) => lines().slice(from).filter(l => re.test(l)).join(' | ').slice(0, 400);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };

function join(name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.chatLog = [];
    bot.bars = [];
    bot.on('messagestr', (m) => bot.chatLog.push(m));
    for (const ev of ['action_bar', 'set_action_bar_text']) bot._client.on(ev, (pk) => bot.bars.push(flat(pk.text)));
    bot._client.on('system_chat', (pk) => { if (pk.isActionBar || pk.overlay) bot.bars.push(flat(pk.content)); });
    bot.on('error', () => {});
    // the pack prompt is left unanswered (a decline is kicked: the pack is required): after the onboarding gate's
    // 30 s the HUD falls back to plain text, which a bot can read (the glyph HUD draws its letters as pack glyphs)
    bot._client.on('show_dialog', (pk) => { bot.dialog = pk; });
    bot.once('spawn', async () => { await sleep(3500); resolve(bot); });
    setTimeout(() => resolve(null), 30000);
  });
}
async function pickClass(bot, slot) {
  for (let i = 0; i < 80 && !bot.currentWindow; i++) await sleep(500);
  if (bot.currentWindow) { await bot.clickWindow(slot, 0, 0).catch(() => {}); await sleep(600); }
  if (bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); } // «Батлах»
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}
const said = (bot, from) => bot.chatLog.slice(from).join(' | ');
// a dialog button press as the 1.21.11 client sends it: serverbound custom_click_action (0x41) = the action id, then a
// length-prefixed network NBT payload ({id: int[4]}, Paper's callback key). minecraft-data's schema lacks the length
// prefix, so the packet is written raw.
const varint = (n) => { const out = []; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; out.push(b); } while (n); return Buffer.from(out); };
function customClick(id, ints) {
  const key = Buffer.from('id', 'utf8');
  const parts = [Buffer.from([0x0a, 0x0b, 0, key.length]), key];
  const n = Buffer.alloc(4); n.writeInt32BE(ints.length); parts.push(n);
  for (const v of ints) { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); }
  parts.push(Buffer.from([0x00]));
  const tag = Buffer.concat(parts);
  const name = Buffer.from(id, 'utf8');
  return Buffer.concat([varint(0x41), varint(name.length), name, varint(tag.length), tag]);
}
const jade = (bot) => bot.inventory.items().filter(it => it.name === 'emerald' && JSON.stringify(it.customName || '').includes('Хас Чулуу'))
  .reduce((n, it) => n + it.count, 0);

async function clearHall() {
  // two waves of wolves, then Саарал (a ravager host); the kills are the console's, the bot stays present
  for (let w = 0; w < 8; w++) { cmd('minecraft:kill @e[type=wolf,tag=suld_dungeon]'); await sleep(3000); }
  for (let b = 0; b < 4; b++) { cmd('minecraft:kill @e[type=ravager,tag=suld_dungeon]'); await sleep(3000); }
  await sleep(3000);
}

(async () => {
  const name = 'Q' + Math.floor(Math.random() * 90000 + 10000);
  const A = await join(name);
  if (!A) { check('the bot joins', false, 'no spawn in 30 s'); process.exit(1); }
  await pickClass(A, 10);
  cmd(`effect give ${name} minecraft:resistance 900 4 true`);

  // ------------------------------------------------------------------ the gates
  let from = A.chatLog.length;
  A.chat('/dungeon heroic khasar_den 1'); await sleep(2000);
  check('below level 60 the key is refused', /60-р түвшнээс/.test(said(A, from)), said(A, from).slice(0, 160));

  cmd(`suld exp ${name} 90000000`); cmd(`suld quest ${name} 5`); await sleep(2500);
  from = A.chatLog.length;
  A.chat('/dungeon heroic khasar_den 1'); await sleep(2000);
  check('without a normal clear the key is refused', /энгийнээр нэг удаа/.test(said(A, from)), said(A, from).slice(0, 160));

  cmd(`dungeon grant ${name} khasar_den`); await sleep(1500);
  from = A.chatLog.length;
  A.chat('/dungeon heroic khasar_den 4'); await sleep(2000);
  check('a key above the open one is refused', /\+1 хүртэлх түлхүүр/.test(said(A, from)), said(A, from).slice(0, 160));

  // ------------------------------------------------------------------ a timed +1
  cmd(`spreadplayers 200 0 1 10 false ${name}`); await sleep(5000);
  from = A.chatLog.length;
  A.chat('/dungeon heroic khasar_den'); await sleep(2500);
  const xz = said(A, from).match(/хаалга дээр ирж орно: (-?\d+), (-?\d+)/);
  check('away from the gate the heroic key names it', xz, said(A, from).slice(0, 160));
  if (xz) { cmd(`spreadplayers ${xz[1]} ${xz[2]} 1 3 false ${name}`); await sleep(6000); }
  let n = lines().length;
  from = A.chatLog.length;
  const bars0 = A.bars.length;
  A.chat('/dungeon heroic khasar_den'); await sleep(8000);
  check('the heroic run starts at +1 (no affix below +2)', /Баатарлаг \+1 · 7:30 · нөхцөлгүй/.test(said(A, from)), said(A, from).slice(0, 220));
  check('the clock is on the HUD', A.bars.slice(bars0).some(b => /Баатарлаг \+1 · 0:\d\d \/ 7:30/.test(b)),
    A.bars.slice(bars0).filter(b => b.includes('Баатарлаг')).slice(-1)[0] || A.bars.slice(-1)[0] || 'no bar');
  const d0 = lines().length;
  cmd('minecraft:data get entity @e[type=wolf,tag=suld_dungeon,limit=1] CustomName'); await sleep(1500);
  const plate = lines().slice(d0).find(l => l.includes('CustomName') || l.includes('entity data')) || '';
  check('a hall mob stands at level 60', plate.includes('Lv 60'), plate.slice(0, 200));
  await clearHall();
  const done1 = said(A, from);
  check('a timed +1 opens the next keys and pays the jade', /хугацаандаа/.test(done1) && /одоо \+3 хүртэл/.test(done1) && /\+1 Эртний Хас Чулуу/.test(done1),
    done1.slice(-260));
  check('the run is logged', new RegExp(`\\[heroic\\] dungeon\\.khasar_den \\+1 TIMED .* jade 1 members ${name}`).test(lines().slice(n).join('\n')),
    grab(n, /\[heroic\]/));
  await sleep(1500);
  check('the jade is in the bag', jade(A) >= 1, 'jade ' + jade(A));
  A.chat('/dungeon heroic');
  const w = await new Promise((res) => { const t = setTimeout(() => res(null), 5000); A.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  await sleep(800);
  const plinth = w && w.slots[37] ? flat(w.slots[37].customName ? JSON.parse(JSON.stringify(w.slots[37].customName)) : '') + JSON.stringify(w.slots[37].customName || '') : '';
  check('the keys window shows +3 open', w && JSON.stringify(w.title).includes('Баатарлаг Агуй') && plinth.includes('+3'), (w ? JSON.stringify(w.title).slice(0, 80) : 'no window') + ' :: ' + plinth.slice(0, 120));
  if (A.currentWindow) A.closeWindow(A.currentWindow);
  await sleep(1500);

  // ------------------------------------------------------------------ +3 with «Цагаан Зуд» (staff option)
  cmd(`op ${name}`); await sleep(800);
  const toGate = async () => { if (xz) { cmd(`spreadplayers ${xz[1]} ${xz[2]} 1 3 false ${name}`); await sleep(5000); } };
  await toGate();
  n = lines().length;
  from = A.chatLog.length;
  A.chat('/dungeon heroic khasar_den 3 ZUD'); await sleep(7000);
  check('a +3 run starts with the forced affix', /Баатарлаг \+3 · 7:30 · Цагаан Зуд/.test(said(A, from)), said(A, from).slice(0, 220));
  // the first blizzard comes 30 s after the start; the bot holds no torch and stands by no fire
  const frozen = () => /Цагаан Зуд: гал, бамбар, дэнлүүгүй тул хөлдлөө/.test(said(A, from));
  for (let i = 0; i < 40 && !frozen(); i++) await sleep(1000);
  check('«Цагаан Зуд» freezes a player without a fire', frozen(), said(A, from).slice(-200));
  cmd(`minecraft:item replace entity ${name} weapon.offhand with minecraft:torch`); // Essentials owns /item
  const warmFrom = A.bars.length;
  const warm = () => A.bars.slice(warmFrom).some(b => b.includes('Цагаан Зуд: гал таныг дулаацууллаа'));
  for (let i = 0; i < 35 && !warm(); i++) await sleep(1000);
  check('a torch in hand keeps the next blizzard off', warm(), A.bars.slice(warmFrom).filter(b => b.includes('Зуд')).slice(-1)[0] || 'no blizzard line');
  cmd(`minecraft:item replace entity ${name} weapon.offhand with minecraft:air`);
  await clearHall();
  const done3 = said(A, from);
  check('a timed +3 opens +5', /Баатарлаг \+3 хугацаандаа/.test(done3) && /одоо \+5 хүртэл/.test(done3), done3.slice(-220));
  check('the +3 run is logged with its affix', /\[heroic\] dungeon\.khasar_den \+3 TIMED .* affixes ZUD/.test(lines().slice(n).join('\n')), grab(n, /\[heroic\]/));
  await sleep(1500);

  // ------------------------------------------------------------------ a failed run keeps the keys
  await toGate();
  n = lines().length;
  from = A.chatLog.length;
  A.chat('/dungeon heroic khasar_den 2'); await sleep(7000);
  A.chat('/dungeon abort'); await sleep(3000);
  check('a failed run keeps the keys', /Баатарлаг \+2 бүтсэнгүй · түлхүүр хэвээр \(\+5 хүртэл\)/.test(said(A, from)), said(A, from).slice(-220));
  check('the failed run is logged', /\[heroic\] dungeon\.khasar_den \+2 FAILED/.test(lines().slice(n).join('\n')), grab(n, /\[heroic\]/));
  await sleep(1500);

  // ------------------------------------------------------------------ the jade at the smith
  const before = jade(A);
  cmd(`itemsadmin give ${name} weapon.zevsegt_ild rare 60`); await sleep(1500);
  const blade = A.inventory.items().find(it => it.name !== 'emerald' && JSON.stringify(it.customName || '').includes('Зэвсэгт'));
  if (blade) { await A.equip(blade, 'hand').catch(() => {}); await sleep(600); }
  cmd(`suld coins ${name} 20000`);
  const names = {};
  A._client.on('entity_metadata', (pk) => { for (const m of pk.metadata) if (m.key === 2 && m.value) { const t = flat(m.value); if (t) names[pk.entityId] = t; } });
  cmd(`tp ${name} -15 74 -45`); await sleep(5000);
  const smith = Object.values(A.entities).find(e => (names[e.id] || '').includes('Дархан'));
  let rerolled = false;
  let detail = 'smith ' + (smith ? 'found' : 'not found') + ', blade ' + !!blade;
  if (smith && blade) {
    cmd(`tp ${name} ${smith.position.x + 1.5} ${smith.position.y} ${smith.position.z + 1.5}`); await sleep(2000);
    const s2 = A.entities[smith.id] || smith;
    await A.lookAt(s2.position.offset(0, 1.6, 0));
    A.dialog = null;
    A.activateEntity(s2); await sleep(2000);
    // the smith talks first (a Paper dialog): press its «Засвар · Хуяг» button the way a client does
    const acts = A.dialog?.dialog?.data?.value?.actions?.value?.value || [];
    const work = acts.find(a => flat(a.label).includes('Засвар'));
    detail += ', dialog ' + (A.dialog ? acts.length + ' buttons' : 'none');
    if (work) {
      A._client.writeRaw(customClick(work.action.value.id.value, work.action.value.additions.value.id.value));
      await sleep(2000);
    }
    const sw = A.currentWindow;
    const button = sw && sw.slots[17];
    detail += ', window ' + !!sw + ', button ' + (button ? button.name : 'none');
    if (button && button.name === 'emerald') {
      from = A.chatLog.length;
      n = lines().length;
      await A.clickWindow(17, 0, 0).catch(() => {}); await sleep(2000);
      rerolled = /шинжүүд шинэчлэгдлээ/.test(said(A, from)) && lines().slice(n).some(l => l.includes('item.affix_reroll'));
      detail += ' :: ' + said(A, from).slice(-140);
    }
  }
  check('the smith rerolls a level-60 rare weapon for jade', rerolled && jade(A) === before - 1, detail + ' :: jade ' + before + ' → ' + jade(A));

  cmd(`deop ${name}`);
  A.quit();
  const failed = results.filter(r => !r.ok);
  console.log(JSON.stringify({ passed: results.length - failed.length, failed: failed.length, results }, null, 1));
  process.exit(failed.length ? 1 : 0);
})();
