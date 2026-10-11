#!/usr/bin/env python3
"""The first-five-minutes funnel and D1/D7 retention from suld_analytics_events / suld_sessions (docs/ANALYTICS.md).

Reads the database with the psql CLI (no Python driver needed) and prints Markdown tables, so the weekly review of
the closed alpha is one command:

    tools/analytics/funnel.py --url postgresql://suld:pass@127.0.0.1:5432/suld --days 7

Funnel: distinct players per step, counted since --days ago (a step counts once per player, on their first time).
Retention: of the players whose first_login fell in a day, the share seen again (session_start) 1 and 7 days later.
"""
import argparse
import csv
import io
import subprocess
import sys

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


def psql(url, sql):
    out = subprocess.run(["psql", url, "-X", "-A", "-F", ",", "--csv", "-c", sql], capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("psql failed: " + out.stderr.strip())
    return list(csv.DictReader(io.StringIO(out.stdout)))


def funnel(url, days):
    since = f"(extract(epoch from now()) * 1000 - {days} * 86400000)"
    rows = psql(url, f"""
        SELECT type, COUNT(DISTINCT player_uuid) AS players
        FROM suld_analytics_events
        WHERE at >= {since} AND player_uuid IS NOT NULL
        GROUP BY type""")
    by = {r["type"]: int(r["players"]) for r in rows}
    base = by.get("first_login", 0) or by.get("join_attempt", 0) or 1
    print(f"## Эхний 5 минутын funnel (сүүлийн {days} хоног, тоглогчоор)\n")
    print("| Алхам | Тоглогч | Анх нэвтэрснээс % |")
    print("|---|---:|---:|")
    for key, label in STEPS:
        n = by.get(key, 0)
        print(f"| {label} (`{key}`) | {n} | {100.0 * n / base:.0f} % |")
    print()


def retention(url, days):
    since = f"(extract(epoch from now()) * 1000 - {days} * 86400000)"
    rows = psql(url, f"""
        WITH firsts AS (
            SELECT player_uuid, MIN(at) AS first_at FROM suld_analytics_events
            WHERE type = 'first_login' AND at >= {since} GROUP BY player_uuid),
        seen AS (
            SELECT f.player_uuid, f.first_at,
                   BOOL_OR(e.at BETWEEN f.first_at + 86400000 AND f.first_at + 2 * 86400000) AS d1,
                   BOOL_OR(e.at BETWEEN f.first_at + 7 * 86400000 AND f.first_at + 8 * 86400000) AS d7
            FROM firsts f LEFT JOIN suld_analytics_events e
              ON e.player_uuid = f.player_uuid AND e.type = 'session_start'
            GROUP BY f.player_uuid, f.first_at)
        SELECT to_timestamp(first_at / 1000)::date AS day, COUNT(*) AS new_players,
               SUM(CASE WHEN d1 THEN 1 ELSE 0 END) AS d1, SUM(CASE WHEN d7 THEN 1 ELSE 0 END) AS d7
        FROM seen GROUP BY 1 ORDER BY 1""")
    print(f"## D1 / D7 retention (cohort = anх нэвтэрсэн өдөр, сүүлийн {days} хоног)\n")
    print("| Өдөр | Шинэ тоглогч | D1 | D7 |")
    print("|---|---:|---:|---:|")
    for r in rows:
        n = int(r["new_players"]) or 1
        print(f"| {r['day']} | {r['new_players']} | {int(r['d1'])} ({100 * int(r['d1']) / n:.0f} %) | {int(r['d7'])} ({100 * int(r['d7']) / n:.0f} %) |")
    print()


def sessions(url, days):
    rows = psql(url, f"""
        SELECT COUNT(*) AS sessions, COUNT(DISTINCT player_uuid) AS players,
               ROUND(AVG(duration_s) / 60) AS avg_min, ROUND(AVG(active_min)) AS avg_active_min,
               SUM(deaths) AS deaths, SUM(mobs) AS mobs
        FROM suld_sessions WHERE ended_at >= (extract(epoch from now()) * 1000 - {days} * 86400000)""")
    r = rows[0] if rows else {}
    print(f"## Session-ууд (сүүлийн {days} хоног)\n")
    print("| Session | Тоглогч | Дундаж минут | Дундаж идэвхтэй минут | Үхэл | Устгал |")
    print("|---:|---:|---:|---:|---:|---:|")
    print(f"| {r.get('sessions', 0)} | {r.get('players', 0)} | {r.get('avg_min') or 0} | {r.get('avg_active_min') or 0} | {r.get('deaths') or 0} | {r.get('mobs') or 0} |")
    print()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", required=True, help="postgresql://user:pass@host:port/db (the analytics.sink: database storage)")
    ap.add_argument("--days", type=int, default=7)
    a = ap.parse_args()
    funnel(a.url, a.days)
    retention(a.url, a.days)
    sessions(a.url, a.days)


if __name__ == "__main__":
    main()
