#!/usr/bin/env bash
# Sao lưu database của một môi trường ra file .sql.gz, xóa bản cũ hơn BACKUP_KEEP_DAYS ngày.
#   bash backup-db.sh prd
# Chạy tự động mỗi đêm 2h (crontab -e):
#   0 2 * * * bash /opt/nhaminh/prd/backend/deploy/scripts/backup-db.sh prd >> /opt/nhaminh/backups/backup.log 2>&1
# Khôi phục:
#   gunzip -c <file>.sql.gz | docker compose -p nhaminh-prd -f /opt/nhaminh/prd/backend/deploy/docker-compose.yml \
#     --env-file /opt/nhaminh/prd/.env exec -T -e MYSQL_PWD=<DB_ROOT_PASSWORD> db mysql -uroot chitieugiadinh
set -euo pipefail

ENV="${1:?Cách dùng: $0 <dev|test|prd>}"
ROOT="${NHAMINH_ROOT:-/opt/nhaminh}"
DIR="$ROOT/$ENV"
ENV_FILE="$DIR/.env"
OUT_DIR="$ROOT/backups/$ENV"
mkdir -p "$OUT_DIR"

ROOT_PASSWORD="$(grep -E '^DB_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
KEEP_DAYS="$(grep -E '^BACKUP_KEEP_DAYS=' "$ENV_FILE" | cut -d= -f2- || true)"
KEEP_DAYS="${KEEP_DAYS:-14}"

FILE="$OUT_DIR/chitieugiadinh-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose -p "nhaminh-$ENV" -f "$DIR/backend/deploy/docker-compose.yml" --env-file "$ENV_FILE" \
  exec -T -e MYSQL_PWD="$ROOT_PASSWORD" db \
  mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces chitieugiadinh \
  | gzip > "$FILE"

# File rỗng = sao lưu hỏng: xóa và báo lỗi thay vì giữ bản hỏng.
if [ ! -s "$FILE" ] || [ "$(gzip -cd "$FILE" | head -c 100 | wc -c)" -lt 10 ]; then
  rm -f "$FILE"
  echo "!! Sao lưu $ENV thất bại" >&2
  exit 1
fi
find "$OUT_DIR" -name '*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo ">> Đã sao lưu: $FILE ($(du -h "$FILE" | cut -f1))"
