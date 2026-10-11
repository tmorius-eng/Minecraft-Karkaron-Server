const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const suites = process.argv.slice(3).join(' ') || 'all'; const name = 'Qa' + Math.floor(Math.random() * 10000);
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot._client.on('entity_velocity', (p) => { if (bot.entity && p.entityId === bot.entity.id && p.velocity) { const v = p.velocity; setImmediate(() => bot.entity.velocity.set(v.x, v.y, v.z)); } }); // this protocol version's knockback packet is not applied by mineflayer itself
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('kicked', (r) => console.log('KICKED ' + JSON.stringify(r)));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(3000); // let a first-join class choice be saved before the suite asks for it
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
