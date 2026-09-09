#!/bin/sh
set -eu

release_dir=${1:?Usage: verify-server.sh RELEASE_DIR SECRET_ENV_FILE}
secret_file=${2:?Usage: verify-server.sh RELEASE_DIR SECRET_ENV_FILE}
release_env="$release_dir/release.env"
compose_file="$release_dir/infra/compose/compose.test-server.yml"

compose() {
  docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$release_env" -f "$compose_file" "$@"
}

compose config --quiet
compose ps
curl --fail --silent --show-error http://127.0.0.1/healthz >/dev/null
curl --fail --silent --show-error http://127.0.0.1/api/v1/public/home >/dev/null
curl --fail --silent --show-error http://127.0.0.1/ >/dev/null

for service in mysql minio api web; do
  container_id=$(compose ps -q "$service")
  [ -n "$container_id" ] || { echo "Missing container for $service" >&2; exit 1; }
  bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container_id")
  [ "$bindings" = '{}' ] || [ "$bindings" = 'null' ] || {
    echo "$service unexpectedly publishes a host port" >&2
    exit 1
  }
done

echo 'PASS: HTTP, health and public API respond; mysql/minio/api/web publish no host ports.'
