#!/usr/bin/env bash
# M1 内核闭包普查驱动 — feature-resolution
# 用法: census.sh <label> [timeout_s]
# 候选集: runs/<label>/kernel-closure.txt（每行一个 jar 文件名, 支持 # 注释）
# 每轮: 候选集 → 域级 osgi.properties 覆盖(auto.install/auto.start) → 清 osgi-cache
#       → 启动 → 8080/4848 探测 → 日志取证 → 停机
set -euo pipefail

BIN="$(cd "$(dirname "$0")" && pwd)"
PROG="$(cd "$BIN/.." && pwd)"
RUNS="$PROG/runs"
DOMAIN="bench"

label="${1:?label 必填}"; to="${2:-90}"
run="$RUNS/$label"
closure="$run/kernel-closure.txt"
[ -f "$closure" ] || { echo "缺候选集: $closure"; exit 1; }

kill_stale() {  # 杀掉残留的、属于本 run 域的 ASMain JVM（DNF 轮 stop-domain 失败的遗留）
  for pid in $(jps -m 2>/dev/null | awk -v d="$label" '/ASMain/ && index($0, d) > 0 {print $1}'); do
    echo "杀残留 ASMain: $pid"
    taskkill //F //PID "$pid" > /dev/null 2>&1 || true
  done
  sleep 1
}
kill_stale

GF="$(ls -d "$run"/install/*/glassfish 2>/dev/null | head -1)"
[ -d "$GF/bin" ] || { echo "找不到 glassfish: $run/install"; exit 1; }
dom="$run/install/domains/$DOMAIN"
dist_osgi="$GF/config/osgi.properties"

python -I - "$dist_osgi" "$dom/config/osgi.properties" "$closure" "$GF/modules" <<'PY'
import os, re, sys
dist, out, closure, mods = sys.argv[1:5]
jars = [l.strip() for l in open(closure, encoding="utf-8")
        if l.strip() and not l.strip().startswith("#")]
def path(j):
    sub = "modules/autostart/" if os.path.exists(os.path.join(mods, "autostart", j)) else "modules/"
    return f" ${{com.sun.aas.installRootURI}}{sub}{j}"
lst = "\\\n" + " \\\n".join(path(j) for j in jars)
text = open(dist, encoding="utf-8").read()
def repl(name, val):
    global text
    pat = re.compile(r"^" + name + r"=[^\n]*(?:\n[ \t]+[^\n]*)*", re.M)
    assert pat.search(text), name
    text = pat.sub(name + "=" + val, text, count=1)
repl("glassfish.osgi.auto.install", lst)
repl("glassfish.osgi.auto.start", lst)
repl("felix.log.level", "3")
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print(f"override: {len(jars)} bundles")
PY

rm -rf "$dom/osgi-cache"
( sh "$GF/bin/asadmin" --interactive=false start-domain --verbose \
    --domaindir "$run/install/domains" "$DOMAIN" > "$run/boot-stdout.log" 2>&1 ) &

t0=$(date +%s%3N); end=$(( t0 + to * 1000 )); ms="DNF"
while :; do
  if curl -s -o /dev/null --max-time 2 "http://localhost:8080/" 2>/dev/null; then
    ms=$(( $(date +%s%3N) - t0 )); break
  fi
  [ "$(date +%s%3N)" -gt "$end" ] && break
  sleep 0.1
done

admin="NO"
if curl -s -o /dev/null --max-time 2 "http://localhost:4848/" 2>/dev/null; then admin="YES"; fi
echo "BOOT=${ms}ms ADMIN=$admin"

if [ "$ms" != "DNF" ]; then
  echo "== server.log SEVERE 摘要（前 15 条）=="
  if grep -qE "SEVERE" "$dom/logs/server.log" 2>/dev/null; then
    grep -E "SEVERE" "$dom/logs/server.log" | sed 's/^\t*//' | head -15
  else
    echo "(无)"
  fi
else
  echo "== boot-stdout 尾部 30 行 =="
  tail -30 "$run/boot-stdout.log" 2>/dev/null
  echo "== server.log 尾部 30 行 =="
  tail -30 "$dom/logs/server.log" 2>/dev/null || echo "(server.log 不存在)"
fi

sh "$GF/bin/asadmin" --interactive=false stop-domain \
  --domaindir "$run/install/domains" "$DOMAIN" > /dev/null 2>&1 || true
sleep 2
kill_stale
