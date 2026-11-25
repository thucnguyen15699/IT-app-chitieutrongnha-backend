#!/usr/bin/env bash
# Cổng nhận lệnh deploy từ GitHub Actions qua SSH.
# SSH key của GitHub được gắn "forced command" trỏ vào file này (xem deploy/README.md, mục 9),
# nên key đó CHỈ chạy được đúng "deploy <dev|test|prd>", không mở được shell trên server.
set -euo pipefail

ROOT="${NHAMINH_ROOT:-/opt/nhaminh}"
read -r action env extra <<< "${SSH_ORIGINAL_COMMAND:-}"

if [ "${action:-}" != "deploy" ] || [ -n "${extra:-}" ]; then
  echo "Chỉ cho phép lệnh: deploy <dev|test|prd>" >&2
  exit 2
fi
case "${env:-}" in
  dev|test|prd) ;;
  *) echo "Môi trường không hợp lệ: '${env:-}'" >&2; exit 2 ;;
esac

LOG="$ROOT/$env/deploy.log"
echo "===== $(date '+%F %T') deploy $env (GitHub Actions) =====" >> "$LOG"
bash "$ROOT/$env/backend/deploy/scripts/deploy.sh" "$env" 2>&1 | tee -a "$LOG"
