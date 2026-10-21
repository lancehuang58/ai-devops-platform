# ai-devops-platform

以 Spring Boot／Spring AI 建置的智慧 DevOps 機器人平台。此儲存庫包含 Spring Boot 應用骨架、規格、方案 Schema、測試規劃與既有架構文件。

請從 **[文件索引](doc/README.md)** 開始閱讀。

- 現行需求、系統分析與系統設計：**0.40**。
- 模型品質評估（MQ）及方案與工具測試（ST）：兩份獨立案例集，**0.5**。
- 首批案例：Jenkins Job 內容查詢、單一 Job 設定備份與刪除。
- 正式端點與實際測試資料尚待提供；規格中的預期結果不是實測通過證明。

## 開發環境

目前為單一 Gradle 專案的 Spring Boot 空白骨架，程式位於儲存庫根目錄的 `src/`，設計文件位於 `doc/`。

| 項目 | 設定 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.1 |
| Gradle | Wrapper 固定 9.8.1，使用 Groovy DSL，無須另外安裝 Gradle |
| 專案座標 | `com.delta.dms.ops:ai-devops-platform:0.0.1-SNAPSHOT` |
| Java package | `com.delta.dms.ops.aidevopsplatform` |
| 基礎依賴 | Spring Web MVC、Actuator、測試 starter |
| 設定檔 | [application.properties](src/main/resources/application.properties) |

設定 `JAVA_HOME` 指向 JDK 21，並確認 `java -version` 與 `./gradlew --version`（Windows 使用 `.\gradlew.bat --version`）。首次執行 Wrapper 需要下載 Gradle 與依賴。公司如需 Nexus／Proxy，請依組織標準配置 plugin repository、dependency repository 與 Wrapper 下載來源。憑證與代理設定放在環境變數、使用者的 `~/.gradle/gradle.properties` 或組織管理的 Gradle init script，不存入儲存庫。

## 建置與啟動

macOS／Linux：

```bash
./gradlew clean build
./gradlew bootRun
```

Windows PowerShell：

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

`build` 會執行測試並產生 JAR；單獨測試使用 `./gradlew test`，單獨打包使用 `./gradlew bootJar`（後者不執行測試）。Windows 使用對應的 `.\gradlew.bat` 指令。

也可在建置後執行可執行 JAR：

```bash
java -jar build/libs/ai-devops-platform-0.0.1-SNAPSHOT.jar
```

`build/libs/` 中若另有 `-plain.jar`，那是一般 library JAR；啟動應用請使用上面的無 classifier JAR。

預設使用 8080 port；可透過 `SERVER_PORT` 環境變數覆寫。健康端點為 `GET http://localhost:8080/actuator/health`，正常回傳 HTTP 200，JSON 的 `status` 為 `UP`。目前未建立業務 API 或首頁，請以健康端點確認啟動。

基礎測試以隨機 port 啟動真實內嵌 HTTP server，確認健康端點回傳 HTTP 200／UP。此測試僅驗證骨架，不代表 `doc/testing/` 的 MQ／ST 案例已執行。

在 IntelliJ IDEA 開啟專案根目錄或 `build.gradle`，使用 Gradle Wrapper 並將 Gradle JVM 設為 JDK 21，執行 `AiDevopsPlatformApplication` 即可。Spring AI／Qwen、MCP、PostgreSQL、LDAP 等整合依規格於後續實作階段加入，目前啟動不需其連線資訊。

版本依據：[Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/gradle-plugin/)；建置骨架與 Wrapper 由 [Spring Initializr](https://start.spring.io/) 產生後整理。後續引入 Spring AI 時須另外鎖定並驗證相容版本。
