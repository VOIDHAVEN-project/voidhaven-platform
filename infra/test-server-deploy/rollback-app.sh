#!/bin/sh
set -eu

# Application-only rollback. Keeps Flyway V11 columns/tables.
# Forces a single API replica. Never compose down -v.

release_dir=${1:?Usage: rollback-app.sh RELEASE_DIR SECRET_ENV_FILE}
secret_file=${2:?Usage: rollback-app.sh RELEASE_DIR SECRET_ENV_FILE}
target=${3:-fallback}

if [ "${CONFIRM:-}" != 'YES' ]; then
  echo 'Refusing to rollback. Re-run with CONFIRM=YES.' >&2
  exit 1
fi

release_env="$release_dir/release.env"
compose_file="$release_dir/deploy/compose.test-server.yml"
. "$release_env"

case "$target" in
  fallback)
    api_image=$VOIDHAVEN_API_FALLBACK_IMAGE
    web_image=$VOIDHAVEN_WEB_FALLBACK_IMAGE
    ;;
  current)
    api_image=$VOIDHAVEN_API_IMAGE
    web_image=$VOIDHAVEN_WEB_IMAGE
    ;;
  *)
    echo "Unknown target '$target' (use fallback or current)." >&2
    exit 1
    ;;
esac

compose() {
  docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$release_env" -f "$compose_file" "$@"
}

VOIDHAVEN_API_IMAGE="$api_image" compose up -d --no-deps --scale api=1 api
VOIDHAVEN_WEB_IMAGE="$web_image" compose up -d --no-deps web

i=0
while [ "$i" -lt 40 ]; do
  if compose exec -T api sh -c 'curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness | grep -q UP'; then
    break
  fi
  i=$((i + 1))
  sleep 3
done

chmod +x "$release_dir/deploy/scripts/verify-server.sh"
"$release_dir/deploy/scripts/verify-server.sh" "$release_dir" "$secret_file"

echo "PASS: rolled application to $api_image / $web_image with a single API. Flyway was not undone."
