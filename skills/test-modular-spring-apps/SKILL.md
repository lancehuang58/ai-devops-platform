---
name: test-modular-spring-apps
description: Plan and implement unit, architecture, and integration tests for modular Spring/Gradle applications. Use when placing tests, protecting dependency direction, testing event failures, or separating real model evaluation from tool tests.
---

# 依驗證邊界配置測試

1. 讀實際 settings.gradle 和各 module build script，不假設 task 已存在。列出這次改動需要驗證的行為和剩餘風險。
2. model/src/test 測不變量、狀態、value objects、events；service/src/test 用 fake ports 測授權／順序／停止；adapter/src/test 測 mapping、event forwarding、例外傳遞。這些測試不載入完整 Spring、不連真外部服務。
3. web/src/test 放組裝輔助及 ArchUnit；檢查 model／service 無框架依賴、adapter 不依賴 service implementation、ports 是 interfaces、production 不依賴測試模組。規則不可因零 class 匹配而假性通過。
4. web/src/integrationTest 放 Spring context、真 HTTP、跨模組協作，以及日後 PostgreSQL／MCP／LDAP stub 或隔離實體環境測試。把 integrationTest 接到 web:check／根 build；單跑 test 不等於整合測試通過。
5. 把共享 doubles 放 java-test-fixtures，只用 testFixtures dependency 引用。檢查 executable JAR 不含 fixtures、gold、MQ、ST 或測試憑證。
6. 事件測試包含 handler failure、ack gap、duplicate、order；DB atomicity 與鎖不能靠 in-memory fixture 驗收，須用同種 DB 的 web integrationTest。
7. 測試失敗要修原因，禁止 testFailureIgnore／忽略例外／刪除斷言來綠燈。未提供真環境要記 NOT_RUN 或 BLOCKED_ENV，不能寫 PASS。

## MQ 與 ST 的獨立性

- model-quality-evaluation：模型、prompt、context、檢索品質。STUB 只能 HARNESS_ONLY，不能算 Qwen 通過；gold 不進 model input。
- solution-tool-testing：固定模型答案下的授權、workflow、Connector、effect、停止。只連隔離測試資源，沒有 production bypass。
- 兩者的 src/test 都是測試工具自身的 unit tests；公司案例集的實測分數仍需專用 runner／環境，不能拿 Gradle 綠燈回填。
- 跨模組整合測試統一放 web；若需 MQ/ST 工具，只能加 integrationTestImplementation，不得加 production implementation。

執行 `./gradlew clean build`；需要定位才縮到 `:model:test`、`:service:test`、`:adapter:test`、`:web:test`、`:web:integrationTest` 或兩個品質模組 test。報告實際 test 數、範圍及未測外部依賴。詳見 [來源與取捨](references/source-notes.md)。
