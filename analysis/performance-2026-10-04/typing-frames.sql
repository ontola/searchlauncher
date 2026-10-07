WITH inputs AS (
  SELECT ts FROM slice
  WHERE name = 'SL:SearchScreen.updateQuery'
  ORDER BY ts LIMIT 12
), frames AS (
  SELECT s.ts, s.dur
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t USING(utid)
  JOIN process p USING(upid)
  WHERE p.name = 'com.searchlauncher.app.gecko' AND t.tid = p.pid
    AND s.name GLOB 'Choreographer#doFrame *'
    AND s.name NOT GLOB '*resynced*' AND s.dur > 0
    AND EXISTS (
      SELECT 1 FROM inputs i
      WHERE s.ts BETWEEN i.ts AND i.ts + 300000000
    )
)
SELECT round(dur / 1e6, 3) frame_cpu_ms FROM frames ORDER BY ts;
