#!/bin/sh
set -eu

# Hot or cold logical dump of the live test-server MySQL.
# Never stops containers and never uses compose down.

release_dir=${1:?Usage: backup-mysql.sh RELEASE_DIR SECRET_ENV_FILE}
secret_file=${2:?Usage: backup-mysql.sh RELEASE_DIR SECRET_ENV_FILE}
release_env="$release_dir/release.env"
compose_file="$release_dir/deploy/compose.test-server.yml"
backup_root=${3:-/opt/voidhaven/backups}

. "$release_env"

compose() {
  docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$release_env" -f "$compose_file" "$@"
}

stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="$backup_root/$stamp"
install -d -m 0750 "$backup_dir"

compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --set-gtid-purged=OFF voidhaven' > "$backup_dir/voidhaven.sql"
chmod 0640 "$backup_dir/voidhaven.sql"

compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --database=voidhaven --batch --skip-column-names' > "$backup_dir/baseline-counts.tsv" <<'SQL'
SELECT 'accounts', COUNT(*) FROM accounts
UNION ALL SELECT 'content_items', COUNT(*) FROM content_items
UNION ALL SELECT 'idempotency_records', COUNT(*) FROM idempotency_records
UNION ALL SELECT 'activation_token_idempotency', COUNT(*) FROM idempotency_records WHERE CAST(response_body AS CHAR) LIKE '%activationToken%'
UNION ALL SELECT 'media_deletion_jobs', COUNT(*) FROM media_deletion_jobs;
SQL

echo "BACKUP_DIR=$backup_dir"
echo "DUMP=$backup_dir/voidhaven.sql"
cat "$backup_dir/baseline-counts.tsv"
