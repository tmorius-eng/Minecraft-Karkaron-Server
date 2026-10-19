[Unit]
Description=SULD alert: unit %i failed

[Service]
Type=oneshot
ExecStart=@BIN_DIR@/notify.sh "unit failed: %i" "systemctl status %i; journalctl -u %i -n 50"
