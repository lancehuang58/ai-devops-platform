---
name: implement-use-case-ports
description: Implement or review application use cases and inbound/outbound ports in a hexagonal Java service layer. Use for service orchestration, adapter boundaries, authorization ordering, repository contracts, and composition-root wiring.
---

# 以 Port 實作 Use Case

1. 定義 inbound interface、typed request、typed result、失敗語意。只公開完成案例所需欄位；Actor 由可信 ingress 解析，不能相信 body／prompt 內角色。
2. service 實作 inbound interface，透過 constructor 接收 outbound interfaces；不要依賴 Spring annotations、HTTP clients、JPA repositories、MCP 或 Qwen SDK。
3. 按「輸入驗證 → 當前授權／控制條件 → domain 行為 → 原子持久化 → result」協調。service 不重寫 aggregate 的不變量。
4. 在消費者需要的層定義 outbound port，例如 QueryAuthorization、WorkCommitPort；adapter 把第三方 DTO 轉成內部契約。
5. 用一個明確的 transaction／unit-of-work port 保存 aggregate 與 outbox 事件。禁止假設兩次獨立 save 自動原子；持久化失敗不得回成功。
6. 外部副作用不能靠 DB rollback 還原。明確記錄派送前後狀態、結果不明與人工處理；不在 catch 裡偷偷重試另一個 transport。
7. composition root 可引用 concrete service 與 adapter 來組裝；一般 controller／跨模組呼叫者只依賴 inbound interface 和 immutable contracts。
8. 用 fake ports 做 service unit tests，驗證拒絕時副作用為零、呼叫順序、失敗傳遞與結果。使用真 Spring 組裝和外部資源的測試放 web integrationTest。

## DevOps 控制路徑

- LLM 回傳的是 proposal。CommandDispatcher 才能在當次 RBAC、已發布 schema、resource、health、approval 與 permit 全通過後操作工具。
- MCP > API > 公司 VM gateway 是能力發布時選路，不能當故障時自動 fallback。
- Jenkins delete 的備份、hash 核對與唯一派送維持明確順序；不得改成無條件的多事件 listener 鏈。
- 未提供 LDAP／DB／工具 binding 時，不建立 allow-all 的 production adapter。fake authorization 限測試來源集。

交付契約、實作、組裝位置與測試。遇到未決 API／schema 只建立清楚邊界，不宣稱連線已完成。參考 [來源與取捨](references/source-notes.md)。
