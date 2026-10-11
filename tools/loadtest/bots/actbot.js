const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process'); const fs = require('fs');
const [ctl, logf] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'actbot' + (Date.now() % 1000);
const chat = [], titles = [];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
bot.on('title', (t, type) => titles.push(type + ':' + (typeof t === 'string' ? t : JSON.stringify(t)).slice(0, 80)));
const win = () => new Promise(r => { const t = setTimeout(() => r(null), 4000); bot.once('windowOpen', w => { clearTimeout(t); r(w); }); });
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd(`gamemode survival ${N}`); cmd(`effect give ${N} minecraft:resistance 300 4 true`); cmd(`effect give ${N} minecraft:slow_falling 300 0 true`);
    await sleep(3000); titles.length = 0;
    if (false) for (const [x, z] of [[150, -64], [-180, -150], [-16, 55], [100, -210], [-190, 40], [160, 60]]) { cmd(`tp ${N} ${x} 90 ${z}`); await sleep(2500); }
    console.log('titles inside the city:', titles.length ? titles : 'none');
    cmd(`suld quest ${N} 19`); await sleep(800);
    let w = win(); bot.chat('/quest'); let m = await w; await sleep(300);
    console.log('quest window title has Act II:', m && JSON.stringify(m.title).includes('Хөх Сүлдний Зам'), 'act-switch button:', m && !!m.slots[51]);
    if (m) bot.closeWindow(m);
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 100000);
