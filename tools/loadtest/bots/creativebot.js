// creative "destroy slot" test for class gear: node creativebot.js <ctl> <name>
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, name] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const chat = []; const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
const desc = (it) => it && (it.name) + '×' + it.count;
const inv = () => bot.inventory.slots.map((s, i) => s ? i + ':' + desc(s) : null).filter(Boolean);
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(2500);
    console.log('before', JSON.stringify(inv()));
    cmd('gamemode creative ' + name); await sleep(1500);
    console.log('gamemode', bot.game.gameMode);
    // the creative "destroy" slot: the client just tells the server each slot is now empty
    const gone = [5, 6, 7, 8, 36].filter(s => bot.inventory.slots[s]);
    for (const s of gone) { await bot.creative.setInventorySlot(s, null).catch(e => console.log('e', e.message)); await sleep(200); }
    await sleep(800);
    console.log('after destroy (server view)', JSON.stringify(inv()));
    chat.length = 0;
    bot._client.write('close_window', { windowId: 0 }); // closing the creative inventory
    await sleep(2000);
    console.log('after close', JSON.stringify(inv()), chat.slice(-2));
    // the client then drops a carried copy outside: must not duplicate
    cmd('gamemode survival ' + name); await sleep(1500);
    const items = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 6).length;
    console.log('item entities near bot', items);
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
