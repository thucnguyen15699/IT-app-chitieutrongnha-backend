#!/usr/bin/env bash
# Chuẩn bị server Ubuntu/Debian LẦN ĐẦU (chạy lại nhiều lần cũng an toàn):
#   - cài Docker, tạo user "deploy" (chủ sở hữu mã nguồn, chạy deploy, dùng cho GitHub Actions)
#   - tạo /opt/nhaminh/{dev,test,prd,edge,backups,bin}, clone backend + frontend cho từng môi trường
#   - cài cổng nhận lệnh deploy từ GitHub: /opt/nhaminh/bin/ci-deploy.sh
# Cách chạy:  sudo bash setup-server.sh
# Repo private: nếu clone báo lỗi xác thực, chạy
#   sudo -u deploy -H git config --global credential.helper store
# rồi chạy lại script; git sẽ hỏi Username + Personal Access Token (chỉ quyền đọc) MỘT lần.
set -euo pipefail

ROOT="${NHAMINH_ROOT:-/opt/nhaminh}"
DEPLOY_USER="${DEPLOY_USER:-deploy}"
BACKEND_REPO="${BACKEND_REPO:-https://github.com/thucnguyen15699/IT-app-chitieutrongnha-backend.git}"
FRONTEND_REPO="${FRONTEND_REPO:-https://github.com/thucnguyen15699/IT-app-chitieutrongnha-fontend.git}"
ENVIRONMENTS="${ENVIRONMENTS:-dev test prd}"

declare -A BRANCH=([dev]=develop [test]=test [prd]=main)
as_deploy() { sudo -u "$DEPLOY_USER" -H "$@"; }

[ "$(id -u)" -eq 0 ] || { echo "Hãy chạy bằng sudo." >&2; exit 1; }

if ! command -v docker >/dev/null 2>&1; then
  echo ">> Cài Docker (script chính thức của Docker)..."
  curl -fsSL https://get.docker.com | sh
fi
command -v flock >/dev/null 2>&1 || apt-get install -y util-linux >/dev/null

if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  echo ">> Tạo user $DEPLOY_USER"
  useradd --create-home --shell /bin/bash "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"

docker network inspect nhaminh-edge >/dev/null 2>&1 || docker network create nhaminh-edge >/dev/null
mkdir -p "$ROOT/edge" "$ROOT/backups" "$ROOT/bin"
chown "$DEPLOY_USER:$DEPLOY_USER" "$ROOT" "$ROOT/edge" "$ROOT/backups"

for env in $ENVIRONMENTS; do
  dir="$ROOT/$env"
  branch="${BRANCH[$env]}"
  mkdir -p "$dir" "$ROOT/backups/$env"
  chown "$DEPLOY_USER:$DEPLOY_USER" "$dir" "$ROOT/backups/$env"
  for pair in "backend|$BACKEND_REPO" "frontend|$FRONTEND_REPO"; do
    name="${pair%%|*}"; repo="${pair#*|}"
    if [ ! -d "$dir/$name/.git" ]; then
      echo ">> Clone $name cho $env"
      as_deploy git clone --quiet "$repo" "$dir/$name"
    fi
    if as_deploy git -C "$dir/$name" ls-remote --exit-code --heads origin "$branch" >/dev/null 2>&1; then
      as_deploy git -C "$dir/$name" checkout -q "$branch"
    else
      echo "!! Repo $name chưa có nhánh '$branch' — tạo nhánh này trên GitHub (xem README mục 1)." >&2
    fi
  done
  if [ ! -f "$dir/.env" ]; then
    as_deploy cp "$dir/backend/deploy/env/$env.env.example" "$dir/.env"
    chmod 600 "$dir/.env"
    echo ">> Đã tạo $dir/.env từ mẫu — HÃY SỬA các giá trị CHANGE_ME trước khi deploy."
  fi
done

first_env="${ENVIRONMENTS%% *}"
src="$ROOT/$first_env/backend/deploy"
[ -f "$ROOT/edge/docker-compose.yml" ] || as_deploy cp "$src/edge/docker-compose.yml" "$ROOT/edge/docker-compose.yml"
if [ ! -f "$ROOT/edge/.env" ]; then
  as_deploy cp "$src/edge/edge.env.example" "$ROOT/edge/.env"
  chmod 600 "$ROOT/edge/.env"
fi

# Cổng nhận lệnh deploy từ GitHub: đặt ngoài thư mục mã nguồn và thuộc root,
# để code kéo về từ GitHub không thể tự sửa cổng này.
install -o root -g root -m 755 "$src/scripts/ci-deploy.sh" "$ROOT/bin/ci-deploy.sh"

if command -v ufw >/dev/null 2>&1; then
  echo ">> Tường lửa: chỉ mở SSH (Cloudflare Tunnel không cần mở cổng vào)."
  ufw allow OpenSSH >/dev/null
  ufw --force enable >/dev/null
fi

cat <<EOF

Xong phần chuẩn bị. Việc tiếp theo (chi tiết trong backend/deploy/README.md):
  1. Token tunnel:        sudo -u $DEPLOY_USER nano $ROOT/edge/.env
                          cd $ROOT/edge && sudo -u $DEPLOY_USER docker compose up -d
  2. Bí mật từng môi trường: sudo -u $DEPLOY_USER nano $ROOT/<dev|test|prd>/.env
  3. Deploy tay lần đầu:  sudo -u $DEPLOY_USER bash $ROOT/prd/backend/deploy/scripts/deploy.sh prd
  4. Deploy tự động:      thêm SSH key của GitHub Actions (README mục 9)
EOF
