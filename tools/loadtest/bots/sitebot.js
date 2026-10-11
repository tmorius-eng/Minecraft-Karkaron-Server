const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process'); const fs = require('fs');
const [ctl, logf] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'sitebot' + (Date.now() % 1000);
const chat = []; const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
async function tryBreak(names, label) {
  const b = bot.findBlock({ matching: (bl) => names.includes(bl.name), maxDistance: 5 });
  if (!b) { console.log(label, 'no target block found'); return; }
  const pos = b.position.clone(); const before = b.name;
  await bot.lookAt(pos.offset(0.5, 0.5, 0.5)).catch(() => {});
  try { await bot.dig(b, true); } catch (e) { console.log(label, 'dig error', e.message); }
  await sleep(1200);
  const after = bot.blockAt(pos);
  console.log(label, before, '@', pos.toString(), '->', after ? after.name : '?', after && after.name === before ? 'PROTECTED' : 'BROKEN');
}
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd(`gamemode survival ${N}`); cmd(`effect give ${N} minecraft:resistance 300 4 true`); cmd(`effect give ${N} minecraft:slow_falling 300 0 true`);
    cmd(`tp ${N} 525 140 120`); await sleep(9000); // chunks load, the site is built within 5 s
    const line = fs.readFileSync(logf, 'utf8').split('\n').filter(l => l.includes('[sites] built site.aurag')).pop();
    console.log('built:', line ? line.slice(-60) : 'NO');
    const y = line ? +line.trim().split(' ').slice(-2)[0] : 100;
    chat.length = 0;
    cmd(`tp ${N} 525 ${y + 1} 106`); await sleep(4000);
    console.log('visit:', chat.filter(m => /Аураг|EXP|Шошго/.test(m)).slice(0, 4));
    await tryBreak(['packed_mud', 'mud_bricks', 'polished_andesite', 'coarse_dirt', 'grass_block'], 'site:');
    cmd(`tp ${N} -464 120 -208`); await sleep(6000);
    for (let i = 0; i < 20 && !bot.entity.onGround; i++) await sleep(500);
    await tryBreak(['oak_planks', 'cobblestone', 'oak_log', 'stripped_oak_log', 'glass_pane', 'white_bed', 'hay_block', 'oak_stairs', 'dirt_path'], 'village:');
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 90000);
