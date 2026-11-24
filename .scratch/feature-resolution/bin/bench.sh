#!/usr/bin/env bash
# Payara 启动基准 — feature-resolution M0
# 重建自 develop 启动优化专项 bench.sh（原文件已丢失，从未进 git）
#
# 口径: 发出启动命令 → http://localhost:8080/ 首次返回任意 HTTP 响应的墙钟(ms)
#       第 1 轮冷启动仅标记; 热启动中位 = 第 2..N 轮中位（默认 N=6 → 5 热轮）
# 纪律: 结论只信同天同窗口 A/B; 环境噪声大时加大轮数
#
# 用法:
#   bench.sh setup <payara-web.zip> <label>   # 解压发行包 + create-domain（出厂配置）
#   bench.sh tune  <label>                    # 应用配置最优三项（Hazelcast off / 去 flashlight / 裁 gogo 系）
#   bench.sh run   <label> [rounds]           # N 轮启动计时, 写 result.csv + 打印热启动中位
#   bench.sh tomcat <catalina_home> [rounds]  # Tomcat 对照（同口径）
#
# run 目录: .scratch/feature-resolution/runs/<label>/{
#   install/    发行包副本 + 域（tune 直接改本副本, 不动原包）
#   result.csv  round,type,ms
#   manifest.json
# }
set -euo pipefail

BIN="$(cd "$(dirname "$0")" && pwd)"
PROG="$(cd "$BIN/.." && pwd)"
RUNS="$PROG/runs"
ROUNDS="${ROUNDS:-6}"
TIMEOUT_S="${TIMEOUT_S:-180}"
DOMAIN="bench"
ZIP_DEFAULT="/d/AIspace/Payara/appserver/distributions/payara-web/target/payara-web.zip"

now_ms() { date +%s%3N; }
die() { echo "bench: $*" >&2; exit 1; }

# 域与 asadmin 路径（asadmin sh 脚本在 Git Bash 下直接可用; 传给 Windows java 的参数用混合路径）
gf_bin() {  # $1=run_dir → glassfish/bin（兼容 zip 根 payara7/）
  local run="$1"
  if [ -d "$run/install/glassfish/bin" ]; then echo "$run/install/glassfish/bin"
  elif [ -d "$run/install/payara7/glassfish/bin" ]; then echo "$run/install/payara7/glassfish/bin"
  else die "找不到 glassfish/bin: $run/install"; fi
}
asadmin() {  # asadmin <run_dir> <args...>
  local run="$1"; shift
  sh "$(gf_bin "$run")/asadmin" --interactive=false "$@"
}
dom_dir() { cygpath -m "$1/install/domains" 2>/dev/null || echo "$1/install/domains"; }

wait_http() {  # $1=url $2=timeout_s → stdout: elapsed_ms; 超时 return 1
  local url="$1" t0 end
  t0=$(now_ms); end=$(( t0 + $2 * 1000 ))
  while :; do
    if curl -s -o /dev/null --max-time 2 "$url" 2>/dev/null; then
      echo $(( $(now_ms) - t0 )); return 0
    fi
    [ "$(now_ms)" -gt "$end" ] && return 1
    sleep 0.05
  done
}
wait_http_down() {  # $1=url $2=timeout_s
  local url="$1" end
  end=$(( $(now_ms) + $2 * 1000 ))
  while curl -s -o /dev/null --max-time 2 "$url" 2>/dev/null; do
    [ "$(now_ms)" -gt "$end" ] && return 1
    sleep 0.2
  done
}

sha256() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1 || python -I -c "import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())" "$1"; }

setup() {  # setup <zip> <label>
  local zip="$1" label="$2"
  local run="$RUNS/$label"
  [ -f "$zip" ] || die "zip 不存在: $zip"
  [ -e "$run" ] && die "run 目录已存在: $run"
  mkdir -p "$run"
  python -I -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$zip" "$run/install"
  asadmin "$run" create-domain --nopassword \
    --domaindir "$(dom_dir "$run")" --adminport 4848 --instanceport 8080 "$DOMAIN" \
    > "$run/setup.log" 2>&1 || die "create-domain 失败, 见 $run/setup.log"
  cat > "$run/manifest.json" <<EOF
{
  "label": "$label", "created": "$(date -Iseconds)",
  "artifact": {"zip": "$zip", "sha256": "$(sha256 "$zip")"},
  "config": "factory",
  "java": "$(java -version 2>&1 | head -1 | tr -d '\"')",
  "host": "$(hostname)",
  "caliber": "start-cmd -> first HTTP response on :8080, median of rounds 2..N"
}
EOF
  echo "setup 完成: $run"
}

tune() {  # tune <label> — 配置最优三项
  local label="$1"
  local run="$RUNS/$label"
  [ -d "$run/install" ] || die "先 setup: $run"
  echo "== tune 1/3: Hazelcast disabled =="
  asadmin "$run" start-domain --domaindir "$(dom_dir "$run")" "$DOMAIN" > "$run/tune.log" 2>&1 \
    || die "tune 启动域失败, 见 $run/tune.log"
  wait_http "http://localhost:4848" 120 > /dev/null || die "4848 未就绪"
  asadmin "$run" set-hazelcast-configuration --enabled=false >> "$run/tune.log" 2>&1 \
    || die "set-hazelcast-configuration 失败, 见 $run/tune.log"
  asadmin "$run" stop-domain --domaindir "$(dom_dir "$run")" "$DOMAIN" >> "$run/tune.log" 2>&1
  wait_http_down "http://localhost:4848" 60
  echo "== tune 2/3: 去 flashlight-agent =="
  local dxml="$run/install/domains/$DOMAIN/config/domain.xml"
  [ -f "$dxml" ] || die "domain.xml 不存在: $dxml"
  sed -i '/flashlight-agent/d' "$dxml"
  echo "== tune 3/3: 裁 autostart 调试 bundle（gogo 系 + shell）=="
  local autostart_dir
  autostart_dir="$(dirname "$(gf_bin "$run")")/modules/autostart"
  rm -f "$autostart_dir/"*gogo*.jar "$autostart_dir/"*shell*.jar
  # 注意: 特性声明域(经 FeatureResolver)不要对同一 run 再跑 tune 3/3——
  # 解析器校验清单 jar 的存在性, 文件被删会导致启动 fail-fast
  python -I - "$run/manifest.json" <<'PY'
import json, sys, datetime
p = sys.argv[1]
m = json.load(open(p, encoding="utf-8"))
m["config"] = "tuned"
m["config_diff"] = [
  "hazelcast disabled (asadmin set-hazelcast-configuration --enabled=false)",
  "removed jvm-options lines matching /flashlight-agent/ from domain.xml",
  "removed modules/autostart/*gogo*.jar and *shell*.jar (4 debug bundles)",
]
m["tuned_at"] = datetime.datetime.now().isoformat()
json.dump(m, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
  echo "tune 完成: $run"
}

run_payara() {  # run <label> [rounds]
  local label="$1" rounds="${2:-$ROUNDS}"
  local run="$RUNS/$label"
  [ -d "$run/install" ] || die "先 setup: $run"
  local csv="$run/result.csv"; echo "round,type,ms" > "$csv"
  local i type ms bgpid
  for i in $(seq 1 "$rounds"); do
    type=$([ "$i" -eq 1 ] && echo cold || echo hot)
    ( asadmin "$run" start-domain --domaindir "$(dom_dir "$run")" "$DOMAIN" >/dev/null 2>&1 ) & bgpid=$!
    if ms=$(wait_http "http://localhost:8080/" "$TIMEOUT_S"); then
      echo "$i,$type,$ms" | tee -a "$csv"
    else
      echo "$i,$type,DNF>$TIMEOUT_S" | tee -a "$csv"; kill "$bgpid" 2>/dev/null || true
    fi
    asadmin "$run" stop-domain --domaindir "$(dom_dir "$run")" "$DOMAIN" > /dev/null 2>&1 || true
    wait_http_down "http://localhost:8080/" 90
    sleep 2
  done
  local median
  median=$(python -I - "$csv" <<'PY'
import sys, statistics
hot = [int(l.split(",")[2]) for l in open(sys.argv[1]).read().splitlines()[1:]
       if l.split(",")[1] == "hot" and l.split(",")[2].isdigit()]
print(int(statistics.median(hot)) if hot else "N/A")
PY
)
  python -I - "$run/manifest.json" "$median" "$rounds" <<'PY'
import json, sys, datetime
p, med, rounds = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(p, encoding="utf-8"))
m.setdefault("runs", []).append({"at": datetime.datetime.now().isoformat(),
                                 "rounds": int(rounds), "hot_median_ms": med})
json.dump(m, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
  echo "== $label 热启动中位: ${median}ms ($rounds 轮, result.csv 见 $csv) =="
}

run_tomcat() {  # tomcat <catalina_home> [rounds]
  local ch="$1" rounds="${2:-$ROUNDS}"
  [ -d "$ch/bin" ] || die "CATALINA_HOME 无效: $ch"
  local run="$RUNS/$(ts)-tomcat"; mkdir -p "$run"
  local csv="$run/result.csv"; echo "round,type,ms" > "$csv"
  local i type ms
  for i in $(seq 1 "$rounds"); do
    type=$([ "$i" -eq 1 ] && echo cold || echo hot)
    ( unset CATALINA_HOME CATALINA_BASE; sh "$ch/bin/startup.sh" >/dev/null 2>&1 ) || true
    if ms=$(wait_http "http://localhost:8080/" "$TIMEOUT_S"); then
      echo "$i,$type,$ms" | tee -a "$csv"
    else
      echo "$i,$type,DNF>$TIMEOUT_S" | tee -a "$csv"
    fi
    ( unset CATALINA_HOME CATALINA_BASE; sh "$ch/bin/shutdown.sh" >/dev/null 2>&1 ) || true
    wait_http_down "http://localhost:8080/" 90; sleep 2
  done
  python -I - "$csv" <<'PY'
import sys, statistics
hot = [int(l.split(",")[2]) for l in open(sys.argv[1]).read().splitlines()[1:]
       if l.split(",")[1] == "hot" and l.split(",")[2].isdigit()]
print("tomcat 热启动中位:", int(statistics.median(hot)) if hot else "N/A")
PY
}

ts() { date +%Y%m%d-%H%M%S; }

case "${1:-}" in
  setup)  shift; setup "${1:-$ZIP_DEFAULT}" "${2:?label 必填}" ;;
  tune)   shift; tune "${1:?label 必填}" ;;
  run)    shift; run_payara "$@" ;;
  tomcat) shift; run_tomcat "$@" ;;
  *) sed -n '2,16p' "$0"; exit 1 ;;
esac
