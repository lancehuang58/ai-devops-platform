# ai-devops-platform

以 Spring Boot／Spring AI 建置的智慧 DevOps 機器人平台。此儲存庫包含 Spring Boot 應用骨架、規格、方案 Schema、測試規劃與既有架構文件。

請從 **[文件索引](doc/README.md)** 開始閱讀。

- 對齊需求（`doc/specifications/aligned/aligned-requirements.md`，U1–U48）：**0.54**。舊版 SRS／SA／SD 草稿若與對齊需求衝突，以對齊需求為準。
- 模型品質評估（MQ）及方案與工具測試（ST）：兩份獨立案例集，**0.5**。
- 首批案例：Jenkins Job 內容查詢、單一 Job 設定備份與刪除。
- 正式端點與實際測試資料尚待提供；規格中的預期結果不是實測通過證明。

## v1 平台切片

`platform` profile 提供第一個可運作的垂直切片。預設 profile 仍只有健康檢查；`demo` profile 仍是 H2／mock 的受管服務查詢與管理登入，兩者不要同時開啟。

v1 包含：

- 單一 `WorkItem` 狀態機、結案原因、工作類型與 `work_activity`／`case_event` 歷史；非法轉換會被拒絕。
- Alertmanager webhook 與 generic API 收告警。相同 `source` + `sourceEventId` 冪等；fingerprint 為 fp-v1（不含 severity、source）。同一未結 fingerprint 只開一張單。跨資源關聯候選先通知、確認前不開票；單一成員超過 60 秒才開一般案件。
- `work_task` 以 `FOR UPDATE SKIP LOCKED` 領取。`platform.workflow.workers` 可配置（建議 8）。等待狀態會寫回資料庫並釋放 worker。同一資源的補救鎖依 SD-18：執行中的租約過期不會自動放開。
- Jira 同步 outbox 存在 PostgreSQL。暫時失敗最多 5 次重試後停止並留給人工。Jira、Teams、Qwen／LiteLLM、MCP／VM 都在 port 後面；既有工作流維持假實作，AI 對話則可依下節設定連接 Qwen 與唯讀 MCP。
- 已知解法與自動補救白名單在領域政策裡。沒有命中白名單就停在待核准；沒有人核准就維持 pending。結案草稿必須由使用者確認。
- GitLab OIDC 登入（`openid profile email`），不看角色，登入後每個人看到相同清單，並回到原本的深連結。清單可依系統、台北時間範圍、狀態篩選，每頁 20／50／100（預設 50），預設只顯示未結案。明細頁有時間軸。
- 受管服務與整合服務寫在 [`config/platform-services.yml`](config/platform-services.yml)。秘密只寫 `${NAME}`。啟動時檢查缺欄位、解不出來的參考、未知 template、重複 id。
- 監控模板的探測順序是 endpoint、MCP、API、VM（Glove）。通道本身失敗才換下一層；服務不健康立刻開案；全部失敗才標 down 並把每一層原因放進單裡。較低通道仍健康時只發一則通道降級通知。週期探測預設關閉。
- 人工商用模型接管：下載遮罩後的 zip、寫回、以 `FORCED_BY_COMMERCIAL_MODEL` 結案。平台不會呼叫商用模型。

本地執行需要 PostgreSQL 16：

```bash
docker run -d --name aidevops-pg -e POSTGRES_USER=aidevops -e POSTGRES_PASSWORD=aidevops -e POSTGRES_DB=aidevops -p 5432:5432 postgres:16-alpine
./gradlew :web:bootRun --args='--spring.profiles.active=platform'
```

啟動前要提供 GitLab 與目錄裡的秘密參考。值放在環境變數或 Kubernetes Secret，不要寫進 YAML：

```bash
export GITLAB_BASE_URL=https://gitlab.example.internal
export GITLAB_OAUTH_CLIENT_ID=...
export GITLAB_OAUTH_CLIENT_SECRET=...
export GITLAB_TOKEN=... JENKINS_TOKEN=... GLOVE_TOKEN=...
export JIRA_TOKEN=... TEAMS_WEBHOOK=... LITELLM_API_KEY=...
```

開啟 `http://127.0.0.1:8080/login`，用「以 GitLab 登入」。GitLab 必須已登記 redirect URI `{平台網址}/login/oauth2/code/gitlab`。告警權杖是 `dev-alert-token`（標頭 `X-Alert-Token`）。請在正式環境改掉這個值。Alertmanager 路徑是 `POST /api/v1/alerts/alertmanager`，通用路徑是 `POST /api/v1/alerts/generic`。

服務目錄的範例在 `config/platform-services.yml`：gitlab、jenkins、vm 三種模板，以及 gitlab-prod、jenkins-prod、vm-jenkins-01 三個實例。改 `platform.catalog.file` 可指向別的檔案。`platform.monitoring.enabled=true` 才會每分鐘探測；預設的通道 adapter 不會連外，接上真實 endpoint／MCP／Glove client 之前請保持關閉。

```bash
./gradlew build
```

`build` 會跑全部單元測試、ArchUnit 與 web 整合測試。告警、佇列與畫面測試使用 Testcontainers，需要本機 Docker。

## 開發環境

目前為 Gradle 六模組的六角形架構骨架：`model`、`service`、`adapter`、`web`、`model-quality-evaluation`、`solution-tool-testing`。單一 Spring Boot 應用由 `web` 啟動；model／service 為純 Java 核心。

請閱讀 [模組與事件設計](doc/design/gradle-hexagonal-architecture.md) 及 [dddcleankanban 架構分析](doc/design/dddcleankanban-architecture-analysis.md)。開發 Skills 的版控來源位於 `skills/`。

| 項目 | 設定 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.1 |
| Gradle | Wrapper 固定 9.8.1，使用 Groovy DSL，無須另外安裝 Gradle |
| 專案座標 | `com.delta.dms.ops:ai-devops-platform:0.0.1-SNAPSHOT` |
| Java package | `com.delta.dms.ops.aidevopsplatform` |
| 基礎依賴 | Spring Web MVC、Security、Actuator；adapter 使用 SnakeYAML／H2 |
| 設定檔 | [application.properties](web/src/main/resources/application.properties) |

設定 `JAVA_HOME` 指向 JDK 21，並確認 `java -version` 與 `./gradlew --version`（Windows 使用 `.\gradlew.bat --version`）。首次執行 Wrapper 需要下載 Gradle 與依賴。公司如需 Nexus／Proxy，請依組織標準配置 plugin repository、dependency repository 與 Wrapper 下載來源。憑證與代理設定放在環境變數、使用者的 `~/.gradle/gradle.properties` 或組織管理的 Gradle init script，不存入儲存庫。

## 建置與啟動

macOS／Linux：

```bash
./gradlew clean build
./gradlew :web:bootRun
```

Windows PowerShell：

```powershell
.\gradlew.bat clean build
.\gradlew.bat :web:bootRun
```

`build` 會執行所有模組單元／架構測試與 web 整合測試並產生 JAR。`./gradlew test` 只跑各模組單元／架構測試，`./gradlew :web:integrationTest` 跑整合測試；`./gradlew :web:bootJar` 只打包，不執行測試。Windows 使用對應的 `.\gradlew.bat` 指令。

也可在建置後執行可執行 JAR：

```bash
java -jar web/build/libs/ai-devops-platform-0.0.1-SNAPSHOT.jar
```

只有 web 產生可執行 Spring Boot JAR；model／service／adapter 與兩個品質模組產生一般 library JAR。MQ／ST 與 test fixtures 不打入 web 正式包。

預設使用 8080 port；可透過 `SERVER_PORT` 環境變數覆寫。健康端點為 `GET http://localhost:8080/actuator/health`，正常回傳 HTTP 200，JSON 的 `status` 為 `UP`。預設 profile 只提供健康端點；下述 demo profile 另提供受管服務查詢 API。

各模組有獨立單元測試；`web/src/integrationTest` 以隨機 port 驗證健康端點 HTTP 200／UP，並驗證 Spring 事件組裝與重複投遞。`web/src/test` 的 ArchUnit 守住依賴方向。

查詢工作受理／停止是初始範例；InMemoryWorkJournal 仍僅為 test fixture。新增 demo profile 使用 H2 交易、NotificationRelay 與模擬服務完成受管服務查詢；沒有 production persistence／scheduler 或變更 API。開發測試不代表 `doc/testing/` 的真實 MQ／ST 案例已執行。

在 IntelliJ IDEA 開啟專案根目錄或 `build.gradle`，使用 Gradle Wrapper 並將 Gradle JVM 設為 JDK 21，執行 `AiDevopsPlatformApplication` 即可。預設 profile 不啟用外部整合；platform 的 AI 對話需依下節明確設定後啟用。

版本依據：[Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/gradle-plugin/)；建置骨架與 Wrapper 由 [Spring Initializr](https://start.spring.io/) 產生後整理。後續引入 Spring AI 時須另外鎖定並驗證相容版本。

## 受管服務查詢 Demo

```bash
./gradlew :web:bootRun --args='--spring.profiles.active=demo'
curl -u normal:demo-normal http://127.0.0.1:8080/api/v1/managed-services
curl -u normal:demo-normal http://127.0.0.1:8080/api/v1/managed-services/jenkins-demo/status
```

使用 H2 記憶體 DB、mock LDAP／LLM／Jenkins；回應明示 mock 來源，重啟會清除報告。範例帳號 normal/demo-normal、admin/demo-admin 僅於明確啟用 demo 時可用，預設綁定 localhost。

- [system-demo.yml](config/system-demo.yml)：DB／LDAP／LLM 初始化設定。
- [managed-services.yml](config/managed-services.yml)：Jenkins 等受管服務獨立清單。
- [roles.yml](config/roles.yml)：帳號角色映射。
- [完整流程與設定說明](doc/design/managed-service-demo.md)：API、環境變數、DB catalog 演進、事件與失敗語意。

## 管理介面（demo）

```bash
./gradlew :web:bootRun --args='--spring.profiles.active=demo'
```

開啟 `http://127.0.0.1:8080/login`，使用上述 demo 測試帳號以表單登入；登入成功導向 `/home`，失敗到 `/login-failure`，`POST /logout` 登出回登入頁。管理頁面僅在 demo profile 提供，採 Spring Security 表單登入＋Session＋CSRF；`/api/**` 仍為 HTTP Basic，API 行為不受 UI Session 影響。設計與驗收條件見 [管理介面第一階段設計](doc/design/management-login-demo.md)。

執行流程用 model／service／adapter／web；web 整合測試另外接入 MQ／ST，涵蓋全部六模組。尚未實作真連線 adapter，因此改 URL／key 不會自動接通，改成正式 provider mode 會拒絕啟動。

## AI 唯讀對話（platform）

登入後開啟 `http://127.0.0.1:8080/chat`。預設顯示尚未啟用；要連 Qwen，先提供 `QWEN_API_KEY` 環境變數，在 [config/ai-chat.yml](config/ai-chat.yml) 調整模型與 base URL，再執行：

```bash
./gradlew :web:bootRun --args='--spring.profiles.active=platform --platform.chat.enabled=true'
```

仍需前述 PostgreSQL、GitLab OIDC 與服務目錄秘密參考。模型預設 `qwen-flash 3.8` 依使用者指定保留；官方文件未證實此精確 id，請依實際帳戶改成有效模型或 LiteLLM alias。DashScope base URL 包含 `/compatible-mode/v1`；LiteLLM 請使用其 OpenAI 相容 `/v1` base URL。

`platform.chat.file` 可指定另一份原始 YAML，API key 僅允許 `${NAME}`；`platform.chat.skill` 可指定 `file:/.../SKILL.md`。遠端 MCP／Glove 的 Streamable HTTP 與唯讀 binding 範例見設定檔。對話只提出建議，不執行修復；歷史只保存在 session，最多 20 回合。詳細契約見 SD-21，驗證與限制見根目錄 `PR_DESCRIPTION.md`。
