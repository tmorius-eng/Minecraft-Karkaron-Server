// A Саарлын Агуй run to completion and what it must write (docs/KHURALDAI.md §9): the CLEAR_DUNGEON campaign
// contribution, the chronicle's first clear and the fastest-clear record. Dev server only, with the dev campaign
// campaign.test_clear (CLEAR_DUNGEON dungeon.khasar_den, target 60) in plugins/SULD/content/campaigns.json:
//   node dungeon-clear.js <srv2ctl.sh> <logfile> [port]
// The bot is a level-appropriate Баатар who walks to the open-world gate and enters; the waves and the boss are
// cleared from the console (the bot stands in the hall the whole time, so it counts as present and not downed).
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25571'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the last element after the final newline is '' (or a half-written line): never count it, or slice(n) skips a line
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.error((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' :: ' + detail : '')); };
const grab = (from, re) => lines().slice(from).filter(l => re.test(l)).join(' | ').slice(0, 400);

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
  if (bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); } // «Батлах»
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}

(async () => {
  const name = 'G' + Math.floor(Math.random() * 900 + 100);
  const A = await join(name); await pickClass(A, 10);
  cmd(`suld exp ${name} 2500`); cmd(`suld quest ${name} 5`); await sleep(1500);
  cmd(`effect give ${name} minecraft:resistance 600 4 true`);
  cmd('khuraldai cancel'); await sleep(2000);
  cmd('khuraldai force test_clear'); await sleep(2500);

  // the gate: the refusal names its coordinates
  cmd(`spreadplayers 200 0 1 10 false ${name}`); await sleep(5000);
  let from = A.chatLog.length;
  A.chat('/dungeon enter khasar_den'); await sleep(2500);
  const refusal = A.chatLog.slice(from).join(' | ');
  const xz = refusal.match(/хаалга дээр ирж орно: (-?\d+), (-?\d+)/);
  check('the gate is named when the party stands elsewhere', xz, refusal.slice(0, 200));
  if (xz) { cmd(`spreadplayers ${xz[1]} ${xz[2]} 1 3 false ${name}`); await sleep(6000); }

  let n = lines().length;
  from = A.chatLog.length;
  A.chat('/dungeon enter khasar_den'); await sleep(7000);
  const entered = A.chatLog.slice(from).join(' | ');
  check('the run starts at the gate', /Давалгаа 1\//.test(entered),
    entered.slice(0, 250));

  // two waves of wolves, then the boss
  for (let w = 0; w < 8; w++) { cmd('minecraft:kill @e[type=wolf,tag=suld_dungeon]'); await sleep(4000); }
  for (let b = 0; b < 4; b++) { cmd('minecraft:kill @e[type=ravager,tag=suld_dungeon]'); await sleep(4000); }
  await sleep(4000);
  const done = A.chatLog.slice(from).join(' | ');
  check('the dungeon completes with a reward', /EXP/.test(done) && /(цэвэрлэ|дуусла|ялалт|Ялалт)/i.test(done), done.slice(-300));

  const sfrom = A.chatLog.length;
  A.chat('/khuraldai status'); await sleep(2500);
  const status = A.chatLog.slice(sfrom).filter(l => l.includes('/60')).join(' | ');
  const m = status.match(/(\d+)\/60/);
  check('the clear counts toward the CLEAR_DUNGEON campaign', m && Number(m[1]) >= 1, status);
  // the first clear is written once per server: a repeat run finds it in the book (console /chronicle prints it)
  const c0 = lines().length;
  cmd('chronicle'); await sleep(2000);
  const book = lines().slice(c0).filter(l => l.includes('Саарлын Агуй')).join(' | ');
  check('the chronicle holds the first clear', /Саарлын Агуй»-ийг анх цэвэрлэв/.test(book), (grab(n, /\[chronicle\]/) || book).slice(0, 300));
  cmd('khuraldai cancel');
  A.quit();
  const failed = results.filter(r => !r.ok);
  console.log(JSON.stringify({ passed: results.length - failed.length, failed: failed.length, results }, null, 1));
  process.exit(failed.length ? 1 : 0);
})();
