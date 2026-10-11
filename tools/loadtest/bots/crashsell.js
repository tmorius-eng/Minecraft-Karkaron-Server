// Sells the held item and kills the server `delayMs` after the confirm (crash test of PlayerDataSaves ordering).
const mineflayer = require('mineflayer'); const { execFileSync, execSync } = require('child_process');
const [ctl, name, delayMs] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const msgs = []; bot.on('messagestr', m => msgs.push(m));
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd(`itemsadmin give ${name} weapon.talyn_ild rare`); await sleep(1500);
    const slot = bot.inventory.slots.findIndex((it, i) => i >= 36 && i <= 44 && it && it.name === 'iron_sword' && JSON.stringify(it).includes('talyn'));
    const swords = bot.inventory.slots.filter(it => it && it.name === 'iron_sword').length;
    bot.chat('/balance'); await sleep(800);
    const bal = msgs.filter(m => /₮/.test(m)).slice(-1)[0];
    console.log('before: swords', swords, 'balance', bal);
    if (slot < 0) { console.log('NO ITEM'); bot.quit(); return; }
    bot.setQuickBarSlot(slot - 36); await sleep(400);
    bot.chat('/item sell'); await sleep(900);
    const pid = execSync("pgrep -f 'paper.jar --nogui' | head -1").toString().trim();
    bot.chat('/item sell confirm');
    await sleep(+delayMs);
    execSync(`kill -9 ${pid}`);
    console.log('killed', pid, 'after', delayMs, 'ms; last msgs:', msgs.slice(-2));
  } catch (e) { console.log('ERR', e.message); }
  setTimeout(() => process.exit(0), 500);
});
setTimeout(() => { console.log('timeout'); process.exit(1); }, 60000);
