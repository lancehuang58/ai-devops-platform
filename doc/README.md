# 專案文件索引

本次匯入以 2026-10-17 的文件版次為基準：三份主規格 **0.40**、兩份獨立測試案例集 **0.5**。現有 HTML 架構文件保留；原始 ZIP 套件保持原始位元組。2026-10-21 系統設計升至 **0.41**，新增 Gradle／六角形架構實作基準；SRS／SA 仍為 0.40。

## 現行規格與閱讀順序

| 順序 | 文件 | 版本 | 用途 |
|---|---|---|---|
| 1 | [需求規格書](specifications/DevOps_智慧維運應用需求規格書_草案.md) | 0.40 | 範圍、權限、驗收與 SRS-10 的 D01～D11 決策 |
| 2 | [系統分析規格書](specifications/DevOps_智慧維運應用系統分析規格書_草案.md) | 0.40 | 使用案例、流程、狀態與例外 |
| 3 | [系統設計規格書](specifications/DevOps_智慧維運應用系統設計規格書_草案.md) | 0.41 | 元件、契約、資料模型、設定、知識生命週期及測試規劃 |
| 4 | [模型品質評估與回歸案例集](testing/DevOps_模型品質評估與回歸模組_測試案例集.md) | 0.5 | MQ：Qwen／模型、Prompt、Context 與輸出品質 |
| 5 | [方案與工具測試案例集](testing/DevOps_方案與工具測試模組_測試案例集.md) | 0.5 | ST：工作流、授權、交易、工具、隔離與服務效果 |

需求範圍與業務規則以 SRS 為準，流程語意以 SA 為準，實作契約以 SD 為準。舊文件與套件如有相反描述，依各主規格的最新明確決策處理；無法判定的差異需記錄並確認，不能自行擴張執行權限。

## Jenkins 方案與命令

| 方案 | 詳規 | 可檢視的套件內容 | 原始封存檔 | 狀態 |
|---|---|---|---|---|
| Job 內容查詢 | [Schema v1](solutions/DevOps_Jenkins_Job內容查詢_方案與命令Schema_v1.md) | [definitions／schemas／examples／tests](solutions/jenkins-job-query-v1/) | [query v1 ZIP](archive/packages/jenkins-job-query-v1.zip) | DRAFT；與現行主規格一起使用 |
| Job 設定備份與刪除 | [Schema v1](solutions/DevOps_Jenkins_Job刪除_方案與命令Schema_v1.md) | [definitions／schemas／examples／tests](solutions/jenkins-job-delete-v1/) | [delete v1 ZIP](archive/packages/jenkins-job-delete-v1.zip) | 封存 v1 基線；須依 SD-14.2 升版 1.1 並驗證後才可發布 |

解出的 JSON Schema、DRAFT 定義、範例及驗證器保留原始內容，僅套件 README 補上版本提示與有效連結。ZIP 內另帶的舊主規格留在封存檔中，不覆蓋現行 `specifications/` 或 `testing/`。

## 歷史與參考文件

| 文件 | 定位 |
|---|---|
| [平台需求與架構討論原稿](archive/devops-agent-platform-design.md) | 0.1 歷史來源，已整合至主規格；其待確認事項不取代後續決策 |
| [模型品質評估案例集 v0.1](archive/DevOps_智慧維運機器人_模型品質評估案例集_v0.1.md) | 已拆分為 MQ／ST，保留追溯用途 |
| [既有平台架構 HTML](design/ai-devops-platform-plan.html) | 原儲存庫文件，保留供參考；實作仍以現行主規格為準 |
| [既有 Java 實作規劃 HTML](design/ai-devops-java-implementation-plan.html) | 原儲存庫文件，保留供參考；實作仍以現行主規格為準 |

## 測試與尚待完成事項

MQ 與 ST 分開計分。尚未取得公司 Qwen／MCP／Jenkins 端點及真實測試資料，正式案例維持文件中的 `actual_result=null`、`NOT_RUN`；不能用 stub 或既有 schema 靜態檢查結果替代模型品質、真實服務或發布驗收。

刪除方案 schema 1.1 的完整設定、binding 與測試尚待依 SD-14.2 落實。TencentDB-Agent-Memory 仍屬近期評估議題，此次匯入不宣告正式採用，也不變更 0.40 的知識儲存與發布設計。

## 匯入與維護

- 已將匯入 Markdown 的 `sandbox:` 連結轉為可在 GitHub 使用的相對連結；原始 ZIP 不改寫。
- [匯入清單](import-manifest.json) 保存 11 個來源檔與解出項目的 SHA-256，供版本追溯。
- 後續修改權限、工作流程或故障政策時，同步檢查 SRS、SA、SD 與 MQ／ST；不要只改舊套件。
- 此儲存庫存放專案設計，不代表正式執行能力已發布，也不取代系統設計中正式知識／Runbook 的審查發布流程。


## Gradle 與開發架構（2026-10-21）

- [dddcleankanban 架構分析](design/dddcleankanban-architecture-analysis.md)：讀取的 commit、實際設計、可採用與需修正的地方。
- [Gradle 六角形架構](design/gradle-hexagonal-architecture.md)：六模組、領域映射、介面、事件、測試與下一階段。
- 根目錄 `skills/`：五個可重用的架構與測試 Skills。
