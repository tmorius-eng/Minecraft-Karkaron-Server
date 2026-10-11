const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process'); const fs=require('fs');
const ctl = process.argv[2]; const name = 'Dbg' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(5000); if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  execFileSync(ctl, ['cmd', 'skillsadmin qakit ' + name + ' states']); await sleep(1500);
  bot.chat('/skills'); await sleep(1800);
  const s = bot.currentWindow.slots[20];
  console.log(s.name, s.components.map(c => c.type).join(','));
  const lc = s.components.find(c => c.type === 'lore'); console.log(JSON.stringify(lc).slice(0, 700));
  execFileSync(ctl, ['cmd', 'skillsadmin inspect ' + name]); await sleep(1500);
  console.log(fs.readFileSync('/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log','utf8').split('\n').filter(l=>l.includes(name)||l.includes('оноо')).slice(-6).join('\n'));
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
