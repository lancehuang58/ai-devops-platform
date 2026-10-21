# ai-devops-platform

以 Spring Boot／Spring AI 建置的智慧 DevOps 機器人平台。此儲存庫包含 Spring Boot 應用骨架、規格、方案 Schema、測試規劃與既有架構文件。

請從 **[文件索引](doc/README.md)** 開始閱讀。

- 現行需求與系統分析：**0.40**；系統設計：**0.41**。
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
| 基礎依賴 | Spring Web MVC、Actuator、測試 starter |
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

預設使用 8080 port；可透過 `SERVER_PORT` 環境變數覆寫。健康端點為 `GET http://localhost:8080/actuator/health`，正常回傳 HTTP 200，JSON 的 `status` 為 `UP`。目前未建立業務 API 或首頁，請以健康端點確認啟動。

各模組有獨立單元測試；`web/src/integrationTest` 以隨機 port 驗證健康端點 HTTP 200／UP，並驗證 Spring 事件組裝與重複投遞。`web/src/test` 的 ArchUnit 守住依賴方向。

查詢工作受理／停止與事件是初始範例。RegisterQueryService、NotificationRelay 目前只在測試組裝；InMemoryWorkJournal 僅為 test fixture，沒有 production persistence／scheduler 或維運 API。27 項骨架測試不代表 `doc/testing/` 的真實 MQ／ST 案例已執行。

在 IntelliJ IDEA 開啟專案根目錄或 `build.gradle`，使用 Gradle Wrapper 並將 Gradle JVM 設為 JDK 21，執行 `AiDevopsPlatformApplication` 即可。Spring AI／Qwen、MCP、PostgreSQL、LDAP 等整合依規格於後續實作階段加入，目前啟動不需其連線資訊。

版本依據：[Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/gradle-plugin/)；建置骨架與 Wrapper 由 [Spring Initializr](https://start.spring.io/) 產生後整理。後續引入 Spring AI 時須另外鎖定並驗證相容版本。
