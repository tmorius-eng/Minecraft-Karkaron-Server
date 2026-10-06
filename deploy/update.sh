#!/usr/bin/env bash
# Update a deployed SULD server to the latest commit of REPO_BRANCH, with automatic rollback.
#
#   sudo /opt/suld/bin/update.sh [options]
#     --branch NAME    deploy another branch (also changes REPO_BRANCH for this run only)
#     --paper          also move to the newest Paper build for MC_VERSION
#     --warn SECONDS   tell players and wait before stopping (default 0)
#     --skip-tests     build without running unit tests (faster, less safe)
#     --no-backup      skip the pre-update backup (not recommended)
#     --force          redeploy even if the commit is unchanged
#     --rollback       reactivate the previous release (plugin jar + resource pack)
#
# Order of operations (server downtime is only the file swap + restart):
#   backup -> fetch -> build + test + pack-validate (server still running!) -> stop -> swap -> start
#   -> health check. If the new version fails to boot or fails the health check, the previous
#   release is reinstalled automatically.
#
# DATABASE NOTE: schema migrations are forward-only and applied on plugin start. A jar rollback
# does not undo them (they are additive). The pre-update backup is the true database rollback.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"

main() {
  need_root "$@"   # before parsing: the sudo re-exec must receive the original arguments
  local branch="" want_paper=0 warn_secs=0 do_backup=1 force=0 rollback=0
  while (($#)); do
    case "$1" in
      --branch) branch="${2:?--branch needs a name}"; shift ;;
      --paper) want_paper=1 ;;
      --warn) warn_secs="${2:?--warn needs seconds}"; shift ;;
      --skip-tests) export SKIP_TESTS=true ;;
      --no-backup) do_backup=0 ;;
      --force) force=1 ;;
      --rollback) rollback=1 ;;
      -h|--help) sed -n '2,22p' "${BASH_SOURCE[0]}"; return 0 ;;
      *) die "unknown option: $1" ;;
    esac
    shift
  done
  load_env
  [[ -n "$branch" ]] && REPO_BRANCH="$branch"

  mkdir -p "$RUN_DIR"
  exec 9>"$RUN_DIR/update.lock"
  flock -n 9 || die "Another update/backup-sensitive operation is running."

  local cur="$RELEASES_DIR/current.state" prev="$RELEASES_DIR/previous.state"
  mkdir -p "$RELEASES_DIR"

  if ((rollback)); then
    [[ -f "$prev" ]] || die "No previous release recorded ($prev)."
    log "Rolling back to the previous release"
    activate_release "$prev" "$warn_secs" || die "Rollback failed — inspect: journalctl -u suld -n 80"
    mv -f "$cur" "$RELEASES_DIR/rolled-back.state" 2>/dev/null || true
    cp -f "$prev" "$cur"
    ok "Rolled back. Database changes (if any) were NOT reverted."
    return 0
  fi

  [[ -d "$REPO_DIR/.git" ]] || die "No repository at $REPO_DIR — run deploy.sh first."

  # ---- 1. is there anything to do?
  local old_commit new_commit
  old_commit="$(as_suld_in "$REPO_DIR" git rev-parse HEAD)"
  as_suld_in "$REPO_DIR" git fetch --depth 1 origin "$REPO_BRANCH"
  new_commit="$(as_suld_in "$REPO_DIR" git rev-parse FETCH_HEAD)"
  if [[ "$old_commit" == "$new_commit" && $want_paper -eq 0 && $force -eq 0 ]]; then
    ok "Already up to date ($old_commit)."
    return 0
  fi
  log "Updating ${old_commit:0:8} -> ${new_commit:0:8} ($REPO_BRANCH)"

  # ---- 2. safety backup
  if ((do_backup)); then
    as_suld "$BIN_DIR/backup.sh" || die "Pre-update backup failed — aborting (use --no-backup to override)."
  fi

  # ---- 3. build everything while the server keeps running
  as_suld_in "$REPO_DIR" git reset --hard FETCH_HEAD
  local jar pack_out pack sha
  if ! build_plugin; then
    as_suld_in "$REPO_DIR" git reset --hard "$old_commit"
    die "Build/tests FAILED. Server untouched, still running ${old_commit:0:8}."
  fi
  jar="$(find_plugin_jar "$REPO_DIR")"
  [[ -n "$jar" ]] || { as_suld_in "$REPO_DIR" git reset --hard "$old_commit"; die "Build produced no plugin jar."; }
  if ! pack_out="$(as_suld bash "$BIN_DIR/build-pack.sh" "$REPO_DIR")"; then
    as_suld_in "$REPO_DIR" git reset --hard "$old_commit"
    die "Resource pack validation/build FAILED. Server untouched."
  fi
  pack="$(sed -n 's/^PACK_FILE=//p' <<<"$pack_out")"
  sha="$(sed -n 's/^PACK_SHA1=//p' <<<"$pack_out")"

  local staged="SULD-${new_commit:0:12}.jar"
  install -o "$SULD_USER" -g "$SULD_USER" -m 644 "$jar" "$RELEASES_DIR/$staged"
  [[ -f "$cur" ]] && cp -f "$cur" "$prev"
  local paper_build=""
  [[ -f "$SERVER_DIR/.paper-build" ]] && paper_build="$(cat "$SERVER_DIR/.paper-build")"
  cat >"$RELEASES_DIR/next.state" <<STATE
COMMIT=$new_commit
JAR=$staged
PACK_FILE=$pack
PACK_SHA1=$sha
PAPER_BUILD=$paper_build
STATE

  # ---- 4. stop, swap, start
  if server_running; then
    if ((warn_secs > 0)); then
      console_send "say Сервер шинэчлэгдэж байна / Updating in ${warn_secs}s"
      sleep "$warn_secs"
    fi
    systemctl stop suld
  fi
  ((want_paper)) && install_paper force
  if activate_release "$RELEASES_DIR/next.state" 0; then
    mv -f "$RELEASES_DIR/next.state" "$cur"
    install_toolkit "$REPO_DIR/deploy"
    # Keep the five newest staged jars for manual rollback; prune the rest.
    # shellcheck disable=SC2012
    ls -1t "$RELEASES_DIR"/SULD-*.jar 2>/dev/null | tail -n +6 | xargs -r rm -f --
    ok "Update complete: ${new_commit:0:8}${paper_build:+ (Paper $(cat "$SERVER_DIR/.paper-build"))}"
    return 0
  fi

  # ---- 5. failure: restore the previous release
  fail "New release failed to start or failed its health check. Restoring the previous release."
  as_suld_in "$REPO_DIR" git reset --hard "$old_commit"
  if [[ -f "$prev" ]] && activate_release "$prev" 0; then
    cp -f "$prev" "$cur"
    die "Update FAILED and was rolled back to ${old_commit:0:8}. Investigate: tail -n 100 $SERVER_DIR/logs/latest.log"
  fi
  die "Update FAILED and the automatic rollback ALSO failed. Server is down. Use the pre-update backup in $BACKUP_DIR (DEPLOYMENT.md > Restore)."
}

# Install the release described by a state file, start the server, and verify it boots healthy.
# Assumes the server is stopped. Returns non-zero if it does not come up healthy.
activate_release() {
  local state="$1" warn_secs="${2:-0}"
  local COMMIT="" JAR="" PACK_FILE="" PACK_SHA1="" PAPER_BUILD=""
  # shellcheck disable=SC1090
  source "$state"
  [[ -f "$RELEASES_DIR/$JAR" ]] || { fail "Release jar missing: $RELEASES_DIR/$JAR"; return 1; }
  if server_running; then
    ((warn_secs > 0)) && { console_send "say Сервер ${warn_secs} секундын дараа дахин ачаалагдана / Restarting in ${warn_secs}s"; sleep "$warn_secs"; }
    systemctl stop suld
  fi
  install -o "$SULD_USER" -g "$SULD_USER" -m 644 "$RELEASES_DIR/$JAR" "$SERVER_DIR/plugins/SULD.jar" || return 1
  install_plugins
  if [[ -n "$PACK_FILE" && -f "$PACK_DIR/$PACK_FILE" ]]; then
    apply_pack_config "$PACK_FILE" "$PACK_SHA1"
  else
    warn "Pack ${PACK_FILE:-<none>} no longer on disk; leaving the resource-pack config as is."
  fi
  sync_db_config
  chown "$SULD_USER:$SULD_USER" "$PLUGIN_CONFIG"
  local started
  started="$(date +%s)"
  systemctl start suld
  wait_ready 300 "$started" || { fail "Server did not finish booting in 5 minutes."; return 1; }
  sleep 5
  "$BIN_DIR/health-check.sh" || return 1
}

main "$@"
exit $?
