// «Ангийн Мастер» (docs/CLASS_CHANGE.md): the class armour level is each class's mastery, 1–60.
//   node masterybot.js <srvctl.sh> [port]
// A level-60 Баатар goes 39 → 40 (a milestone line), 59 → 60 (a server-wide «Мастер» line), and after the five
// classes are at 60 the «Таван Ангийн Эзэн» line goes out.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'K' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = []; b.on('messagestr', m => chat.push(m)); b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
const CLASSES = ['baatar', 'mergen', 'boo', 'darkhan', 'khulegchin'];
b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd(`suld exp ${name} 90000000`); await sleep(1500);
  cmd(`classgear set ${name} level 39`); await sleep(800);
  let k = chat.length;
  for (let i = 0; i < 40 && !said(k, 'Ангийн Мастер 40 /'); i++) { cmd(`classgear set ${name} xp 40`); await sleep(250); }
  await sleep(800);
  check(said(k, 'Ангийн Мастер 40 / 60') && !said(k, 'Мастер боллоо'), 'passing 40 tells the player «Ангийн Мастер 40 / 60» (not the server)', chat.slice(k).filter(l => l.includes('Мастер')).slice(-2).join(' | ').slice(0, 160));
  k = chat.length; cmd(`classgear set ${name} xp 100000`); await sleep(1500);
  check(said(k, 'Мастер боллоо'), 'reaching 60 is announced to the server', chat.slice(k).filter(l => l.includes('Мастер')).join(' | ').slice(0, 160));
  // the other four: change class (the staff reset clears the wait), set 59 and push over
  let last = '';
  for (const c of CLASSES.slice(1)) {
    cmd(`suld classreset ${name}`); await sleep(400);
    cmd(`suld coins ${name} 20000`); await sleep(300);
    cmd(`suld classchange ${name} ${c}`); await sleep(1500);
    cmd(`classgear set ${name} level 59`); await sleep(500);
    k = chat.length; cmd(`classgear set ${name} xp 100000`); await sleep(1500);
    last = chat.slice(k).join(' | ');
  }
  check(last.includes('Таван Ангийн Эзэн'), 'the fifth mastered class gives «Таван Ангийн Эзэн»', last.slice(0, 200));
  b.quit();
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  process.exit(results.every(Boolean) ? 0 : 1);
});
