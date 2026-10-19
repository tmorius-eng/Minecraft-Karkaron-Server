#!/usr/bin/env python3
"""The first-five-minutes funnel, D1/D7 retention and the session summary from suld_analytics_events / suld_sessions
(docs/ANALYTICS.md) as Markdown, so the weekly review of the alpha is one command:

    PGHOST=127.0.0.1 PGDATABASE=suld PGUSER=suld PGPASSWORD=… tools/analytics/funnel.py --days 7
    … tools/analytics/funnel.py --days 7 --out reports/funnel.md --webhook

The database is read with the psql CLI (no Python driver) through psql's own PG* environment variables, so the
password is never on a command line (--url postgresql://… still works, but `ps` shows it). --out writes the report to
a file and prints the short summary instead; --webhook posts that summary to REPORT_WEBHOOK_URL (else
ALERT_WEBHOOK_URL; both blank = nothing is posted, exit 0). deploy/funnel-report.sh runs it every Monday
(suld-funnel.timer).

Funnel: distinct players per step since --days ago (a step counts once per player). Retention: of the players whose
first_login fell in a day, the share seen again (session_start) 1 and 7 days later. The summary names the three
largest drops between consecutive steps and marks the alpha's exit numbers (docs/ANALYTICS.md): first login → first
fight ≥ 70 %, active_5m ≥ 50 %, D1 ≥ 40 % over the cohorts whose day-1 window has closed (three days old).
"""
import argparse
import csv
import datetime as dt
import io
import json
import os
import subprocess
import sys
import urllib.request

STEPS = [
    ("join_attempt", "Нэвтрэх оролдлого"),
    ("first_login", "Анх нэвтэрсэн (профайл үүссэн)"),
    ("resource_pack_status", "Дүрс багцад хариулсан"),
    ("class_selected", "Анги сонгосон"),
    ("tutorial_step", "Зааврын эхний алхам"),
    ("quest_complete", "Эхний бүлэг дууссан"),
    ("first_mob_kill", "Эхний тулаан (устгал)"),
    ("first_item", "Эхний олз"),
    ("active_5m", "5 минут идэвхтэй тоглосон"),
    ("retention_15m", "15 минут идэвхтэй"),
    ("first_boss", "Эхний агуй цэвэрлэсэн"),
    ("vote_cast", "Хуралдайд санал өгсөн"),
    ("campaign_contribution", "Аянд шат хүрсэн"),
]
LABEL = dict(STEPS)
# the onboarding sequence the drops are read over: from the first login to the 5th active minute (join_attempt counts
# returning players too, and the steps after active_5m are later milestones, not the first five minutes)
ONBOARDING = [k for k, _ in STEPS][1:[k for k, _ in STEPS].index("active_5m") + 1]
# the alpha's exit numbers (docs/ANALYTICS.md, 09-public-launch-and-growth.md §3.3)
EXIT = [("first_mob_kill", 70.0, "анх нэвтэрсэн → эхний тулаан"), ("active_5m", 50.0, "5 минут идэвхтэй"), ("d1", 40.0, "D1")]
URL = None  # --url; None = psql's PG* environment
DAY_MS = 86_400_000


def psql(sql):
    cmd = ["psql"] + ([URL] if URL else []) + ["-X", "-A", "--csv", "-c", sql]
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("psql failed: " + out.stderr.strip())
    return list(csv.DictReader(io.StringIO(out.stdout)))


def since(days):
    return f"(extract(epoch from now()) * 1000 - {days} * {DAY_MS})"


def funnel_rows(days):
    rows = psql(f"""
        SELECT type, COUNT(DISTINCT player_uuid) AS players
        FROM suld_analytics_events
        WHERE at >= {since(days)} AND player_uuid IS NOT NULL
        GROUP BY type""")
    return {r["type"]: int(r["players"]) for r in rows}


def base_of(by):
    return by.get("first_login", 0) or by.get("join_attempt", 0) or 1


def pct(by, key):
    return 100.0 * by.get(key, 0) / base_of(by)


def funnel_table(by, days):
    out = [f"## Эхний 5 минутын funnel (сүүлийн {days} хоног, тоглогчоор)", "", "| Алхам | Тоглогч | Анх нэвтэрснээс % |", "|---|---:|---:|"]
    for key, label in STEPS:
        out.append(f"| {label} (`{key}`) | {by.get(key, 0)} | {pct(by, key):.0f} % |")
    return "\n".join(out) + "\n"


def retention_rows(days):
    return psql(f"""
        WITH firsts AS (
            SELECT player_uuid, MIN(at) AS first_at FROM suld_analytics_events
            WHERE type = 'first_login' AND at >= {since(days)} GROUP BY player_uuid),
        seen AS (
            SELECT f.player_uuid, f.first_at,
                   BOOL_OR(e.at BETWEEN f.first_at + {DAY_MS} AND f.first_at + 2 * {DAY_MS}) AS d1,
                   BOOL_OR(e.at BETWEEN f.first_at + 7 * {DAY_MS} AND f.first_at + 8 * {DAY_MS}) AS d7
            FROM firsts f LEFT JOIN suld_analytics_events e
              ON e.player_uuid = f.player_uuid AND e.type = 'session_start'
            GROUP BY f.player_uuid, f.first_at)
        SELECT to_timestamp(first_at / 1000)::date AS day, COUNT(*) AS new_players,
               SUM(CASE WHEN d1 THEN 1 ELSE 0 END) AS d1, SUM(CASE WHEN d7 THEN 1 ELSE 0 END) AS d7
        FROM seen GROUP BY 1 ORDER BY 1""")


def retention_table(rows, days):
    out = [f"## D1 / D7 retention (cohort = анх нэвтэрсэн өдөр, сүүлийн {days} хоног)", "", "| Өдөр | Шинэ тоглогч | D1 | D7 |", "|---|---:|---:|---:|"]
    for r in rows:
        n = int(r["new_players"]) or 1
        out.append(f"| {r['day']} | {r['new_players']} | {int(r['d1'])} ({100 * int(r['d1']) / n:.0f} %) | {int(r['d7'])} ({100 * int(r['d7']) / n:.0f} %) |")
    return "\n".join(out) + "\n"


def d1_rate(rows, today):
    """D1 over the cohorts whose day-1 window has closed: a first login on day D is seen again between D+1 and D+2, so
    the cohort counts once today is past D+2 (D at least three days ago)."""
    seen = new = 0
    for r in rows:
        if dt.date.fromisoformat(r["day"]) <= today - dt.timedelta(days=3):
            seen += int(r["d1"])
            new += int(r["new_players"])
    return (100.0 * seen / new) if new else None


def sessions_row(days):
    rows = psql(f"""
        SELECT COUNT(*) AS sessions, COUNT(DISTINCT player_uuid) AS players,
               ROUND(AVG(duration_s) / 60) AS avg_min, ROUND(AVG(active_min)) AS avg_active_min,
               SUM(deaths) AS deaths, SUM(mobs) AS mobs
        FROM suld_sessions WHERE ended_at >= {since(days)}""")
    return rows[0] if rows else {}


def sessions_table(r, days):
    return (f"## Session-ууд (сүүлийн {days} хоног)\n\n| Session | Тоглогч | Дундаж минут | Дундаж идэвхтэй минут | Үхэл | Устгал |\n"
            "|---:|---:|---:|---:|---:|---:|\n"
            f"| {r.get('sessions', 0)} | {r.get('players', 0)} | {r.get('avg_min') or 0} | {r.get('avg_active_min') or 0} | {r.get('deaths') or 0} | {r.get('mobs') or 0} |\n")


def drops(by, n=3):
    """The n largest drops between consecutive onboarding steps, as (from, to, percentage points)."""
    out = []
    for a, b in zip(ONBOARDING, ONBOARDING[1:]):
        d = pct(by, a) - pct(by, b)
        if d > 0:
            out.append((a, b, d))
    out.sort(key=lambda t: -t[2])
    return out[:n]


def summary(by, ret_rows, sess, days, today):
    """The short text for the webhook: the numbers that decide the week (docs/ANALYTICS.md «Weekly report»)."""
    base = by.get("first_login", 0)
    d1 = d1_rate(ret_rows, today)
    lines = [f"SÜLD funnel · сүүлийн {days} хоног ({today.isoformat()})",
             f"Шинэ тоглогч {base} · анги сонгосон {pct(by, 'class_selected'):.0f} % · эхний тулаан {pct(by, 'first_mob_kill'):.0f} % · 5 мин идэвхтэй {pct(by, 'active_5m'):.0f} %",
             f"D1 {'—' if d1 is None else f'{d1:.0f} %'} (3+ хоногийн өмнөх cohort) · session {sess.get('sessions', 0)}, тоглогч {sess.get('players', 0)}, дундаж идэвхтэй {sess.get('avg_active_min') or 0} мин"]
    top = drops(by)
    if top:
        lines.append("Хамгийн их алдагдал: " + " · ".join(f"{LABEL[a]} → {LABEL[b]} −{d:.0f} пункт" for a, b, d in top))
    marks = []
    for key, need, label in EXIT:
        have = d1 if key == "d1" else pct(by, key)
        marks.append(("—" if have is None else ("✔" if have >= need else "✘")) + f" {label} {'—' if have is None else f'{have:.0f} %'} (босго {need:.0f} %)")
    lines.append("Альфагийн босго: " + " · ".join(marks))
    return "\n".join(lines)


def post(text):
    """Post the summary to REPORT_WEBHOOK_URL (else ALERT_WEBHOOK_URL). Blank = no-op; the URL is never printed."""
    url = os.environ.get("REPORT_WEBHOOK_URL") or os.environ.get("ALERT_WEBHOOK_URL") or ""
    if not url:
        return False
    body = json.dumps({"content": text[:1900]}).encode("utf-8")
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10):
        pass
    return True


def report(days, today):
    by = funnel_rows(days)
    ret_rows = retention_rows(days)
    sess = sessions_row(days)
    md = funnel_table(by, days) + "\n" + retention_table(ret_rows, days) + "\n" + sessions_table(sess, days)
    return md, summary(by, ret_rows, sess, days, today)


def main():
    global URL
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", help="postgresql://user:pass@host:port/db; prefer the PG* environment (the password stays off the command line)")
    ap.add_argument("--days", type=int, default=7)
    ap.add_argument("--out", help="write the Markdown report here and print only the summary")
    ap.add_argument("--webhook", action="store_true", help="post the summary to REPORT_WEBHOOK_URL (else ALERT_WEBHOOK_URL)")
    a = ap.parse_args()
    URL = a.url
    md, text = report(a.days, dt.datetime.now(dt.timezone.utc).date())
    if a.out:
        with open(a.out, "w", encoding="utf-8") as f:
            f.write(md)
        print(text)
    else:
        print(md)
        print(text)
    if a.webhook:
        try:
            posted = post(text)
        except Exception as e:  # noqa: BLE001 - the reason matters, the URL must not
            sys.exit("the webhook did not accept the report: " + e.__class__.__name__)
        print("summary posted to the webhook" if posted else "no REPORT_WEBHOOK_URL / ALERT_WEBHOOK_URL: summary not posted", file=sys.stderr)


if __name__ == "__main__":
    main()
