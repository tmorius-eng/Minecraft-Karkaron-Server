[Unit]
Description=SULD backup (database + world + config)
After=postgresql.service
# A failed start or a crash loop that systemd gave up on reaches the operators (notify.sh, ALERT_WEBHOOK_URL).
OnFailure=suld-alert@%n.service

[Service]
Type=oneshot
User=@SULD_USER@
Group=@SULD_USER@
ExecStart=@BIN_DIR@/backup.sh
Nice=10
IOSchedulingClass=idle
