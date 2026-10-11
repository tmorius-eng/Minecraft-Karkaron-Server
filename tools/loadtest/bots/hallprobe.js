const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const fs = require('fs');
const [ctl, logfile] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const name = 'Hp' + Math.floor(Math.random() * 900 + 100);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const log = []; bot.on('messagestr', (m) => log.push(m));
bot._client.on('set_title_text', (pk) => log.push('TITLE ' + JSON.stringify(pk.text).slice(0, 80)));
bot.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 10 && !bot.currentWindow; i++) await sleep(400);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd(`suld exp ${name} 30000`); cmd(`effect give ${name} minecraft:resistance 600 4 true`);
  cmd(`tp ${name} 351.5 66 -140.5`); await sleep(4000);
  bot.chat('/dungeon enter khasar_den'); await sleep(5000);
  console.log('in hall at', bot.entity.position.toString(), 'dim', bot.game.dimension);
  for (let w = 0; w < 14; w++) {
    cmd('execute in minecraft:suld_halls run minecraft:kill @e[type=!player,tag=suld_dungeon]');
    await sleep(3000);
  }
  await sleep(8000);
  console.log('end at', bot.entity.position.toString());
  console.log(log.filter(l => !/Шинэ газар|хуяг|Түвшин|оноо|Заавар/.test(l)).slice(-14).join('\n'));
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
setTimeout(() => process.exit(0), 120000);
