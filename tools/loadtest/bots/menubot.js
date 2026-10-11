// node dunmenubot.js <name> <port>: /dungeon -> read the dungeon window, click rung 1, report the guide bar.
const mineflayer = require('mineflayer');
const [name, port] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [], window: null, slots: {}, bars: [] };
bot.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
bot.on('bossBarCreated', (b) => out.bars.push(String(b.title)));
bot.on('bossBarUpdated', (b) => out.bars.push(String(b.title)));
bot.once('spawn', async () => {
  await new Promise(r => setTimeout(r, 4000));
  const chatBefore = out.chat.length;
  bot.chat(process.env.CMD || '/dungeon');
  const w = await new Promise((res) => { const t = setTimeout(() => res(null), 6000); bot.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  out.chatAfterCommand = out.chat.slice(chatBefore);
  if (w) {
    out.window = { title: JSON.stringify(w.title).slice(0, 200), size: w.inventoryStart };
    for (let i = 0; i < w.inventoryStart; i++) { const it = w.slots[i]; if (it) out.slots[i] = { item: it.name, count: it.count, name: it.customName ? String(it.customName).slice(0, 160) : null }; }
    if (process.env.CLICK) await bot.clickWindow(Number(process.env.CLICK), 0, 0);
    await new Promise(r => setTimeout(r, 2500));
  }
  bot.quit();
});
bot.on('end', () => { out.bars = [...new Set(out.bars)].slice(-3); console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { out.timeout = true; console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 60000);
