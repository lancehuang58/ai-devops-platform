---
name: design-safe-domain-events
description: Design or implement domain events, outbox delivery, event handlers, and idempotent projections in modular Java applications. Use for event-driven workflows, commit/publication gaps, duplicate delivery, or event retry policy decisions.
---

# 先分類事件及副作用

1. 區分 command（要求做事）、domain event（已發生事實）、integration event（跨邊界契約）。列出 producer、consumer、資料所有權、授權、投遞與失敗政策。
2. 讓 domain 產生 immutable facts；service 原子保存 aggregate 與 outbox。禁止「DB save 後直接 publish」當成可靠投遞。Spring／Guava memory event bus 只是 adapter，不是 durable queue。
3. 定義 eventId、aggregateId、correlationId、occurredAt，正式持久契約補 schemaVersion、sequence、causationId、payload safe projection。不得用 timestamp 當唯一排序依據。
4. 只在所有同步 handler 成功後 acknowledge；失敗保留 pending。發布成功但 ack 失敗可再投遞，因此 consumer 用 eventId＋consumerId 去重，並把 dedup 標記與自身效果放同一交易。
5. 單程序範例可以用同步 bus；不要加 @Async 後仍把 publish 返回當成處理完成。無 consumer 不代表交付義務完成；正式 outbox 需保存各必需 consumer 的義務、版本與健康狀態。
6. 在設計中列出多 worker 的 claim／lease／owner fencing、同 aggregate 排序、有界重試、dead-letter／人工處理、監控和保留策略；未實作就明確標註，不以 memory Set 宣稱可靠 exactly-once。
7. 用測試注入 commit 失敗、consumer 失敗、publish 後 ack 前中斷、重複事件及亂序。斷言實際效果次數和未完成義務，不能只看 handler 被呼叫。

## ai-devops-platform 的三種通道

| 通道 | 用途 | 重送規則 |
|---|---|---|
| 通知事件 | 案件／工作事實與安全投影 | 僅已定義冪等 consumer，可靠實作完成前不自動排程 |
| execution_outbox | 維運首次派送 | 不因 fault／restart／lease 到期重播 CHANGE；timeout 查核僅既定一次唯讀例外 |
| record_outbox | Jira／Confluence 紀錄義務 | 依專屬 policy 有界補送；未知遠端效果先查核，避免重建與重複 transition |

不要讓 generic notification relay 讀 execution_outbox 或 record_outbox。事件到達不能繞過權限、健康或固定方案。事件驅動不等於必須 Kafka，也不等於 Event Sourcing。

目前 NotificationRelay 是無 scheduler 的單 worker 骨架，InMemoryWorkJournal 僅 testFixtures。若新增真實持久化，先完成 DB 交易／唯一鍵／consumer ledger 的整合測試，再啟用 worker。詳見 [來源與取捨](references/source-notes.md)。
