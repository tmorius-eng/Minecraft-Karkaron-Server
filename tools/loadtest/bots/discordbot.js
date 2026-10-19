// SÜLD -> Discord (plugin/social/DiscordNotifier, docs/DISCORD.md) against a local stand-in: the dev server's config.yml
// has discord.webhook and discord.staff-webhook = http://127.0.0.1:8089/suld. The bot listens there and checks that a
// player's /report reaches the staff channel with their name, place and text; that `suld discord test` from the console
// posts to both channels; that every post is JSON {"content"} under 2000 characters to that path; and that a report
// too short to be accepted posts nothing. Dev server, offline dev flag:
//   node discordbot.js <srvctl.sh> [port] [hookPort]
const mineflayer = require('mineflayer');
const http = require('http');
const { execFileSync } = require('child_process');
const [ctl, port = '25572', hookPort = '8089'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const tag = Math.floor(Math.random() * 90000 + 10000);
const name = 'D' + tag;
const posts = [];
const server = http.createServer((req, res) => {
  let body = '';
  req.on('data', d => { body += d; });
  req.on('end', () => { posts.push({ path: req.url, type: req.headers['content-type'] || '', body }); res.writeHead(204); res.end(); });
});
const content = (p) => { try { return JSON.parse(p.body).content; } catch (e) { return null; } };
const withText = (s) => posts.filter(p => (content(p) || '').includes(s));
async function waitFor(pred, ms = 8000) { for (let t = 0; t < ms; t += 250) { if (pred()) return true; await sleep(250); } return pred(); }

function join() {
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

(async () => {
  await new Promise((res, rej) => { server.on('error', rej); server.listen(Number(hookPort), '127.0.0.1', res); });
  const b = await join();

  b.chat('/report abc'); await sleep(4000);
  check(withText('⚑ Мэдэгдэл ' + name).length === 0 && b.chatLog.some(l => l.includes('Хэрэглээ: /report')),
    'a report too short to be accepted posts nothing', withText('⚑ Мэдэгдэл ' + name).length + ' posts, usage line ' + b.chatLog.some(l => l.includes('Хэрэглээ: /report')));

  const text = 'агуйд гацлаа тест ' + tag;
  b.chat('/report ' + text);
  const got = await waitFor(() => withText('⚑ Мэдэгдэл ' + name).length > 0);
  const report = withText('⚑ Мэдэгдэл ' + name)[0];
  check(got && (content(report) || '').includes(text) && /\(world -?\d+ -?\d+ -?\d+\)/.test(content(report) || ''),
    'a /report reaches the staff channel with the player, the place and the text', got ? (content(report) || '').slice(0, 120) : 'no post in 8 s');
  check(got && report.path === '/suld' && /application\/json/.test(report.type), 'the post is JSON to the configured path',
    got ? report.path + ' ' + report.type : 'no post');

  const before = withText('холболтын шалгалт').length;
  cmd('suld discord test');
  const both = await waitFor(() => withText('холболтын шалгалт').length >= before + 2);
  check(both, 'suld discord test from the console posts to the public and the staff channel', (withText('холболтын шалгалт').length - before) + ' posts');

  const bad = posts.filter(p => { const c = content(p); return c === null || c.length > 2000 || Object.keys(JSON.parse(p.body)).join() !== 'content'; });
  check(posts.length >= 3 && bad.length === 0, 'every post is {"content"} under 2000 characters', posts.length + ' posts, ' + bad.length + ' malformed');

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); server.close();
  process.exit(results.every(Boolean) ? 0 : 1);
})().catch(e => { console.log('FAIL the bot itself :: ' + e.message); process.exit(1); });
