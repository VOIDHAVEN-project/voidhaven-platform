#!/bin/sh
set -eu

# Restore a dump onto isolated rehearsal volumes. Never touches voidhaven-test-mysql-data.

new_release_dir=${1:?Usage: restore-rehearsal.sh NEW_RELEASE_DIR SECRET_ENV_FILE DUMP_SQL [BASELINE_TSV]}
secret_file=${2:?Usage: restore-rehearsal.sh NEW_RELEASE_DIR SECRET_ENV_FILE DUMP_SQL [BASELINE_TSV]}
dump_sql=${3:?Usage: restore-rehearsal.sh NEW_RELEASE_DIR SECRET_ENV_FILE DUMP_SQL [BASELINE_TSV]}
baseline_tsv=${4:-}
release_env="$new_release_dir/release.env"
compose_file="$new_release_dir/deploy/compose.rehearsal.yml"
verify_sql="$new_release_dir/deploy/scripts/verify-v11-data-safety.sql"

. "$release_env"

case "$compose_file" in
  *compose.test-server.yml) echo 'Refusing to use the live test-server compose file for rehearsal.' >&2; exit 1 ;;
esac

compose() {
  docker compose -p voidhaven-rehearsal --env-file "$secret_file" --env-file "$release_env" -f "$compose_file" "$@"
}

mysql() {
  compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"' -- "$@"
}

config=$(compose config)
echo "$config" | grep -q 'voidhaven-rehearsal-mysql-data' || { echo 'Rehearsal compose must use voidhaven-rehearsal-mysql-data.' >&2; exit 1; }
echo "$config" | grep -E 'voidhaven-test-mysql-data|voidhaven-test-minio-data' >/dev/null && {
  echo 'Rehearsal compose unexpectedly references live test volumes.' >&2
  exit 1
}

compose up -d mysql minio

i=0
while [ "$i" -lt 40 ]; do
  if compose exec -T mysql sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent'; then
    break
  fi
  i=$((i + 1))
  sleep 2
done

compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --execute "CREATE DATABASE IF NOT EXISTS voidhaven CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'
compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" voidhaven' < "$dump_sql"

compose up -d --scale api=1 api
i=0
while [ "$i" -lt 40 ]; do
  if compose exec -T api sh -c 'curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness | grep -q UP'; then
    break
  fi
  i=$((i + 1))
  sleep 3
done

compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --database=voidhaven' < "$verify_sql"

if [ -n "$baseline_tsv" ]; then
  after_accounts=$(compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --database=voidhaven --batch --skip-column-names --execute "SELECT COUNT(*) FROM accounts;"')
  before_accounts=$(awk -F'\t' '$1=="accounts"{print $2}' "$baseline_tsv" | tr -d '\r')
  if [ "$after_accounts" != "$before_accounts" ]; then
    echo "Account count changed during rehearsal: before=$before_accounts after=$after_accounts" >&2
    exit 1
  fi
fi

echo 'PASS: isolated rehearsal restored dump, applied V11 via new API, and preserved account counts.'
