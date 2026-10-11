const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const [ctl, name] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(5000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  bot.chat('/spawn'); await sleep(8000);
  const me = bot.entity.position;
  const boards = Object.values(bot.entities).filter(e => e.name === 'text_display' && e.position.distanceTo(me) < 20);
  console.log('me', me.toString());
  for (const b of boards) console.log('board', b.position.minus(me).toString());
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 40000);
