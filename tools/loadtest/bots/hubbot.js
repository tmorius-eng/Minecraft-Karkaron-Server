// The «Командын Өргөө» window on the dev server (docs/UI_HUB.md), one offline bot:
//   node hubbot.js <srvctl.sh> <logfile> [port]
// /menu opens the hub (title carries HUB_FRAME + PAGE_HUB, 8 tab tiles and 8 cards), the Тоглогч tab opens the
// profile page (PAGE_PROFILE, the tab glow, a «Буцах» button), «Буцах» returns to the hub, «✕ Хаах» closes it, and
// /profile opens the window instead of printing chat.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const G = { FRAME: '', HUB: '', PROFILE: '', TAB_ON: '', BACK: '' };   // Glyphs.java
const has = (w, g) => !!w && (JSON.stringify(w.title).includes(g) || JSON.stringify(w.title).includes('\\u' + g.charCodeAt(0).toString(16)));

function join(name) {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.chatLog = [];
    b.on('messagestr', m => b.chatLog.push(m));
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) {   // the class choice
        await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600);
        await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200);
      }
      res(b);
    });
  });
}

async function open(b, command) {
  if (b.currentWindow) { b.closeWindow(b.currentWindow); await sleep(500); }
  const k = b.chatLog.length;
  b.chat(command);
  const w = await new Promise((res) => { const t = setTimeout(() => res(null), 5000); b.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  await sleep(800);
  return { w, chat: b.chatLog.slice(k) };
}

async function click(b, slot) {
  const p = new Promise((res) => { const t = setTimeout(() => res(null), 4000); b.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  await b.clickWindow(slot, 0, 0).catch(() => {});
  const w = await p;
  await sleep(600);
  return w;
}

(async () => {
  const name = 'H' + Math.floor(Math.random() * 900 + 100);
  const b = await join(name);
  cmd(`suld exp ${name} 20000`);
  await sleep(1500);

  let r = await open(b, '/menu');
  check(has(r.w, G.FRAME) && has(r.w, G.HUB), '/menu opens the hub (frame + page glyphs in the title)', r.w ? JSON.stringify(r.w.title).slice(0, 120) : 'no window');
  const tabs = r.w ? [0, 1, 2, 3, 4, 5, 6, 7].filter(i => r.w.slots[i]).length : 0;
  const cards = r.w ? [9, 14, 18, 23, 27, 32, 36, 41].filter(i => r.w.slots[i]).length : 0;
  check(tabs === 8 && cards === 8, 'eight tab tiles and eight cards are set', 'tabs ' + tabs + ' cards ' + cards);
  check(r.w && r.w.slots[49] && !r.w.slots[45], 'the hub has «Хаах» but no «Буцах»', JSON.stringify(!!r.w?.slots[49]) + ' ' + JSON.stringify(!!r.w?.slots[45]));

  let w = await click(b, 0);
  check(has(w, G.PROFILE) && has(w, G.TAB_ON) && has(w, G.BACK), 'the Тоглогч tab opens the profile page with the tab glow and «Буцах»', w ? JSON.stringify(w.title).slice(0, 160) : 'no window');
  const stats = w && w.slots[8] ? JSON.stringify(w.slots[8].customName || '') + ' ' + JSON.stringify(w.slots[8].customLore || '') : '';
  check(stats.includes('Түвшин') && stats.includes('Зоос'), 'the ornament tile carries the player\'s stats tooltip', 'lore chars ' + stats.length);

  w = await click(b, 45);
  check(has(w, G.HUB) && !has(w, G.PROFILE), '«Буцах» returns to the hub', w ? JSON.stringify(w.title).slice(0, 100) : 'no window');

  await b.clickWindow(49, 0, 0).catch(() => {}); await sleep(1200);
  check(!b.currentWindow, '«✕ Хаах» closes the window', String(!!b.currentWindow));

  r = await open(b, '/profile');
  check(has(r.w, G.PROFILE) && r.chat.length === 0, '/profile opens the window and prints no chat', 'chat lines ' + r.chat.length);

  // text pages are dialogs (mineflayer cannot read them): the proof is that no chat lines follow the command
  for (const c of ['/help start', '/rules', '/commands', '/discord', '/exp']) {
    if (b.currentWindow) { b.closeWindow(b.currentWindow); await sleep(400); }
    const k = b.chatLog.length;
    b.chat(c); await sleep(1800);
    const lines = b.chatLog.slice(k).filter(l => !l.includes('Холбоос хараахан'));
    check(lines.length === 0, c + ' answers with a window, not chat', lines.join(' | ').slice(0, 160) || 'no chat');
  }
  r = await open(b, '/help');
  check(has(r.w, '\ue04f') && r.w.slots[8], '/help opens the Тусламж page with the tutorial on the ornament tile', r.w ? 'ok' : 'no window');

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
