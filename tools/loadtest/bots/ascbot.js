const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'ascbot' + (Date.now() % 1000);
const chat = []; const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
const flat = (o) => JSON.stringify(o);
const win = () => new Promise(r => { const t = setTimeout(() => r(null), 4000); bot.once('windowOpen', w => { clearTimeout(t); r(w); }); });
const txt = (c) => { if (c == null) return ''; if (typeof c === 'string') { try { return txt(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(txt).join(''); let s = ''; if (c.text !== undefined) s += (c.text.value ?? c.text); if (c[''] !== undefined) s += c['']; const ex = c.extra && (c.extra.value?.value || c.extra.value || c.extra); if (Array.isArray(ex)) s += ex.map(e => txt(e.value ?? e)).join(''); if (c.value && typeof c.value === 'object' && !Array.isArray(c.value)) s += txt(c.value); return s; };
const name = (it) => { if (!it) return null; const n = it.customName ?? (it.components || []).find(c => c.type === 'custom_name')?.data; return n ? txt(n) : it.name; };
const lore = (it) => { const l = it && (it.customLore ?? (it.components || []).find(c => c.type === 'lore')?.data); return Array.isArray(l) ? l.map(txt) : []; };
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(1000);
    chat.length = 0; let w = win(); bot.chat('/ascend'); let m = await w; await sleep(300); console.log('chat after /ascend', chat.slice(-3), 'win', !!m);
    if (!m) { bot.quit(); return; }
    console.log('L1 window:', m && m.slots[4] && name(m.slots[4]), '| button:', m && m.slots[31] && name(m.slots[31]));
    bot.closeWindow(m); await sleep(500);
    cmd(`suld exp ${N} 40000000`); await sleep(800);
    cmd(`dungeon grant ${N} all`); cmd(`suld endgame ${N} rank 1`); cmd(`suld endgame ${N} palace 2`); cmd(`suld endgame ${N} points 99999999`);
    cmd(`classgear set ${N} mastery 5`); cmd(`suld coins ${N} 60000`); await sleep(1500);
    w = win(); bot.chat('/ascend'); m = await w; await sleep(300);
    console.log('ready window:', name(m.slots[4]), '| button:', name(m.slots[31])); console.log('gates:', lore(m.slots[22]));
    w = win(); await bot.clickWindow(31, 0, 0); m = await w; await sleep(300);
    console.log('after 1st click:', name(m.slots[31]));
    chat.length = 0;
    await bot.clickWindow(31, 0, 0).catch(() => {}); await sleep(1500);
    console.log('rite chat:', chat.filter(x => /Зэрэг/.test(x)));
    w = win(); bot.chat('/ascend'); m = await w; await sleep(300);
    console.log('after rite:', name(m.slots[4]), '| next:', name(m.slots[31]));
    bot.closeWindow(m);
    cmd(`suld endgame ${N} palace 2`); await sleep(500);
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
