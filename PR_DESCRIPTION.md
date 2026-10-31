# AI 對話模組：Spring AI、Qwen、需求分析 skill 與唯讀 MCP

新增登入後 `/chat`，先用需求分析 skill 分類與辨識資源，再經 MCP 蒐集證據，最後回覆診斷、未知事項與建議。對話沒有修復執行 port，不建立工作項目、不核准、不取得資源鎖；實際變更仍走既有白名單、核准與資源鎖流程。

## 實作

- Spring AI BOM 2.0.2（Maven Central 實際解析成功），OpenAI 相容 Qwen／LiteLLM；與 Boot 4.1.1、JDK 21 編譯、測試與 bootJar 成功。
- 模型、base URL、API key 由 `config/ai-chat.yml` 設定；原始 YAML 秘密只能是 `${NAME}`，拒絕字面值與未解析參考，設定文字輸出遮罩。不記錄 prompt、回應或 credential。
- service 純 Java ports 與流程；adapter 封裝 Spring AI／MCP；web 負責組裝。既有 ArchUnit 全通過。
- SKILL.md 預設從 classpath 載入，可透過 `platform.chat.skill=file:/.../SKILL.md` 替換。分類限制為 INCIDENT／REQUIREMENT／DISCUSSION，工具限制為 catalog／status／health。
- 本地為真正 MCP SDK client/server，透過私有行程內 JSON-RPC 連線；Spring AI 工具轉換後僅公開三個唯讀工具，沒有對外工具 HTTP 入口。可查安全服務設定、歷史探測狀態、目錄健康端點 HTTP GET；不跟隨轉址、不傳憑證、不回傳 HTTP body。
- `servers` 清單可設定其他 Streamable HTTP MCP；管理者須明確核可唯讀 binding，且遠端工具必須宣告 readOnlyHint。Glove 須提供接受 `{resource}` 的唯讀工具，例如 `vm_status`，不可綁通用 SSH／shell 執行工具。測試以本機 HTTP stub 證明可由設定替換 status route。
- OIDC／CSRF 保護頁面與 POST；Thymeleaf 跳脫純文字，使用本地 htmx。session 最多 20 回合、模型最近 6 回合；輸入 4000 字，支援清除。
- 對齊需求第七版新增 U46～U48；SRS／SA／SD 升至 0.54，新增 FR-54～56、SA-15、SD-21。既有 FR-53 已使用，因此未重複編號。

## 測試結果（2026-10-31）

先寫缺少實作而失敗的測試，再完成實作並確認轉綠；沒有停用、刪除或弱化既有測試。測試不呼叫真 Qwen、公司 Glove 或外部服務。

| 驗證 | 總數 | 通過 | 失敗 | 說明 |
|---|---:|---:|---:|---|
| 最終 `./gradlew test --continue` | 126 | 123 | 3 | 僅既有 adapter Docker 初始化失敗 |
| `./gradlew build --continue` 的 web integrationTest | 31 | 26 | 5 | 僅 PlatformWebIT 因缺 Docker 未能進入測試本體 |
| 合計最新測試報告 | 157 | 149 | 8 | 無其他測試失敗、無 skipped |

unit 分布：model 54／54、service 25／25、adapter 21／24、web 17／17、MQ 3／3、ST 3／3。相對 107 個 unit 基準新增 19 個；另新增 1 個需要 Docker 的 platform Web 案例。

Docker 阻礙的 adapter 類別：`PlatformFlowPersistenceTest`、`PlatformPersistenceTest`、`ProbePublisherTest`。PlatformWebIT 原有 4 個加新增 1 個，共 5 個案例，因 `Could not find a valid Docker environment` 初始化失敗，未驗證實際 OIDC＋PostgreSQL 對話流程。

完整 build 已實際執行，當時為 125 個 unit；其後加上 1 個 profile 隔離測試，最終 unit 126 個。`build` 無法全綠的原因只有上述 Docker 限制。`:web:bootJar` 成功。新增覆蓋含 stub ChatModel＋真正行程內 MCP 完整流程、禁止寫入工具、錯誤遮罩、固定 GET／不轉址、遠端 MCP binding、設定秘密驗證、session 隔離、CSRF、匿名拒絕與 XSS 跳脫。

可重現指令：

```bash
# 使用 JDK 21
./gradlew test --continue
./gradlew build --continue
./gradlew :web:bootJar
```

只驗證新增的非 Docker 案例：

```bash
# 使用 JDK 21
./gradlew :service:test --tests '*ChatServiceTest' :adapter:test --tests '*adapter.chat.*' :web:test --tests '*web.chat.*' --tests '*ModuleArchitectureTest'
```

## 本地執行

先提供 PostgreSQL 16 與已登記的 GitLab OIDC client／redirect URI。本次驗證環境未提供 Docker；執行時可使用可連線的 PostgreSQL，或在具 Docker 的環境依 README 啟動。

由環境變數或 Secret 注入下列值，不寫入版本庫：

- `GITLAB_BASE_URL`、`GITLAB_OAUTH_CLIENT_ID`、`GITLAB_OAUTH_CLIENT_SECRET`。
- 目錄秘密：`GITLAB_TOKEN`、`JENKINS_TOKEN`、`GLOVE_TOKEN`、`JIRA_TOKEN`、`TEAMS_WEBHOOK`、`LITELLM_API_KEY`。
- AI：`QWEN_API_KEY`；若 YAML 改用其他 `${NAME}`，提供相對應名稱即可。
- 需要時以 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆寫資料庫連線。

調整 `config/ai-chat.yml` 的 model 與 base-url（DashScope 範例含 `/compatible-mode/v1`，LiteLLM 通常是其 `/v1` base URL）。API key 欄位只保留 `${QWEN_API_KEY}` 參考。

```bash
# 使用 JDK 21
./gradlew :web:bootRun --args='--spring.profiles.active=platform --platform.chat.enabled=true'
```

登入 `http://127.0.0.1:8080/login` 後進入 `http://127.0.0.1:8080/chat`，例如送出「jenkins-prod 無法使用，請診斷」。預設未設定 `platform.chat.enabled=true` 時只提供尚未啟用訊息，不建立模型／MCP 連線。`platform.chat.file` 可指向其他原始 YAML。

## 模型 id 查證

已於 2026-10-31 上網查證，官方 [qwen-flash 模型文件](https://help.aliyun.com/en/model-studio/qwen-flash) 列出 `qwen-flash` 與 `qwen-flash-2025-07-28`；未找到精確字串 **`qwen-flash 3.8`** 的官方存在證明，不能宣稱它可直接呼叫。依使用者要求仍保留該預設；部署者應依地區／帳戶確認有效 id，或在 LiteLLM 定義相應 alias。沒有呼叫真實模型驗證帳戶可用性。

版本依據：[Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html) 明示 2.0.x 支援 Boot 4.0.x／4.1.x；[Maven Central BOM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/2.0.2/) 可解析。使用官方 API，不套用舊版 OpenAiApi 建構方式。

## 延後項目

串流回應、跨 session／持久化歷史、工作項目自動建立、真 Qwen 模型品質與帳戶可用性、公司 Glove 實際契約驗收，以及有 Docker 環境的 PostgreSQL／OIDC 整合重跑。遠端 MCP 唯讀宣告須由部署者驗證，不能據此開放通用 shell 工具。
