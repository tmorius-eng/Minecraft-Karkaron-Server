[Unit]
Description=SULD Velocity proxy (AUTH_MODE=hybrid) in tmux session "@TMUX_PROXY_SESSION@"
Documentation=file://@REPO_DIR@/docs/AUTHENTICATION.md
After=network-online.target suld.service
Wants=network-online.target
StartLimitIntervalSec=600
StartLimitBurst=5

[Service]
Type=forking
User=@SULD_USER@
Group=@SULD_USER@
WorkingDirectory=@PROXY_DIR@
ExecStart=@BIN_DIR@/start.sh --proxy-direct
ExecStop=@BIN_DIR@/stop.sh --proxy-direct
TimeoutStartSec=60
TimeoutStopSec=30
Restart=always
RestartSec=10
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
