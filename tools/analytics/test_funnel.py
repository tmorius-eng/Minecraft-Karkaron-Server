#!/usr/bin/env python3
"""Unit test of the weekly funnel report (tools/analytics/funnel.py) with psql and the webhook stubbed out.

    python3 -I tools/analytics/test_funnel.py

Rules under test: every step is a row with its share of first logins; the summary names the largest drops and marks
the alpha's exit numbers; D1 counts only cohorts whose day-1 window has closed; a blank webhook posts nothing; psql is called
without a connection string when --url is not given (the password rides in PGPASSWORD, never in argv).
"""
import datetime as dt
import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import funnel  # noqa: E402

TODAY = dt.date(2026, 10, 19)
BY = {"join_attempt": 120, "first_login": 100, "resource_pack_status": 95, "class_selected": 80, "tutorial_step": 78,
      "quest_complete": 40, "first_mob_kill": 72, "first_item": 70, "active_5m": 45}
RET = [{"day": "2026-10-10", "new_players": "10", "d1": "5", "d7": "2"},
       {"day": "2026-10-17", "new_players": "20", "d1": "4", "d7": "0"},
       {"day": "2026-10-18", "new_players": "30", "d1": "0", "d7": "0"}]
SESS = {"sessions": "300", "players": "90", "avg_min": "40", "avg_active_min": "25", "deaths": "12", "mobs": "900"}


class FunnelTest(unittest.TestCase):
    def test_every_step_is_a_row_with_its_share_of_first_logins(self):
        md = funnel.funnel_table(BY, 7)
        for key, label in funnel.STEPS:
            self.assertIn(f"| {label} (`{key}`) |", md)
        self.assertIn("| Анги сонгосон (`class_selected`) | 80 | 80 % |", md)
        self.assertIn("| Эхний агуй цэвэрлэсэн (`first_boss`) | 0 | 0 % |", md, "a step nobody reached is a 0 row, not a missing one")

    def test_summary_names_the_largest_drops_and_marks_the_exit_numbers(self):
        text = funnel.summary(BY, RET, SESS, 7, TODAY)
        self.assertIn("Шинэ тоглогч 100", text)
        self.assertIn("эхний тулаан 72 %", text)
        top = funnel.drops(BY)
        self.assertEqual(("tutorial_step", "quest_complete", 38.0), top[0], "the chapter step loses the most")
        self.assertEqual(3, len(top))
        self.assertIn("Зааврын эхний алхам → Эхний бүлэг дууссан −38 пункт", text)
        self.assertIn("✔ анх нэвтэрсэн → эхний тулаан 72 % (босго 70 %)", text)
        self.assertIn("✘ 5 минут идэвхтэй 45 % (босго 50 %)", text)
        self.assertIn("✔ D1 50 % (босго 40 %)", text, "only the 2026-10-10 cohort's day-1 window has closed: 5 of 10")
        self.assertTrue(all(a in funnel.ONBOARDING and b in funnel.ONBOARDING for a, b, _ in top), "drops are read over the onboarding steps only")

    def test_d1_skips_cohorts_too_young_to_have_a_day_one(self):
        self.assertEqual(50.0, funnel.d1_rate(RET, TODAY))
        self.assertIsNone(funnel.d1_rate(RET[1:], TODAY), "no cohort old enough: no D1, never 0 %")
        self.assertIn("D1 —", funnel.summary(BY, RET[1:], SESS, 7, TODAY))

    def test_retention_table_shows_each_cohort(self):
        md = funnel.retention_table(RET, 7)
        self.assertIn("| 2026-10-10 | 10 | 5 (50 %) | 2 (20 %) |", md)

    def test_blank_webhook_posts_nothing(self):
        with mock.patch.dict(os.environ, {"REPORT_WEBHOOK_URL": "", "ALERT_WEBHOOK_URL": ""}), \
                mock.patch("urllib.request.urlopen") as open_:
            self.assertFalse(funnel.post("hello"))
            open_.assert_not_called()

    def test_report_webhook_wins_over_the_alert_webhook_and_sends_json(self):
        with mock.patch.dict(os.environ, {"REPORT_WEBHOOK_URL": "https://example.invalid/report", "ALERT_WEBHOOK_URL": "https://example.invalid/alert"}), \
                mock.patch("urllib.request.urlopen") as open_:
            open_.return_value.__enter__.return_value = None
            self.assertTrue(funnel.post("x" * 3000))
            req = open_.call_args[0][0]
            self.assertEqual("https://example.invalid/report", req.full_url)
            self.assertEqual(1900, len(json.loads(req.data.decode("utf-8"))["content"]), "Discord takes 2000 characters")

    def test_psql_runs_on_the_environment_without_a_connection_string(self):
        done = mock.Mock(returncode=0, stdout="type,players\nfirst_login,3\n", stderr="")
        with mock.patch("subprocess.run", return_value=done) as run:
            funnel.URL = None
            self.assertEqual({"first_login": 3}, funnel.funnel_rows(7))
            argv = run.call_args[0][0]
            self.assertEqual("psql", argv[0])
            self.assertEqual("-X", argv[1], "no URL in argv: the password rides in PGPASSWORD")
            self.assertIn("--csv", argv)


if __name__ == "__main__":
    unittest.main()
