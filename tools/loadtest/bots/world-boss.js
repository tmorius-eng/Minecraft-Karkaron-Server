// World boss smoke on the dev server (docs/WORLD_BOSSES.md), offline test bots:
//   node world-boss.js <srvctl.sh> <logfile> [port]
// An admin raises Хөвсгөлийн Их Баавгай; bot A deals all the damage, bot B only stands at the fight (the healer's
// case). Both must be rewarded, the kill must go into the chronicle, nothing may be left behind; then a second boss
// is raised and sent back with `boss stop` (no rewards, entity gone).
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const lines = () => fs.readFileSync(logfile, 'utf8').split('\n');
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const since = (n, re) => lines().slice(n).filter(l => re.test(l));

function join(name) {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.chatLog = [];
    b.on('messagestr', m => b.chatLog.push(m));
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) { // class pick, then the confirm page
        await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600);
        await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200);
      }
      res(b);
    });
  });
}

(async () => {
  const an = 'W' + Math.floor(Math.random() * 900 + 100), bn = 'H' + Math.floor(Math.random() * 900 + 100);
  const A = await join(an), B = await join(bn);
  cmd('suldevent boss stop'); await sleep(1000); // a leftover from an earlier run

  let n = lines().length;
  cmd('suldevent boss ikh_baavgai'); await sleep(3000);
  const rose = since(n, /\[worldboss\] worldboss\.ikh_baavgai rose at (-?\d+) (-?\d+) (-?\d+)/)[0];
  check(!!rose, 'the admin raises the boss', rose || lines().slice(n).join(' | ').slice(0, 300));
  if (!rose) { A.quit(); B.quit(); process.exit(1); }
  const [, x, , z] = rose.match(/rose at (-?\d+) (-?\d+) (-?\d+)/);
  check(A.chatLog.some(l => l.includes('бослоо')), 'the server is told', A.chatLog.filter(l => l.includes('бослоо')).slice(-1).join(''));

  cmd(`spreadplayers ${x} ${z} 1 8 false @a`); await sleep(4000);
  cmd('effect give @a minecraft:resistance 600 4 true'); // the bots only have to stand there
  const dist = Math.hypot(A.entity.position.x - Number(x), A.entity.position.z - Number(z));
  check(dist < 40, 'the fighters stand at the boss', 'A is ' + Math.round(dist) + ' blocks away');
  await sleep(20000); // presence for both

  n = lines().length;
  for (let i = 0; i < 60 && !since(n, /\[worldboss\] worldboss\.ikh_baavgai (slain|withdrew)/).length; i++) {
    cmd(`damage @e[tag=suld_world_boss,limit=1,sort=nearest] 100000 minecraft:magic by ${an}`);
    await sleep(700);
  }
  const end = since(n, /\[worldboss\] worldboss\.ikh_baavgai (slain|withdrew)/)[0] || '';
  check(/slain/.test(end), 'the boss falls', end);
  const m = end.match(/(\d+) fighters, (\d+) rewarded/);
  check(m && Number(m[2]) >= 2, 'both are rewarded: the damage dealer and the one who only stood there', end);
  await sleep(2000);
  check(A.chatLog.some(l => /⚔ #1 /.test(l)), 'the top fighter gets the podium reward', A.chatLog.filter(l => l.includes('⚔ #')).join(''));
  check(B.chatLog.some(l => /⚔ #2 /.test(l)), 'the healer gets the full reward', B.chatLog.filter(l => l.includes('⚔ #')).join(''));
  let chron = since(n, /\[chronicle\].*Их Баавгай/);
  if (!chron.length) { // a repeat run: the first kill is claimed already and the record stands, so the book has it
    const k = lines().length;
    cmd('chronicle'); await sleep(1500);
    chron = since(k, /Их Баавгай/);
  }
  check(chron.length > 0, 'the kill is in the chronicle', chron.join(' | ').slice(0, 200));

  n = lines().length;
  cmd('suldevent boss ikh_baavgai'); await sleep(3000);
  cmd('suldevent boss stop'); await sleep(2000);
  const withdrew = since(n, /\[worldboss\] worldboss\.ikh_baavgai withdrew \(admin\)/);
  check(withdrew.length === 1 && / 0 rewarded/.test(withdrew[0]), 'boss stop sends it back without rewards', withdrew.join(''));
  n = lines().length;
  cmd(`execute if entity @e[tag=suld_world_boss] run say WB_LEFT`); await sleep(1500);
  check(!since(n, /WB_LEFT/).length, 'nothing is left behind', '');

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  A.quit(); B.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
