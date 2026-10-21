---
name: model-domain-invariants
description: Model Java domain behavior with aggregates, value objects, invariants, immutable snapshots, and domain events. Use when implementing business state transitions or reviewing anemic models; do not use for persistence mappings alone.
---

# 從規則建立 Domain Model

1. 讀現行需求和狀態表，列出名詞、identity、ownership、合法轉移、前置條件及失敗語意。缺少政策時保留缺口，不擅自創造可恢復／可重試規則。
2. 選 Aggregate 作一致性邊界，將狀態變更寫成業務動詞。禁止 public setter 讓外部任意改狀態；避免只有 getter/setter 而規則全塞 service。
3. 用 immutable value objects／records 表達識別與安全資料；驗證 null、空白、範圍及跨欄位約束。不要把 JPA entity、HTTP DTO 或 SDK response 當領域模型。
4. 先驗證所有前置條件，再修改狀態、建立事件。失敗不能留下部分狀態或半份事件。
5. 使用注入的 Clock／ID supplier 或方法參數提供時間與識別，讓測試可重現。事件用已發生的事實命名，保存 eventId、aggregateId、correlationId、occurredAt；涉及排序時加入 aggregateVersion／sequence。
6. 僅回傳 immutable snapshot／事件集合。領域物件收集事件；持久化和發送由外層處理，不在 aggregate 呼叫 event bus、HTTP 或 repository。
7. 區分重建既有 aggregate 與建立新 aggregate；從 DB rehydrate 不得再次產生 Created 事件。不要在資料 mapping 偷執行業務命令。
8. 用純 JUnit 測合法行為、非法轉移、邊界值、事件內容與失敗後不變性，不啟動 Spring。

## DevOps 限制

- PENDING_HUMAN／故障停止工作不可因健康恢復、重啟、事件重播或新 eventId 自動回到執行。
- QUERY、CHANGE、SYSTEM_RECORD 是不同控制類別；事件不賦予權限。
- 不在事件存帳密、token、原始對話、raw evidence 或可執行命令字串；使用受控參照。
- 不為尚未實作的完整工作流新增可呼叫的假轉移。本版 WorkRequest 只示範 receiveQuery 與 stop。

交付 aggregate／value objects、規則表和行為測試；標註尚未實作的政策。詳見 [來源與取捨](references/source-notes.md)。
