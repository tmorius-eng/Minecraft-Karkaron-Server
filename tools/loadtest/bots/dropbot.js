// Q-drop test for soulbound class gear: node dropbot.js <ctl> <name>
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, name] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const chat = []; const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
const desc = (it) => it && (it.name) + '×' + it.count;
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(2000);
    const inv = () => bot.inventory.slots.map((s, i) => s ? i + ':' + desc(s) : null).filter(Boolean);
    console.log('before', JSON.stringify(inv()));
    for (let hb = 0; hb < 9; hb++) {
      const s = bot.inventory.slots[36 + hb]; if (!s) continue;
      bot.setQuickBarSlot(hb); await sleep(300); chat.length = 0;
      bot._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); // Q
      await sleep(900);
      console.log('Q slot', hb, desc(s), '->', desc(bot.inventory.slots[36 + hb]) || 'GONE', chat.filter(m => /холбоотой|хаях/.test(m)).slice(-1));
    }
    // inventory-screen paths on the class weapon (hotbar slot 0 = window slot 36)
    const W = 36; const name0 = () => desc(bot.inventory.slots[W]);
    console.log('weapon', name0());
    await bot.clickWindow(W, 0, 4).catch(e => console.log('e', e.message)); await sleep(700); console.log('Q in inventory ->', name0() || 'GONE');
    await bot.clickWindow(W, 1, 4).catch(e => console.log('e', e.message)); await sleep(700); console.log('Ctrl+Q in inventory ->', name0() || 'GONE');
    await bot.clickWindow(W, 0, 0).catch(e => console.log('e', e.message)); await sleep(500);
    await bot.clickWindow(-999, 0, 0).catch(e => console.log('e', e.message)); await sleep(700);
    console.log('click outside with it on cursor ->', JSON.stringify(inv().filter(x => x.startsWith('36:') || x.startsWith('-'))), 'cursor', desc(bot.inventory.selectedItem));
    await bot.clickWindow(W, 0, 0).catch(() => {}); await sleep(400);
    await bot.clickWindow(1, 0, 0).catch(e => console.log('e', e.message)); await sleep(700); // into the 2x2 grid
    console.log('into crafting grid ->', JSON.stringify(inv().filter(x => /^[1-4]:/.test(x))));
    await sleep(500);
    const items = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 6).length;
    console.log('item entities near bot', items);
    console.log('after', JSON.stringify(inv()));
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
