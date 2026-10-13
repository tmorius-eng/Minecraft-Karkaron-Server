// What comes due while a player trains (docs/TRAINING_GROUNDS.md «What comes due while training»), one offline bot. Dev server only,
// with the dev campaign campaign.test_deliver (DELIVER_ITEMS, 60 items, tiers 1/2/3) in plugins/SULD/content/campaigns.json:
//   node trainingpay.js <srvctl.sh> <logfile> [port]
// The bot delivers 5 of a stack (tier 3 of the test campaign), enters the yard, and the campaign is resolved while it trains:
// the reward waits («⏳ Аяны шагнал … дасгалаас гарахад олгоно»), nothing lands on the stand-in, and leaving pays it to
// the real character (the database coins grow by the paid amount, the tier is marked claimed). A trainee's blow never
// reaches a mob that is not a training dummy (a named husk with 2 hp lives through three swings), and the same bot's
// swing kills it once the training is over (so the blows do land outside training).
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'P' + Math.floor(Math.random() * 90000 + 10000);
const CAMPAIGN = 'Шалгалт: хүргэлт';

const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = [];
b.on('messagestr', m => chat.push(m));
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
const lastWith = (k, s) => chat.slice(k).filter(l => l.includes(s)).pop() || '';
const coins = () => Number(psql(`select currency from suld_profiles where name='${name}'`) || -1);
// the console's answer to «execute if entity …»: «Test passed, count: N» or «Test failed»
async function count(selector) {
  const n = lines().length;
  cmd(`execute if entity ${selector}`); await sleep(1200);
  const l = lines().slice(n).reverse().find(x => /Test (passed|failed)/.test(x)) || '';
  const m = l.match(/[Cc]ount: (\d+)/);
  return m ? Number(m[1]) : 0;
}
async function swingAt(tag, times) {
  for (let i = 0; i < times; i++) {
    const h = b.nearestEntity(e => e.name === 'husk' && e.position.distanceTo(b.entity.position) < 6);
    if (!h) return;
    await b.lookAt(h.position.offset(0, 1.2, 0), true);
    b.attack(h); await sleep(700);
  }
}
async function husk(tag) {
  // NoAI, named (the city's sweep keeps named mobs), a husk (never burns by day), 2 hp: any blow that lands kills it
  cmd(`execute at ${name} run summon minecraft:husk ^ ^ ^2 {NoAI:1b,Health:2f,PersistenceRequired:1b,CustomName:"${tag}",Tags:["${tag}"]}`);
  await sleep(1500);
}

b.once('spawn', async () => {
  try {
    await sleep(3500);
    for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
    if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
    if (b.currentWindow) b.closeWindow(b.currentWindow);
    cmd(`suld exp ${name} 30000`); await sleep(1500);

    // a week's campaign the bot takes part in: 5 items delivered (tier 3 of 1/2/3), the week still open (5/60)
    cmd('khuraldai cancel'); await sleep(2000);
    cmd('khuraldai force test_deliver'); await sleep(2500);
    cmd(`itemsadmin give ${name} item.chonon_arisan`); // one full stack (a material comes as its max stack, 64)
    await sleep(2500);
    let k = chat.length;
    b.chat('/khuraldai deliver 5'); await sleep(3000); // exactly 5: the week must stay open (5/60) until the bot trains
    check(said(k, 'хүргэв. Таны хувь: 5'), 'the bot delivers 5 items to the campaign', lastWith(k, 'хүргэв'));
    await sleep(12000); // the contributions reach the database (flush every 10 s)

    // into the yard: the real profile is saved right before the stand-in takes its place, so the database holds the
    // real coins from here on (a tutorial reward paid earlier is in it too)
    k = chat.length;
    b.chat('/dasgal baatar'); await sleep(4500);
    check(said(k, '— Дасгал: Баатар · 60-р түвшин · Build A —'), 'the bot trains as Баатар 60', lastWith(k, 'Дасгал'));
    const c0 = coins();

    // a trainee's blow reaches the training dummies only
    await husk('tpz');
    const before = await count('@e[tag=tpz]');
    await swingAt('tpz', 3);
    await sleep(800);
    const after = await count('@e[tag=tpz]');
    check(before === 1 && after === 1, 'a trainee cannot hurt a mob that is not a training dummy (the 2-hp husk lives)', `before ${before} after ${after}`);
    cmd('minecraft:kill @e[tag=tpz]'); await sleep(500);

    // the week resolves while the bot trains: the reward waits for the real character
    k = chat.length;
    const n = lines().length;
    cmd('khuraldai resolve'); await sleep(5000);
    const resolved = lines().slice(n).some(l => /test_deliver resolved (SUCCEEDED|FAILED)/.test(l));
    check(resolved && said(k, `⏳ Аяны шагнал «${CAMPAIGN}»: дасгалаас гарахад олгоно.`) && !said(k, `Аян «${CAMPAIGN}»:`),
      'resolved during training: the tier reward waits («⏳ …»), nothing paid to the stand-in', lastWith(k, '⏳') || chat.slice(k).join(' | ').slice(0, 160));
    const midway = coins();
    check(midway === c0, 'the database coins did not move during training', `${c0} -> ${midway}`);

    // leaving: paid to the real character, in the database, the tier marked claimed
    k = chat.length;
    b.chat('/dasgal leave'); await sleep(4000);
    const paid = lastWith(k, `Аян «${CAMPAIGN}»:`);
    const m = paid.match(/\+(\d+) зоос/);
    const c1 = coins();
    check(!!m && c1 === c0 + Number(m[1]), 'leaving pays the reward to the real character (database coins + the paid amount)',
      `${paid.slice(0, 140)} :: ${c0} -> ${c1}`);
    const row = psql(`select t.reward_tier, t.reward_claimed_at is not null from suld_campaign_totals t join suld_profiles p on p.player_uuid = t.player_uuid `
      + `where p.name='${name}' order by t.cycle_id desc limit 1`);
    check(row === '3|t', 'the tier is marked claimed (paid once: no second payout on the next join)', row);

    // the control: the same bot, no longer training, kills the same kind of husk with the same swings
    await husk('tpc');
    const ctrl0 = await count('@e[tag=tpc]');
    await swingAt('tpc', 3);
    await sleep(1500);
    const ctrl1 = await count('@e[tag=tpc]');
    check(ctrl0 === 1 && ctrl1 === 0, 'out of training the same swings kill it (the blows do land)', `before ${ctrl0} after ${ctrl1}`);
    cmd('minecraft:kill @e[tag=tpc]');
    cmd('khuraldai cancel');
  } catch (e) {
    check(false, 'no exception', String(e && e.stack || e).slice(0, 300));
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
