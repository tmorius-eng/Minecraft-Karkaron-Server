const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Rg' + Math.floor(Math.random() * 1000), version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const titleOf = (w) => { const s = JSON.stringify(w.title); const m = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(x => x[1]).join(''); return m || s.slice(0, 60); };
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(2500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  const out = [];
  for (const c of ['/menu', '/help', '/tutorial', '/class', '/skills', '/quest', '/rankup', '/lvlup', '/cosmetics', '/shop', '/buy', '/daily', '/tasks', '/top', '/top coins']) {
    bot.chat(c); await sleep(1300);
    const w = bot.currentWindow;
    out.push(c.padEnd(11) + ' → ' + (w ? titleOf(w) + ' (' + w.slots.slice(0, w.inventoryStart).filter(Boolean).length + ')' : 'no window'));
    if (w) { bot.closeWindow(w); await sleep(300); }
  }
  console.log(out.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => process.exit(1), 90000);
