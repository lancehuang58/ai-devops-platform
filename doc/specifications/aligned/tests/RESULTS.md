# PostgreSQL 實測結果（2026-10-26）

- 環境：PostgreSQL 17.11（Debian），單機 cluster。
- 執行：`./run_tests.sh`。腳本會清空 public schema，從 `../alert-event-and-dedup-design.md` 擷取 §3 的 DDL 重新載入（20 張表全部成功建立），再載入 `correlation_concurrency_test.sql` 的測試函式。並行案例用多個 psql 行程同時送出。
- 連續執行兩次，結果相同。

| 測試 | 對應 | 結果 |
|---|---|---|
| T0：20 個並行交易送同一 fingerprint，資源不在關聯範圍 | DT-02（回歸） | 群組 1、`occurrence_count=20`、案件 1、候選 0 ✅ |
| T1：10 個關聯範圍內的資源並行開新群組 | DT-19 | 候選 1、成員 10、**案件 0、Jira 0**、`CANDIDATE_OPENED` 1 則 ✅ |
| T2：同一候選 6 個並行確認（5 位使用者＋系統） | DT-21 | 1 個 CONFIRMED，其他 5 個回 NOOP；案件 1、Jira CREATE 1；10 個群組都連到該案件 ✅ |
| T2b：確認後 root（儲存本身）才告警 | §5.4 | 自動併入（`ATTACHED_LATE`），仍是 1 個案件、11 個群組 ✅ |
| T3：hold 到期 sweeper 與第二個成員並行，20 次（一半 sweeper 先到、一半成員先到） | DT-20 | 每個候選最多 1 個案件；10 次「先開單一案件、成員待確認併入」，10 次「無案件、待確認」✅ |
| T4：hold 到期開單一案件 → 下游加入 → 確認 | DT-15、DT-16 | 仍是 1 個案件、2 個群組；Jira CREATE 1、COMMENT 1 ✅ |
| T5：兩個成員「全部獨立」 | DT-17 | 拆分前 0 案件，拆分後 2 案件；關聯鍵已釋放，下一個同鍵告警建立新候選 ✅ |
| T6：`auto_confirm=true` 規則，root 告警到齊 | DT-12 | 第二個成員（root）加入時系統自動確認，`confirmation_source=SYSTEM_RULE`；之後的成員自動併入；1 個案件、3 個群組 ✅ |
| 不變量 | §5.10 | 同一群組在兩個案件 0；重複 `CANDIDATE_OPENED` 0；PENDING 成員卻已有案件 0 ✅ |

未涵蓋（需在應用程式整合測試中驗證）：Teams 卡片原地更新與 30 秒節流、提醒排程、`DISMISSED` 與重開窗計時、拆分時的「誤報」分支、hold 期間的實際時間行為（測試中以把 `hold_until` 設為過去來模擬到期）。

## 原始輸出

```text
scope rows: 67
== T0 (DT-02 regression) 20 parallel FIRING, same fingerprint, uncovered resource
      1 CASE
     19 COUNTED
groups=1 occ=20
cases=1 candidates=0
== T1 (DT-19) 10 covered resources open new groups in parallel
candidates=1 members=10 cases=0
candidate_status=AWAITING_CONFIRMATION
CANDIDATE_OPENED notifications=1
jira_requests_for_svc=0
== T2 (DT-21) 6 parallel confirms on that candidate (5 users + system)
CONFIRMED
NOOP:CONFIRMED
NOOP:CONFIRMED
NOOP:CONFIRMED
NOOP:CONFIRMED
NOOP:CONFIRMED
cases_for_candidate=1 groups_linked=10
jira_create=1
late joiner (storage root itself): ATTACHED_LATE
cases_for_candidate_after_late=1 groups_linked=11
== T3 (DT-20) hold expiry vs 2nd member, 20 races
max_cases_per_candidate=1
candidates_without_two_members=0
outcome AWAITING_CONFIRMATION item=false x10
outcome AWAITING_CONFIRMATION item=true x10
== T4 (DT-16) released single, later joiner, confirm -> still 1 case
release: RELEASED  join: JOINED_FROM_RELEASED_SINGLE  confirm: CONFIRMED
cases=1 groups=2
jira COMMENT=1
jira CREATE=1
== T5 (DT-17) split all -> each member own case; key released; next alert -> new candidate
before split cases: 0  split: SPLIT
cases_after_split=2
next alert on same key: NEW_CANDIDATE
== T6 (DT-12) auto_confirm rule with root evidence
app-01: NEW_CANDIDATE
db-01 : JOINED_AUTO_CONFIRMED
app-02: ATTACHED_LATE
status=CONFIRMED source=SYSTEM_RULE
cases=1 groups=3
== Invariants
groups_in_two_cases=0
duplicate_CANDIDATE_OPENED=0
pending_members_with_case=0
```
