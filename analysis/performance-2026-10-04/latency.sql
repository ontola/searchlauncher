WITH input AS (
 SELECT ts,lead(ts,1,9223372036854775807) OVER (ORDER BY ts) next_ts
 FROM slice WHERE name='SL:SearchScreen.updateQuery'
), timing AS (
 SELECT ts,
 (SELECT min(s.ts) FROM slice s WHERE s.name='SL:SearchRepository.searchApps' AND s.ts>=i.ts AND s.ts<i.next_ts) search_ts,
 (SELECT min(s.ts+s.dur) FROM slice s WHERE s.name='SL:SearchRepository.searchApps' AND s.ts>=i.ts AND s.ts<i.next_ts) ready_ts,
 (SELECT min(s.ts) FROM slice s WHERE s.name='SL:SearchScreen.drawFirstResult' AND s.ts>=i.ts AND s.ts<i.next_ts) draw_ts
 FROM input i
)
SELECT round(ts/1e6,3) input_ms, round((search_ts-ts)/1e6,3) launch_ms, round((ready_ts-search_ts)/1e6,3) search_ms, round((draw_ts-ts)/1e6,3) first_draw_ms
FROM timing WHERE search_ts IS NOT NULL;
