// Live check of the Naadam archery: field placement, scoring from behind the line, the 20-arrow quiver.
//   node archbot.js <ctl.sh> <logfile> <port>
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const fs = require('fs'); const { execFileSync } = require('child_process');
const [ctl, logfile, port] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = []; const check = (n, ok, d) => { results.push({ n, ok: !!ok }); console.error((ok ? 'PASS ' : 'FAIL ') + n + (d ? ' :: ' + d : '')); };
const name = 'Ar' + Math.floor(Math.random() * 900 + 100);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const log = []; bot.on('messagestr', (m) => log.push(m)); bot._client.on('system_chat', (pk) => { if (pk.isActionBar) log.push('[AB] ' + JSON.stringify(pk.content)); });
bot._client.on('action_bar', (pk) => log.push('[AB] ' + JSON.stringify(pk.text)));
bot.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 10 && !bot.currentWindow; i++) await sleep(500);
  if (bot.currentWindow) { await bot.clickWindow(12, 0, 0).catch(() => {}); await sleep(1200); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd(`minecraft:tp ${name} -16 90 30`); cmd(`effect give ${name} minecraft:slow_falling 20 0 true`); await sleep(5000);
  cmd('naadam start'); await sleep(2500);
  check('the archery opens', log.some(l => /Наадам эхэллээ/.test(l)));
  const tid = bot.registry.blocksByName.target.id;
  let targets = [];
  for (let i = 0; i < 10 && targets.length < 15; i++) { targets = bot.findBlocks({ matching: tid, maxDistance: 96, count: 40 }); await sleep(500); }
  check('15 сур placed', targets.length === 15, targets.length + ' targets');
  if (targets.length < 15) { bot.quit(); return; }
  const zs = [...new Set(targets.map(t => t.z))].sort((a, b) => a - b);
  const row20 = targets.filter(t => t.z === zs[0]).sort((a, b) => a.x - b.x);
  const mid = row20[2];
  const lineZ = zs[0] - 20, lineX = mid.x;
  cmd(`minecraft:tp ${name} ${lineX + 0.5} ${mid.y + 1} ${lineZ + 0.5}`); await sleep(2500);
  cmd(`minecraft:clear ${name}`);
  cmd(`minecraft:give ${name} minecraft:bow 1`); cmd(`minecraft:give ${name} minecraft:arrow 64`); await sleep(1500);
  const bow = bot.inventory.items().find(i => i.name === 'bow'); if (bow) await bot.equip(bow, 'hand');
  let hits = 0;
  const arrowsLeft = () => bot.inventory.items().filter(i => i.name === 'arrow').reduce((t, i) => t + i.count, 0);
  for (let k = 0; k < 34; k++) {
    // aim a little above the face so a full draw lands on it at 20 blocks
    const tgt = new Vec3(mid.x + 0.5, mid.y + 0.5 + 0.9, mid.z + 0.5);
    await bot.lookAt(tgt, true);
    bot.activateItem(); await sleep(1500); bot.deactivateItem(); await sleep(1100);
    const n = log.filter(l => /Сур! \+/.test(l)).length; if (n > hits) hits = n;
  }
  await sleep(1500);
  const left = bot.inventory.items().filter(i => i.name === 'arrow').reduce((t, i) => t + i.count, 0);
  console.error('arrows loosed:', 64 - left, ' action bars with Сур:', log.filter(l => l.startsWith('[AB]') && l.includes('Сур!')).length);
  const scored = log.filter(l => l.includes('Сур! +')).length;
  check('arrows on the сур score from behind the line', scored > 0, scored + ' scored');
  check('no more than 20 arrows score (quiver)', scored <= 20, scored + ' scored');
  check('the quiver end is announced', log.some(l => /сум дууслаа/.test(l)));
  cmd('naadam'); await sleep(800);
  console.error(fs.readFileSync(logfile, 'utf8').split('\n').filter(l => /Наадам · Сур харваа —|\d\. Ar/.test(l)).slice(-3).join('\n'));
  console.error(`${results.filter(r => r.ok).length}/${results.length} PASS`);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
