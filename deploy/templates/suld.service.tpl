[Unit]
Description=SULD Minecraft server (Paper) in tmux session "@TMUX_SESSION@"
Documentation=file://@REPO_DIR@/DEPLOYMENT.md
After=network-online.target postgresql.service
Wants=network-online.target postgresql.service
# Crash-loop guard: at most 5 automatic restarts per 10 minutes, then stay failed.
StartLimitIntervalSec=600
StartLimitBurst=5

[Service]
Type=forking
User=@SULD_USER@
Group=@SULD_USER@
WorkingDirectory=@SERVER_DIR@
ExecStart=@BIN_DIR@/start.sh --direct
ExecStop=@BIN_DIR@/stop.sh --direct
# Graceful stop saves worlds + all SULD profiles; give it room before systemd kills the group.
TimeoutStartSec=120
TimeoutStopSec=150
# Restart on crash. (An in-game /stop also exits cleanly and is restarted: use
# `systemctl stop suld` / stop.sh to keep it down.)
Restart=always
RestartSec=15
# Hardening that does not interfere with a JVM + tmux.
NoNewPrivileges=true
ProtectSystem=full
ProtectHome=true
PrivateTmp=false
ProtectKernelModules=true
ProtectControlGroups=true
RestrictRealtime=true
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
