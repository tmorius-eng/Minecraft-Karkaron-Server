[Unit]
Description=SULD weekly funnel report (docs/ANALYTICS.md)
After=postgresql.service
OnFailure=suld-alert@%n.service

[Service]
Type=oneshot
ExecStart=@BIN_DIR@/funnel-report.sh
Nice=10
