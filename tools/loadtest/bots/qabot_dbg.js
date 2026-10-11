const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const suites = process.argv.slice(3).join(' ') || 'all'; const name = 'Qa' + Math.floor(Math.random() * 10000);
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot._client.on('entity_velocity', (p) => { if (bot.entity && p.entityId === bot.entity.id && p.velocity) bot.entity.velocity.set(p.velocity.x, p.velocity.y, p.velocity.z); }); // this protocol version's knockback packet is not applied by mineflayer itself
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('kicked', (r) => console.log('KICKED ' + JSON.stringify(r)));
bot._client.on('entity_velocity', (p) => { if (bot.entity && p.entityId === bot.entity.id) { console.log('VEL', JSON.stringify(p.velocity)); let n = 0; const iv = setInterval(() => { console.log('  t', n, bot.entity.position.toString(), bot.entity.velocity.toString(), 'phys', bot.physicsEnabled, 'ground', bot.entity.onGround); if (++n > 4) clearInterval(iv); }, 100); } }); bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  const start = fs.readFileSync(LOG, 'utf8').split('\n').length;
  execFileSync(ctl, ['cmd', `skillsadmin qa ${name} ${suites}`]);
  const t0 = Date.now();
  while (Date.now() - t0 < 900000) {
    await sleep(3000);
    const l = fs.readFileSync(LOG, 'utf8').split('\n').slice(start);
    if (l.some(x => x.includes('[skill-qa] DONE'))) {
      console.log(l.filter(x => /\[skill-qa\]/.test(x)).map(x => x.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')).join('\n'));
      break;
    }
    if (!bot.entity) { console.log('bot lost'); break; }
  }
  console.log('NAME ' + name + ' elapsed ' + Math.round((Date.now() - t0) / 1000) + 's');
  bot.quit();
});
bot.on('end', () => process.exit(0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 960000);
