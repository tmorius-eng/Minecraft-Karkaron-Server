// The server list (plugin/branding/ServerListService, LiveMotd): the MOTD's second line follows what is happening.
//   node motdbot.js <srvctl.sh> [port]
// Pings the list: line 1 names SÜLD and line 2 is the counts line or a live line; then a world boss is forced from the
// console (`suldevent boss ikh_baavgai`) and the list says «босоод байна» within the 10 s cache; after `suldevent boss
// stop` that line is gone again. Dev server only (it raises and stops a world boss).
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
// a chat component (object with text/extra, or a string) to plain text
const plain = (d) => typeof d === 'string' ? d : !d ? '' : (d.text || '') + (d.extra || []).map(plain).join('');
async function motd() {
  const r = await mineflayer.ping({ host: '127.0.0.1', port: Number(port), version: '1.21.11' });
  return plain(r.description).split('\n');
}
async function until(pred, ms = 14000) { let l = []; for (let t = 0; t < ms; t += 2000) { l = await motd(); if (pred(l)) return l; await sleep(2000); } return l; }

(async () => {
  cmd('suldevent boss stop'); await sleep(1500); // a leftover from an earlier run
  let lines = await until(l => !/босоод байна/.test(l[1] || ''));
  check(/SÜLD/.test(lines[0] || ''), 'line 1 names the server', JSON.stringify(lines[0]));
  check(/бүлэг эрэл|⚔|✦/.test(lines[1] || '') && !/босоод байна/.test(lines[1] || ''), 'line 2 is the counts line or a live line, no boss standing', JSON.stringify(lines[1]));

  cmd('suldevent boss ikh_baavgai');
  lines = await until(l => /босоод байна/.test(l[1] || ''));
  check(/⚔ «.+» босоод байна/.test(lines[1] || ''), 'a standing world boss takes line 2 within the 10 s cache', JSON.stringify(lines[1]));
  check((lines[1] || '').length <= 60, 'the live line stays short enough for the list', (lines[1] || '').length + ' chars');

  cmd('suldevent boss stop');
  lines = await until(l => !/босоод байна/.test(l[1] || ''));
  check(!/босоод байна/.test(lines[1] || ''), 'after the boss is gone the line goes back', JSON.stringify(lines[1]));

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  process.exit(results.every(Boolean) ? 0 : 1);
})().catch(e => { console.log('FAIL the bot itself :: ' + e.message); process.exit(1); });
