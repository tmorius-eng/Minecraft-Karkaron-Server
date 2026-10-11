const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: 'namebot', version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(4000);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd('gamemode creative namebot'); cmd('tp namebot 400 90 0'); await sleep(3000);
  cmd('suld spawnmob namebot mob.deeremchin 1'); await sleep(1500);
  cmd('execute at namebot run data get entity @e[type=pillager,limit=1,sort=nearest] CustomName'); await sleep(500);
  cmd('execute at namebot run kill @e[type=pillager,distance=..20]');
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 30000);
