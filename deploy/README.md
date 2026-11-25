# Deploy NhaMinh: 3 môi trường dev / test / prd

Một server chạy cả 3 môi trường bằng Docker. Cloudflare Tunnel đưa người dùng từ tên miền vào đúng môi trường, nên **không cần mở cổng 80/443** và **không cần tự làm chứng chỉ SSL**.

```
                         Cloudflare (DNS + HTTPS)
     dev.<miền>          test.<miền>            <miền>
          │                   │                    │
          └──────────── Cloudflare Tunnel ─────────┘
                              │   (container cloudflared trên server)
      ┌───────────────────────┼───────────────────────┐
      ▼                       ▼                       ▼
 nhaminh-dev-web        nhaminh-test-web        nhaminh-prd-web     ← nginx: giao diện + chuyển /api
      │                       │                       │
   backend (dev)          backend (test)          backend (prd)     ← Spring Boot, profile mysql,<env>
      │                       │                       │
   MySQL (dev)            MySQL (test)            MySQL (prd)       ← dữ liệu tách riêng hoàn toàn
```

| Môi trường | Nhánh Git | Địa chỉ | Dùng để |
|---|---|---|---|
| **dev** | `develop` | `https://dev.<miền>` | Thử tính năng mới; cho phép dữ liệu bẩn |
| **test** | `test` | `https://test.<miền>` | Kiểm thử trước khi phát hành; cấu hình giống prd |
| **prd** | `main` | `https://<miền>` | Người dùng thật |

Quy trình phát hành: làm trên `develop` (tự deploy dev) → merge vào `test` (tự deploy test, kiểm thử) → merge vào `main` (duyệt → tự deploy prd). Thiết lập deploy tự động ở **mục 9**.

---

## 0. Yêu cầu

- Server Ubuntu 22.04/24.04 (hoặc Debian 12), **RAM ≥ 4 GB** cho cả 3 môi trường (mỗi môi trường ~0,8 GB; lúc build cần thêm ~1 GB). RAM 2 GB thì chỉ nên chạy prd + test, hoặc thêm swap.
- Tên miền đã trỏ nameserver về Cloudflare.
- Server clone được 2 repo GitHub. Repo **private** thì dùng [Deploy key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/managing-deploy-keys) (mỗi repo một key, chỉ quyền đọc) hoặc Personal Access Token.

## 1. Tạo nhánh cho từng môi trường (làm 1 lần, trên máy bạn)

Làm cho **cả repo backend và frontend**:

```bash
git checkout main && git pull
git push origin main:develop
git push origin main:test
```

## 2. Chuẩn bị server (làm 1 lần)

```bash
# Copy deploy/scripts/setup-server.sh lên server, rồi:
sudo bash setup-server.sh
```

Script sẽ:
- cài Docker,
- tạo user **`deploy`** (sở hữu mã nguồn, chạy deploy),
- tạo `/opt/nhaminh/{dev,test,prd,edge,backups,bin}`,
- clone backend + frontend vào từng môi trường, tạo file `.env` từ mẫu,
- cài `/opt/nhaminh/bin/ci-deploy.sh` (cổng nhận lệnh deploy tự động),
- bật tường lửa (chỉ mở SSH).

**Repo private:** nếu bước clone báo lỗi xác thực, chạy `sudo -u deploy -H git config --global credential.helper store` rồi chạy lại script. Git sẽ hỏi Username và **Personal Access Token** một lần. Tạo token loại *fine-grained*, chỉ cấp quyền đọc *Contents* cho 2 repo.

Từ đây, mọi lệnh thao tác với app chạy bằng user `deploy`: `sudo -iu deploy`.

Cấu trúc sau khi chạy:

```
/opt/nhaminh/
├── bin/ci-deploy.sh cổng nhận lệnh deploy từ GitHub (thuộc root)
├── edge/            docker-compose.yml + .env (token Cloudflare)
├── dev/             backend/  frontend/  .env
├── test/            backend/  frontend/  .env
├── prd/             backend/  frontend/  .env
└── backups/         dev/ test/ prd/   (file .sql.gz)
```

## 3. Cloudflare Tunnel

1. Vào **Cloudflare dashboard → Zero Trust → Networks → Tunnels → Create a tunnel** → chọn **Cloudflared** → đặt tên `nhaminh`.
2. Ở bước *Install connector*, chọn **Docker**, copy chuỗi sau `--token` và dán vào server:
   ```bash
   nano /opt/nhaminh/edge/.env        # CLOUDFLARE_TUNNEL_TOKEN=<chuỗi token>
   cd /opt/nhaminh/edge && docker compose up -d
   ```
   Trên dashboard, tunnel chuyển sang trạng thái **Healthy**.
3. Tab **Public Hostname → Add a public hostname**, thêm 3 dòng (Type luôn là **HTTP**):

   | Subdomain | Domain | Service |
   |---|---|---|
   | `dev` | `<miền>` | `http://nhaminh-dev-web:80` |
   | `test` | `<miền>` | `http://nhaminh-test-web:80` |
   | *(để trống)* | `<miền>` | `http://nhaminh-prd-web:80` |

   Cloudflare tự tạo bản ghi DNS. Nếu tên đó đã có bản ghi A/CNAME cũ thì xóa bản ghi cũ trước.
4. **SSL/TLS → Edge Certificates**: bật **Always Use HTTPS**.
5. **Nên làm:** khóa dev và test để người ngoài không vào được. Vào **Zero Trust → Access → Applications → Add → Self-hosted**, thêm `dev.<miền>` và `test.<miền>`, đặt policy *Allow* cho email của bạn và nhóm. Prd để công khai.

## 4. Điền bí mật cho từng môi trường

```bash
nano /opt/nhaminh/prd/.env     # làm tương tự cho dev, test
```

- Thay mọi giá trị `CHANGE_ME`. Tạo chuỗi ngẫu nhiên bằng `openssl rand -hex 24`. **Mỗi môi trường dùng giá trị khác nhau.**
- `FRONTEND_URL` = địa chỉ thật của môi trường đó, ví dụ `https://example.com`.
- `ADMIN_PASSWORD`: **bắt buộc ở test/prd** (ít nhất 12 ký tự). Khi khởi động, app đặt mật khẩu tài khoản `admin` theo biến này. App **từ chối chạy** ở test/prd nếu admin còn mật khẩu mẫu `12345`, hoặc `JWT_SECRET` yếu.
- Email: `MAIL_VERIFICATION_ENABLED=true` chỉ khi đã điền SMTP (`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`). Gmail cần dùng *Mật khẩu ứng dụng* (App Password).
- `deploy.sh` từ chối chạy nếu file `.env` còn chữ `CHANGE_ME`.

## 5. Deploy tay (lần đầu, hoặc khi cần)

Sau khi thiết lập mục 9, mỗi lần push code sẽ **tự deploy**. Deploy tay vẫn dùng được:

```bash
sudo -iu deploy
bash /opt/nhaminh/dev/backend/deploy/scripts/deploy.sh dev
bash /opt/nhaminh/test/backend/deploy/scripts/deploy.sh test
bash /opt/nhaminh/prd/backend/deploy/scripts/deploy.sh prd
```

Mỗi lần chạy, script sẽ:
1. Lấy code mới nhất của nhánh tương ứng (cả backend và frontend).
2. Với prd: **sao lưu database trước**.
3. Build image, khởi động lại.
4. Chờ backend báo khỏe (`/api/health`). Nếu backend không lên, script in log lỗi.

Lần đầu deploy mất vài phút để build. Database và bảng được tạo tự động (`schema.sql`).

Kiểm tra: mở `https://<miền>/api/health`, kết quả phải là `{"status":"ok","env":"prd"}`.

## 6. Sao lưu tự động (prd)

```bash
sudo crontab -e
# thêm dòng: sao lưu mỗi đêm lúc 2h, giữ 14 ngày (đổi bằng BACKUP_KEEP_DAYS trong .env)
0 2 * * * bash /opt/nhaminh/prd/backend/deploy/scripts/backup-db.sh prd >> /opt/nhaminh/backups/backup.log 2>&1
```

**Nên copy thư mục `/opt/nhaminh/backups/prd` ra nơi khác** (máy khác, Google Drive, R2...). Bản sao lưu nằm cùng server thì mất server là mất luôn.

Cách khôi phục có ghi trong phần đầu file `scripts/backup-db.sh`.

## 7. Lệnh hay dùng

```bash
cd /opt/nhaminh
P="docker compose -p nhaminh-prd -f prd/backend/deploy/docker-compose.yml --env-file prd/.env"

$P ps                      # trạng thái các container
$P logs -f --tail 100 backend   # xem log backend
$P restart backend         # khởi động lại backend
$P stop                    # tạm dừng cả môi trường (dữ liệu vẫn giữ)
$P down                    # gỡ container (dữ liệu vẫn giữ trong volume)
# $P down -v               # ⚠ XÓA LUÔN DỮ LIỆU — đừng chạy với prd
```

Muốn tiết kiệm RAM thì dừng dev/test khi không dùng: `docker compose -p nhaminh-dev ... stop`.

## 8. Chạy trên máy cá nhân (không đổi)

Như cũ: `mvn spring-boot:run` (profile mặc định `mysql`, MySQL ở localhost) và `npm run dev`. Muốn thử cấu hình giống server: đặt `SPRING_PROFILES_ACTIVE=mysql,dev`.

## 9. Deploy tự động bằng GitHub Actions

Cả 2 repo đã có sẵn file `.github/workflows/ci-deploy.yml`:

| Sự kiện | Việc GitHub làm |
|---|---|
| Push `develop` | test/build → deploy **dev** |
| Push `test` | test/build → deploy **test** |
| Push `main` | test/build → **chờ bạn duyệt** → deploy **prd** |
| Pull request | chỉ test/build, không deploy |
| Tab Actions → *Run workflow* | chạy lại deploy cho nhánh đã chọn |

Test hỏng thì **không deploy**. Push ở repo nào thì server cũng lấy code mới nhất của **cả backend và frontend** cùng nhánh. Hai lần deploy cùng môi trường sẽ tự xếp hàng, không chạy chồng lên nhau.

### 9.1. Tạo SSH key cho GitHub (trên máy bạn)

```bash
ssh-keygen -t ed25519 -N "" -C "github-actions-nhaminh" -f nhaminh_deploy_key
```

Lệnh tạo ra 2 file: `nhaminh_deploy_key` (khóa bí mật, đưa cho GitHub) và `nhaminh_deploy_key.pub` (khóa công khai, đưa lên server).

### 9.2. Cho phép key này vào server, nhưng CHỈ được deploy

Trên server:

```bash
sudo -u deploy mkdir -p /home/deploy/.ssh && sudo chmod 700 /home/deploy/.ssh
sudo -u deploy nano /home/deploy/.ssh/authorized_keys
```

Thêm **một dòng**: phần đầu cố định như dưới, phần sau dán nội dung file `nhaminh_deploy_key.pub`:

```
command="/opt/nhaminh/bin/ci-deploy.sh",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ssh-ed25519 AAAA...nội-dung-key... github-actions-nhaminh
```

Nhờ `command=...`, key này chỉ chạy được `deploy dev|test|prd`. Lộ key thì kẻ gian cũng không mở được shell trên server.

Lấy "dấu vân tay" của server (để GitHub chắc chắn đang kết nối đúng server):

```bash
ssh-keyscan -p 22 <IP-hoặc-tên-server>        # chạy trên máy bạn, copy toàn bộ kết quả
```

### 9.3. Khai báo Secrets trên GitHub (làm cho CẢ 2 repo)

Vào **Settings → Secrets and variables → Actions → New repository secret**:

| Tên | Giá trị |
|---|---|
| `DEPLOY_HOST` | IP hoặc tên server (để SSH, **không phải** tên miền đi qua Cloudflare) |
| `DEPLOY_PORT` | cổng SSH (bỏ qua nếu là 22) |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_KEY` | toàn bộ nội dung file `nhaminh_deploy_key` (khóa bí mật) |
| `DEPLOY_KNOWN_HOSTS` | kết quả lệnh `ssh-keyscan` ở trên |

Sau đó **xóa file `nhaminh_deploy_key` trên máy bạn** (GitHub đã giữ).

### 9.4. Bắt buộc duyệt trước khi lên production

**Settings → Environments → `prd`** (tự xuất hiện sau lần chạy đầu, hoặc bấm *New environment* tạo trước) → bật **Required reviewers** → chọn bạn. Làm cho cả 2 repo. Từ đó mỗi lần push `main`, GitHub dừng lại chờ bạn bấm **Approve and deploy**.

> Lưu ý: *Required reviewers* miễn phí với repo **public**. Repo **private** cần gói GitHub Pro/Team. Nếu không có, thay bằng cách chỉ merge vào `main` khi đã chắc chắn, hoặc bật *Branch protection* cho `main`.

### 9.5. Thử

Push một commit nhỏ lên `develop`, rồi xem tab **Actions** của repo. Log deploy trên server nằm ở `/opt/nhaminh/<env>/deploy.log`.

**Cổng SSH:** GitHub Actions dùng nhiều IP thay đổi liên tục, nên cổng SSH phải mở ra Internet. Hãy dùng **đăng nhập bằng key, tắt đăng nhập bằng mật khẩu**: trong `/etc/ssh/sshd_config` đặt `PasswordAuthentication no`, rồi chạy `sudo systemctl restart ssh`.

## Cấu hình theo môi trường (tóm tắt)

| | dev | test | prd |
|---|---|---|---|
| Spring profile | `mysql,dev` | `mysql,test` | `mysql,prd` |
| `JWT_SECRET`, `DB_PASSWORD` | bắt buộc | bắt buộc, không có mặc định | bắt buộc, không có mặc định |
| Admin còn mật khẩu `12345` | cho phép | **chặn khởi động** | **chặn khởi động** |
| Xác minh email | tắt | tùy `.env` | bật (cần SMTP) |
| Log | DEBUG | INFO | WARN (app: INFO) |
| Google lập chỉ mục | không | không | có |
| Sao lưu trước deploy | không | không | có |
