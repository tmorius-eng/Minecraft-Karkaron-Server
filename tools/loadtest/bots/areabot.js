const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, name] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const chat = []; const titles = [];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m)); bot.on('title', (t) => titles.push(typeof t === 'string' ? t : JSON.stringify(t)));
bot.once('spawn', async () => {
  await sleep(5000); if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); } if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd(`effect give ${name} minecraft:resistance 120 4 true`);
  for (const [x, z] of [[300, -30], [300, 200], [1000, -400], [1000, 300], [-300, 0], [0, -900]]) {
    chat.length = 0; cmd(`execute in minecraft:overworld run tp ${name} ${x} 200 ${z}`); await sleep(4500);
    console.log(x, z, chat.filter(m => /газар|нутаг|Шинэ/i.test(m)).slice(-2));
  }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
