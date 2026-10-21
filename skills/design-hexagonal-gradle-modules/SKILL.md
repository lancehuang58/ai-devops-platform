---
name: design-hexagonal-gradle-modules
description: Design or refactor Java/Gradle modules using hexagonal architecture and domain boundaries. Use for model/service/web layering, composition roots, adapter placement, dependency cycles, or modular-monolith planning; not for routine Java edits.
---

# 建立模組邊界

1. 先讀專案的 AGENTS.md、settings.gradle、build.gradle、現行需求與設計版本。把「已實作」「骨架」「未來規劃」分開記錄。
2. 先畫 business capability／bounded context，再選實體 Gradle module；不要把每個 class、外部服務、事件或技術名詞都變成 module。
3. 對要求 model → service → web 的專案，說明這是建構順序；compile dependency 應為 web → service → model。model 不依賴 service、Spring、HTTP、DB、MCP 或模型 SDK。
4. 讓 service 定義 inbound use-case interfaces 與 outbound ports。讓 adapter 實作 outbound ports；web 作 composition root 組裝。DTO、value object 與 immutable event 可跨邊界傳遞，不為每個資料物件機械式新增 interface。
5. 避免 adapter 依賴 service implementation；只在 composition root 引用實作以建構物件。避免跨模組讀寫別人的資料表。
6. 優先保留單一可執行 Spring Boot web 包；其他 production module 用 java-library。不要把 module 拆分當成微服務或多 Bot 的部署授權。
7. 用 api 暴露公開契約確實需要的類型，其餘用 implementation。防止 evaluation、fixtures、test credentials 進入 production runtime。
8. 更新來源檔案位置、啟動指令、JAR 路徑、文件索引與驗證 task。執行 Gradle projects、build 與架構測試；檢查 runtime JAR 內容。

## ai-devops-platform 的既定映射

- model：work／case、access、resource、catalog、workflow、evidence 等領域 package。
- service：各領域 use cases、ports、流程與通知協調。
- adapter：Spring events、未來 PostgreSQL／MCP／API／VM gateway／Qwen adapters。
- web：啟動、組裝、未來 Web／Teams 入口、所有應用整合測試。
- model-quality-evaluation、solution-tool-testing：分開建置的品質工具，不進 production dependencies。

目前優先保持這 6 個 Gradle modules；個別領域規模和 ownership 確實需要時才按 context 拆出新 modules。新增模組應列出責任、公開契約、允許依賴、資料所有權及測試位置。

## 驗收

以依賴表、短 Mermaid 圖及可執行 build 交付。指出尚未接通的 external ports。不要以空 module 編譯成功宣稱領域功能完成。

讀取 [來源與取捨](references/source-notes.md) 以確認參考專案實際採用的方式及不能直接沿用的部分。
