#!/usr/bin/env bash
# Consistent backup of the SULD server: PostgreSQL dump + world/config archive + checksums.
#   backup.sh [--no-db] [--no-world]
# Runs while the server is online: it flushes and pauses world saving (save-all / save-off)
# for the few seconds the archive takes, then ALWAYS re-enables saving. Output goes to
#   $BACKUP_DIR/suld-<timestamp>/{database.sql.gz,server.tar.gz,SHA256SUMS}
# and old backups beyond BACKUP_KEEP are rotated out. Restore steps: DEPLOYMENT.md.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

DO_DB=1; DO_WORLD=1
for a in "$@"; do
  case "$a" in --no-db) DO_DB=0 ;; --no-world) DO_WORLD=0 ;; *) die "unknown option $a" ;; esac
done

stamp="$(date -u +%Y%m%d-%H%M%S)"
dest="$BACKUP_DIR/suld-$stamp"
mkdir -p "$dest"
chmod 750 "$dest"
log "Backup -> $dest"

saving_paused=0
resume_saving() {
  if ((saving_paused)) && server_running; then console_send "save-on" || true; saving_paused=0; fi
}
trap resume_saving EXIT

if ((DO_DB)); then
  [[ -n "$DB_PASS" ]] || die "DB_PASS is empty; cannot dump the database"
  PGPASSWORD="$DB_PASS" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --no-owner --format=plain | gzip -9 >"$dest/database.sql.gz"
  gzip -t "$dest/database.sql.gz"
  ok "database dumped ($(du -h "$dest/database.sql.gz" | cut -f1))"
fi

if ((DO_WORLD)); then
  if server_running; then
    console_send "save-all flush"
    sleep 6
    console_send "save-off"
    saving_paused=1
    sleep 2
  else
    warn "Server is not running; archiving files as they are."
  fi
  # Everything needed to rebuild the server except the large, re-downloadable Paper jar/cache.
  tar -C "$SERVER_DIR" -czf "$dest/server.tar.gz" \
      --exclude='./logs' --exclude='./cache' --exclude='./libraries' --exclude='./versions' \
      --exclude='./paper.jar' --exclude='./plugins/.paper-remapped' \
      .
  resume_saving
  tar -tzf "$dest/server.tar.gz" >/dev/null
  ok "server archive written ($(du -h "$dest/server.tar.gz" | cut -f1))"
fi

(cd "$dest" && sha256sum -- * >SHA256SUMS)
ok "checksums written"

# Rotate: keep the newest BACKUP_KEEP backups.
# shellcheck disable=SC2012
ls -1dt "$BACKUP_DIR"/suld-* 2>/dev/null | tail -n +"$((BACKUP_KEEP + 1))" | xargs -r rm -rf --
ok "backup complete (keeping the newest $BACKUP_KEEP)"
