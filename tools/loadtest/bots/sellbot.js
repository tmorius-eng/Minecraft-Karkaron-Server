const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const chat = []; const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: 'sellbot', version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
const inv = () => bot.inventory.slots.map((s, i) => s ? i + ':' + s.name + '×' + s.count : null).filter(Boolean);
const win = () => new Promise(r => { const t = setTimeout(() => r(null), 4000); bot.once('windowOpen', w => { clearTimeout(t); r(w); }); });
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd('itemsadmin give sellbot item.talyn_tuvshin'); cmd('itemsadmin give sellbot item.chonon_arisan'); cmd('itemsadmin give sellbot item.chonon_arisan');
    await sleep(1500);
    console.log('bag', JSON.stringify(inv()));
    let w = win(); bot.chat('/shop'); let shop = await w; await sleep(400);
    w = win(); await bot.clickWindow(30, 0, 0); let sell = await w; await sleep(500);
    const shown = sell.slots.slice(0, 45).filter(Boolean).map(s => s.name + '×' + s.count);
    console.log('sell window', JSON.stringify(shown), 'title', JSON.stringify(sell.title).slice(0, 60));
    const gearSlot = sell.slots.slice(0, 45).findIndex(s => s && s.name === 'amethyst_shard');
    w = win(); await bot.clickWindow(gearSlot, 0, 0); sell = await w; await sleep(500);
    console.log('after 1st click on gear, still in window:', !!sell.slots[gearSlot], 'bag', JSON.stringify(inv().filter(x => /amethyst/.test(x))));
    chat.length = 0;
    w = win(); await bot.clickWindow(gearSlot, 0, 0); sell = await w; await sleep(500);
    console.log('after 2nd click:', chat.filter(m => /Зарагдлаа/.test(m)));
    w = win(); await bot.clickWindow(49, 0, 0); sell = await w; await sleep(500);
    console.log('sell all:', chat.filter(m => /олз зарж/.test(m)));
    bot.closeWindow(bot.currentWindow); await sleep(500);
    console.log('bag after', JSON.stringify(inv()));
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
