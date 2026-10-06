[Unit]
Description=SULD backup (database + world + config)
After=postgresql.service

[Service]
Type=oneshot
User=@SULD_USER@
Group=@SULD_USER@
ExecStart=@BIN_DIR@/backup.sh
Nice=10
IOSchedulingClass=idle
