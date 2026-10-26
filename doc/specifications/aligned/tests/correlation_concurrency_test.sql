-- 測試用函式：依 alert-event-and-dedup-design.md §4.2 與 §5.6 實作，只用於驗證 DDL 與併發保證。
-- 不是正式實作（正式實作在 Java／Spring 的交易內完成，邏輯相同）。

-- 重建關聯範圍（§5.3）：root = 符合上游 resourceType 的資源；沿 DEPENDS_ON 反向，深度 ≤ max_depth
CREATE TABLE IF NOT EXISTS t_resource (resource_id text PRIMARY KEY, resource_type text NOT NULL, environment text NOT NULL);

CREATE OR REPLACE FUNCTION t_rebuild_scope() RETURNS integer LANGUAGE plpgsql AS $$
DECLARE n integer;
BEGIN
  DELETE FROM correlation_scope;
  INSERT INTO correlation_scope (rule_id, rule_version, environment, root_resource_id, resource_id, depth)
  WITH RECURSIVE roots AS (
      SELECT r.rule_id, r.version, r.max_depth, x.environment, x.resource_id AS root
        FROM correlation_rule r JOIN t_resource x ON x.resource_type = r.upstream_selector->>'resourceType'
       WHERE r.enabled),
  walk AS (
      SELECT rule_id, version, max_depth, environment, root, root AS resource_id, 0 AS depth FROM roots
      UNION
      SELECT w.rule_id, w.version, w.max_depth, w.environment, w.root, rr.from_resource_id, w.depth + 1
        FROM walk w JOIN resource_relation rr ON rr.to_resource_id = w.resource_id AND rr.relation_type = 'DEPENDS_ON'
       WHERE w.depth < w.max_depth)
  SELECT rule_id, version, environment, root, resource_id, min(depth)
    FROM walk GROUP BY rule_id, version, environment, root, resource_id;
  GET DIAGNOSTICS n = ROW_COUNT; RETURN n;
END $$;

-- §4.2 步驟 5：為單一群組開案
CREATE OR REPLACE FUNCTION t_open_case(p_group uuid, p_title text, p_link text DEFAULT 'PRIMARY') RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE v_item uuid; v_env text;
BEGIN
  SELECT environment INTO v_env FROM alert_group WHERE group_id = p_group;
  INSERT INTO work_item (item_no, item_type, status, source_kind, title, environment, signal_state)
  VALUES ('OPS-' || lpad(nextval('work_item_no_seq')::text, 10, '0'), 'INCIDENT', 'NEW', 'MONITOR', p_title, v_env, 'FIRING')
  RETURNING item_id INTO v_item;
  UPDATE alert_group SET item_id = v_item WHERE group_id = p_group;
  INSERT INTO work_item_alert_group (item_id, group_id, link_type) VALUES (v_item, p_group, p_link);
  INSERT INTO case_event (item_id, seq, event_id, type, actor, payload_safe)
  VALUES (v_item, 1, gen_random_uuid(), 'STATE_CHANGED', 'system', '{"to":"NEW"}');
  INSERT INTO external_sync_request (item_id, destination, operation, dedup_key, desired_version, payload_safe, payload_sha256)
  VALUES (v_item, 'JIRA', 'CREATE', 'jira:create:' || v_item, 1, '{}', repeat('0', 64));
  RETURN v_item;
END $$;

-- §5.5 系統確認證據 E1–E3
CREATE OR REPLACE FUNCTION t_evidence_ok(p_cid uuid) RETURNS boolean LANGUAGE sql AS $$
  WITH c AS (SELECT * FROM incident_candidate WHERE candidate_id = p_cid),
  rule AS (SELECT r.* FROM incident_candidate_key k JOIN correlation_rule r ON r.rule_id = k.rule_id AND r.version = k.rule_version
            WHERE k.candidate_id = p_cid AND r.auto_confirm LIMIT 1),
  root AS (SELECT g.* FROM incident_candidate_member m JOIN alert_group g USING (group_id)
            WHERE m.candidate_id = p_cid AND m.member_state = 'PENDING' AND m.depth_from_root = 0 AND g.status = 'FIRING' LIMIT 1)
  SELECT EXISTS (SELECT 1 FROM rule) AND EXISTS (SELECT 1 FROM root)
     AND (SELECT count(*) FROM incident_candidate_member WHERE candidate_id = p_cid AND member_state = 'PENDING') >= 2
     AND NOT EXISTS (SELECT 1 FROM incident_candidate_member m JOIN alert_group g USING (group_id), root, rule
                      WHERE m.candidate_id = p_cid AND m.member_state = 'PENDING'
                        AND abs(extract(epoch FROM g.first_seen_at - root.first_seen_at)) > rule.window_seconds)
$$;

-- §5.6 B：確認（系統或使用者）
CREATE OR REPLACE FUNCTION t_confirm(p_cid uuid, p_actor text, p_src text DEFAULT 'USER') RETURNS text LANGUAGE plpgsql AS $$
DECLARE c incident_candidate; v_item uuid; v_primary uuid; m record;
BEGIN
  SELECT * INTO c FROM incident_candidate WHERE candidate_id = p_cid FOR UPDATE;
  IF c.status <> 'AWAITING_CONFIRMATION' THEN RETURN 'NOOP:' || c.status; END IF;
  v_item := c.item_id;
  IF v_item IS NULL THEN
    SELECT m2.group_id INTO v_primary FROM incident_candidate_member m2 JOIN alert_group g USING (group_id)
     WHERE m2.candidate_id = p_cid AND m2.member_state = 'PENDING'
     ORDER BY m2.depth_from_root, g.first_seen_at LIMIT 1;
    v_item := t_open_case(v_primary, '連鎖事件：' || c.candidate_no);
  ELSE
    INSERT INTO external_sync_request (item_id, destination, operation, dedup_key, desired_version, payload_safe, payload_sha256)
    VALUES (v_item, 'JIRA', 'COMMENT', 'jira:corr-confirm:' || p_cid || ':' || c.row_version, 1, '{}', repeat('0', 64));
  END IF;
  FOR m IN SELECT group_id FROM incident_candidate_member WHERE candidate_id = p_cid AND member_state = 'PENDING' LOOP
    UPDATE alert_group SET item_id = v_item WHERE group_id = m.group_id;
    INSERT INTO work_item_alert_group (item_id, group_id, link_type) VALUES (v_item, m.group_id, 'CORRELATED')
    ON CONFLICT DO NOTHING;
  END LOOP;
  UPDATE incident_candidate_member SET member_state = 'IN_CASE', decided_by = p_actor, decided_at = now()
   WHERE candidate_id = p_cid AND member_state = 'PENDING';
  UPDATE incident_candidate SET status = 'CONFIRMED', item_id = v_item, confirmation_source = p_src, confirmed_by = p_actor,
         confirmed_at = now(), row_version = row_version + 1 WHERE candidate_id = p_cid;
  UPDATE notification_message SET render_version = render_version + 1 WHERE candidate_id = p_cid;
  RETURN 'CONFIRMED';
END $$;

-- §5.6 A：範圍內新群組加入或建立候選
CREATE OR REPLACE FUNCTION t_correlate(p_group uuid) RETURNS text LANGUAGE plpgsql AS $$
DECLARE g alert_group; v_keys text[]; v_depth integer; k text; c incident_candidate; v_cid uuid; v_rule record;
BEGIN
  SELECT * INTO g FROM alert_group WHERE group_id = p_group;
  SELECT array_agg(DISTINCT s.corr_key ORDER BY s.corr_key), min(s.depth) INTO v_keys, v_depth
    FROM correlation_scope s JOIN correlation_rule r ON r.rule_id = s.rule_id AND r.version = s.rule_version
   WHERE s.environment = g.environment AND s.resource_id = g.resource_id AND r.enabled;
  IF v_keys IS NULL THEN PERFORM t_open_case(p_group, 'single'); RETURN 'CASE'; END IF;
  FOREACH k IN ARRAY v_keys LOOP PERFORM pg_advisory_xact_lock(hashtextextended(k, 0)); END LOOP;
  SELECT ic.* INTO c FROM incident_candidate_key ck JOIN incident_candidate ic USING (candidate_id)
   WHERE ck.active_key = ANY (v_keys) ORDER BY ic.first_seen_at LIMIT 1 FOR UPDATE OF ic;
  IF NOT FOUND THEN
    SELECT r.* INTO v_rule FROM correlation_scope s JOIN correlation_rule r ON r.rule_id = s.rule_id AND r.version = s.rule_version
     WHERE s.corr_key = v_keys[1] LIMIT 1;
    INSERT INTO incident_candidate (candidate_no, environment, status, primary_group_id, root_resource_id, first_seen_at, hold_until, window_until)
    VALUES ('EVC-' || lpad(nextval('incident_candidate_no_seq')::text, 10, '0'), g.environment, 'HOLDING', p_group,
            CASE WHEN v_depth = 0 THEN g.resource_id END, g.first_seen_at,
            now() + make_interval(secs => v_rule.hold_seconds), g.first_seen_at + make_interval(secs => v_rule.window_seconds))
    RETURNING candidate_id INTO v_cid;
    INSERT INTO incident_candidate_key (candidate_id, corr_key, active_key, rule_id, rule_version)
    SELECT DISTINCT v_cid, s.corr_key, s.corr_key, s.rule_id, s.rule_version FROM correlation_scope s WHERE s.corr_key = ANY (v_keys);
    INSERT INTO incident_candidate_member (candidate_id, group_id, member_state, depth_from_root, active_group_id)
    VALUES (v_cid, p_group, 'PENDING', v_depth, p_group);
    UPDATE alert_group SET candidate_id = v_cid WHERE group_id = p_group;
    INSERT INTO notification_outbox (candidate_id, group_epoch, kind, payload_safe) VALUES (v_cid, 0, 'CANDIDATE_OPENED', '{}')
    ON CONFLICT ON CONSTRAINT uq_notify_once DO NOTHING;
    INSERT INTO notification_message (candidate_id, render_version) VALUES (v_cid, 1);
    RETURN 'NEW_CANDIDATE';
  END IF;
  v_cid := c.candidate_id;
  UPDATE alert_group SET candidate_id = v_cid WHERE group_id = p_group;
  INSERT INTO incident_candidate_key (candidate_id, corr_key, active_key, rule_id, rule_version)
  SELECT DISTINCT v_cid, s.corr_key, s.corr_key, s.rule_id, s.rule_version FROM correlation_scope s
   WHERE s.corr_key = ANY (v_keys) AND NOT EXISTS (SELECT 1 FROM incident_candidate_key x WHERE x.active_key = s.corr_key)
  ON CONFLICT DO NOTHING;
  UPDATE notification_message SET render_version = render_version + 1 WHERE candidate_id = v_cid;
  IF c.status = 'CONFIRMED' THEN
    INSERT INTO incident_candidate_member (candidate_id, group_id, member_state, depth_from_root, late_joined, active_group_id)
    VALUES (v_cid, p_group, 'IN_CASE', v_depth, true, p_group);
    UPDATE alert_group SET item_id = c.item_id WHERE group_id = p_group;
    INSERT INTO work_item_alert_group (item_id, group_id, link_type) VALUES (c.item_id, p_group, 'CORRELATED');
    INSERT INTO external_sync_request (item_id, destination, operation, dedup_key, desired_version, payload_safe, payload_sha256)
    VALUES (c.item_id, 'JIRA', 'COMMENT', 'jira:corr-attach:' || v_cid || ':' || p_group, 1, '{}', repeat('0', 64));
    RETURN 'ATTACHED_LATE';
  END IF;
  INSERT INTO incident_candidate_member (candidate_id, group_id, member_state, depth_from_root, active_group_id)
  VALUES (v_cid, p_group, 'PENDING', v_depth, p_group);
  IF c.status IN ('HOLDING', 'RELEASED_SINGLE') THEN
    UPDATE incident_candidate SET status = 'AWAITING_CONFIRMATION', row_version = row_version + 1 WHERE candidate_id = v_cid;
  END IF;
  IF t_evidence_ok(v_cid) THEN
    PERFORM t_confirm(v_cid, 'system:rule', 'SYSTEM_RULE');
    RETURN 'JOINED_AUTO_CONFIRMED';
  END IF;
  RETURN 'JOINED_FROM_' || c.status;
END $$;

-- §4.2：接收一筆 FIRING（冪等接收＋fingerprint 去重＋開案或候選）
CREATE OR REPLACE FUNCTION t_ingest(p_env text, p_res text, p_symptom text, p_src_event text) RETURNS text LANGUAGE plpgsql AS $$
DECLARE v_rid uuid; v_new boolean; v_fp char(64); v_gid uuid;
BEGIN
  INSERT INTO alert_receipt (source_id, source_event_id, payload_sha256, raw_alert)
  VALUES ('am-test', p_src_event, repeat('a', 64), '{}')
  ON CONFLICT (source_id, source_event_id) DO UPDATE SET duplicate_count = alert_receipt.duplicate_count + 1
  RETURNING receipt_id, (xmax = 0) INTO v_rid, v_new;
  IF NOT v_new THEN RETURN 'DUP_RECEIPT'; END IF;
  v_fp := encode(sha256(convert_to(p_env || '|' || p_res || '|' || p_symptom, 'UTF8')), 'hex');
  LOOP
    SELECT group_id INTO v_gid FROM alert_group WHERE active_fingerprint = v_fp FOR UPDATE;
    IF FOUND THEN
      UPDATE alert_group SET occurrence_count = occurrence_count + 1, suppressed_count = suppressed_count + 1, last_seen_at = now()
       WHERE group_id = v_gid;
      UPDATE alert_receipt SET group_id = v_gid WHERE receipt_id = v_rid;
      RETURN 'COUNTED';
    END IF;
    INSERT INTO alert_group (fingerprint, fingerprint_version, active_fingerprint, environment, resource_id, resource_resolution,
                             symptom_type, status, severity_current, severity_max, first_seen_at, last_seen_at, last_firing_at,
                             last_state_change_at, source_ids)
    VALUES (v_fp, 'fp-v1', v_fp, p_env, p_res, 'RESOLVED', p_symptom, 'FIRING', 'CRITICAL', 'CRITICAL', now(), now(), now(), now(),
            ARRAY['am-test'])
    ON CONFLICT (active_fingerprint) DO NOTHING RETURNING group_id INTO v_gid;
    IF v_gid IS NOT NULL THEN
      UPDATE alert_receipt SET group_id = v_gid, normalize_status = 'NORMALIZED' WHERE receipt_id = v_rid;
      RETURN t_correlate(v_gid);
    END IF;
  END LOOP;
END $$;

-- §5.6 C：hold 到期
CREATE OR REPLACE FUNCTION t_release(p_cid uuid) RETURNS text LANGUAGE plpgsql AS $$
DECLARE c incident_candidate; v_gid uuid; v_item uuid; n integer;
BEGIN
  SELECT * INTO c FROM incident_candidate WHERE candidate_id = p_cid AND status = 'HOLDING' AND hold_until <= now() FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN 'SKIP'; END IF;
  SELECT count(*), min(group_id::text)::uuid INTO n, v_gid FROM incident_candidate_member WHERE candidate_id = p_cid AND member_state = 'PENDING';
  IF n <> 1 THEN RETURN 'NOT_SINGLE'; END IF;
  v_item := t_open_case(v_gid, 'single');
  UPDATE incident_candidate_member SET member_state = 'IN_CASE' WHERE candidate_id = p_cid AND group_id = v_gid;
  UPDATE incident_candidate SET status = 'RELEASED_SINGLE', item_id = v_item, row_version = row_version + 1 WHERE candidate_id = p_cid;
  RETURN 'RELEASED';
END $$;

-- §5.6 D：全部獨立
CREATE OR REPLACE FUNCTION t_split_all(p_cid uuid, p_actor text) RETURNS text LANGUAGE plpgsql AS $$
DECLARE c incident_candidate; m record;
BEGIN
  SELECT * INTO c FROM incident_candidate WHERE candidate_id = p_cid FOR UPDATE;
  IF c.status <> 'AWAITING_CONFIRMATION' THEN RETURN 'NOOP:' || c.status; END IF;
  FOR m IN SELECT group_id FROM incident_candidate_member WHERE candidate_id = p_cid AND member_state = 'PENDING' LOOP
    PERFORM t_open_case(m.group_id, 'split');
    UPDATE incident_candidate_member SET member_state = 'SPLIT_OUT', active_group_id = NULL, decided_by = p_actor, decided_at = now()
     WHERE candidate_id = p_cid AND group_id = m.group_id;
  END LOOP;
  UPDATE incident_candidate_key SET active_key = NULL WHERE candidate_id = p_cid;
  UPDATE incident_candidate SET status = 'SPLIT', ended_at = now(), row_version = row_version + 1 WHERE candidate_id = p_cid;
  RETURN 'SPLIT';
END $$;
