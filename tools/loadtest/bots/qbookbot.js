const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'qbook' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const win = () => new Promise(r => { const t = setTimeout(() => r(null), 4000); bot.once('windowOpen', w => { clearTimeout(t); r(w); }); });
const txt = (o) => JSON.stringify(o);
const loreOf = (it) => { const c = (it.components || []).find(x => x.type === 'lore'); return c ? txt(c.data) : ''; };
const styleOf = (it) => { const c = (it.components || []).find(x => x.type === 'tooltip_style'); return c ? txt(c.data) : null; };
const has = (s, cp) => s.includes(cp) || s.includes('\\u' + cp.charCodeAt(0).toString(16));
function report(label, m) {
  if (!m) { console.log(label, 'NO WINDOW'); return; }
  const t = txt(m.title);
  const items = [];
  for (let i = 9; i < 45; i++) if (m.slots[i]) items.push(i);
  console.log(label, 'bg:', ['', '', '', ''].filter(g => has(t, g)).map(g => g.charCodeAt(0).toString(16)), 'cards:', items.length,
    'tabs:', [0, 3, 6].map(s => !!m.slots[s]), 'map btn:', !!m.slots[51]);
  return items;
}
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd(`suld quest ${N} 19`); await sleep(800);
    let w = win(); bot.chat('/quest'); let m = await w; await sleep(300);
    const items = report('open (ch19):', m);
    if (m) {
      const a = m.slots[9], act = m.slots[9 + 0];
      console.log('slot9 name:', txt((a.components||[]).find(x=>x.type==='custom_name')?.data).slice(0, 120));
      const lore = loreOf(a);
      console.log('slot9 tooltip_style:', styleOf(a), 'badge ACTIVE:', has(lore, ''), 'mouse:', has(lore, ''), 'Мэдээлэл:', lore.includes('Мэдээлэл'), 'Шагнал:', lore.includes('Шагнал'), '┃:', lore.includes('┃'));
      const lk = m.slots[10]; console.log('slot10 LOCKED badge:', has(loreOf(lk), ''), 'tooltip_style:', styleOf(lk));
      console.log('tab0 style:', styleOf(m.slots[0]));
      w = win(); await bot.clickWindow(6, 0, 0); m = await w; await sleep(300);
      report('sites tab:', m);
      if (m) { const s = m.slots[9]; console.log('site9 lore unseen badge:', has(loreOf(s), ''), 'style:', styleOf(s)); }
      w = win(); await bot.clickWindow(0, 0, 0); m = await w; await sleep(300);
      report('act I tab:', m);
      if (m) console.log('act1 slot9 DONE badge:', has(loreOf(m.slots[9]), ''));
      w = win(); await bot.clickWindow(51, 0, 0); m = await w; await sleep(300);
      console.log('map view title:', m && txt(m.title).slice(0, 80), 'book btn 52:', m && !!m.slots[52]);
      if (m) bot.closeWindow(m);
    }
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 100000);
