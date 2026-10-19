#!/usr/bin/env bash
# Consistent backup of the SULD server: PostgreSQL dump + world/config archive + checksums, copied off-host.
#   backup.sh [--no-db] [--no-world]
# Order (audit 2026-10-11 OPS-001: the dump used to be taken before the world flush, and SÜLD's in-memory state
# was never flushed):
#   1. /suld flush         SÜLD writes every profile, style and clan; we wait for plugins/SULD/flush.done
#   2. save-all flush      the world and player files reach disk
#   3. save-off            world saving pauses while the snapshot is taken (ALWAYS re-enabled on exit)
#   4. pg_dump             the database at (almost) the same instant as the world on disk
#   5. tar                 world, configs and plugin data
#   6. save-on, checksums, off-host copy ($BACKUP_REMOTE), rotation
# Players stay online, so a change made in the few seconds between 1 and 4 can still split; for a fully consistent
# image take it with the server stopped (stop.sh; backup.sh; start.sh). Output goes to
#   $BACKUP_DIR/suld-<timestamp>/{database.sql.gz,server.tar.gz,SHA256SUMS}
# Restore steps: DEPLOYMENT.md "Restore" and docs/operations/RESTORE_DRILL.md.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

# a backup that fails reaches the operators (notify.sh; a no-op without ALERT_WEBHOOK_URL)
die() { "$LIB_DIR/notify.sh" "backup FAILED" "$*" || true; printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }
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

flush_marker="$SERVER_DIR/plugins/SULD/flush.done"
if server_running; then
  # 1. SÜLD's own state first: wait until the plugin reports every write finished (it writes the marker)
  since_ms=$(($(date +%s) * 1000))
  console_send "suld flush"
  flushed=0
  for _ in $(seq 1 45); do
    if [[ -f "$flush_marker" ]]; then
      read -r state at _ <"$flush_marker" || true
      if [[ "${at:-0}" =~ ^[0-9]+$ ]] && ((at >= since_ms - 2000)); then
        [[ "$state" == "ok" ]] && flushed=1
        break
      fi
    fi
    sleep 1
  done
  if ((flushed)); then
    ok "SÜLD flushed ($(cat "$flush_marker"))"
  else
    warn "SÜLD flush did not confirm within 45 s ($( [[ -f "$flush_marker" ]] && cat "$flush_marker" || echo 'no marker')): the dump may miss the last minute of in-memory changes"
    echo "UNFLUSHED: $(date -u +%FT%TZ)" >"$dest/NOTE"
  fi
  # 2-3. world and player files to disk, then pause saving for the snapshot
  console_send "save-all flush"
  sleep 6
  console_send "save-off"
  saving_paused=1
  sleep 2
else
  warn "Server is not running; archiving files as they are (that is the fully consistent case)."
fi

if ((DO_DB)); then
  [[ -n "$DB_PASS" ]] || die "DB_PASS is empty; cannot dump the database"
  PGPASSWORD="$DB_PASS" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --no-owner --format=plain | gzip -9 >"$dest/database.sql.gz"
  gzip -t "$dest/database.sql.gz"
  ok "database dumped ($(du -h "$dest/database.sql.gz" | cut -f1))"
fi

if ((DO_WORLD)); then
  # Everything needed to rebuild the server except the large, re-downloadable Paper jar/cache.
  tar -C "$SERVER_DIR" -czf "$dest/server.tar.gz" \
      --exclude='./logs' --exclude='./cache' --exclude='./libraries' --exclude='./versions' \
      --exclude='./paper.jar' --exclude='./plugins/.paper-remapped' \
      .
  resume_saving
  tar -tzf "$dest/server.tar.gz" >/dev/null
  ok "server archive written ($(du -h "$dest/server.tar.gz" | cut -f1))"
fi

resume_saving
(cd "$dest" && sha256sum -- * >SHA256SUMS)
ok "checksums written"

# Off-host copy: a backup that lives only on this disk dies with it.
#   BACKUP_REMOTE=user@host:/srv/suld-backups   (rsync over ssh; key-based login for the service user)
#   BACKUP_REMOTE=rclone:remote:bucket/suld      (any rclone remote: S3, B2, Google Drive, ...)
if [[ -n "${BACKUP_REMOTE:-}" ]]; then
  case "$BACKUP_REMOTE" in
    rclone:*)
      target="${BACKUP_REMOTE#rclone:}/suld-$stamp"
      rclone copy "$dest" "$target" && rclone check "$dest" "$target" \
        || die "off-host copy to $target failed or does not match"
      ;;
    *)
      rsync -a "$dest" "$BACKUP_REMOTE/" \
        || die "off-host copy to $BACKUP_REMOTE failed"
      [[ -z "$(rsync -an --checksum --itemize-changes "$dest" "$BACKUP_REMOTE/")" ]] \
        || die "off-host copy at $BACKUP_REMOTE does not match the local backup"
      ;;
  esac
  ok "copied off-host to $BACKUP_REMOTE and verified"
else
  warn "BACKUP_REMOTE is not set: this backup exists ONLY on this host (set it in $SULD_ENV)"
fi

# Rotate: keep the newest BACKUP_KEEP backups.
# shellcheck disable=SC2012
ls -1dt "$BACKUP_DIR"/suld-* 2>/dev/null | tail -n +"$((BACKUP_KEEP + 1))" | xargs -r rm -rf --
ok "backup complete (keeping the newest $BACKUP_KEEP)"
