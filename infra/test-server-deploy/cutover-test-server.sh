#!/bin/sh
set -eu

# Non-mixed cutover onto the live test-server named volumes.
# Requires CONFIRM=YES. Never runs compose down or bootstrap-admin.

new_release_dir=${1:?Usage: cutover-test-server.sh NEW_RELEASE_DIR SECRET_ENV_FILE}
secret_file=${2:?Usage: cutover-test-server.sh NEW_RELEASE_DIR SECRET_ENV_FILE}

if [ "${CONFIRM:-}" != 'YES' ]; then
  echo 'Refusing to cut over. Re-run with CONFIRM=YES after the change window is approved.' >&2
  exit 1
fi

new_env="$new_release_dir/release.env"
new_compose_file="$new_release_dir/deploy/compose.test-server.yml"
current_link=/opt/voidhaven/current
backup_script="$new_release_dir/deploy/scripts/backup-mysql.sh"
verify_server="$new_release_dir/deploy/scripts/verify-server.sh"
verify_sql="$new_release_dir/deploy/scripts/verify-v11-data-safety.sql"

. "$new_env"
old_release_dir=$(readlink -f "$current_link")
old_env="$old_release_dir/release.env"
old_compose_file="$old_release_dir/deploy/compose.test-server.yml"

run_old() {
  docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$old_env" -f "$old_compose_file" "$@"
}
run_new() {
  docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$new_env" -f "$new_compose_file" "$@"
}

config=$(run_new config)
echo "$config" | grep -q 'voidhaven-test-mysql-data' || { echo 'Live cutover compose must keep voidhaven-test-mysql-data.' >&2; exit 1; }
echo "$config" | grep -q 'voidhaven-rehearsal-mysql-data' && { echo 'Live cutover must not use rehearsal volumes.' >&2; exit 1; }

echo "== hot backup =="
sh "$backup_script" "$old_release_dir" "$secret_file"

echo "== stop old API only =="
run_old stop api
run_old ps

echo "== cold backup =="
sh "$backup_script" "$old_release_dir" "$secret_file"

echo "== start one new API (Flyway V11) =="
run_new up -d --no-deps --scale api=1 api

i=0
while [ "$i" -lt 40 ]; do
  if run_new exec -T api sh -c 'curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness | grep -q UP'; then
    break
  fi
  i=$((i + 1))
  sleep 3
done
run_new exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --database=voidhaven' < "$verify_sql"

echo "== start new Web =="
run_new up -d --no-deps web
run_new up -d nginx
chmod +x "$verify_server"
sh "$verify_server" "$new_release_dir" "$secret_file"

temporary_link="/opt/voidhaven/.current-$VOIDHAVEN_RELEASE_ID"
ln -sfn "$new_release_dir" "$temporary_link"
mv -Tf "$temporary_link" "$current_link"

echo "PASS: cut over to $VOIDHAVEN_RELEASE_ID with a single API. Do not run bootstrap-admin."
echo 'Smoke: create ADMIN, activate, login, suspend/restore, reissue, revoke, session invalidation.'
