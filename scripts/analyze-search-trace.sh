#!/usr/bin/env bash
set -euo pipefail

TRACE="${1:?Usage: $0 analysis/traces/file.perfetto-trace}"
TP="${TP:-.cache/perfetto/trace_processor}"

if [[ ! -x "$TP" ]]; then
  mkdir -p "$(dirname "$TP")"
  curl -L https://get.perfetto.dev/trace_processor -o "$TP"
  chmod +x "$TP"
fi

run_query() {
  local title="$1"
  local sql="$2"
  echo
  echo "== $title =="
  "$TP" -Q "$sql" "$TRACE"
}

run_query "SearchLauncher trace sections" "
select
  name,
  count(*) as n,
  round(avg(dur) / 1e6, 3) as avg_ms,
  round(max(dur) / 1e6, 3) as max_ms,
  round(sum(dur) / 1e6, 3) as total_ms
from slice
where name glob 'SL:*'
group by name
order by total_ms desc;
"

run_query "Slow SearchLauncher trace slices" "
select
  round(ts / 1e9, 3) as ts_s,
  round(dur / 1e6, 3) as dur_ms,
  name
from slice
where name glob 'SL:*'
order by dur desc
limit 40;
"

run_query "Slow Compose and frame slices" "
select
  round(ts / 1e9, 3) as ts_s,
  round(dur / 1e6, 3) as dur_ms,
  name
from slice
where
  name glob 'Compose:*' or
  name glob 'Recomposer:*' or
  name glob 'Choreographer#doFrame*'
order by dur desc
limit 40;
"

run_query "Slow main-thread slices for app process" "
select
  round(slice.ts / 1e9, 3) as ts_s,
  round(slice.dur / 1e6, 3) as dur_ms,
  slice.name,
  thread.name as thread_name
from slice
join thread_track on slice.track_id = thread_track.id
join thread using(utid)
join process using(upid)
where process.name = 'com.searchlauncher.app'
order by slice.dur desc
limit 60;
"

