// The hybrid login (docs/AUTHENTICATION.md, AUTH_MODE=hybrid) on the dev pair from tools/loadtest/hybrid-dev.sh:
//   node authbot.js <proxy port> <backend port> <backend latest.log>
// Velocity (offline, modern forwarding) in front of Paper on loopback, AuthMe on both, SÜLD's gate in between. AuthMe's
// pre-join dialog arrives in the configuration phase; the bot answers it like a 1.21.11 client, with a serverbound
// custom_click_action carrying the dialog's inputs (docs/qa/2026-10-18-authme-spike.md, S7). Checks:
//   a new name registers in the dialog and only then enters SÜLD's game (welcome after AuthMe's login line, join line);
//   the same name logs in again; a wrong password is kicked and SÜLD's join never runs; a cancelled register dialog
//   leaves the player waiting in the world with SÜLD shut, and /register by chat lets them in; a waiting player's
//   /l <password> is AuthMe's login, not SÜLD's local chat; a lower-case twin is refused; /premium after login only
//   shows SÜLD's warning (it runs as /premium confirm); the backend refuses a direct connection; the backend log names
//   HYBRID_AUTHME and audits the pending and the login; `suld auth free <name>` on the console prints the staff steps.
const mineflayer = require('mineflayer');
const nbt = require('prismarine-nbt');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const [proxyPort = '25580', backendPort = '25581', logPath] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const log = () => (logPath ? fs.readFileSync(logPath, 'utf8') : '');
const count = (s) => log().split(s).length - 1;

const varint = (n) => { const out = []; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; out.push(b); } while (n); return Buffer.from(out); };
const str = (s) => { const b = Buffer.from(s, 'utf8'); const l = Buffer.alloc(2); l.writeUInt16BE(b.length); return Buffer.concat([l, b]); };
// serverbound custom_click_action (configuration 0x08): the action id, then a length-prefixed network NBT compound
function click(id, fields) {
  const parts = [Buffer.from([0x0a])];
  for (const [k, v] of Object.entries(fields)) parts.push(Buffer.from([0x08]), str(k), str(v));
  parts.push(Buffer.from([0x00]));
  const tag = Buffer.concat(parts); const n = Buffer.from(id, 'utf8');
  return Buffer.concat([varint(0x08), varint(n.length), n, varint(tag.length), tag]);
}

// one connection: answer (or cancel) the pre-join dialog, type `cmds` once in the world, collect what comes back
function visit(port, name, answer, cmds = [], stay = 5000) {
  return new Promise((resolve) => {
    const out = { chat: [], kick: '', spawned: false, dialog: '' };
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    let answered = false;
    const done = () => { clearTimeout(timer); resolve(out); };
    b._client.on('packet', (d, meta) => {
      if (meta.name === 'show_dialog' && meta.state === 'configuration' && !answered) {
        answered = true;
        const dlg = nbt.simplify(d.dialog);
        out.dialog = dlg.title && dlg.title.text;
        const act = dlg.actions[answer === null ? 1 : 0].action.id;
        const fields = {};
        for (const i of dlg.inputs || []) fields[i.key] = answer || '';
        setTimeout(() => b._client.writeRaw(click(act, fields)), 300);
      }
      if (meta.name === 'kick_disconnect' || (meta.name === 'disconnect' && meta.state !== 'play')) out.kick += JSON.stringify(d);
    });
    b.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
    b.on('error', () => {});
    b.on('end', done);
    b.once('spawn', async () => {
      out.spawned = true;
      for (const c of cmds) { await sleep(2000); b.chat(c); }
      await sleep(stay);
      b.quit();
    });
    const timer = setTimeout(() => { try { b.quit(); } catch (e) {} resolve(out); }, 50000);
  });
}
// AuthMe speaks Mongolian with deploy/authme/messages_mn.yml installed, English without it
const has = (o, s) => o.chat.some(m => (s instanceof RegExp ? s.test(m) : m.includes(s)));
const index = (o, s) => o.chat.findIndex(m => (s instanceof RegExp ? s.test(m) : m.includes(s)));
const REGISTERED = /Successfully registered|Бүртгэл амжилттай/;
const WRONG = /Wrong password|Нууц үг буруу/;
const TWIN = /You should join using username|нэрээр орно уу/;
const UNREGISTERED = /isn't registered|not registered|бүртгэлгүй/;
const REGISTER = /^(Register|Бүртгүүлэх)$/, LOGIN = /^(Login|Нэвтрэх)$/;
const WELCOME = 'Тавтай морил';
// the pair's directory is three levels over the backend log (<dir>/server/logs/latest.log): hybrid-dev.sh <dir> b <cmd>
const pairDir = logPath ? path.resolve(logPath, '..', '..', '..') : null;
const console_ = (c) => execFileSync(path.join(__dirname, '..', 'hybrid-dev.sh'), [pairDir, 'b', c]);
const tag = String(Math.floor(Math.random() * 90000 + 10000));

(async () => {
  check(/Authentication: HYBRID_AUTHME/.test(log()) && !/AuthMe cannot be reached/.test(log()), 'SÜLD runs HYBRID_AUTHME with AuthMe connected',
    logPath ? 'log line found: ' + /Authentication: HYBRID_AUTHME/.test(log()) : 'no log path');
  const name = 'A' + tag, pw = 'pw-' + tag;
  const logins0 = count('auth.authme_login');

  let o = await visit(proxyPort, name, pw);
  const ok = index(o, REGISTERED), w = index(o, WELCOME);
  check(REGISTER.test(o.dialog) && ok >= 0 && w > ok, 'a new name registers in the pre-join dialog and only then enters SÜLD',
    'dialog ' + o.dialog + ', registered at ' + ok + ', welcome at ' + w);
  check(has(o, name + ' ирлээ'), 'the join line is announced after the login', o.chat.filter(m => m.includes('ирлээ')).join(' | ') || 'none');
  await sleep(4000);

  o = await visit(proxyPort, name, pw);
  check(LOGIN.test(o.dialog) && has(o, WELCOME), 'the same name logs in again with its password', 'dialog ' + o.dialog + ', welcome ' + has(o, WELCOME));
  await sleep(4000);

  const before = count('auth.authme_login');
  o = await visit(proxyPort, name, 'wrong-' + tag);
  await sleep(1000);
  check(WRONG.test(o.kick) && !has(o, WELCOME) && count('auth.authme_login') === before && !has(o, name + ' ирлээ'),
    'a wrong password is kicked and SÜLD never lets the connection in', 'kick ' + WRONG.test(o.kick) + ', welcome '
    + has(o, WELCOME) + ', new login audits ' + (count('auth.authme_login') - before));
  await sleep(4000);

  // a cancelled LOGIN dialog is a kick («Та нэвтрэлтийг цуцаллаа»), so the only player who waits in the world is an
  // unregistered one: their /l <password> must reach AuthMe (its answer: not registered), never SÜLD's local chat
  const late = 'B' + tag;
  o = await visit(proxyPort, late, null, ['/menu', '/l pw-' + late, '/register pw-' + late + ' pw-' + late], 4000);
  const reg = index(o, REGISTERED), w2 = index(o, WELCOME), unreg = index(o, UNREGISTERED);
  check(o.spawned && reg >= 0 && w2 > reg && o.chat.slice(0, Math.max(reg, 0)).every(m => !m.includes(WELCOME)),
    'a cancelled register dialog leaves the player waiting; /register by chat lets them in', 'registered at ' + reg + ', welcome at ' + w2);
  check(unreg >= 0 && unreg < reg, 'a waiting player\'s /l <password> is AuthMe\'s login (SÜLD\'s /l chat alias steps aside)',
    'AuthMe\'s not-registered answer at ' + unreg + ', registered at ' + reg);
  check(count('auth.authme_pending') > 0, 'the wait is audited (auth.authme_pending)', count('auth.authme_pending') + ' rows');
  await sleep(4000);

  o = await visit(proxyPort, name.toLowerCase(), pw);
  check(!o.spawned && TWIN.test(o.kick), 'a lower-case twin of a registered name is refused', o.kick.slice(0, 160));
  await sleep(4000);

  o = await visit(proxyPort, name, pw, ['/premium'], 3000);
  check(has(o, '/premium confirm') && !/Premium verification requested/.test(o.kick), '/premium only warns until /premium confirm',
    'warning ' + has(o, '/premium confirm') + ', kicked for verification ' + /Premium verification requested/.test(o.kick));
  await sleep(4000);

  if (pairDir) {
    console_('suld auth free ' + name); await sleep(3000);
    check(count('authme unregister ' + name) > 0 && count('БҮРТГЭЛТЭЙ') > 0, 'suld auth free <name> prints the staff steps for a registered name',
      'unregister line ' + count('authme unregister ' + name) + ', registered ' + count('БҮРТГЭЛТЭЙ'));
  }

  o = await visit(backendPort, 'C' + tag, pw);
  check(!o.spawned && /connect with Velocity/.test(o.kick), 'the backend refuses a direct connection', o.kick.slice(0, 120));
  check(count('auth.authme_login') - logins0 >= 3, 'every entry is audited (auth.authme_login)', (count('auth.authme_login') - logins0) + ' new rows');

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  process.exit(results.every(Boolean) ? 0 : 1);
})();
