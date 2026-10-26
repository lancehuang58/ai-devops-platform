# 管理介面第一階段：固定測試帳號登入

## 目標與範圍

先交付最小可操作的管理介面，使用 Spring Security 表單登入與 HTTP Session。此階段僅於明確啟用的 `demo` profile 提供頁面，沿用既有 mock directory 與角色設定中的固定測試帳號，不增加正式身分來源。

LDAP 是後續階段；未來可透過現有 `DirectoryAuthenticationPort` 接入真實 directory adapter。本次不將固定帳號驗證宣稱為真 LDAP 驗證。

## 頁面與流程

| 路徑 | 行為 |
|---|---|
| `GET /` | 導向 `/home`；未登入者先進入登入流程 |
| `GET /login` | 公開的繁體中文登入頁：平台名稱、帳號、密碼、登入按鈕、測試模式提示 |
| `POST /login` | 由 Spring Security 驗證；表單包含 CSRF token，不由 controller 手動比對帳密 |
| `GET /home` | 必須登入；顯示平台首頁、已驗證帳號、測試模式提示與登出表單 |
| `GET /login-failure` | 公開的獨立失敗頁；通用錯誤訊息與返回登入頁的連結，不顯示密碼、例外或內部設定 |
| `POST /logout` | 必須提供 CSRF token；清除登入 Session 後回到 `/login?logout` |

有效帳密登入一律導向 `/home`，不因 saved request 或使用者提供的 URL 改變目標。無效帳密導向 `/login-failure`，不能取得已驗證 Session。密碼欄位不回填。

## 實作界線

- 採伺服器端 HTML 模板與一份本機 CSS；無 SPA、CDN 或外部前端建置流程。
- 建議使用 Thymeleaf，由模板輸出安全跳脫文字與 CSRF hidden input。
- 新增僅適用於頁面路徑的高優先序 SecurityFilterChain；原有 HTTP Basic／STATELESS chain 保留為其餘請求的 fallback。
- 頁面 chain 使用既有 AuthenticationManager，Session 採 IF_REQUIRED，維持 CSRF 與 Spring Security 預設安全標頭。
- `/api/**` 仍使用原有 HTTP Basic 驗證；瀏覽器頁面的 Session 不應讓 API 改成 Session 驗證或將 API 的 401 改成登入頁 redirect。
- 預設 profile 維持健康端點可用、不載入 demo 帳號或管理頁面。
- 不修改既有帳密、mock directory、角色映射與其他核心模組；頁面功能集中於 `web`。

## 驗收條件

1. 匿名請求 `/home` 會導向 `/login`；匿名可正常載入登入頁、失敗頁及必要的 CSS。
2. 登入頁有標籤、password input、POST action 與非空 CSRF token；密碼不回顯。
3. 有效帳密及 CSRF token 可登入，收到 redirect 至 `/home`，保留 Session 後可讀取首頁與已驗證帳號。
4. 錯誤密碼、未知帳號及空白輸入無法登入，導向失敗頁，仍不能讀取首頁。
5. 缺少／無效 CSRF token 的登入 POST 與登出 POST 被拒絕。
6. 正常登出後，舊 Session 不能再讀取首頁。
7. 健康端點匿名 HTTP 200；API 匿名 HTTP 401，原有 HTTP Basic 查詢測試通過；UI Session 不授權 API。
8. 預設 profile 不提供 demo 管理頁面或可登入的 demo 身分。
9. 既有六模組單元／架構／整合測試及新增測試通過；正式可執行 JAR 可打包。

## 執行方式

使用 JDK 21 與本機安裝的 Gradle 9.8.1，從專案根目錄執行：

```powershell
$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' :web:bootRun --args='--spring.profiles.active=demo'
```

開啟 `http://127.0.0.1:8080/login`，使用既有 demo 測試帳號。帳密來源與環境變數覆寫方式參閱 [受管服務查詢 Demo](managed-service-demo.md)。
