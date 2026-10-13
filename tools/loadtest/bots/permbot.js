// Staff commands refuse a plain player (audit 2026-10-13 I12). One offline, non-op bot on the dev server:
//   node permbot.js <srvctl.sh> <latest.log> [port]
// Each command must answer with a refusal (SÜLD's «Эрх алга», or Brigadier hiding a command the player lacks).
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'Q' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = [];
b.on('messagestr', m => chat.push(m));
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const REFUSED = /Эрх алга|эрх алга|permission|Unknown or incomplete command|Үл мэдэгдэх|I'm sorry|Дарханы|Дархан ахиулна/i;
const STAFF = [
  `/suld coins ${name} 100000`, `/suld exp ${name} 9000000`, `/suld content reload`, `/suld flush`, `/suld classreset ${name}`, `/suld endgame ${name} rank 3`, `/suld classchange ${name} boo`,
  `/classgear set ${name} level 60`, `/classgear upgrade`, `/khuraldai force campaign.chonyn_ayan`, `/khuraldai open`,
  `/suldevent start event.chonyn_dovtolgoo`, `/suldpack reload`, `/deathrevive ${name}`, `/deathstatus ${name}`,
  `/itemsadmin give ${name} weapon.talyn_ild rare`, `/skillsadmin inspect ${name}`, `/worldbuild status`, `/suldworld site uushig`,
];
b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  const ops = JSON.parse(fs.readFileSync(require('path').join(require('path').dirname(logfile), '..', 'ops.json'), 'utf8'));
  check(!ops.some(o => o.name === name), 'the bot is not an operator', ops.length + ' ops');
  for (const c of STAFF) {
    const k = chat.length; b.chat(c); await sleep(900);
    const said = chat.slice(k).join(' | ');
    check(REFUSED.test(said), 'refused: ' + c.split(' ').slice(0, 2).join(' '), said.slice(0, 110) || '(no reply)');
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
setTimeout(() => { console.log('FAIL timeout'); process.exit(2); }, 120000);
