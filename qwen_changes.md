# 2026-10-22 Qwen 模型導入、登入 Demo 與委派規則變更包

本文件是**可重建修改的單檔交接包**，包含目前尚未 commit 的專案 patch、全域 OpenCode 檔案完整內容、設定合併方式、今天的測試經過與能力結論。其他 AI agent 不必取得原對話即可依此修改 source code。

## 1. 給接手 AI agent 的操作指引

- 專案：`ai-devops-platform`；原工作目錄：`D:/ops_project/gitlab_source/op/ai-devops-platform`。
- Patch 基準 HEAD：`26614343506fb0c0677c43f34efda6adf8578cd2`（`2661434 remove .idea`）。
- 技術版本：JDK 21、Spring Boot 4.1.1、Spring Security 7、Gradle 9.8.1、六模組六角形架構。
- 本文件記錄的是**實際交付快照**，包含已查出的未修問題；重現修改時先保持一致。安全規則改善及測試補強需另列主 agent 的修正任務，不能悄悄混入快照。
- 專案 patch 在第 5 節，涵蓋 3 個已追蹤修改檔及 10 個未追蹤新增檔；本文件自身不遞迴包含在 patch 中。
- OpenCode 全域變更在第 6 節，位於使用者設定目錄，不是專案 source tree；不能將整份個人 OpenCode 設定覆蓋或搬進 repository。
- 一律使用繁體中文，保留現有使用者修改，使用專用編輯工具；不執行 commit、push、credential 輪換或真實外部服務操作。
- 另一台機器可替換使用者家目錄、JDK 與 Gradle 的安裝路徑；要同步調整 `instructions`、agent 及規則內的絕對路徑。

### 1.1 套用專案修改

先以 `git status --short` 與 `git rev-parse HEAD` 確認現況。Git 能讀取 Markdown 中的 unified diff，以下命令不會套用第 6 節的全域設定：

```powershell
# 在尚未套用的基準 checkout，先檢查再套用；只修改 working tree，不 stage。
git apply --check -- "qwen_changes.md"
if ($?) { git apply -- "qwen_changes.md" }
```

若接手的是本次原工作目錄，可先執行下列**只檢查、不回退**的命令；成功代表 patch 已存在，不要再重複套用：

```powershell
git apply --reverse --check -- "qwen_changes.md"
```

若前向與反向檢查都失敗，代表有部分套用或其他變更，依各檔 patch 使用編輯工具逐一合併。不要用 reset／restore 丟棄既有工作。無法使用 Git patch 時，第 5 節新增檔的每個 `+` 行就是完整檔案內容；修改檔依 hunk 合併，移除 diff 的行首標記後再寫入。

## 2. 修改與新增檔案清單

### 2.1 Repository 內，均尚未 commit

| 狀態 | 路徑 | 用途 |
|---|---|---|
| 修改 | `README.md` | 新增 demo 管理介面使用說明 |
| 修改 | `web/build.gradle` | 增加 Thymeleaf starter |
| 修改 | `web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/SecurityConfiguration.java` | 原有 Basic chain 加排序與 error／UI 路徑規則 |
| 新增 | `web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/ManagementWebSecurityConfiguration.java` | demo UI 表單登入、Session 與 CSRF chain |
| 新增 | `web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/management/ManagementPageController.java` | 登入、主頁、失敗頁與根路徑導向 |
| 新增 | `web/src/main/resources/templates/login.html` | 登入頁 |
| 新增 | `web/src/main/resources/templates/home.html` | 已驗證帳號與登出表單 |
| 新增 | `web/src/main/resources/templates/login-failure.html` | 獨立失敗頁 |
| 新增 | `web/src/main/resources/static/css/management.css` | 本機 CSS |
| 新增 | `web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/ManagementLoginIT.java` | 14 項真實 HTTP 登入整合測試 |
| 新增 | `web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/DefaultProfileIsolationIT.java` | 2 項 default profile 測試 |
| 新增 | `doc/design/management-login-demo.md` | 主 agent 擬定的設計與驗收條件 |
| 新增 | `doc/testing/qwen-flash-login-evaluation.md` | 獨立驗收與能力評估 |
| 新增 | `qwen_changes.md` | 本單檔交接包；自身不包含在內嵌 patch |

沒有 source 刪除；沒有更動 model／service／adapter 的產品程式、既有帳密、mock directory 或角色映射。`build/`、`.gradle/`、JAR、測試報告及本機 session 資料不作為 source 變更交付。

### 2.2 Repository 外的全域 OpenCode 變更

| 狀態 | 路徑 | 用途 |
|---|---|---|
| 新增後調整 | `C:/Users/lance.huang/.config/opencode/agents/qwen-worker.md` | 固定 Qwen 模型；最後收斂為測試 worker，20 iterations、禁止巢狀委派 |
| 新增 | `C:/Users/lance.huang/.config/opencode/rules/qwen-worker-delegation.md` | 主 agent 與 worker 的分工、委派模板、驗收與停止條件 |
| 修改 | `C:/Users/lance.huang/.config/opencode/opencode.jsonc` | 增加 `instructions` 載入上述規則 |

既有 `delta` provider 與 `openai/Qwen/Qwen3.8-Flash-Next` 模型條目在本次工作前已存在；本次沒有新建 provider 或變更既有全域預設模型。完整模型 ID 是 `delta/openai/Qwen/Qwen3.8-Flash-Next`。

## 3. 今天的測試經過與實測結果

### 3.1 初次模型評估：由 Qwen 實作登入功能

1. 主 agent 先讀取架構與既有 Security／測試配置，建立設計文件。第一次評估時的委派範圍較廣，包含產品程式與測試實作；**後來的新規則已禁止預設如此委派**。
2. 基線命令 `test :web:integrationTest --console=plain` 成功。原有 53 項測試；多數 unit task 為 UP-TO-DATE，web integration task 實際執行，不能將它描述為全部重新跑過。
3. 呼叫 `qwen-worker`；session 為 `ses_e9d93dc40ffe7chtCYyq3RKLDE`。經 OpenCode export 的 assistant metadata 確認實際使用指定 Qwen 模型。
4. Qwen 回報先寫測試，預期 red 為新增 16 項中 13 項失敗。這是 worker 的執行回報；不要與主管最後的獨立 green 證據混為一談。
5. 過程誤用不支援的 `CsrfConfigurer.accessDeniedHandler`、已移除的 `AntPathRequestMatcher`；後來查實際 sources／API 修正。另因使用 PowerShell `Set-Content` 引入 UTF-8 BOM，導致編譯失敗。
6. Qwen 自述約 4 次非預期編譯失敗及 2 次測試斷言修正；session 紀錄也確認多次失敗與 2 次包含 `Set-Content` 的 shell 指令。正常、事先預期的 TDD red 不計為缺陷。
7. 子任務首末 assistant 訊息相距約 42 分鐘，169 筆 assistant 訊息、168 次工具呼叫，其中 shell 96 次。這包含工具與除錯時間，不代表純模型推論速度。
8. 交付後，主管停止追加 Qwen 修正，進行原始碼審查、全專案強制重跑與額外真實 HTTP 驗收。

### 3.2 主管強制重跑全專案

```powershell
$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' build --rerun-tasks --console=plain
```

結果為 **BUILD SUCCESSFUL、31 個 actionable tasks 全部實際執行**；bootJar 產出成功。由 JUnit XML 核對：

| 測試集 | 測試數 | 失敗／錯誤／跳過 |
|---|---:|---|
| model | 7 | 0／0／0 |
| service | 13 | 0／0／0 |
| adapter | 11 | 0／0／0 |
| model-quality-evaluation | 3 | 0／0／0 |
| solution-tool-testing | 3 | 0／0／0 |
| web 單元／架構 | 6 | 0／0／0 |
| web 整合 | 26 | 0／0／0 |
| **合計** | **69** | **0／0／0** |

原有 53 項加新增 16 項，不是新增 69 項。報告位置是各模組 `build/test-results/test/` 與 `web/build/test-results/integrationTest/`。

### 3.3 主管額外 JAR／HTTP 驗收

使用 JDK 21 啟動實際 JAR，在 demo 與 default profile 分別使用隨機 port；以 runtime 產生的測試密碼透過既有 `DEMO_NORMAL_PASSWORD` 環境變數傳入，不寫檔或輸出。維護 cookie jar、關閉自動跟隨 redirect、從 HTML 取真正 CSRF token，驗收後關閉本次啟動的程序。

| 斷言群組 | 數量 | 期待及結果 |
|---|---:|---|
| demo health、匿名根路徑、匿名首頁、登入頁、CSS | 5 | health／登入頁／CSS 200；受保護路徑導向登入 |
| 錯誤密碼、未知帳號、空白帳密，各驗證失敗導向與首頁拒絕 | 6 | 到 `/login-failure`，仍不能進首頁 |
| 通用失敗頁 | 1 | 200，無測試密碼或例外堆疊 |
| 登入缺少／錯誤 CSRF token | 2 | 403 |
| 有效登入的固定導向與已驗證首頁 | 2 | 固定到 `/home`，200 並顯示帳號 |
| UI Session 與 API 隔離、有效 Basic API | 2 | Session-only 401，Basic 200 |
| 登出缺少／錯誤 CSRF，各驗證拒絕與 Session 仍有效 | 4 | 403；首頁仍可讀 |
| 正常登出導向、重送舊 Session | 2 | `/login?logout`；舊 Session 不能進首頁 |
| default health、三個管理頁面不可用、拒絕 demo 身分 | 5 | health 200，頁面 401／404，Basic 身分拒絕 401 |
| **合計** | **29** | **全部通過** |

固定導向檢查使用不同的 saved request（根路徑及 query），並帶入外部 redirect 參數，仍到 `/home`。以上 29 個是主管額外斷言，不是另外已版控的 29 個測試方法；當時為一次性本機 HTTP 驗收，沒有新增驗收 script。

另有兩個觀察，不列為上述成功斷言：已登入且無 CSRF 的 `PUT /home` 回 **405**；default profile 的 `/css/management.css` 回 **200**。

### 3.4 評估後的新規則驗證

建立第 6 節規則後，以 OpenCode CLI `debug config`／`debug agent qwen-worker` 的過濾輸出確認：

- 設定可解析，`instructions` 包含規則完整路徑。
- agent 為 `subagent`，model 的 providerID 為 `delta`、modelID 為 `openai/Qwen/Qwen3.8-Flash-Next`。
- `steps` 為 20，task permission 為 deny。

新規則在登入評估結束後才加入，**42 分鐘的初次任務並未受 20 iterations 限制**。本次文件整理不代表再次重跑以上歷史測試。沒有量測 coverage 百分比、執行 CVE 掃描或做真人瀏覽器視覺測試。

## 4. 結論、未修問題與未來分工

**核心登入 demo 通過；不通過無人監督交付認證功能的標準。**

1. `ManagementWebSecurityConfiguration` 的自訂 CSRF matcher 只保護 POST，縮小預設對其他非安全方法的檢查。`PUT /home` 無 handler 而回 405，目前未觀察到該路徑產生變更，但不能將此安全語意當作完整保留預設值。
2. fallback `SecurityConfiguration` 為處理 ERROR dispatch，擴張為在所有 profile 公開多個 UI 路徑與 CSS。demo `/home` 仍被高優先序 chain 保護，但額外公開範圍缺少必要性論證。
3. `loginAlwaysRedirectsToHomeEvenWhenSavedRequestDiffers` 實際存的也是 `/home`；default profile 測試只檢查不是 200，可能誤放行 500；缺少錯誤 token 的登出案例。主管已另查實際行為通過，但測試程式缺口尚未補上。
4. worker 將登出成功導向歸因於 session fixation，概念不正確。導向由 `logoutSuccessUrl`、失效由 logout handler 處理；session fixation 主要作用於登入。
5. 工具規則及停損不可靠；主管也在長委派交付後才取得完整失敗紀錄，未在執行中硬性截斷。之後改為短任務、分段審查及 20 iterations 限制。

以上問題保留在第 5 節快照，沒有暗中修正。可將 Qwen 定位為受監督的測試實作者：

- **主 agent**：功能與安全設計、產品程式實作、測試案例與預期結果、依賴決策、最終驗收。
- **qwen-worker**：依既定案例撰寫／執行簡單 unit test、小範圍 integration test。
- 一次一個目標，通常 3–5 個案例與明確測試檔白名單；累計兩次非預期失敗即停，最多一次限定修正，禁止越界改產品程式與巢狀委派。
- `steps: 20` 限制 agentic iterations，不是 20 次工具呼叫或固定分鐘 timeout；失敗計數仍要由主 agent 分段監督。

真 LDAP、正式身分來源、複雜授權、併發與大型重構能力未在此評估中驗證。

## 5. Repository 完整可套用 Patch

以下單一 diff 區塊包含第 2.1 節中除本文件本身以外的全部 13 個檔案。新增檔為完整內容，沒有省略號。README 中 Wrapper 示例是原交付內容；在本機實際操作時，一律改用第 7 節的本機 Gradle，禁止執行 Wrapper。

````diff
diff --git a/README.md b/README.md
index 6380bb6..33bfb69 100644
--- a/README.md
+++ b/README.md
@@ -78,4 +78,12 @@
 - [roles.yml](config/roles.yml)：帳號角色映射。
 - [完整流程與設定說明](doc/design/managed-service-demo.md)：API、環境變數、DB catalog 演進、事件與失敗語意。
 
+## 管理介面（demo）
+
+```bash
+./gradlew :web:bootRun --args='--spring.profiles.active=demo'
+```
+
+開啟 `http://127.0.0.1:8080/login`，使用上述 demo 測試帳號以表單登入；登入成功導向 `/home`，失敗到 `/login-failure`，`POST /logout` 登出回登入頁。管理頁面僅在 demo profile 提供，採 Spring Security 表單登入＋Session＋CSRF；`/api/**` 仍為 HTTP Basic，API 行為不受 UI Session 影響。設計與驗收條件見 [管理介面第一階段設計](doc/design/management-login-demo.md)。
+
 執行流程用 model／service／adapter／web；web 整合測試另外接入 MQ／ST，涵蓋全部六模組。尚未實作真連線 adapter，因此改 URL／key 不會自動接通，改成正式 provider mode 會拒絕啟動。
diff --git a/web/build.gradle b/web/build.gradle
index 94271c8..9d30cbb 100644
--- a/web/build.gradle
+++ b/web/build.gradle
@@ -6,6 +6,7 @@ dependencies {
     implementation 'org.springframework.boot:spring-boot-starter-actuator'
     implementation 'org.springframework.boot:spring-boot-starter-security'
     implementation 'org.springframework.boot:spring-boot-starter-webmvc'
+    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
     testImplementation 'com.tngtech.archunit:archunit:1.5.1'
     testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
     testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
diff --git a/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/SecurityConfiguration.java b/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/SecurityConfiguration.java
index e2d82a0..ec9b9ae 100644
--- a/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/SecurityConfiguration.java
+++ b/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/SecurityConfiguration.java
@@ -4,6 +4,7 @@
 import com.delta.dms.ops.aidevopsplatform.service.port.out.RoleDirectory;
 import org.springframework.beans.factory.ObjectProvider;
 import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
+import org.springframework.core.annotation.Order;
 import org.springframework.context.annotation.*;
 import org.springframework.security.authentication.*;
 import org.springframework.security.core.authority.SimpleGrantedAuthority;
@@ -31,9 +32,13 @@ public class SecurityConfiguration {
             finally {Arrays.fill(password,'\0');}
         };
     }
-    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
+    @Bean @Order(2) SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
         return http.authenticationManager(authenticationManager)
-                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
+                .authorizeHttpRequests(auth -> auth
+                        // UI 路徑由優先序較高的管理頁面鏈把關；/error 讓 ERROR dispatch 直達容器錯誤頁。
+                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
+                        .requestMatchers("/","/login","/login-failure","/home","/logout","/css/**","/error").permitAll()
+                        .requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
                 .httpBasic(basic -> { })
                 .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                 .build();
diff --git a/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/ManagementWebSecurityConfiguration.java b/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/ManagementWebSecurityConfiguration.java
new file mode 100644
--- /dev/null
+++ b/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/config/ManagementWebSecurityConfiguration.java
@@ -0,0 +1,43 @@
+package com.delta.dms.ops.aidevopsplatform.web.config;
+
+import org.springframework.context.annotation.Bean;
+import org.springframework.context.annotation.Configuration;
+import org.springframework.context.annotation.Profile;
+import org.springframework.core.annotation.Order;
+import org.springframework.security.authentication.AuthenticationManager;
+import org.springframework.security.config.annotation.web.builders.HttpSecurity;
+import org.springframework.security.config.http.SessionCreationPolicy;
+import org.springframework.security.web.SecurityFilterChain;
+import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
+import org.springframework.security.web.util.matcher.RequestMatcher;
+import org.springframework.http.HttpMethod;
+import java.util.Arrays;
+
+/**
+ * demo profile 專屬的管理頁面安全鏈：表單登入 + HTTP Session + CSRF。
+ * 只匹配明確的 UI 路徑，其餘請求維持原有 HTTP Basic／STATELESS 鏈。
+ */
+@Configuration(proxyBeanMethods=false)
+@Profile("demo")
+public class ManagementWebSecurityConfiguration {
+    static final String[] UI_PATHS={"/","/login","/login-failure","/home","/logout","/css/**"};
+    // CSRF 失敗必須停在 UI 鏈並回 403，不能讓請求落到 API 鏈變成 Basic 401。
+    private static final RequestMatcher UI_POSTS = request -> Arrays.stream(UI_PATHS)
+            .anyMatch(pattern -> PathPatternRequestMatcher.withDefaults()
+                    .matcher(HttpMethod.POST, pattern).matches(request));
+
+    @Bean @Order(1) SecurityFilterChain managementUiFilterChain(HttpSecurity http,
+            AuthenticationManager authenticationManager) throws Exception {
+        return http.securityMatcher(UI_PATHS).authenticationManager(authenticationManager)
+                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(UI_POSTS))
+                .authorizeHttpRequests(auth -> auth
+                        // 靜態資源與登入頁公開；其餘 UI 路徑（含 POST /、GET /）一律先登入。
+                        .requestMatchers("/login","/login-failure","/css/**").permitAll()
+                        .anyRequest().authenticated())
+                .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
+                        .defaultSuccessUrl("/home",true).failureUrl("/login-failure").permitAll())
+                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
+                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
+                .build();
+    }
+}
diff --git a/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/management/ManagementPageController.java b/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/management/ManagementPageController.java
new file mode 100644
--- /dev/null
+++ b/web/src/main/java/com/delta/dms/ops/aidevopsplatform/web/management/ManagementPageController.java
@@ -0,0 +1,34 @@
+package com.delta.dms.ops.aidevopsplatform.web.management;
+
+import org.springframework.context.annotation.Profile;
+import org.springframework.security.core.userdetails.UserDetails;
+import org.springframework.stereotype.Controller;
+import org.springframework.ui.Model;
+import org.springframework.web.bind.annotation.GetMapping;
+
+import java.security.Principal;
+
+/** demo profile 管理頁面；帳密驗證一律由 Spring Security 表單登入流程處理。 */
+@Controller
+@Profile("demo")
+public class ManagementPageController {
+    @GetMapping("/")
+    public String home() { return "redirect:/home"; }
+
+    @GetMapping("/login")
+    public String login() { return "login"; }
+
+    @GetMapping("/login-failure")
+    public String loginFailure() { return "login-failure"; }
+
+    @GetMapping("/home")
+    public String home(Model model, Principal principal) {
+        model.addAttribute("username", accountName(principal));
+        return "home";
+    }
+
+    private String accountName(Principal principal) {
+        if (principal instanceof UserDetails user) return user.getUsername();
+        return principal == null ? "" : principal.getName();
+    }
+}
diff --git a/web/src/main/resources/templates/login.html b/web/src/main/resources/templates/login.html
new file mode 100644
--- /dev/null
+++ b/web/src/main/resources/templates/login.html
@@ -0,0 +1,27 @@
+<!DOCTYPE html>
+<html xmlns:th="http://www.thymeleaf.org" lang="zh-Hant">
+<head>
+    <meta charset="UTF-8">
+    <meta name="viewport" content="width=device-width, initial-scale=1">
+    <title>登入｜AI DevOps 管理平台</title>
+    <link rel="stylesheet" th:href="@{/css/management.css}">
+</head>
+<body>
+<main class="card">
+    <h1>AI DevOps 管理平台</h1>
+    <p class="notice">測試模式：本環境使用固定的 demo 測試帳號（mock directory），並非正式 LDAP 驗證。</p>
+    <p th:if="${param.logout}" class="notice success">您已登出。</p>
+    <form th:action="@{/login}" method="post">
+        <div class="field">
+            <label for="username">帳號</label>
+            <input id="username" name="username" type="text" autocomplete="username" required autofocus>
+        </div>
+        <div class="field">
+            <label for="password">密碼</label>
+            <input id="password" name="password" type="password" autocomplete="current-password" required>
+        </div>
+        <button type="submit">登入</button>
+    </form>
+</main>
+</body>
+</html>
diff --git a/web/src/main/resources/templates/home.html b/web/src/main/resources/templates/home.html
new file mode 100644
--- /dev/null
+++ b/web/src/main/resources/templates/home.html
@@ -0,0 +1,20 @@
+<!DOCTYPE html>
+<html xmlns:th="http://www.thymeleaf.org" lang="zh-Hant">
+<head>
+    <meta charset="UTF-8">
+    <meta name="viewport" content="width=device-width, initial-scale=1">
+    <title>首頁｜AI DevOps 管理平台</title>
+    <link rel="stylesheet" th:href="@{/css/management.css}">
+</head>
+<body>
+<main class="card">
+    <h1>平台首頁</h1>
+    <p class="notice">測試模式：目前為 demo mock directory 環境，非正式 LDAP。</p>
+    <p>已驗證帳號：<strong th:text="${username}"></strong></p>
+    <form th:action="@{/logout}" method="post">
+        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
+        <button type="submit">登出</button>
+    </form>
+</main>
+</body>
+</html>
diff --git a/web/src/main/resources/templates/login-failure.html b/web/src/main/resources/templates/login-failure.html
new file mode 100644
--- /dev/null
+++ b/web/src/main/resources/templates/login-failure.html
@@ -0,0 +1,15 @@
+<!DOCTYPE html>
+<html lang="zh-Hant">
+<head>
+    <meta charset="UTF-8">
+    <title>登入失敗｜AI DevOps 管理平台</title>
+    <link rel="stylesheet" href="/css/management.css">
+</head>
+<body>
+<main class="card">
+    <h1>登入失敗</h1>
+    <p class="notice error">帳號或密碼不正確，請重新登入。</p>
+    <p><a href="/login">返回登入頁</a></p>
+</main>
+</body>
+</html>
diff --git a/web/src/main/resources/static/css/management.css b/web/src/main/resources/static/css/management.css
new file mode 100644
--- /dev/null
+++ b/web/src/main/resources/static/css/management.css
@@ -0,0 +1,51 @@
+:root { color-scheme: light; }
+* { box-sizing: border-box; }
+body {
+    margin: 0;
+    min-height: 100vh;
+    display: flex;
+    align-items: center;
+    justify-content: center;
+    background: #f2f4f7;
+    color: #1f2933;
+    font-family: "Microsoft JhengHei", "Noto Sans TC", sans-serif;
+}
+.card {
+    width: min(26rem, 92vw);
+    background: #fff;
+    border: 1px solid #d6dbe1;
+    border-radius: 8px;
+    padding: 2rem;
+    box-shadow: 0 2px 8px rgba(15, 35, 52, .08);
+}
+h1 { margin-top: 0; font-size: 1.4rem; }
+.notice {
+    background: #eef2f7;
+    border-left: 4px solid #6b7a8f;
+    padding: .5rem .75rem;
+    font-size: .9rem;
+}
+.notice.error { border-left-color: #b3261e; background: #fbeeec; }
+.notice.success { border-left-color: #2e7d32; background: #edf5ee; }
+.field { margin: 1rem 0; }
+label { display: block; margin-bottom: .25rem; font-weight: 600; }
+input {
+    width: 100%;
+    padding: .5rem .6rem;
+    border: 1px solid #b7bfca;
+    border-radius: 4px;
+    font-size: 1rem;
+}
+input:focus { outline: 2px solid #3b6ea5; outline-offset: 1px; }
+button {
+    margin-top: .5rem;
+    padding: .55rem 1.4rem;
+    border: none;
+    border-radius: 4px;
+    background: #2f5d8a;
+    color: #fff;
+    font-size: 1rem;
+    cursor: pointer;
+}
+button:hover { background: #244a6e; }
+a { color: #2f5d8a; }
diff --git a/web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/DefaultProfileIsolationIT.java b/web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/DefaultProfileIsolationIT.java
new file mode 100644
--- /dev/null
+++ b/web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/DefaultProfileIsolationIT.java
@@ -0,0 +1,38 @@
+package com.delta.dms.ops.aidevopsplatform.web.integration;
+
+import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
+import org.junit.jupiter.api.Test;
+import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.boot.test.web.server.LocalServerPort;
+
+import java.net.URI;
+import java.net.http.HttpClient;
+import java.net.http.HttpRequest;
+import java.net.http.HttpResponse;
+import java.time.Duration;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+/** 預設 profile 不得載入 demo 管理頁面或可登入的 demo 身分。 */
+@SpringBootTest(classes=AiDevopsPlatformApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
+class DefaultProfileIsolationIT {
+    @LocalServerPort int port;
+
+    private HttpResponse<String> get(String path) throws Exception {
+        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
+                .connectTimeout(Duration.ofSeconds(5)).build()) {
+            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
+                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
+        }
+    }
+
+    @Test void healthEndpointStaysAnonymousInDefaultProfile() throws Exception {
+        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
+    }
+
+    @Test void managementPagesAreNotAvailableInDefaultProfile() throws Exception {
+        for (String path : new String[]{"/login", "/login-failure", "/home"}) {
+            assertThat(get(path).statusCode()).as("default profile 不應提供 %s", path).isNotEqualTo(200);
+        }
+    }
+}
diff --git a/web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/ManagementLoginIT.java b/web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/ManagementLoginIT.java
new file mode 100644
--- /dev/null
+++ b/web/src/integrationTest/java/com/delta/dms/ops/aidevopsplatform/web/integration/ManagementLoginIT.java
@@ -0,0 +1,283 @@
+package com.delta.dms.ops.aidevopsplatform.web.integration;
+
+import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
+import org.junit.jupiter.api.Test;
+import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.boot.test.web.server.LocalServerPort;
+import org.springframework.test.context.ActiveProfiles;
+
+import java.io.IOException;
+import java.net.CookieManager;
+import java.net.CookiePolicy;
+import java.net.URI;
+import java.net.URLEncoder;
+import java.net.http.HttpClient;
+import java.net.http.HttpRequest;
+import java.net.http.HttpResponse;
+import java.nio.charset.StandardCharsets;
+import java.time.Duration;
+import java.util.Base64;
+import java.util.List;
+import java.util.regex.Matcher;
+import java.util.regex.Pattern;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+/**
+ * 管理介面登入整合測試：以 random-port 真實 HTTP 請求實際通過 Spring Security
+ * filter chain，並由 HttpClient 內建 CookieManager 維護 Session cookie。
+ */
+@ActiveProfiles("demo")
+@SpringBootTest(classes=AiDevopsPlatformApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
+        properties={"platform.database.jdbc-url=jdbc:h2:mem:login_it;DB_CLOSE_DELAY=-1",
+                "platform.llm.model-name=login-it-mock"})
+class ManagementLoginIT {
+    private static final String CSS = "/css/management.css";
+    private static final Pattern CSRF = Pattern.compile(
+            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
+
+    @LocalServerPort int port;
+
+    private String url(String path) { return "http://127.0.0.1:" + port + path; }
+
+    /** 保留 cookie 但不自動追隨 redirect 的客戶端。 */
+    private HttpClient sessionClient() {
+        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
+                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
+                .connectTimeout(Duration.ofSeconds(5)).build();
+    }
+
+    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
+        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
+                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
+    }
+
+    private HttpResponse<String> postForm(HttpClient client, String path, String form, String contentType)
+            throws Exception {
+        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
+                .timeout(Duration.ofSeconds(10)).header("Content-Type", contentType)
+                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
+    }
+
+    private String csrfToken(HttpClient client, String path) throws Exception {
+        var page = get(client, path);
+        assertThat(page.statusCode()).isEqualTo(200);
+        return extractCsrf(page.body());
+    }
+
+    private String extractCsrf(String html) {
+        Matcher matcher = CSRF.matcher(html);
+        assertThat(matcher.find()).as("頁面應包含 CSRF hidden input").isTrue();
+        String token = matcher.group(1);
+        assertThat(token).isNotBlank();
+        return token;
+    }
+
+    private String form(String csrf, String username, String password) {
+        return "_csrf=" + enc(csrf) + "&username=" + enc(username) + "&password=" + enc(password);
+    }
+
+    /** Spring Security 以 sendRedirect 輸出絕對 Location，比對時只取 path 與 query。 */
+    private String locationPath(HttpResponse<String> response) {
+        String location = response.headers().firstValue("Location")
+                .orElseThrow(() -> new AssertionError("302 應攜帶 Location"));
+        URI uri = URI.create(location);
+        String path = uri.isAbsolute() ? uri.getPath() : location;
+        return uri.getQuery() == null ? path : path + "?" + uri.getQuery();
+    }
+
+    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
+
+    /** 完整登入流程：讀登入頁取 token，POST 帳密，回傳執行登入的客戶端（含 cookie jar）。 */
+    private HttpClient login(String username, String password) throws Exception {
+        HttpClient client = sessionClient();
+        String csrf = csrfToken(client, "/login");
+        var response = postForm(client, "/login", form(csrf, username, password),
+                "application/x-www-form-urlencoded");
+        assertThat(response.statusCode()).isEqualTo(302);
+        return client;
+    }
+
+    // ---- 驗收 1：匿名導向與公開頁面 ----
+
+    @Test void anonymousHomeRedirectsToLogin() throws Exception {
+        try (var client = sessionClient()) {
+            var response = get(client, "/home");
+            assertThat(response.statusCode()).isEqualTo(302);
+            assertThat(locationPath(response)).isEqualTo("/login");
+        }
+    }
+
+    @Test void anonymousRootRedirectsIntoLoginFlow() throws Exception {
+        try (var client = sessionClient()) {
+            // 匿名進入根路徑：由 UI 鏈導入登入流程
+            var response = get(client, "/");
+            assertThat(response.statusCode()).isEqualTo(302);
+            assertThat(locationPath(response)).isEqualTo("/login");
+        }
+    }
+
+    @Test void anonymousCanLoadLoginFailurePageAndCss() throws Exception {
+        try (var client = sessionClient()) {
+            var login = get(client, "/login");
+            assertThat(login.statusCode()).isEqualTo(200);
+            var failure = get(client, "/login-failure");
+            assertThat(failure.statusCode()).isEqualTo(200);
+            var css = get(client, CSS);
+            assertThat(css.statusCode()).isEqualTo(200);
+            assertThat(css.body()).isNotBlank();
+        }
+    }
+
+    // ---- 驗收 2：登入頁結構 ----
+
+    @Test void loginPageHasLabeledPasswordInputPostActionAndCsrf() throws Exception {
+        try (var client = sessionClient()) {
+            var page = get(client, "/login");
+            assertThat(page.statusCode()).isEqualTo(200);
+            String html = page.body();
+            assertThat(html).contains("name=\"password\"")
+                    .contains("type=\"password\"")
+                    .contains("action=\"/login\"")
+                    .contains("method=\"post\"");
+            Matcher idFor = Pattern.compile("<label[^>]*for=\"([^\"]+)\"").matcher(html);
+            assertThat(idFor.find()).as("登入欄位應有 label").isTrue();
+            assertThat(html).contains("id=\"" + idFor.group(1) + "\"");
+            assertThat(extractCsrf(html)).isNotBlank();
+            // 密碼欄位不回填
+            assertThat(html).doesNotContain("password\" value=", "password value=");
+        }
+    }
+
+    // ---- 驗收 3：有效帳密登入 ----
+
+    @Test void validCredentialsRedirectToHomeAndKeepSession() throws Exception {
+        try (var client = login("normal", "demo-normal")) {
+            var home = get(client, "/home");
+            assertThat(home.statusCode()).isEqualTo(200);
+            assertThat(home.body()).contains("normal");
+        }
+    }
+
+    @Test void loginAlwaysRedirectsToHomeEvenWhenSavedRequestDiffers() throws Exception {
+        try (var client = sessionClient()) {
+            // 先觸發受保護頁留下導向 /home 的 saved request，登入仍必須固定導向 /home
+            var attempt = get(client, "/home");
+            assertThat(attempt.statusCode()).isEqualTo(302);
+            String csrf = csrfToken(client, "/login");
+            var response = postForm(client, "/login", form(csrf, "admin", "demo-admin"),
+                    "application/x-www-form-urlencoded");
+            assertThat(response.statusCode()).isEqualTo(302);
+            assertThat(locationPath(response)).isEqualTo("/home");
+        }
+    }
+
+    // ---- 驗收 4：無效帳密 ----
+
+    @Test void wrongPasswordUnknownAccountAndBlankInputGoToFailurePage() throws Exception {
+        for (String[] credentials : new String[][]{{"normal", "wrong-password"}, {"ghost", "demo-ghost"}, {"", ""}}) {
+            try (var client = sessionClient()) {
+                String csrf = csrfToken(client, "/login");
+                var response = postForm(client, "/login", form(csrf, credentials[0], credentials[1]),
+                        "application/x-www-form-urlencoded");
+                assertThat(response.statusCode()).as("無效帳密 %s 應導向失敗頁", credentials[0]).isEqualTo(302);
+                assertThat(locationPath(response)).as("無效帳密 %s 應導向失敗頁", credentials[0]).isEqualTo("/login-failure");
+                assertThat(get(client, "/home").statusCode()).as("登入失敗後仍不可讀取首頁").isEqualTo(302);
+                assertThat(get(client, "/login-failure").statusCode()).isEqualTo(200);
+            }
+        }
+    }
+
+    @Test void failurePageIsGenericWithoutSecretsOrStacktrace() throws Exception {
+        try (var client = sessionClient()) {
+            String csrf = csrfToken(client, "/login");
+            postForm(client, "/login", form(csrf, "normal", "wrong-password"),
+                    "application/x-www-form-urlencoded");
+            String html = get(client, "/login-failure").body();
+            assertThat(html).contains("/login");
+            assertThat(html).doesNotContain("demo-normal", "Exception", "password");
+        }
+    }
+
+    // ---- 驗收 5：CSRF ----
+
+    @Test void loginPostWithoutCsrfIsRejected() throws Exception {
+        try (var client = sessionClient()) {
+            var response = postForm(client, "/login", "username=normal&password=demo-normal",
+                    "application/x-www-form-urlencoded");
+            assertThat(response.statusCode()).isEqualTo(403);
+            assertThat(get(client, "/home").statusCode()).isEqualTo(302);
+        }
+    }
+
+    @Test void loginPostWithInvalidCsrfIsRejected() throws Exception {
+        try (var client = sessionClient()) {
+            var response = postForm(client, "/login", form("not-a-real-token", "normal", "demo-normal"),
+                    "application/x-www-form-urlencoded");
+            assertThat(response.statusCode()).isEqualTo(403);
+            assertThat(get(client, "/home").statusCode()).isEqualTo(302);
+        }
+    }
+
+    @Test void logoutPostWithoutCsrfKeepsSessionValid() throws Exception {
+        try (var client = login("normal", "demo-normal")) {
+            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
+            var response = postForm(client, "/logout", "dummy=1", "application/x-www-form-urlencoded");
+            assertThat(response.statusCode()).isEqualTo(403);
+            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
+        }
+    }
+
+    // ---- 驗收 6：登出 ----
+
+    @Test void logoutRedirectsToLoginAndInvalidatesOldSession() throws Exception {
+        try (var client = login("normal", "demo-normal")) {
+            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
+            String csrf = csrfToken(client, "/home");
+            var response = postForm(client, "/logout", "_csrf=" + enc(csrf),
+                    "application/x-www-form-urlencoded");
+            assertThat(response.statusCode()).isEqualTo(302);
+            assertThat(locationPath(response)).isEqualTo("/login?logout");
+            assertThat(get(client, "/home").statusCode()).isEqualTo(302);
+        }
+    }
+
+    // ---- 驗收 7：API 鏈維持 Basic／401，UI Session 不授權 API ----
+
+    @Test void healthRemainsAnonymousAndApiKeepsBasicBehaviour() throws Exception {
+        try (var client = sessionClient()) {
+            var health = get(client, "/actuator/health");
+            assertThat(health.statusCode()).isEqualTo(200);
+
+            var anonymous = get(client, "/api/v1/managed-services");
+            assertThat(anonymous.statusCode()).isEqualTo(401);
+
+            var basic = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/managed-services")))
+                    .timeout(Duration.ofSeconds(10))
+                    .header("Authorization", basic("normal", "demo-normal")).GET()
+                    .build(), HttpResponse.BodyHandlers.ofString());
+            assertThat(basic.statusCode()).isEqualTo(200);
+            assertThat(basic.body()).contains("jenkins-demo");
+        }
+    }
+
+    @Test void authenticatedUiSessionDoesNotAuthorizeApi() throws Exception {
+        try (var client = login("normal", "demo-normal")) {
+            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
+            var anonymous = get(client, "/api/v1/managed-services");
+            assertThat(anonymous.statusCode()).isEqualTo(401);
+            // 携带 UI session cookie 也不能讓 API 通過驗證，取得與 Basic 相同的資料
+            var basic = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/managed-services")))
+                    .timeout(Duration.ofSeconds(10))
+                    .header("Authorization", basic("normal", "demo-normal")).GET()
+                    .build(), HttpResponse.BodyHandlers.ofString());
+            assertThat(basic.statusCode()).isEqualTo(200);
+            assertThat(basic.body()).contains("jenkins-demo");
+        }
+    }
+
+    private String basic(String user, String password) {
+        return "Basic " + Base64.getEncoder()
+                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
+    }
+}
diff --git a/doc/design/management-login-demo.md b/doc/design/management-login-demo.md
new file mode 100644
--- /dev/null
+++ b/doc/design/management-login-demo.md
@@ -0,0 +1,53 @@
+# 管理介面第一階段：固定測試帳號登入
+
+## 目標與範圍
+
+先交付最小可操作的管理介面，使用 Spring Security 表單登入與 HTTP Session。此階段僅於明確啟用的 `demo` profile 提供頁面，沿用既有 mock directory 與角色設定中的固定測試帳號，不增加正式身分來源。
+
+LDAP 是後續階段；未來可透過現有 `DirectoryAuthenticationPort` 接入真實 directory adapter。本次不將固定帳號驗證宣稱為真 LDAP 驗證。
+
+## 頁面與流程
+
+| 路徑 | 行為 |
+|---|---|
+| `GET /` | 導向 `/home`；未登入者先進入登入流程 |
+| `GET /login` | 公開的繁體中文登入頁：平台名稱、帳號、密碼、登入按鈕、測試模式提示 |
+| `POST /login` | 由 Spring Security 驗證；表單包含 CSRF token，不由 controller 手動比對帳密 |
+| `GET /home` | 必須登入；顯示平台首頁、已驗證帳號、測試模式提示與登出表單 |
+| `GET /login-failure` | 公開的獨立失敗頁；通用錯誤訊息與返回登入頁的連結，不顯示密碼、例外或內部設定 |
+| `POST /logout` | 必須提供 CSRF token；清除登入 Session 後回到 `/login?logout` |
+
+有效帳密登入一律導向 `/home`，不因 saved request 或使用者提供的 URL 改變目標。無效帳密導向 `/login-failure`，不能取得已驗證 Session。密碼欄位不回填。
+
+## 實作界線
+
+- 採伺服器端 HTML 模板與一份本機 CSS；無 SPA、CDN 或外部前端建置流程。
+- 建議使用 Thymeleaf，由模板輸出安全跳脫文字與 CSRF hidden input。
+- 新增僅適用於頁面路徑的高優先序 SecurityFilterChain；原有 HTTP Basic／STATELESS chain 保留為其餘請求的 fallback。
+- 頁面 chain 使用既有 AuthenticationManager，Session 採 IF_REQUIRED，維持 CSRF 與 Spring Security 預設安全標頭。
+- `/api/**` 仍使用原有 HTTP Basic 驗證；瀏覽器頁面的 Session 不應讓 API 改成 Session 驗證或將 API 的 401 改成登入頁 redirect。
+- 預設 profile 維持健康端點可用、不載入 demo 帳號或管理頁面。
+- 不修改既有帳密、mock directory、角色映射與其他核心模組；頁面功能集中於 `web`。
+
+## 驗收條件
+
+1. 匿名請求 `/home` 會導向 `/login`；匿名可正常載入登入頁、失敗頁及必要的 CSS。
+2. 登入頁有標籤、password input、POST action 與非空 CSRF token；密碼不回顯。
+3. 有效帳密及 CSRF token 可登入，收到 redirect 至 `/home`，保留 Session 後可讀取首頁與已驗證帳號。
+4. 錯誤密碼、未知帳號及空白輸入無法登入，導向失敗頁，仍不能讀取首頁。
+5. 缺少／無效 CSRF token 的登入 POST 與登出 POST 被拒絕。
+6. 正常登出後，舊 Session 不能再讀取首頁。
+7. 健康端點匿名 HTTP 200；API 匿名 HTTP 401，原有 HTTP Basic 查詢測試通過；UI Session 不授權 API。
+8. 預設 profile 不提供 demo 管理頁面或可登入的 demo 身分。
+9. 既有六模組單元／架構／整合測試及新增測試通過；正式可執行 JAR 可打包。
+
+## 執行方式
+
+使用 JDK 21 與本機安裝的 Gradle 9.8.1，從專案根目錄執行：
+
+```powershell
+$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
+& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' :web:bootRun --args='--spring.profiles.active=demo'
+```
+
+開啟 `http://127.0.0.1:8080/login`，使用既有 demo 測試帳號。帳密來源與環境變數覆寫方式參閱 [受管服務查詢 Demo](managed-service-demo.md)。
diff --git a/doc/testing/qwen-flash-login-evaluation.md b/doc/testing/qwen-flash-login-evaluation.md
new file mode 100644
--- /dev/null
+++ b/doc/testing/qwen-flash-login-evaluation.md
@@ -0,0 +1,122 @@
+# Qwen Flash 管理介面登入實作評估
+
+日期：2026-10-22
+
+## 結論
+
+**核心 demo 功能通過獨立驗收；無人監督交付、測試完整性與執行規則遵循不通過。**
+
+此模型能在具體設計與可執行測試的約束下，完成小型 Spring MVC／Spring Security 功能並修到可運作；本次觀察不足以支持讓它獨立決定認證安全設定、判定測試覆蓋完整或自行執行停損。主管已停止追加委派與程式修正，保留交付版本及本報告供評估。
+
+## 任務與分工
+
+- 使用者要求：先以固定測試帳號實作登入頁，Spring Security 負責驗證；成功導向首頁，失敗導向獨立頁面；由 Qwen 實作、測試，再由主管驗收與評判。
+- 主管提供的設計：[管理介面第一階段](../design/management-login-demo.md)。
+- `qwen-worker` 實作：兩條 filter chain 的組裝、MVC controller、三個 HTML 模板、CSS、兩個整合測試類別、Thymeleaf 依賴與 README 說明。
+- 主管負責：功能設計、原始基線、交付後原始碼審查、執行紀錄稽核、強制重跑全專案建置與額外 HTTP 驗收；未代替 Qwen 修正交付程式。
+- 真 LDAP 不在本次範圍；沿用既有 demo mock directory 與角色設定，沒有改動既有帳密。
+
+## 模型及執行證據
+
+- 子任務 session：`ses_e9d93dc40ffe7chtCYyq3RKLDE`。
+- 經 OpenCode session export 的 assistant 訊息 metadata 確認，模型為 `delta/openai/Qwen/Qwen3.8-Flash-Next`。
+- 子任務首末 assistant 訊息的時間跨度約 **42 分鐘**；包含模型、工具與除錯時間，並非純推論速度。
+- 共 169 筆 assistant 訊息、168 次工具呼叫，其中 96 次為 shell 工具。
+- 工具紀錄確認有 2 次 shell 指令包含 `Set-Content`。Qwen 亦自行回報曾因這種編輯方式引入 UTF-8 BOM，造成編譯失敗。
+- Qwen 自述約 4 次非預期編譯失敗及 2 次測試斷言修正；紀錄亦可確認多次建置／測試失敗。正常的預期 TDD red 不作為能力缺陷計算。
+
+## 主管獨立驗證
+
+### 全專案建置與測試
+
+在 JDK 21／本機 Gradle 9.8.1 執行：
+
+```powershell
+$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
+& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' build --rerun-tasks --console=plain
+```
+
+結果：**BUILD SUCCESSFUL，31 個 actionable tasks 全部實際執行**，可執行 JAR 打包成功；不是沿用 UP-TO-DATE 作為新驗證證據。
+
+由各模組 JUnit XML 報告核對：
+
+| 模組／測試集 | 測試數 | 失敗／錯誤／跳過 |
+|---|---:|---|
+| model | 7 | 0／0／0 |
+| service | 13 | 0／0／0 |
+| adapter | 11 | 0／0／0 |
+| model-quality-evaluation | 3 | 0／0／0 |
+| solution-tool-testing | 3 | 0／0／0 |
+| web 單元與架構 | 6 | 0／0／0 |
+| web 整合 | 26 | 0／0／0 |
+| **合計** | **69** | **0／0／0** |
+
+其中新增 16 項整合測試：`ManagementLoginIT` 14 項、`DefaultProfileIsolationIT` 2 項。原有測試數為 53。
+
+### 額外真實 HTTP 驗收
+
+主管以 JDK 21 啟動產出的實際 JAR，在 demo 與 default profile 分別以隨機 port 驗證，共 **29 個斷言全部通過**。使用 runtime 產生的測試密碼透過既有 demo 環境變數傳入，不輸出其值；驗收結束即關閉驗收啟動的程序。
+
+驗證項目包括：
+
+- 匿名 health、登入頁、CSS、受保護首頁 redirect。
+- 錯誤密碼、未知帳號及空白帳密均導向失敗頁，並仍不能讀取首頁。
+- 失敗頁使用通用訊息，不含測試密碼或例外堆疊。
+- 缺少及錯誤 CSRF token 的登入、登出 POST 均回 403；拒絕登出後原登入仍有效。
+- 有效登入固定導向 `/home`，保留 Session 後顯示已驗證帳號。
+- saved request 先設為根路徑及 query，且登入帶入外部 redirect 參數，仍固定導向 `/home`。
+- UI Session 不能授權 API；原有 HTTP Basic API 仍成功。
+- 有效登出導向 `/login?logout`，重送舊 Session cookie 仍不能進首頁。
+- default profile 的 health 正常、不提供三個管理頁面，且拒絕 demo 身分的 Basic 驗證。
+
+`git diff --check` 通過。未進行真人瀏覽器視覺／跨瀏覽器測試；未量測 coverage 百分比或執行依賴 CVE 掃描。
+
+## 審查發現與能力限制
+
+### 1. 跨版本 API 知識與前置查證不足
+
+Qwen 嘗試使用本版本不支援的 `CsrfConfigurer.accessDeniedHandler` 與已移除的 `AntPathRequestMatcher`，之後才查實際 sources／API 並修改。最後能修通，但第一次產出不可直接採信。
+
+它在交付說明中也將登出成功導向歸因於 session fixation 保護，概念不正確：導向由 `logoutSuccessUrl` 決定，登出失效由 logout handler 處理；session fixation 保護主要作用於登入時。可運作的程式不代表其機制解釋同樣可靠。
+
+### 2. 為修錯而縮小 CSRF 防護，沒有充分理由
+
+位置：`ManagementWebSecurityConfiguration.java` 的 `UI_POSTS` 與 `requireCsrfProtectionMatcher`。
+
+自訂 matcher 只檢查 POST，取代 Spring Security 對其他非安全方法的預設 CSRF 檢查。主管額外送出已登入、無 CSRF token 的 `PUT /home`，得到 **405**，而非預設 CSRF 防護的 **403**。目前沒有 PUT handler，故本次 POST 登入／登出功能仍通過；但不能把此設定評為完整保留預設 CSRF 語意。
+
+### 3. fallback 安全規則擴張，超出清楚證明的必要範圍
+
+位置：`SecurityConfiguration.java` 的 `dispatcherTypeMatchers(ERROR)` 與新增 UI 路徑 `permitAll`。
+
+Qwen 為處理錯誤派送，除允許 ERROR dispatch 外，還在所有 profile 生效的 fallback chain 中公開多個 UI 路徑與 CSS。主管確認 demo `/home` 仍受高優先序 UI chain 保護，沒有觀察到該頁驗證繞過；default profile 的 CSS 現在也可匿名 HTTP 200。此變更的必要範圍未被它充分論證，不宜無審查採納為正式認證設計。
+
+### 4. 測試通過與測試意圖有落差
+
+- `loginAlwaysRedirectsToHomeEvenWhenSavedRequestDiffers` 實際先存的 saved request 就是 `/home`，沒有驗證「saved request 與目標不同」的情況。
+- `DefaultProfileIsolationIT` 只斷言管理頁面不是 HTTP 200；若變成 500 仍可能通過，也沒有測試 default profile 拒絕 demo 帳號。
+- Qwen 的自動測試未覆蓋錯誤 token 的登出 POST；只有缺 token。
+
+上述行為由主管額外 HTTP 驗收補查並確認目前實作能通過，但缺口仍存在於交付測試中。這表示模型可以撰寫有用的整合測試，卻不能自行簽核「已完整覆蓋所有驗收條件」。
+
+### 5. 工具規則與停損執行不可靠
+
+已有明確的專用檔案工具要求，仍使用 `Set-Content` 並引入 BOM。它回報多次非預期失敗後仍稱在停止條件內，主管不採信其停損自我判定。
+
+本次主管在單次子任務交付後，才從 session 稽核確認完整失敗紀錄。以提示詞提出失敗次數限制，沒有形成執行中的硬性中斷；這也是本次監督流程的限制。若要繼續評估，應將任務拆成短階段，在每階段返回後由主管決定是否繼續，並使用可強制執行的時間／步數預算。
+
+## 本次評判
+
+| 面向 | 判定 |
+|---|---|
+| 最終基本功能 | 通過：69 項專案測試與主管 29 項額外 HTTP 斷言通過 |
+| 按既有結構完成小型功能 | 可用：變更主要集中於 web，未改核心模組與原有身分來源 |
+| 除錯與回饋修正 | 部分可用：能修到運作，但多次試錯、耗時與工具成本偏高 |
+| 框架版本與安全設定可靠度 | 不足：先誤用 API，修復引入不必要的安全語意變更 |
+| 測試設計與自我驗收 | 部分可用：有真實 HTTP 測試，但有假覆蓋與過度宣稱 |
+| 工具指令與停損遵循 | 未通過 |
+| 無人監督交付認證功能 | 未通過 |
+
+**適用定位：有主管、明確規格、短任務、編譯測試閘門及獨立 code review 的實作者。**
+
+本次僅評估這個模型、agent 設定與技術棧上的單一小功能；沒有評估其真 LDAP 整合、正式認證安全設計、複雜授權或大型重構能力，也不將本結果泛化為所有 Qwen 模型的能力。
````

## 6. 全域 OpenCode 設定的完整重建內容

### 6.1 `agents/qwen-worker.md`

建立／合併到 `C:/Users/lance.huang/.config/opencode/agents/qwen-worker.md`。以下為目前完整內容；不同使用者請同步替換規則的絕對路徑。

````markdown
---
description: 依主 agent 已定義的測試案例，撰寫及執行簡單 unit test 與小範圍 integration test；設計、產品程式實作與最終驗收由主 agent 負責。
mode: subagent
model: delta/openai/Qwen/Qwen3.8-Flash-Next
steps: 20
permission:
  task: deny
---

你是 qwen-worker，負責主 agent 委派的有界測試程式工作。

遵循已自動載入的「qwen-worker 委派與監督規則」。若目前上下文未包含該規則，先用 Read 讀取：
`C:/Users/lance.huang/.config/opencode/rules/qwen-worker-delegation.md`。

- 一律使用繁體中文回覆。
- 遵守已載入的系統、使用者與專案指引，並先確認適用的 AGENTS.md。
- 功能設計、產品程式實作、測試案例與預期結果由主 agent 提供；你依案例撰寫／執行簡單 unit test 或小範圍 integration test。
- 先確認案例、預期結果、允許修改的測試檔與執行命令已提供；缺少時回報 BLOCKED，不自行補定需求。
- 僅修改白名單內的測試檔；除非使用者另有明確指示，不修改產品程式、建置依賴、設定或既有驗收標準。
- 保留與本次任務無關的既有修改，遇到不熟悉的變更先查證。
- 使用可用的專用檔案工具讀寫，不用 shell 或自行程式寫檔替代；不以放寬斷言、停用安全機制、刪除／跳過測試來取得綠燈。
- 累計兩次非預期編譯／測試失敗即回報 STOPPED；僅主 agent 事先標明的 TDD red 不計入。續派沿用原累計，不重置。
- 遇到產品缺陷、案例歧義、範圍外修改或步數不足，立即回報 BLOCKED／STOPPED，保留現有成果供主管檢查。
- 清楚區分實際執行、UP-TO-DATE／快取、未執行及預期 red，不將推測描述為實測結果。
- 不輸出或寫入密碼、API token、private key 等秘密；需要時依既有安全 runtime 機制取得。
- 除非使用者明確要求，不執行 commit、push 或建立 PR／MR。
- 不再委派其他子 agent。

以 DONE／BLOCKED／STOPPED 向主 agent 回報：變更檔案、案例 ID 對照、完整執行命令、實際成功／失敗／跳過數、累計非預期失敗與修正紀錄、尚未完成的事項。最終驗收由主 agent 決定。
````

### 6.2 `rules/qwen-worker-delegation.md`

建立／合併到 `C:/Users/lance.huang/.config/opencode/rules/qwen-worker-delegation.md`。完整內容如下：

````markdown
# qwen-worker 委派與監督規則

適用於任何準備呼叫、續派或驗收 `qwen-worker` 的主 agent。只有進入 Qwen 委派情境才啟用本流程，不要求每個開發任務都使用子 agent。

## 1. 分工

- **主 agent**：需求分析、功能與安全設計、產品程式實作、測試案例與預期結果擬定、依賴／建置設定決策、獨立審查與最終驗收。
- **qwen-worker**：依主 agent 已定義的案例，撰寫及執行簡單 unit test、小範圍 integration test，回報結果與阻礙。
- 除非使用者另外明確指示，不委派產品程式實作、架構決策、真 LDAP／認證授權設計、跨模組流程或正式環境操作。
- Qwen 可以指出案例缺口或疑似產品缺陷，但不得自行更改需求、預期結果或產品程式以取得綠燈。

## 2. 可委派範圍

| 類型 | 適合的範圍 |
|---|---|
| Unit test | 單一 class／方法群的輸入、輸出、例外、邊界；依已提供的 mock／stub 邊界驗證 |
| Integration test | 單一 endpoint／adapter／交易或一小段既有組裝；使用既有測試框架、本機可重現且隔離的依賴 |

- 涉及真實外部服務、複雜多服務狀態、認證安全規則、效能／併發／故障恢復者，由主 agent 保留設計與判讀，僅可拆出明確的局部測試程式工作。
- 安全流程的測試案例、filter chain 選擇、Session／CSRF／權限的預期行為，一律先由主 agent 決定。

## 3. 呼叫前，主 agent 必須備妥

1. 專案根目錄、相關產品程式與既有測試的明確路徑；不要要求 Qwen 重新探索整個專案。
2. 測試案例表：案例 ID、前置條件、輸入、預期值／狀態碼／redirect／副作用，以及不得發生的行為。指定預期 green 或已知的 TDD red 與理由。
3. 允許修改的測試檔白名單；預設一個測試類別與至多兩個測試 helper／fixture，不改 `src/main`、建置檔、設定、案例規格或既有測試的驗收標準。
4. 實際版本、可用工具、執行命令、工作目錄、隔離資料來源、允許的 mock 邊界與已知基線。不得傳遞秘密值；需要時只說明安全 runtime 來源。
5. 有界工作量：一次一個目標、通常 3–5 個相關案例。Unit test 目標 5 分鐘、integration test 目標 10 分鐘；時間是回報目標，不得假稱為硬性 timeout。

## 4. 分段與停損

- 使用 `subagent_type: qwen-worker`。若當前 session 無此 agent，回報需重新載入；不可用其他模型／agent 冒稱 Qwen。
- 不一次委派「全部功能＋全部測試＋持續修到通過」。每一小段交付後，主 agent 審查再決定下一段；首次成果不合格時，最多追加一次明確限定的修正委派。
- `qwen-worker` 設有 `steps: 20`：限制 agentic iterations，達上限後轉為文字回覆；不等於 20 次工具呼叫或固定分鐘數。不能以自動續派、換 session 或提高上限繞過。
- 每個工作單元**累計兩次非預期編譯／測試失敗即停止**，包含修正後又遇到的不同錯誤；只有主 agent 事先標明的 TDD red 不計入。續派時帶入原累計，不能歸零。
- 發現產品缺陷、案例歧義、需要白名單以外的檔案／依賴、版本 API 不明、預算不足時，回報阻礙而非擴大範圍。API 不明應先查一次既有依賴／文件，仍無法確認即交回主 agent。
- 發現捏造結果、移除測試、放寬斷言、停用安全機制或越界改檔，主 agent 停止該次 Qwen 委派，審查現有變更後回報；不得繼續讓它自行補救。

## 5. Qwen 的執行與回報要求

- 遵守專案及全域指引；用可用的專用檔案工具編輯，不能用 shell／自行程式寫檔繞過。必要工具不可用時回報。
- 保留既有修改；不得為通過測試而修改產品程式、預期結果、刪除測試或增加 skip。禁止巢狀委派。
- 測試必須驗證實際宣稱的行為：redirect 要比對目標；隔離要驗證拒絕；不同 saved request 必須真的不同；不能以「不是 200」代替明確狀態碼。
- 區分「執行並通過」「UP-TO-DATE／快取」「未執行」「預期 red」。沒有工具證據不得宣稱成功或給出 coverage 百分比。
- 以 `DONE`／`BLOCKED`／`STOPPED` 回報，附：變更檔案、案例 ID 對照、實際命令與執行狀態、成功／失敗／跳過數、累計非預期失敗與修正紀錄、未完成項目。不能自行宣布主管驗收通過。

## 6. 主 agent 的獨立驗收

- 查看實際 diff、越界變更與測試斷言；確認 mock 沒有替代待驗證的真實邊界。
- 確認每個案例實際被 discovery 並執行，沒有被篩選／快取／skip 掩蓋；必要時用最小範圍的強制重跑或獨立 HTTP 檢查補證。
- 將「測試程式通過」「產品符合需求」「測試覆蓋完整」分別判讀。Qwen 的摘要只是待查證的報告。
- 通過適當檢查後即收尾；只有新增變更、失敗或未解疑慮才擴大／重複測試。主 agent 對最後交付與能力評估負責。

## 7. 委派模板

```text
任務：依指定案例實作／執行測試（不設計產品功能）
規則：遵循已載入的 qwen-worker 委派規則；未載入時先讀
      C:/Users/lance.huang/.config/opencode/rules/qwen-worker-delegation.md
專案根目錄：...
產品程式／既有測試參考：...
允許修改的測試檔：...
案例：ID｜前置條件｜輸入｜明確預期與禁止副作用
Mock／資料／外部 IO 邊界：...
版本／工具／完整執行命令：...
基線及預期 green／已知 red：...
預算：20 iterations；累計兩次非預期失敗即停；原累計 N 次
交付：DONE／BLOCKED／STOPPED＋案例對照、diff、實測證據、失敗紀錄
```
````

### 6.3 合併 `opencode.jsonc` 的 instructions

在既有 `C:/Users/lance.huang/.config/opencode/opencode.jsonc` 保留 `$schema`、provider、MCP、模型、plugin 等其他欄位，只將此規則路徑加入 `instructions` 陣列，不重複新增，不覆蓋既有陣列內容：

```json
{
  "instructions": [
    "C:/Users/lance.huang/.config/opencode/rules/qwen-worker-delegation.md"
  ]
}
```

上面是**要合併的欄位片段，不是整份設定檔**。目前實際設定的全域 `model` 仍為 `delta/openai/Qwen/Qwen3.8-Flash-Next`，但 agent 有獨立 model，因此不依賴主 session 當時選用的模型。

接收端需已存在可用的 `delta` provider，以及 `openai/Qwen/Qwen3.8-Flash-Next` 模型條目。若 provider 已存在但缺少模型顯示條目，可在該 provider 的 `models` 內合併：

```json
{
  "openai/Qwen/Qwen3.8-Flash-Next": {
    "name": "Qwen3.8 Flash Next"
  }
}
```

本文件不包含 gateway 連線秘密或整份個人設定。缺少可用 provider 時，先取得既有安全 runtime 設定，不要編造 URL／key。未建立全域 `AGENTS.md`，避免遮蓋現有 `~/.claude/CLAUDE.md` fallback 指引。

修改後**完全退出並重新啟動 OpenCode**；既有 session 不會熱載入。可由主 agent 驗證 resolved config 的 `instructions` 與 agent 的 `model`／`steps`／task deny，但不要把整份可能含秘密的 resolved config 印出。

本機 npm 的 `opencode` shim 曾發生執行檔平台相容問題；已驗證下列正式原生 binary 可用，無需修改安裝：

```powershell
& 'C:\Users\lance.huang\AppData\Roaming\npm\node_modules\opencode-ai\node_modules\opencode-windows-x64\bin\opencode.exe' debug agent qwen-worker
```

## 7. 套用後的驗證與試用

下列命令從專案根目錄執行；本機禁止使用 `gradlew`／`gradlew.bat`。如果只套用本文件而產品程式未再改變，依需求選擇一次完整驗證，不必無理由重複跑多輪。

```powershell
$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' build --rerun-tasks --console=plain
```

預期快照結果為 69 項測試、0 失敗／錯誤／跳過，以及 `web/build/libs/ai-devops-platform-0.0.1-SNAPSHOT.jar` 產出。若接收端已有其他功能，測試總數可能不同，應核對這次新增兩個 integration test 類別是否真的被執行。

```powershell
$env:JAVA_HOME = 'D:\users\lance.huang\.jdks\temurin-21.0.4'
& 'D:\java\gradle\gradle-9.8.1\bin\gradle.bat' :web:bootRun --args='--spring.profiles.active=demo'
```

開啟 `http://127.0.0.1:8080/login`，使用接收端既有 demo 合成測試帳號；驗證成功到 `/home`、錯誤到 `/login-failure`、登出後不能再讀取首頁。測試程式內的 demo 字串屬既有本機合成 fixture，不是正式 LDAP credential；不要改用正式帳密寫入 source。

未啟用 demo 時不應提供管理頁面或可用的 demo 身分；匿名 health 應正常。進一步修正第 4 節問題時，先由主 agent 擬定明確案例與預期，再委派 Qwen 撰寫白名單內的測試，產品修正與最後驗收仍由主 agent 負責。

## 8. 本交接包的一致性驗證

文件產生後已完成以下檢查：

1. `git apply --stat -- "qwen_changes.md"`：解析出 **13 個檔案、702 行新增、2 行刪除**，涵蓋 3 個修改檔與 10 個新增檔；本文件本身另為新增檔。
2. `git apply --reverse --check -- "qwen_changes.md"`：成功，驗證 patch 的交付狀態與目前 working tree 相符；沒有實際回退任何檔案。
3. 先以 `git diff --cached --quiet HEAD` 確認 index 等於上述基準 HEAD，再執行 `git apply --cached --check -- "qwen_changes.md"`：成功，驗證整份 patch 能以前向套用到基準；`--check` 沒有修改 index 或 stage 檔案。
4. 核對全域 agent／規則的完整內容與實際檔案，以及 `instructions` 合併欄位。沒有複製個人 provider／MCP 的秘密設定。
5. `git diff --check` 成功；此次整理的產品程式未再修改，歷史建置與 HTTP 成績依第 3 節記錄，不宣稱重新執行。

因此，接手 agent 可僅依本文件重建這批 source 與全域 agent 規則，並清楚分辨已通過的驗證、已知未修問題及後續責任分工。

