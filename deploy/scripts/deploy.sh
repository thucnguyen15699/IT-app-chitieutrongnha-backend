#!/usr/bin/env bash
# Deploy một môi trường: lấy code mới nhất của nhánh tương ứng, build lại, khởi động lại, chờ app khỏe.
#   bash deploy.sh dev           # nhánh develop → https://dev.<tên-miền>
#   bash deploy.sh test          # nhánh test    → https://test.<tên-miền>
#   bash deploy.sh prd           # nhánh main    → https://<tên-miền>   (tự sao lưu DB trước)
#   bash deploy.sh prd --no-pull # build lại code đang có, không git pull
set -euo pipefail

ENV="${1:-}"
case "$ENV" in dev|test|prd) ;; *) echo "Cách dùng: $0 <dev|test|prd> [--no-pull]" >&2; exit 1 ;; esac
PULL=true; [ "${2:-}" = "--no-pull" ] && PULL=false

ROOT="${NHAMINH_ROOT:-/opt/nhaminh}"
DIR="$ROOT/$ENV"
ENV_FILE="$DIR/.env"
COMPOSE="docker compose -p nhaminh-$ENV -f $DIR/backend/deploy/docker-compose.yml --env-file $ENV_FILE"
SCRIPTS="$(cd "$(dirname "$0")" && pwd)"

# Mỗi môi trường chỉ một lần deploy tại một thời điểm (backend và frontend có thể cùng push).
# Lần sau chờ tối đa 15 phút rồi chạy tiếp, nên luôn deploy code mới nhất.
exec 9>"$DIR/.deploy.lock"
if ! flock -w 900 9; then
  echo "!! [$ENV] Đang có lần deploy khác chạy quá lâu, bỏ qua." >&2
  exit 1
fi

[ -f "$ENV_FILE" ] || { echo "Thiếu $ENV_FILE (copy từ backend/deploy/env/$ENV.env.example)" >&2; exit 1; }
if grep -q "CHANGE_ME" "$ENV_FILE"; then
  echo "$ENV_FILE còn giá trị CHANGE_ME — điền bí mật thật trước khi deploy." >&2
  exit 1
fi
BRANCH="$(grep -E '^GIT_BRANCH=' "$ENV_FILE" | cut -d= -f2)"
docker network inspect nhaminh-edge >/dev/null 2>&1 || docker network create nhaminh-edge >/dev/null

if $PULL; then
  for repo in backend frontend; do
    echo ">> [$ENV] Cập nhật $repo (nhánh $BRANCH)"
    git -C "$DIR/$repo" fetch --quiet origin "$BRANCH"
    git -C "$DIR/$repo" checkout --quiet "$BRANCH"
    # Thư mục deploy chỉ chạy code từ GitHub; mọi sửa tay trên server sẽ bị ghi đè.
    git -C "$DIR/$repo" reset --quiet --hard "origin/$BRANCH"
    echo "   $(git -C "$DIR/$repo" log -1 --format='%h %s')"
  done
fi

# Lấy danh sách trước rồi mới so khớp (tránh pipefail + grep -q bỏ sót bước sao lưu).
RUNNING="$($COMPOSE ps --status running --services 2>/dev/null || true)"
if [ "$ENV" = "prd" ] && printf '%s\n' "$RUNNING" | grep -qx db; then
  echo ">> [prd] Sao lưu database trước khi deploy"
  bash "$SCRIPTS/backup-db.sh" prd
fi

echo ">> [$ENV] Build & khởi động"
$COMPOSE up -d --build --remove-orphans

echo ">> [$ENV] Chờ backend khỏe (tối đa 4 phút)..."
for _ in $(seq 1 48); do
  status="$($COMPOSE ps --format '{{.Service}} {{.Health}}' | awk '$1=="backend"{print $2}')"
  if [ "$status" = "healthy" ]; then
    echo ">> [$ENV] Deploy thành công."
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "!! [$ENV] Backend chưa khỏe. 80 dòng log cuối:" >&2
$COMPOSE logs --tail 80 backend >&2
exit 1
