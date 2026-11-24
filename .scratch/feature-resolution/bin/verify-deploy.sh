#!/usr/bin/env bash
# 部署验证 — feature-resolution M3-②
# 前置: 服务器已用 start-domain --verbose 启动(后台), 启动前已把 server.log 起始字节数写入 <run>/logbase.txt,
#       服务器控制台输出写入 <run>/boot.log
# 用法: verify-deploy.sh <label> <war> [path]   例: verify-deploy.sh m1-census /tmp/hello/hello.war /hello
# 步骤: 等启动完成 → 安装集/未解析汇总 → 管理面探测 → asadmin deploy → 轮询端点 → 本轮 SEVERE 数 → stop-domain
# 只用 asadmin stop-domain 正常停机, 不强杀进程。
set -uo pipefail

PROG="$(cd "$(dirname "$0")/.." && pwd)"
label="${1:?label 必填}"; war="${2:?war 必填}"; path="${3:-/hello}"
run="$PROG/runs/$label"
dom="$run/install/domains/bench"
log="$dom/logs/server.log"
base="$(cat "$run/logbase.txt" 2>/dev/null || echo 0)"
JPS=/d/Java/jdk-21.0.12.1/bin/jps.exe

asadmin() { sh "$run/install/payara7/glassfish/bin/asadmin" --interactive=false "$@"; }
newlog() { tail -c +"$((base + 1))" "$log" 2>/dev/null; }

t0=$(date +%s)
state=TIMEOUT
while :; do
  if newlog | grep -aq "startup time"; then state=UP; break; fi
  if grep -aqE 'Command start-domain failed|Exception in thread "main"' "$run/boot.log" 2>/dev/null; then state=FAILED; break; fi
  [ $(( $(date +%s) - t0 )) -ge 240 ] && break
  sleep 1
done
echo "[1] 启动状态=$state 等待=$(( $(date +%s) - t0 ))s"
newlog | grep -a "startup time" | sed 's/^.*Payara Server/Payara Server/' | head -1
echo "    安装 bundle 数(含 system)=$(find "$dom/osgi-cache/felix" -maxdepth 1 -name 'bundle*' -type d 2>/dev/null | wc -l)"
echo "    未解析 bundle: $(newlog | grep -ao 'Unable to resolve [A-Za-z0-9._-]*' | sed 's/Unable to resolve //' | LC_ALL=C sort -u | tr '\n' ' ')"

[ "$state" = UP ] || { echo "启动未成功, 不做部署验证"; exit 1; }

echo "[2] 管理面探测"
for u in /management/domain /management/domain/applications; do
  printf '    GET %s → ' "$u"
  curl -s -o /dev/null -w '%{http_code} %{content_type}\n' -m 15 "http://localhost:4848$u"
done
printf '    GET /__asadmin/version → '
curl -s -m 15 http://localhost:4848/__asadmin/version | grep -a "Exit Code" | tr -d '\n'
echo "  (kernel 自带通道, 不依赖 REST)"

# CLI 固定用 127.0.0.1: --host localhost 下曾间歇性出现 "No buffer space available ... connect"
echo "[3] asadmin deploy $(basename "$war")"
asadmin --host 127.0.0.1 --port 4848 deploy "$war" > "$run/last-deploy.txt" 2>&1
echo "    CLI 退出码=$? ; 输出中 Usage 段之前的部分:"
sed -n '1,/^Usage:/p' "$run/last-deploy.txt" | head -12 | sed 's/^/    | /'

echo "[4] 轮询 http://localhost:8080$path"
printf '    参照: GET / → '; curl -s -o /dev/null -w '%{http_code} %{errormsg}\n' -m 5 http://localhost:8080/
code=""
for i in $(seq 1 90); do
  code="$(curl -s -o "$run/last-response.txt" -w '%{http_code}' -m 5 "http://localhost:8080$path")"
  [ "$code" = 200 ] && break
  sleep 1
done
echo "    → HTTP $code"
[ "$code" = 200 ] && cat "$run/last-response.txt"
[ "$code" != 200 ] && { printf '    最后一次探测: '; curl -s -o /dev/null -w '%{http_code} %{errormsg} (%{time_total}s)\n' -m 5 "http://localhost:8080$path"; }

echo "[5] 本轮日志: SEVERE=$(newlog | grep -ac 'levelValue: 1000\]') WARNING=$(newlog | grep -ac 'levelValue: 900\]')"
newlog > "$run/this-boot.log"
newlog | grep -a -A3 'levelValue: 1000\]' | grep -aE '^\[|Exception|Error' | cut -c1-200 | head -12

echo "[6] 停机（只用服务器自身的 stop-domain 命令, 不强杀）"
server_alive() { "$JPS" -l 2>/dev/null | grep -q "bootstrap.ASMain"; }
asadmin stop-domain --domaindir "$run/install/domains" bench 2>&1 | tail -1
for i in $(seq 1 20); do server_alive || { echo "    经 asadmin stop-domain 正常退出"; exit 0; }; sleep 1; done
echo "    asadmin stop-domain 未能停机, 改走 /__asadmin/stop-domain（kernel 自带, 不依赖 REST）"
curl -s -o "$run/last-stop.txt" -m 30 http://localhost:4848/__asadmin/stop-domain
grep -a "Exit Code" "$run/last-stop.txt" | head -1
for i in $(seq 1 60); do server_alive || { echo "    经 /__asadmin/stop-domain 正常退出"; exit 0; }; sleep 1; done
echo "    服务器 JVM 仍在运行(未强杀), 需人工处理"
