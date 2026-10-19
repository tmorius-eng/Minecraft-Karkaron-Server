[Unit]
Description=SULD weekly funnel report, Monday 09:10 Ulaanbaatar (01:10 UTC)

[Timer]
OnCalendar=Mon *-*-* 01:10:00 UTC
RandomizedDelaySec=300
Persistent=true

[Install]
WantedBy=timers.target
