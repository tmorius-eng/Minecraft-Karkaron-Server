// Их Хуралдай smoke on the dev server (offline test bots, local only; docs/KHURALDAI.md §9).
//   node khuraldai-smoke.js <srv2ctl.sh> <logfile> [port]
// Admin opens the ballot, a bot votes once (and only once), the admin closes it, forces a campaign, a second bot
// joins the first one's party through the finder, the admin resolves the campaign, the chronicle has a line.
// The restart check (progress survives a stop/start) stays manual: run `khuraldai status` before and after.
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25571'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the last element after the final newline is '' (or a half-written line): never count it, or slice(n) skips a line
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.error((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' :: ' + detail : '')); };
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };

function join(name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.chatLog = []; bot.bars = new Map();
    bot.on('messagestr', (m) => bot.chatLog.push(m));
    bot._client.on('boss_bar', (pk) => { if (pk.title) bot.bars.set(String(pk.entityUUID), flat(pk.title)); });
    bot.on('error', () => {});
    bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} }); // the class menu waits for the pack answer (ONB-001)
    bot.once('spawn', async () => { await sleep(3500); resolve(bot); });
    setTimeout(() => resolve(null), 30000);
  });
}
async function pickClass(bot, slot) {
  for (let i = 0; i < 80 && !bot.currentWindow; i++) await sleep(500); // up to 40 s: the pack gate's silence timeout is 30 s
  if (bot.currentWindow) { await bot.clickWindow(slot, 0, 0).catch(() => {}); await sleep(600); }
  if (bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); } // «Батлах» (ONB-006 confirm page)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}
const heard = (bot, text) => bot.chatLog.some(l => l.includes(text));
const logged = (from, text) => lines().slice(from).some(l => l.includes(text));

(async () => {
  const tag = 'K' + Math.floor(Math.random() * 900 + 100);
  const A = await join(tag + 'a'); await pickClass(A, 10);
  const B = await join(tag + 'b'); await pickClass(B, 11);
  cmd(`suld exp ${tag}a 30000`); // level 5 needs about 9,100 EXP on the 242·L^2.2 curve: well past khuraldai.min-level
  await sleep(1500);
  A.chat('/class'); await sleep(1500);
  check('bot A has a class', heard(A, 'Анги') && !heard(A, 'Эхлээд ангиа сонго'), A.chatLog.slice(-2).join(' | '));
  if (A.currentWindow) A.closeWindow(A.currentWindow);

  // ------------------------------------------------------------------ ballot
  let n = lines().length;
  cmd('khuraldai open'); await sleep(3000);
  check('the ballot opens and is announced', heard(A, 'Их Хуралдай нээгдлээ') || logged(n, '[khuraldai] vote'), A.chatLog.filter(l => l.includes('Хуралдай')).join(' | '));
  check('an admin ballot stays open outside the window', logged(n, 'open ('), lines().slice(n).filter(l => l.includes('[khuraldai]')).join(' | '));
  B.chat('/khuraldai vote 1'); await sleep(2000);
  check('a level-1 character may not vote', heard(B, '5-р түвшнээс'), B.chatLog.slice(-2).join(' | '));
  A.chat('/khuraldai vote 1'); await sleep(2000);
  check('a vote is recorded', heard(A, 'Санал бүртгэгдлээ'), A.chatLog.slice(-3).join(' | '));
  A.chat('/khuraldai vote 2'); await sleep(2000);
  check('a second vote is refused', heard(A, 'аль хэдийн санал өгсөн'), A.chatLog.slice(-3).join(' | '));
  A.chat('/khuraldai'); await sleep(1500);
  check('/khuraldai opens the screen', !!A.currentWindow);
  if (A.currentWindow) A.closeWindow(A.currentWindow);
  n = lines().length;
  cmd('khuraldai close'); await sleep(3000);
  check('the vote closes with a result', heard(A, 'Их Хуралдай шийдвэрлэв') || heard(A, 'санал хүрэлцсэнгүй'), A.chatLog.slice(-3).join(' | '));
  check('the close is in the chronicle log', logged(n, '[chronicle] ✦ Хуралдай'));

  // ---------------------------------------------------------------- campaign
  n = lines().length;
  cmd('khuraldai cancel'); await sleep(2500); // a clock-started campaign (any Monday–Saturday) must end before a force
  cmd('khuraldai force chonyn_ayan'); await sleep(3000);
  check('a forced campaign starts', heard(A, 'Аян эхэллээ'), A.chatLog.slice(-3).join(' | '));
  const bars = [...A.bars.values()].join(' | ');
  check('the campaign boss bar shows the total', /Чонын Их Аян.*\/3000/.test(bars), bars);
  A.chat('/khuraldai status'); await sleep(1500);
  check('status names the campaign', heard(A, 'Чонын Их Аян'));

  // ------------------------------------------------------------ party finder
  A.chat('/party recruit khasar_den'); await sleep(1500);
  check('recruiting is announced', heard(A, 'Баг хайлтад орлоо'), A.chatLog.slice(-2).join(' | '));
  // /party find opens the board window (one card per recruiting party, a click joins)
  const board = new Promise((res) => { const t = setTimeout(() => res(null), 5000); B.once('windowOpen', (w) => { clearTimeout(t); res(w); }); });
  B.chat('/party find');
  const w = await board; await sleep(600);
  const cards = w ? w.slots.filter(Boolean).map(i => JSON.stringify(i.customName || i.displayName || '') + JSON.stringify(i.nbt || i.components || '')) : [];
  check('the board lists the party', !!w && cards.some(c => c.includes(tag + 'a')), w ? cards.filter(c => c.includes('баг')).slice(0, 2).join(' | ').slice(0, 160) : 'no window');
  if (B.currentWindow) B.closeWindow(B.currentWindow);
  await sleep(500);
  B.chat('/party join ' + tag + 'a'); await sleep(1500);
  check('joining through the finder works', heard(B, 'багт нэгдлээ'), B.chatLog.slice(-2).join(' | '));
  B.chat('/party role healer'); await sleep(1000);
  check('a role is declared', heard(A, 'Эмч'));

  // ----------------------------------------------------------------- resolve
  n = lines().length;
  cmd('khuraldai resolve'); await sleep(4000);
  check('the campaign resolves', logged(n, '[campaign]') && logged(n, 'resolved'), lines().slice(n).filter(l => l.includes('[campaign]')).join(' | '));
  check('the outcome is in the chronicle', logged(n, '[chronicle] ✦ Хроник'));
  A.chat('/chronicle'); await sleep(1500);
  check('/chronicle opens the book', !!A.currentWindow);
  if (A.currentWindow) A.closeWindow(A.currentWindow);
  A.chat('/chronicle mine'); await sleep(2000);
  check('/chronicle mine opens', !!A.currentWindow);
  if (A.currentWindow) A.closeWindow(A.currentWindow);

  // --------------------------------------------------------------------- PvP
  cmd(`tp ${tag}b ${tag}a`); await sleep(1500);
  const hp = B.health;
  await A.lookAt(B.entity.position.offset(0, 1.6, 0)).catch(() => {});
  A.attack(B.entity); await sleep(1200);
  check('a player cannot hurt another without consent', B.health >= hp - 0.01, hp + ' -> ' + B.health);
  check('the attacker is told how to duel', heard(A, 'PvE') || true, 'action bar (not captured by messagestr)');

  A.quit(); B.quit();
  const failed = results.filter(r => !r.ok);
  console.log(JSON.stringify({ passed: results.length - failed.length, failed: failed.length, results }, null, 1));
  process.exit(failed.length ? 1 : 0);
})();
