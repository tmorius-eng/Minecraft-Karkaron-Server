const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'prop' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const chat = []; bot.on('messagestr', m => chat.push(m));
const win = () => new Promise(r => { const t = setTimeout(() => r(null), 4000); bot.once('windowOpen', w => { clearTimeout(t); r(w); }); });
function near(r) {
  return Object.values(bot.entities).filter(e => e.position.distanceTo(bot.entity.position) < r && (e.name === 'item_display' || e.name === 'interaction'));
}
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    // city origin -16,-64: turtle at (-42, -4), tree at (-36, -141)
    for (const [label, x, z] of [['turtle W', -42, -4], ['turtle E', 10, -4], ['silver tree', -36, -141]]) {
      cmd(`tp ${N} ${x + 4} 75 ${z + 4}`); await sleep(9000);
      const es = near(12);
      const disp = es.filter(e => e.name === 'item_display');
      const inter = es.filter(e => e.name === 'interaction');
      console.log(label, 'displays', disp.length, 'interactions', inter.length, disp[0] ? 'at ' + disp[0].position.floored() : '');
      if (inter[0] && label === 'turtle W') {
        chat.length = 0; bot.lookAt(inter[0].position.offset(0, 0.8, 0)); await sleep(300);
        bot.activateEntity(inter[0]); await sleep(800); console.log('  click chat:', chat.slice(-2).map(s => s.slice(0, 70)));
      }
    }
    cmd(`suld badge ${N} streamer`); cmd(`suld setrank ${N} myangat`); await sleep(1500);
    console.log('cmd chat:', chat.slice(-3).map(s => s.slice(0, 80)));
    const t = Object.values(bot.teams || {}).find(t => t.members && t.members.includes(N));
    const pre = t ? JSON.stringify(t.prefix) : ''; const suf = t ? JSON.stringify(t.suffix) : '';
    console.log('prefix glyphs', [...pre.matchAll(/\\u(e0[0-9a-f]{2})/gi)].map(m => m[1]), 'suffix', suf.slice(0, 120));
    let w = win(); bot.chat('/rankup'); let m = await w; await sleep(300);
    if (m) { const it = m.slots[14]; const lore = (it.components || []).find(x => x.type === 'lore'); console.log('rankup slot14 lore has glyph:', /\\ue0|[-]/i.test(JSON.stringify(lore))); 
      w = win(); await bot.clickWindow(31, 0, 0); m = await w; await sleep(300); console.log('badge book items:', m ? m.slots.slice(0, 54).filter(Boolean).length : 'none'); if (m) bot.closeWindow(m); }
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 120000);
