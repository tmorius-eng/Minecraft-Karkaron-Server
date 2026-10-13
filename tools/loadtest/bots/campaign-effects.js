// The campaign paths no other script drives (docs/KHURALDAI.md §9): kill contributions, the PROP and NPC world
// effects, and the plaza boards. Dev server only, with the dev campaigns campaign.test_kill (KILL_MOBS, wolves),
// campaign.test_prop and campaign.test_npc (DELIVER_ITEMS, 60 items) in plugins/SULD/content/campaigns.json:
//   node campaign-effects.js <srv2ctl.sh> <logfile> [port]
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
const logged = (from, text) => lines().slice(from).some(l => l.includes(text));
const grab = (from, re) => lines().slice(from).filter(l => re.test(l)).join(' | ').slice(0, 300);

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
const heard = (bot, text, from = 0) => bot.chatLog.slice(from).some(l => l.includes(text));
async function deliverCampaign(A, name, id, effectLog) {
  cmd('khuraldai cancel'); await sleep(2000);
  let n = lines().length;
  cmd(`khuraldai force ${id}`); await sleep(2500);
  for (let i = 0; i < 60; i++) cmd(`itemsadmin give ${name} item.chonon_arisan`);
  await sleep(4500);
  A.chat('/khuraldai deliver'); await sleep(6000);
  check(`${id} succeeds`, logged(n, `${id} resolved SUCCEEDED`), grab(n, /\[campaign\]/));
  // the first success places it ([props]/[npc] dynamic); a repeat run finds the flag live and only records it again
  check(`${id} applies its world effect`, logged(n, `campaign.effect_applied actor=server target=effect:campaign.${id}`)
      && (logged(n, effectLog) || logged(0, effectLog)), grab(n, /\[(props|npc|events)\]|effect_applied/));
}

(async () => {
  const name = 'E' + Math.floor(Math.random() * 900 + 100);
  const A = await join(name); await pickClass(A, 10);
  cmd(`suld exp ${name} 30000`); await sleep(1500);

  // ---------------------------------------------------------------- plaza boards
  cmd('execute if entity @e[type=text_display,tag=suld_plaza_board]'); await sleep(1500);
  const boards = lines().slice(-40).reverse().find(l => /Test (passed|failed)/.test(l)) || '';
  check('both plaza boards stand', /count: 2/i.test(boards), boards.trim());

  // ---------------------------------------------------------------- kill contributions
  cmd('khuraldai cancel'); await sleep(2000);
  cmd('khuraldai force test_kill'); await sleep(2500);
  cmd(`spreadplayers 320 0 1 12 false ${name}`); await sleep(5000);
  cmd(`effect give ${name} minecraft:resistance 120 4 true`);
  cmd(`suld spawnmob ${name} mob.goviin_chono 3`); await sleep(1500);
  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    const wolf = A.nearestEntity(e => e.name === 'wolf' && e.position.distanceTo(A.entity.position) < 16);
    if (!wolf) break;
    await A.lookAt(wolf.position.offset(0, 0.5, 0), true);
    if (wolf.position.distanceTo(A.entity.position) > 3) { A.setControlState('forward', true); await sleep(250); A.setControlState('forward', false); }
    A.attack(wolf); await sleep(650);
  }
  const from = A.chatLog.length;
  A.chat('/khuraldai status'); await sleep(2500);
  const status = A.chatLog.slice(from).filter(l => l.includes('/60')).join(' | ');
  const m = status.match(/(\d+)\/60/);
  check('wolf kills count toward the campaign', m && Number(m[1]) >= 3, status);

  // ---------------------------------------------------------------- world effects
  await deliverCampaign(A, name, 'test_prop', '[props] dynamic stone_man');
  await deliverCampaign(A, name, 'test_npc', '[npc] dynamic merchant.caravan');
  cmd('khuraldai cancel');
  A.quit();
  const failed = results.filter(r => !r.ok);
  console.log(JSON.stringify({ passed: results.length - failed.length, failed: failed.length, results }, null, 1));
  process.exit(failed.length ? 1 : 0);
})();
