const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: 'hpbot', version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(4000);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd('gamemode creative hpbot'); cmd('tp hpbot 300 90 300'); await sleep(3000);
  cmd('suld spawnmob hpbot mob.khukh_suldiin_sakhiul 1'); await sleep(1500);
  const sel = '@e[type=wither_skeleton,limit=1,sort=nearest]';
  cmd(`execute at hpbot run data modify entity ${sel} NoAI set value 1b`);
  cmd(`execute at hpbot run data get entity ${sel} Health`);
  cmd(`execute at hpbot run attribute ${sel} minecraft:max_health get`);
  cmd(`execute at hpbot run data get entity ${sel} CustomName`);
  cmd(`execute at hpbot run damage ${sel} 100 minecraft:generic`); await sleep(500);
  cmd(`execute at hpbot run data get entity ${sel} Health`); await sleep(300);
  cmd(`execute at hpbot run data get entity ${sel} CustomName`);
  cmd(`execute at hpbot run kill ${sel}`); await sleep(500);
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 40000);
