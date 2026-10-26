# 管理介面登入 Demo：整合驗證紀錄

日期：2026-10-26（Asia/Taipei）

## 來源與範圍

將使用者提供的 [2026-10-22 交接包](../../qwen_changes.md) 整合到 `lancehuang58/ai-devops-platform`。開始時遠端 main 為 `26614343506fb0c0677c43f34efda6adf8578cd2`，與附件 patch 基準一致。13 個 patch 檔案（3 個修改、10 個新增；702 行新增、2 行刪除）前向檢查及套用成功，無需衝突合併。

整合後反向 `git apply --reverse --check qwen_changes.md` 成功，確認 13 個檔案與交付快照一致；UTF-8 BOM／行尾空白檢查通過。另保存原交接包、更新文件索引並新增本紀錄。交接包的最外層 Markdown 貼上用五反引號已移除，內部內容與 patch 保留。

保留原產品程式與測試的已知缺口，見下方待修清單。OpenCode agent／rules／instructions 是 repository 外的使用者設定，本次只保存其交接內容，未安裝至本機全域設定，未呼叫 Qwen 或重做模型能力評估。

## 本次建置與測試

使用 Linux x86_64、Amazon Corretto JDK `21.0.13.0.10`、獨立安裝的 Gradle `9.8.1`；未使用 Gradle Wrapper。專案仍維持 Java toolchain 21、Spring Boot 4.1.1 與既有六模組，未變更建置版本。

從專案根目錄執行下列命令（`gradle` 指本機 Gradle 9.8.1 的執行檔）：

```bash
gradle --no-daemon --console=plain --max-workers=2 build --rerun-tasks
```

結果：**BUILD SUCCESSFUL，31 actionable tasks：31 executed**。由本次 JUnit XML 核對如下，沒有以歷史成績或 UP-TO-DATE 取代本次執行：

| 測試集 | 測試數 | 失敗 | 錯誤 | 跳過 |
|---|---:|---:|---:|---:|
| model | 7 | 0 | 0 | 0 |
| service | 13 | 0 | 0 | 0 |
| adapter | 11 | 0 | 0 | 0 |
| model-quality-evaluation | 3 | 0 | 0 | 0 |
| solution-tool-testing | 3 | 0 | 0 | 0 |
| web 單元／架構 | 6 | 0 | 0 | 0 |
| web 整合 | 26 | 0 | 0 | 0 |
| **總計** | **69** | **0** | **0** | **0** |

兩個新增整合測試類別已實際執行：ManagementLoginIT（14）及 DefaultProfileIsolationIT（2）。這 69 項仍包含原有 53 項，並非新增 69 項。

## 本次 JAR／HTTP 驗收

確認可執行 JAR manifest、三個 HTML templates、model／service／adapter libraries 存在，MQ／ST／test fixtures 不進 runtime 包。以此 JAR 分別啟動 demo 與 default profile，綁定 127.0.0.1 隨機 port。

驗收使用 runtime 產生的測試密碼，經 `DEMO_NORMAL_PASSWORD` 傳入；HTTP client 維護 cookie jar，關閉自動 redirect，從實際 HTML 取得 CSRF token。驗收完成後已關閉本次啟動的程序。

| 斷言群組 | 數量 | 本次結果 |
|---|---:|---|
| demo health、匿名根路徑／首頁、登入頁與 CSRF、CSS | 5 | 通過 |
| 錯誤密碼／未知帳號／空白帳密，各驗證失敗導向及首頁拒絕 | 6 | 通過 |
| 通用失敗頁，不含測試密碼或例外內容 | 1 | 通過 |
| 登入缺少／錯誤 CSRF token | 2 | 均 403 |
| 有效登入固定導向與已驗證首頁 | 2 | /home、200 |
| UI Session 不授權 API；有效 Basic API | 2 | 401／200 |
| 登出缺少／錯誤 CSRF，各驗證拒絕及 Session 仍有效 | 4 | 403、首頁仍 200 |
| 正常登出與重送舊 Session cookie | 2 | /login?logout；舊 Session 被拒絕 |
| default health、三個管理頁面、demo 身分拒絕 | 5 | health 200；管理頁面 401／404；Basic 401 |
| **總計** | **29** | **全部通過** |

固定導向案例先留下 `/?next=outside-home` 的 saved request，再於登入表單傳入外部 redirect 參數，確認仍導向 `/home`。舊 Session 案例使用登出前保存的 JSESSIONID，在新的 HTTP client 手動重送；不是只依賴 cookie jar 自動刪除 cookie。

這 29 個斷言是本次獨立的一次性驗收，不是另外 29 個已版控 JUnit 測試方法。它們補查交付測試的弱點，沒有改寫原測試或宣稱測試覆蓋已完整。

## 已知待修事項

| ID | 現象／缺口 | 本次處理 |
|---|---|---|
| LOGIN-F01 | UI 自訂 CSRF matcher 僅保護 POST；已登入、無 token 的 PUT /home 仍為 405 | 已重現，未修；後續恢復完整 CSRF 語意並加回歸測試 |
| LOGIN-F02 | fallback chain 在所有 profile 公開 UI 路徑；default CSS 匿名仍為 200 | 已重現，未修；後續縮小公開路徑與錯誤派送規則 |
| LOGIN-F03 | saved request 測試實際也是 /home，未驗證不同目標 | 本次獨立 HTTP 補查通過；JUnit 待補 |
| LOGIN-F04 | default 測試只斷言不是 200，可誤放行 500，亦未測 demo 帳號拒絕 | 本次以 401／404 及 Basic 401 補查通過；JUnit 待補 |
| LOGIN-F05 | JUnit 缺少錯誤 CSRF token 的登出案例 | 本次 HTTP 403 且 Session 保持有效；JUnit 待補 |

本次結論僅為交接快照成功整合、demo 基本功能及列出的檢查通過。真 LDAP／Qwen／外部 Connector、真人瀏覽器視覺、coverage 百分比及 CVE 掃描未於本次驗證；正式 MQ／ST 案例仍維持原有 NOT_RUN 狀態。
