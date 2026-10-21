# ai-devops-platform

以 Spring Boot／Spring AI 建置的智慧 DevOps 機器人平台。此儲存庫包含 Spring Boot 應用骨架、規格、方案 Schema、測試規劃與既有架構文件。

請從 **[文件索引](doc/README.md)** 開始閱讀。

- 現行需求、系統分析與系統設計：**0.40**。
- 模型品質評估（MQ）及方案與工具測試（ST）：兩份獨立案例集，**0.5**。
- 首批案例：Jenkins Job 內容查詢、單一 Job 設定備份與刪除。
- 正式端點與實際測試資料尚待提供；規格中的預期結果不是實測通過證明。

## 開發環境

目前為單一 Maven 模組的 Spring Boot 空白骨架，程式位於儲存庫根目錄的 `src/`，設計文件位於 `doc/`。

| 項目 | 設定 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.1 |
| Maven | Wrapper 固定 3.10.0，無須另外安裝 Maven |
| Maven 座標 | `com.lancehuang:ai-devops-platform:0.0.1-SNAPSHOT` |
| Java package | `com.lancehuang.aidevopsplatform` |
| 基礎依賴 | Spring Web MVC、Actuator、測試 starter |
| 設定檔 | [application.properties](src/main/resources/application.properties) |

設定 `JAVA_HOME` 指向 JDK 21，並確認 `java -version` 與 `./mvnw --version`（Windows 使用 `.\mvnw.cmd --version`）。首次執行 Wrapper 需要下載 Maven 與依賴；公司網路如需 Maven mirror，可在使用者的 `~/.m2/settings.xml` 設定，憑證不存入儲存庫。

## 建置與啟動

macOS／Linux：

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

Windows PowerShell：

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

也可在建置後執行：

```bash
java -jar target/ai-devops-platform-0.0.1-SNAPSHOT.jar
```

預設使用 8080 port；可透過 `SERVER_PORT` 環境變數覆寫。健康端點為 `GET http://localhost:8080/actuator/health`，正常回傳 HTTP 200，JSON 的 `status` 為 `UP`。目前未建立業務 API 或首頁，請以健康端點確認啟動。

基礎測試以隨機 port 啟動真實內嵌 HTTP server，確認健康端點回傳 HTTP 200／UP。此測試僅驗證骨架，不代表 `doc/testing/` 的 MQ／ST 案例已執行。

在 IntelliJ IDEA 開啟根目錄 `pom.xml`，選擇 JDK 21，執行 `AiDevopsPlatformApplication` 即可。Spring AI／Qwen、MCP、PostgreSQL、LDAP 等整合依規格於後續實作階段加入，目前啟動不需其連線資訊。

版本依據：[Spring Boot 系統需求](https://docs.spring.io/spring-boot/4.1/system-requirements.html)；骨架由 [Spring Initializr](https://start.spring.io/) 產生後整理。後續引入 Spring AI 時須另外鎖定並驗證相容版本。
