const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'ctlbot' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd(`gamemode survival ${N}`); cmd(`effect give ${N} minecraft:resistance 300 4 true`);
    cmd(`execute in minecraft:overworld run spreadplayers 300 300 0 20 false ${N}`); await sleep(6000);
    const p = bot.entity.position; const below = bot.blockAt(p.offset(0, -1, 0));
    console.log('pos', p.toString(), 'below', below && below.name);
    const b = bot.findBlock({ matching: (bl) => ['short_grass','tall_grass','dandelion','poppy','fern','grass_block','dirt','sand'].includes(bl.name), maxDistance: 4 });
    if (!b) { console.log('nothing to dig'); bot.quit(); return; }
    const before = b.name, pos = b.position.clone();
    await bot.dig(b, true).catch(e => console.log('dig err', e.message)); await sleep(1000);
    console.log('wild control:', before, '->', bot.blockAt(pos).name);
  } catch (e) { console.log('ERR', e.message); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 60000);
