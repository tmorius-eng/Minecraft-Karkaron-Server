// The referral loop (docs/REFERRAL.md, plugin/social/ReferralService): a newcomer names the friend who brought them with
// /urih <name>; when the newcomer reaches the reward level both are paid, the inviter at once when online, else at the
// next login. Dev server, offline dev flag, PostgreSQL (SULD_DB, default suldit) for the coins:
//   node referralbot.js <srvctl.sh> [port]
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const coins = (n) => Number(psql(`select currency from suld_profiles where name='${n}'`) || 0);
const row = (n) => psql(`select (r.rewarded_at is not null)::int || ' ' || (r.inviter_paid_at is not null)::int from suld_referrals r join suld_profiles p on p.player_uuid = r.invitee_uuid where p.name='${n}'`);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const tag = Math.floor(Math.random() * 90000 + 10000);
const A = 'RA' + tag, B = 'RB' + tag, C = 'RC' + tag, D = 'RD' + tag, E = 'RE' + tag;

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
      if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
      if (b.currentWindow) b.closeWindow(b.currentWindow);
      await sleep(1000);
      res(b);
    });
  });
}
const said = (b, s) => b.chatLog.some(l => l.includes(s));
async function saidSoon(b, s, ms = 8000) { for (let t = 0; t < ms; t += 250) { if (said(b, s)) return true; await sleep(250); } return said(b, s); }
async function say(b, c) { b.chatLog.length = 0; b.chat(c); await sleep(1500); }

(async () => {
  const a = await join(A), bB = await join(B);
  await say(a, '/urih ' + A);
  check(said(a, 'Өөрийгөө урьж болохгүй'), 'naming yourself is refused', a.chatLog.slice(-1).join('').slice(0, 80));
  await say(a, '/urih Nobody' + tag);
  check(await saidSoon(a, 'олдсонгүй'), 'a name nobody plays is refused', a.chatLog.slice(-1).join('').slice(0, 80));
  await say(a, '/urih ' + B);
  const named = await saidSoon(a, B + ' таныг урьсан гэж бүртгэлээ');
  check(named && await saidSoon(bB, A + ' таныг урьсан гэж нэрлэлээ', 3000), 'a newcomer names the friend; both are told', a.chatLog.slice(-1).join('').slice(0, 100));
  await say(a, '/urih ' + B);
  check(await saidSoon(a, 'аль хэдийн нэрлэсэн'), 'the first name stands', a.chatLog.slice(-1).join('').slice(0, 80));
  await say(a, '/urih');
  check(await saidSoon(a, 'Таныг урьсан: ' + B), '/urih alone shows the inviter', a.chatLog.filter(l => l.includes('урьсан')).join(' | ').slice(0, 120));

  const c = await join(C);
  cmd(`suld exp ${C} 200000`); await sleep(2500);
  await say(c, '/urih ' + B);
  check(await saidSoon(c, 'түвшин хүртэл л нэрлэж болно'), 'a player past level 5 may not name an inviter', c.chatLog.slice(-1).join('').slice(0, 90));
  c.quit();

  const a0 = coins(A), b0 = coins(B);
  cmd(`suld exp ${A} 200000`);
  const paidA = await saidSoon(a, 'Урилгын шагнал: +100 зоос', 40000);
  const paidB = await saidSoon(bB, 'Таны урьсан ' + A, 10000);
  await sleep(3000);
  check(paidA && paidB, 'at the reward level both sides are told within the 20 s check', 'invitee ' + paidA + ', inviter ' + paidB);
  check(coins(A) === a0 + 100 && coins(B) === b0 + 200, 'the coins reached the database (100 / 200)', `A ${a0} -> ${coins(A)}, B ${b0} -> ${coins(B)}`);
  check(row(A) === '1 1', 'the row says both payments were made', 'rewarded,inviterPaid = ' + row(A));
  cmd(`suld exp ${A} 200000`); await sleep(25000);
  check(coins(A) === a0 + 100 && coins(B) === b0 + 200, 'a later level-up pays nothing again', `A ${coins(A)}, B ${coins(B)}`);

  // the inviter offline when the invitee reaches the level: paid at the next login
  const d = await join(D), e = await join(E);
  await say(d, '/urih ' + E);
  check(await saidSoon(d, E + ' таныг урьсан гэж бүртгэлээ'), 'a second pair is recorded', d.chatLog.slice(-1).join('').slice(0, 80));
  e.quit(); await sleep(3000);
  const e0 = coins(E);
  cmd(`suld exp ${D} 200000`);
  check(await saidSoon(d, 'Урилгын шагнал: +100 зоос', 40000) && row(D) === '1 0', 'the invitee is paid while the inviter is away; the inviter is owed', 'row ' + row(D));
  const e2 = await join(E);
  const owed = await saidSoon(e2, 'Таны урьсан ' + D, 12000);
  await sleep(3000);
  check(owed && coins(E) === e0 + 200 && row(D) === '1 1', 'the inviter is paid at the next login', `owed ${owed}, E ${e0} -> ${coins(E)}, row ${row(D)}`);

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  a.quit(); bB.quit(); d.quit(); e2.quit();
  process.exit(results.every(Boolean) ? 0 : 1);
})().catch(e => { console.log('FAIL the bot itself :: ' + e.message); process.exit(1); });
