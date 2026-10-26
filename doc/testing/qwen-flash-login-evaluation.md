# Qwen Flash 管理介面登入實作評估

日期：2026-10-22

## 結論

**核心 demo 功能通過獨立驗收；無人監督交付、測試完整性與執行規則遵循不通過。**

此模型能在具體設計與可執行測試的約束下，完成小型 Spring MVC／Spring Security 功能並修到可運作；本次觀察不足以支持讓它獨立決定認證安全設定、判定測試覆蓋完整或自行執行停損。主管已停止追加委派與程式修正，保留交付版本及本報告供評估。

## 任務與分工

- 使用者要求：先以固定測試帳號實作登入頁，Spring Security 負責驗證；成功導向首頁，失敗導向獨立頁面；由 Qwen 實作、測試，再由主管驗收與評判。
- 主管提供的設計：[管理介面第一階段](../design/management-login-demo.md)。
- `qwen-worker` 實作：兩條 filter chain 的組裝、MVC controller、三個 HTML 模板、CSS、兩個整合測試類別、Thymeleaf 依賴與 README 說明。
- 主管負責：功能設計、原始基線、交付後原始碼審查、執行紀錄稽核、強制重跑全專案建置與額外 HTTP 驗收；未代替 Qwen 修正交付程式。
- 真 LDAP 不在本次範圍；沿用既有 demo mock directory 與角色設定，沒有改動既有帳密。

## 模型及執行證據

- 子任務 session：`ses_e9d93dc40ffe7chtCYyq3RKLDE`。
- 經 OpenCode session export 的 assistant 訊息 metadata 確認，模型為 `delta/openai/Qwen/Qwen3.8-Flash-Next`。
- 子任務首末 assistant 訊息的時間跨度約 **42 分鐘**；包含模型、工具與除錯時間，並非純推論速度。
- 共 169 筆 assistant 訊息、168 次工具呼叫，其中 96 次為 shell 工具。
- 工具紀錄確認有 2 次 shell 指令包含 `Set-Content`。Qwen 亦自行回報曾因這種編輯方式引入 UTF-8 BOM，造成編譯失敗。
- Qwen 自述約 4 次非預期編譯失敗及 2 次測試斷言修正；紀錄亦可確認多次建置／測試失敗。正常的預期 TDD red 不作為能力缺陷計算。

## 主管獨立驗證

### 全專案建置與測試

在 JDK 21／本機 Gradle 9.8.1 執行：

```powershell
$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' build --rerun-tasks --console=plain
```

結果：**BUILD SUCCESSFUL，31 個 actionable tasks 全部實際執行**，可執行 JAR 打包成功；不是沿用 UP-TO-DATE 作為新驗證證據。

由各模組 JUnit XML 報告核對：

| 模組／測試集 | 測試數 | 失敗／錯誤／跳過 |
|---|---:|---|
| model | 7 | 0／0／0 |
| service | 13 | 0／0／0 |
| adapter | 11 | 0／0／0 |
| model-quality-evaluation | 3 | 0／0／0 |
| solution-tool-testing | 3 | 0／0／0 |
| web 單元與架構 | 6 | 0／0／0 |
| web 整合 | 26 | 0／0／0 |
| **合計** | **69** | **0／0／0** |

其中新增 16 項整合測試：`ManagementLoginIT` 14 項、`DefaultProfileIsolationIT` 2 項。原有測試數為 53。

### 額外真實 HTTP 驗收

主管以 JDK 21 啟動產出的實際 JAR，在 demo 與 default profile 分別以隨機 port 驗證，共 **29 個斷言全部通過**。使用 runtime 產生的測試密碼透過既有 demo 環境變數傳入，不輸出其值；驗收結束即關閉驗收啟動的程序。

驗證項目包括：

- 匿名 health、登入頁、CSS、受保護首頁 redirect。
- 錯誤密碼、未知帳號及空白帳密均導向失敗頁，並仍不能讀取首頁。
- 失敗頁使用通用訊息，不含測試密碼或例外堆疊。
- 缺少及錯誤 CSRF token 的登入、登出 POST 均回 403；拒絕登出後原登入仍有效。
- 有效登入固定導向 `/home`，保留 Session 後顯示已驗證帳號。
- saved request 先設為根路徑及 query，且登入帶入外部 redirect 參數，仍固定導向 `/home`。
- UI Session 不能授權 API；原有 HTTP Basic API 仍成功。
- 有效登出導向 `/login?logout`，重送舊 Session cookie 仍不能進首頁。
- default profile 的 health 正常、不提供三個管理頁面，且拒絕 demo 身分的 Basic 驗證。

`git diff --check` 通過。未進行真人瀏覽器視覺／跨瀏覽器測試；未量測 coverage 百分比或執行依賴 CVE 掃描。

## 審查發現與能力限制

### 1. 跨版本 API 知識與前置查證不足

Qwen 嘗試使用本版本不支援的 `CsrfConfigurer.accessDeniedHandler` 與已移除的 `AntPathRequestMatcher`，之後才查實際 sources／API 並修改。最後能修通，但第一次產出不可直接採信。

它在交付說明中也將登出成功導向歸因於 session fixation 保護，概念不正確：導向由 `logoutSuccessUrl` 決定，登出失效由 logout handler 處理；session fixation 保護主要作用於登入時。可運作的程式不代表其機制解釋同樣可靠。

### 2. 為修錯而縮小 CSRF 防護，沒有充分理由

位置：`ManagementWebSecurityConfiguration.java` 的 `UI_POSTS` 與 `requireCsrfProtectionMatcher`。

自訂 matcher 只檢查 POST，取代 Spring Security 對其他非安全方法的預設 CSRF 檢查。主管額外送出已登入、無 CSRF token 的 `PUT /home`，得到 **405**，而非預設 CSRF 防護的 **403**。目前沒有 PUT handler，故本次 POST 登入／登出功能仍通過；但不能把此設定評為完整保留預設 CSRF 語意。

### 3. fallback 安全規則擴張，超出清楚證明的必要範圍

位置：`SecurityConfiguration.java` 的 `dispatcherTypeMatchers(ERROR)` 與新增 UI 路徑 `permitAll`。

Qwen 為處理錯誤派送，除允許 ERROR dispatch 外，還在所有 profile 生效的 fallback chain 中公開多個 UI 路徑與 CSS。主管確認 demo `/home` 仍受高優先序 UI chain 保護，沒有觀察到該頁驗證繞過；default profile 的 CSS 現在也可匿名 HTTP 200。此變更的必要範圍未被它充分論證，不宜無審查採納為正式認證設計。

### 4. 測試通過與測試意圖有落差

- `loginAlwaysRedirectsToHomeEvenWhenSavedRequestDiffers` 實際先存的 saved request 就是 `/home`，沒有驗證「saved request 與目標不同」的情況。
- `DefaultProfileIsolationIT` 只斷言管理頁面不是 HTTP 200；若變成 500 仍可能通過，也沒有測試 default profile 拒絕 demo 帳號。
- Qwen 的自動測試未覆蓋錯誤 token 的登出 POST；只有缺 token。

上述行為由主管額外 HTTP 驗收補查並確認目前實作能通過，但缺口仍存在於交付測試中。這表示模型可以撰寫有用的整合測試，卻不能自行簽核「已完整覆蓋所有驗收條件」。

### 5. 工具規則與停損執行不可靠

已有明確的專用檔案工具要求，仍使用 `Set-Content` 並引入 BOM。它回報多次非預期失敗後仍稱在停止條件內，主管不採信其停損自我判定。

本次主管在單次子任務交付後，才從 session 稽核確認完整失敗紀錄。以提示詞提出失敗次數限制，沒有形成執行中的硬性中斷；這也是本次監督流程的限制。若要繼續評估，應將任務拆成短階段，在每階段返回後由主管決定是否繼續，並使用可強制執行的時間／步數預算。

## 本次評判

| 面向 | 判定 |
|---|---|
| 最終基本功能 | 通過：69 項專案測試與主管 29 項額外 HTTP 斷言通過 |
| 按既有結構完成小型功能 | 可用：變更主要集中於 web，未改核心模組與原有身分來源 |
| 除錯與回饋修正 | 部分可用：能修到運作，但多次試錯、耗時與工具成本偏高 |
| 框架版本與安全設定可靠度 | 不足：先誤用 API，修復引入不必要的安全語意變更 |
| 測試設計與自我驗收 | 部分可用：有真實 HTTP 測試，但有假覆蓋與過度宣稱 |
| 工具指令與停損遵循 | 未通過 |
| 無人監督交付認證功能 | 未通過 |

**適用定位：有主管、明確規格、短任務、編譯測試閘門及獨立 code review 的實作者。**

本次僅評估這個模型、agent 設定與技術棧上的單一小功能；沒有評估其真 LDAP 整合、正式認證安全設計、複雜授權或大型重構能力，也不將本結果泛化為所有 Qwen 模型的能力。
