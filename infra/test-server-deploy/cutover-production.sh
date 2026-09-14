#!/bin/sh
set -eu

# Production cutover helper for compose.candidate.yml (external MySQL).
# Default is dry-run. Set EXECUTE=YES only after ADR-V0-004 approval.

candidate_dir=${1:?Usage: cutover-production.sh CANDIDATE_DIR ENV_FILE}
env_file=${2:?Usage: cutover-production.sh CANDIDATE_DIR ENV_FILE}
compose_file="$candidate_dir/infra/compose/compose.candidate.yml"
verify_sql="$candidate_dir/infra/scripts/verify-v11-data-safety.sql"

cat <<'EOF'
Production cutover checklist (data-preserving, non-mixed):

1. Confirm ADR-V0-004 (Tencent Cloud / managed MySQL / COS / DNS / TLS) is approved.
2. Record rollback image tags (version + full Git SHA, never latest).
3. Take a managed MySQL snapshot and a logical dump. Copy the dump to an isolated replica.
4. Restore the dump on the replica and start one new API against it. Confirm Flyway V11
   and unchanged account/content counts via infra/scripts/verify-v11-data-safety.sql.
5. Pause EVERY old production API replica. Do not mix old and new APIs.
6. Start exactly one new API with SPRING_PROFILES_ACTIVE=production. Wait for readiness.
7. Publish the new Web. Smoke: create ADMIN, activate, login, suspend/restore, reissue,
   revoke, session invalidation. Do not run bootstrap-admin on a non-empty accounts table.
8. Keep a single API until smoke passes. Scale only after that.
9. Emergency rollback: switch API/Web images back, --scale api=1, keep V11 columns.
   Never flyway undo, DROP COLUMN, or DROP DATABASE.

EOF

if [ "${EXECUTE:-}" != 'YES' ]; then
  echo 'Dry-run only. Re-run with EXECUTE=YES after approval to start a single candidate API.'
  exit 0
fi

if [ "${CONFIRM:-}" != 'YES' ]; then
  echo 'Refusing to execute. Re-run with CONFIRM=YES EXECUTE=YES.' >&2
  exit 1
fi

docker compose --env-file "$env_file" -f "$compose_file" config --quiet
docker compose --env-file "$env_file" -f "$compose_file" up -d --no-deps --scale api=1 api
i=0
while [ "$i" -lt 40 ]; do
  if docker compose --env-file "$env_file" -f "$compose_file" exec -T api sh -c 'curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness | grep -q UP'; then
    break
  fi
  i=$((i + 1))
  sleep 3
done

echo "New production API is up. Run $verify_sql against the managed database from an approved SQL client."
echo 'Then publish Web and complete smoke. Keep a single API replica.'
