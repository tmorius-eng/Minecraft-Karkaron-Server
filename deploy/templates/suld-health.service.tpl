[Unit]
Description=SULD health check (alerts the operators' webhook on a failure)
After=suld.service

[Service]
Type=oneshot
ExecStart=@BIN_DIR@/health-check.sh --quiet --notify
