const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [name, ctl] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { chat: [], steps: [] };
bot.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const weapon = () => bot.inventory.slots.filter(Boolean).filter(i => /sword|bow|blaze_rod|axe/.test(i.name)).map(i => {
  const cmd = i.componentMap && i.componentMap.get && i.componentMap.get('custom_model_data');
  return i.name + ' cmd=' + JSON.stringify(cmd ? cmd.data : null) + ' ' + (i.customName || '');
});
bot.once('spawn', async () => {
  await new Promise(r => setTimeout(r, 5000));
  if (bot.currentWindow) await bot.clickWindow(Number(process.argv[4] || 10), 0, 0).catch(() => {});
  await new Promise(r => setTimeout(r, 2000));
  out.steps.push({ at: 'class chosen', weapon: weapon() });
  for (const exp of (process.argv[5] ? [Number(process.argv[5])] : [3000, 60000, 400000])) {
    execFileSync(ctl, ['cmd', `suld exp ${name} ${exp}`]);
    await new Promise(r => setTimeout(r, 2500));
    out.steps.push({ at: '+' + exp, weapon: weapon() });
  }
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 60000);
