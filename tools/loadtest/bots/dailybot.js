const mineflayer = require('mineflayer');
const name = process.argv[2] || ('Dy' + Math.floor(Math.random() * 1000));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const log = [];
bot._client.on('system_chat', (pk) => { if (!(pk.isActionBar || pk.overlay)) { const s = flat(pk.content); if (/шагнал|₮/.test(s)) log.push(s); } });
const title = () => { const w = bot.currentWindow; return w && flat(w.title.value ? w.title : w.title); };
bot.once('spawn', async () => {
  await sleep(4000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(4000);
  bot.chat('/daily'); await sleep(1500);
  log.push('GUI1 ' + JSON.stringify(bot.currentWindow && bot.currentWindow.title));
  if (bot.currentWindow) { log.push('slot10 ' + (bot.currentWindow.slots[10] || {}).name); await bot.clickWindow(10, 0, 0).catch(e => log.push('err ' + e)); }
  await sleep(1500);
  bot.chat('/daily'); await sleep(1500);
  log.push('GUI2 ' + JSON.stringify(bot.currentWindow && bot.currentWindow.title));
  if (bot.currentWindow) log.push('slot10 ' + (bot.currentWindow.slots[10] || {}).name + ' slot11 ' + (bot.currentWindow.slots[11] || {}).name);
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(0); }, 40000);
