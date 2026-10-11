const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: 'gmbot', version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const near = () => Object.values(bot.entities).filter(e => e !== bot.entity && e.type !== 'player' && e.name !== 'item_display' && e.name !== 'text_display' && e.name !== 'block_display' && e.name !== 'interaction' && e.position.distanceTo(bot.entity.position) < 60 && /wolf|zombie|skeleton|husk|stray|pillager|vindicator|spider|drowned|witch|evoker|ravager|illusioner|piglin|vex/.test(e.name));
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd('gamemode survival gmbot'); cmd('effect give gmbot minecraft:resistance 300 4 true'); cmd('effect give gmbot minecraft:regeneration 300 4 true');
    cmd('tp gmbot 600 100 -300'); await sleep(4000);
    let mobs = [];
    for (let i = 0; i < 30 && mobs.length === 0; i++) { await sleep(2000); mobs = near(); }
    console.log('survival: mobs near', mobs.map(m => m.name + '#' + m.id));
    if (!mobs.length) { bot.quit(); return; }
    const ids = mobs.map(m => m.id);
    cmd('gamemode creative gmbot'); await sleep(8000);
    const still = ids.filter(id => bot.entities[id]);
    console.log('creative after 8 s: still there', still.length + '/' + ids.length);
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 120000);
