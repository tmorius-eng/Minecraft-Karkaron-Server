const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'rc' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const chat = []; bot.on('messagestr', m => chat.push(m));
const win = () => new Promise(r => { const t = setTimeout(() => r(null), 4000); bot.once('windowOpen', w => { clearTimeout(t); r(w); }); });
const name = (it) => { const c = (it.components || []).find(x => x.type === 'custom_name'); return c ? JSON.stringify(c.data).replace(/[^Ѐ-ӿA-Za-z0-9 ]/g, '').slice(0, 60) : it.name; };
const model = (it) => { const c = (it.components || []).find(x => x.type === 'item_model'); return c ? JSON.stringify(c.data) : ''; };
async function dump(label, command) {
  chat.length = 0;
  const w = win(); bot.chat(command); const m = await w; await sleep(400);
  if (!m) { console.log(label, 'NO WINDOW; chat:', chat.slice(-3)); return; }
  const items = [];
  for (let i = 0; i < m.inventoryStart; i++) if (m.slots[i]) items.push(i + ':' + m.slots[i].name + (model(m.slots[i]) ? '[' + model(m.slots[i]) + ']' : '') + ' ' + name(m.slots[i]));
  console.log(label, 'type', m.type, 'size', m.inventoryStart, 'items', items.length); console.log('  ' + items.slice(0, 14).join('\n  '));
  console.log('  title:', JSON.stringify(m.title).slice(0, 200));
  bot.closeWindow(m); await sleep(500);
}
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2000); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await dump('RANKUP', '/rankup');
    await dump('COSMETICS', '/cosmetics');
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 60000);
