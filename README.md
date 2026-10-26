# ai-devops-platform

以 Spring Boot／Spring AI 建置的智慧 DevOps 機器人平台。此儲存庫包含 Spring Boot 應用骨架、規格、方案 Schema、測試規劃與既有架構文件。

請從 **[文件索引](doc/README.md)** 開始閱讀。

- 現行需求與系統分析：**0.40**；系統設計：**0.42**。
- 模型品質評估（MQ）及方案與工具測試（ST）：兩份獨立案例集，**0.5**。
- 首批案例：Jenkins Job 內容查詢、單一 Job 設定備份與刪除。
- 正式端點與實際測試資料尚待提供；規格中的預期結果不是實測通過證明。

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

在 IntelliJ IDEA 開啟專案根目錄或 `build.gradle`，使用 Gradle Wrapper 並將 Gradle JVM 設為 JDK 21，執行 `AiDevopsPlatformApplication` 即可。Spring AI／Qwen、MCP、PostgreSQL、LDAP 等整合依規格於後續實作階段加入，目前啟動不需其連線資訊。

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
