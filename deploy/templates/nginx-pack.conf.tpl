# SULD resource pack: static, content-addressed ZIPs only. Nothing else is served.
limit_req_zone $binary_remote_addr zone=suldpack:10m rate=5r/s;

server {
    listen @PACK_PORT@ default_server;
    listen [::]:@PACK_PORT@ default_server;
    server_name _;
    server_tokens off;

    root @PACK_DIR@;
    autoindex off;

    location ~ ^/suld-pack-[0-9a-f]+\.zip$ {
        limit_req zone=suldpack burst=20 nodelay;
        types { }
        default_type application/zip;
        # Filename embeds the content hash, so it is safe to cache forever.
        add_header Cache-Control "public, max-age=31536000, immutable";
        add_header X-Content-Type-Options nosniff;
        try_files $uri =404;
    }

    location / { return 404; }
}
