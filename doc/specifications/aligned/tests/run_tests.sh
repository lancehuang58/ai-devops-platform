#!/usr/bin/env bash
# 需要：PostgreSQL 16+ cluster（預設 /tmp socket、port 55432）。腳本會清空 public schema，重新載入設計文件 §3 的 DDL 與測試函式；請只用在測試資料庫。
set -euo pipefail
export PGOPTIONS="-c client_min_messages=warning"
PSQL="psql -h ${PGHOST:-/tmp} -p ${PGPORT:-55432} -U ${PGUSER:-postgres} -v ON_ERROR_STOP=1 -Atq"
q() { $PSQL -c "$1"; }

DIR="$(cd "$(dirname "$0")" && pwd)"
# 每次從乾淨的 schema 開始：重新載入設計文件 §3 的 DDL（第一個 ```sql 區塊）與測試函式
python3 - "$DIR/../alert-event-and-dedup-design.md" > /tmp/ddl_from_doc.sql <<'PY'
import re, sys
s = open(sys.argv[1], encoding='utf-8').read()
print(re.search(r'```sql\n(.*?)```', s, re.S).group(1))
PY
q "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
$PSQL -f /tmp/ddl_from_doc.sql
$PSQL -f "$DIR/correlation_concurrency_test.sql"
q "INSERT INTO monitor_source (source_id, source_type, display_name, normalizer_ref, enabled)
   VALUES ('am-test','PROMETHEUS_ALERTMANAGER','test','am-v1',true) ON CONFLICT DO NOTHING;
   INSERT INTO symptom_type (symptom_type, description) VALUES ('SERVICE_UNAVAILABLE','x'),('STORAGE_UNRESPONSIVE','x') ON CONFLICT DO NOTHING;"

# 拓樸：st-nas-01 <- vm-01..vm-05 <- svc-01..svc-10；db-01 <- app-01..app-05；st-r1..st-r20 <- vmr-1..vmr-20；st-x <- vx-1,vx-2；st-solo <- vsolo
q "INSERT INTO t_resource VALUES ('st-nas-01','STORAGE','PROD'),('db-01','DATABASE','PROD'),('st-x','STORAGE','PROD'),('st-solo','STORAGE','PROD'),('svc-free','SERVICE','PROD');
   INSERT INTO t_resource SELECT 'st-r'||i,'STORAGE','PROD' FROM generate_series(1,20) i;
   INSERT INTO resource_relation SELECT 'vm-0'||i,'st-nas-01' FROM generate_series(1,5) i;
   INSERT INTO resource_relation SELECT 'svc-'||lpad(i::text,2,'0'),'vm-0'||((i-1)%5+1) FROM generate_series(1,10) i;
   INSERT INTO resource_relation SELECT 'app-0'||i,'db-01' FROM generate_series(1,5) i;
   INSERT INTO resource_relation SELECT 'vmr-'||i,'st-r'||i FROM generate_series(1,20) i;
   INSERT INTO resource_relation VALUES ('vx-1','st-x'),('vx-2','st-x'),('vsolo','st-solo');
   INSERT INTO correlation_rule (rule_id,version,upstream_selector,enabled,auto_confirm) VALUES
     ('storage-deps',1,'{\"resourceType\":\"STORAGE\"}',true,false),
     ('db-deps',1,'{\"resourceType\":\"DATABASE\"}',true,true);"
echo "scope rows: $(q 'SELECT t_rebuild_scope()')"

echo "== T0 (DT-02 regression) 20 parallel FIRING, same fingerprint, uncovered resource"
for i in $(seq 1 20); do q "SELECT t_ingest('PROD','svc-free','SERVICE_UNAVAILABLE','free-$i')" & done | sort | uniq -c; wait
q "SELECT 'groups='||count(*)||' occ='||max(occurrence_count) FROM alert_group WHERE resource_id='svc-free'"
q "SELECT 'cases='||count(DISTINCT item_id)||' candidates='||count(candidate_id) FROM alert_group WHERE resource_id='svc-free'"

echo "== T1 (DT-19) 10 covered resources open new groups in parallel"
for i in $(seq -w 1 10); do q "SELECT t_ingest('PROD','svc-$i','SERVICE_UNAVAILABLE','svc-$i-e1')" > /dev/null & done; wait
q "SELECT 'candidates='||count(DISTINCT candidate_id)||' members='||count(*)||' cases='||count(item_id) FROM alert_group WHERE resource_id LIKE 'svc-0%' OR resource_id='svc-10'"
q "SELECT 'candidate_status='||status FROM incident_candidate c WHERE EXISTS (SELECT 1 FROM alert_group g WHERE g.candidate_id=c.candidate_id AND g.resource_id='svc-01')"
q "SELECT 'CANDIDATE_OPENED notifications='||count(*) FROM notification_outbox WHERE kind='CANDIDATE_OPENED'"
q "SELECT 'jira_requests_for_svc='||count(*) FROM external_sync_request s JOIN alert_group g ON g.item_id=s.item_id WHERE g.resource_id LIKE 'svc-0%' OR g.resource_id='svc-10'"
CID=$(q "SELECT candidate_id FROM alert_group WHERE resource_id='svc-01'")

echo "== T2 (DT-21) 6 parallel confirms on that candidate (5 users + system)"
for u in u1 u2 u3 u4 u5; do q "SELECT t_confirm('$CID','$u')" & done; q "SELECT t_confirm('$CID','system:rule','SYSTEM_RULE')" & wait
q "SELECT 'cases_for_candidate='||count(DISTINCT w.item_id)||' groups_linked='||count(*) FROM work_item_alert_group w JOIN alert_group g USING (group_id) WHERE g.candidate_id='$CID'"
q "SELECT 'jira_create='||count(*) FROM external_sync_request s JOIN incident_candidate c ON c.item_id=s.item_id WHERE c.candidate_id='$CID' AND operation='CREATE'"
echo "late joiner (storage root itself): $(q "SELECT t_ingest('PROD','st-nas-01','STORAGE_UNRESPONSIVE','nas-e1')")"
q "SELECT 'cases_for_candidate_after_late='||count(DISTINCT w.item_id)||' groups_linked='||count(*) FROM work_item_alert_group w JOIN alert_group g USING (group_id) WHERE g.candidate_id='$CID'"

echo "== T3 (DT-20) hold expiry vs 2nd member, 20 races"
for i in $(seq 1 20); do q "SELECT t_ingest('PROD','st-r$i','STORAGE_UNRESPONSIVE','r$i-root')" > /dev/null; done
q "UPDATE incident_candidate SET hold_until = now() - interval '1 second' WHERE status='HOLDING' AND root_resource_id LIKE 'st-r%'"
for i in $(seq 1 20); do
  C=$(q "SELECT candidate_id FROM alert_group WHERE resource_id='st-r$i'")
  if [ $((i % 2)) -eq 0 ]; then D1=0.05; D2=0; else D1=0; D2=0.05; fi   # 交替誰先到
  q "SELECT pg_sleep($D1), t_release('$C')" > /dev/null & q "SELECT pg_sleep($D2), t_ingest('PROD','vmr-$i','SERVICE_UNAVAILABLE','r$i-vm')" > /dev/null &
done; wait
q "SELECT 'max_cases_per_candidate='||max(n) FROM (SELECT g.candidate_id, count(DISTINCT g.item_id) n FROM alert_group g WHERE g.resource_id LIKE 'st-r%' OR g.resource_id LIKE 'vmr-%' GROUP BY 1) x"
q "SELECT 'candidates_without_two_members='||count(*) FROM incident_candidate c WHERE root_resource_id LIKE 'st-r%' AND (SELECT count(*) FROM incident_candidate_member m WHERE m.candidate_id=c.candidate_id)<>2"
q "SELECT 'outcome '||status||' item='||(item_id IS NOT NULL)||' x'||count(*) FROM incident_candidate WHERE root_resource_id LIKE 'st-r%' GROUP BY status, item_id IS NOT NULL ORDER BY 1"

echo "== T4 (DT-16) released single, later joiner, confirm -> still 1 case"
q "SELECT t_ingest('PROD','st-solo','STORAGE_UNRESPONSIVE','solo-root')" >/dev/null
S=$(q "SELECT candidate_id FROM alert_group WHERE resource_id='st-solo'")
q "UPDATE incident_candidate SET hold_until=now()-interval '1 second' WHERE candidate_id='$S'"
echo "release: $(q "SELECT t_release('$S')")  join: $(q "SELECT t_ingest('PROD','vsolo','SERVICE_UNAVAILABLE','solo-vm')")  confirm: $(q "SELECT t_confirm('$S','u1')")"
q "SELECT 'cases='||count(DISTINCT item_id)||' groups='||count(*) FROM alert_group WHERE candidate_id='$S'"
q "SELECT 'jira '||operation||'='||count(*) FROM external_sync_request s JOIN incident_candidate c ON c.item_id=s.item_id WHERE c.candidate_id='$S' GROUP BY operation ORDER BY 1"

echo "== T5 (DT-17) split all -> each member own case; key released; next alert -> new candidate"
q "SELECT t_ingest('PROD','vx-1','SERVICE_UNAVAILABLE','x1')" >/dev/null; q "SELECT t_ingest('PROD','vx-2','SERVICE_UNAVAILABLE','x2')" >/dev/null
X=$(q "SELECT candidate_id FROM alert_group WHERE resource_id='vx-1'")
echo "before split cases: $(q "SELECT count(item_id) FROM alert_group WHERE candidate_id='$X'")  split: $(q "SELECT t_split_all('$X','u2')")"
q "SELECT 'cases_after_split='||count(DISTINCT item_id) FROM alert_group WHERE candidate_id='$X'"
echo "next alert on same key: $(q "SELECT t_ingest('PROD','st-x','STORAGE_UNRESPONSIVE','x-root')")"

echo "== T6 (DT-12) auto_confirm rule with root evidence"
echo "app-01: $(q "SELECT t_ingest('PROD','app-01','SERVICE_UNAVAILABLE','a1')")"
echo "db-01 : $(q "SELECT t_ingest('PROD','db-01','SERVICE_UNAVAILABLE','d1')")"
echo "app-02: $(q "SELECT t_ingest('PROD','app-02','SERVICE_UNAVAILABLE','a2')")"
D=$(q "SELECT candidate_id FROM alert_group WHERE resource_id='db-01'")
q "SELECT 'status='||status||' source='||confirmation_source FROM incident_candidate WHERE candidate_id='$D'"
q "SELECT 'cases='||count(DISTINCT item_id)||' groups='||count(*) FROM alert_group WHERE candidate_id='$D'"

echo "== Invariants"
q "SELECT 'groups_in_two_cases='||count(*) FROM (SELECT group_id FROM work_item_alert_group WHERE link_type IN ('PRIMARY','CORRELATED') GROUP BY 1 HAVING count(*)>1) x"
q "SELECT 'duplicate_CANDIDATE_OPENED='||count(*) FROM (SELECT candidate_id FROM notification_outbox WHERE kind='CANDIDATE_OPENED' GROUP BY 1 HAVING count(*)>1) x"
q "SELECT 'pending_members_with_case='||count(*) FROM incident_candidate_member m JOIN alert_group g USING (group_id) WHERE m.member_state='PENDING' AND g.item_id IS NOT NULL"
