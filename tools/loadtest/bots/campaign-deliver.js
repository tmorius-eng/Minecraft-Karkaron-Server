// End-to-end success of a DELIVER_ITEMS campaign on the dev server (docs/KHURALDAI.md §9):
//   node campaign-deliver.js <srv2ctl.sh> <logfile> <campaign id without "campaign."> <item id> <count> [port]
// Starts the campaign itself (console: khuraldai cancel, khuraldai force <id>). The bot gets <count> items from the
// console, hands them in, and the campaign must succeed: tier reward paid, world effect applied, chronicle written.
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, campaign, item, countText, port = '25571'] = process.argv.slice(2);
const count = Number(countText);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the last element after the final newline is '' (or a half-written line): never count it, or slice(n) skips a line
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.error((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' :: ' + detail : '')); };
const logged = (from, text) => lines().slice(from).some(l => l.includes(text));

function join(name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.chatLog = [];
    bot.on('messagestr', (m) => bot.chatLog.push(m));
    bot.on('error', () => {});
    bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
    bot.once('spawn', async () => { await sleep(3500); resolve(bot); });
    setTimeout(() => resolve(null), 30000);
  });
}
async function pickClass(bot, slot) {
  for (let i = 0; i < 80 && !bot.currentWindow; i++) await sleep(500);
  if (bot.currentWindow) { await bot.clickWindow(slot, 0, 0).catch(() => {}); await sleep(600); }
  if (bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); } // «Батлах» (ONB-006 confirm page)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}
const heard = (bot, text) => bot.chatLog.some(l => l.includes(text));

(async () => {
  const name = 'D' + Math.floor(Math.random() * 90000 + 10000);
  const A = await join(name); await pickClass(A, 10);
  cmd(`suld exp ${name} 30000`); await sleep(1000);
  for (let i = 0; i < count; i++) cmd(`itemsadmin give ${name} ${item}`);
  await sleep(2500 + count * 30);
  const have = A.inventory.items().reduce((n, it) => n + it.count, 0);
  check('the bot holds the items', have >= count, have + ' items');
  cmd('khuraldai cancel'); await sleep(2000);
  cmd(`khuraldai force ${campaign}`); await sleep(2500);
  let n = lines().length;
  A.chat('/khuraldai deliver'); await sleep(6000);
  check('the delivery is counted', heard(A, 'хүргэв'), A.chatLog.filter(l => l.includes('хүргэв') || l.includes('Хүргэх')).join(' | '));
  check('the campaign succeeds', logged(n, 'resolved SUCCEEDED'), lines().slice(n).filter(l => l.includes('[campaign]')).join(' | '));
  check('the tier reward is paid', heard(A, 'алтан шат') && heard(A, 'EXP'), A.chatLog.filter(l => l.includes('шат')).join(' | '));
  // a first success makes the change live ([events]/[props]/[npc]); a repeat finds it live and records it again
  check('the world effect is applied', logged(n, '[events] unlocked') || logged(n, '[props] dynamic') || logged(n, '[npc] dynamic')
      || logged(n, 'campaign.effect_applied actor=server target=effect:campaign.' + campaign), lines().slice(n).filter(l => /\[(events|props|npc)\]|effect_applied/.test(l)).join(' | '));
  check('the outcome is in the chronicle', logged(n, '[chronicle] ✦ Хроник') && logged(n, 'амжилттай'));
  check('everyone hears the headline', heard(A, 'амжилттай'));
  A.quit();
  const failed = results.filter(r => !r.ok);
  console.log(JSON.stringify({ passed: results.length - failed.length, failed: failed.length, results }, null, 1));
  process.exit(failed.length ? 1 : 0);
})();
