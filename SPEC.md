# ConcurrencyGuard 規格書

> **版本：** 0.3（M2 已實作）  
> **文件狀態：** M2 已落地；M3+ 仍為規劃  
> **最後更新：** 2026-07-17  
> **語言：** 繁體中文（台灣）  
> **雙語 README：** [README.md](./README.md)（繁中 + English）  
> **授權：** [MIT License](./LICENSE)

---

## 0. English summary (one page)

**ConcurrencyGuard** is an open-source Java 21 CLI that audits HTTP APIs for **concurrency correctness** (race conditions), not load performance.

- **M2 (done):** barrier-aligned concurrent client (virtual threads), three invariants (`max-successes`, `non-negative`, `conservation`), generic `audit` CLI, text+JSON reports, buggy mock `serve-target`, auth gate for non-localhost.
- **M3 (planned):** HTTP/2 single-packet attack + scenario DSL — **not implemented yet**.
- **Users:** bug bounty / security researchers, backend & QA, SRE (authorized testing only).
- **Monetization:** open-source engine → reputation → bounties/consulting (slow money); tool is a business card, not a cash register.
- **Repo:** https://github.com/benson-code/concurrency-guard  

For full bilingual usage docs, see **README.md**. The sections below remain the authoritative Traditional Chinese design specification.

---

## 0. 一頁摘要（TL;DR）

ConcurrencyGuard 是一個**開源的 HTTP API 併發正確性稽核 CLI 工具**。它對目標端點發動對齊的併發請求，逼出 race condition，並以「預期 vs 實際」的不變式（invariant）比對，自動判定是否發生**超賣、負餘額、帳目不平衡**等競態漏洞，最後產出人類可讀與機器可讀（JSON）的違規報告。

- **M2 能力：** barrier 齊發（JDK 21 虛擬執行緒）+ 三條不變式 + 通用 CLI。  
  **M3 規劃：** HTTP/2 單封包攻擊（尚未實作，勿對外宣稱已具備）。
- **給誰用：** 資安研究員 / 賞金獵人、後端與 QA 工程師、平台可靠性團隊。
- **解決什麼：** 通用壓測工具（k6、JMeter）只測「快不快」，不測「併發下對不對」。ConcurrencyGuard 專測後者。
- **怎麼變現（見 §11）：** 開源引擎建立名聲 → 自用於賞金獵取 → 顧問/滲透接案（Jepsen 模式）→ 選配雲端付費層。

---

## 1. 願景與定位

### 1.1 問題陳述

競態條件（race condition）是最難用一般測試抓到的一類 bug：它只在特定的執行緒交錯時序下出現，單執行緒測試、CI 綠燈、code review 都可能完全看不到它。在金流、庫存、額度、優惠券等場景，它會直接造成金錢損失：

- **超賣（oversell）：** 最後一件庫存被多個並行請求同時買走。
- **雙重扣款 / 重複發放：** 同一筆退款、儲值、優惠被領取多次。
- **負餘額 / 額度突破：** 併發提款讓餘額變負、或突破額度上限。
- **冪等性失效：** 帶相同 `order_id` 的重試被當成兩筆處理。

這類問題同時是**工程正確性缺陷**與**資安漏洞**（TOCTOU）。在賞金市場，單一支付 race condition 曾價值數千美元。

### 1.2 市場定位

| 象限 | 代表 | ConcurrencyGuard 的關係 |
|---|---|---|
| 企業級確定性模擬（DST） | Antithesis、Jepsen | 不競爭；我們是輕量、可自助、聚焦 HTTP 邊界的工具 |
| 通用負載/壓力測試 | k6、JMeter、Gatling、Locust | 不競爭；它們測效能，我們測正確性 |
| 資安 race condition 工具 | Burp Turbo Intruder、race-the-web | **主戰場**；提供現代、開源、CI 友善、含單封包攻擊的替代方案 |

**一句話定位（M2）：** 「開源 HTTP race condition 稽核 CLI（barrier 齊發 + 不變式斷言）」；單封包攻擊為 M3 roadmap。  
（對外溝通勿寫成「已具備 Turbo Intruder 級單封包」——那是目標，不是現況。）

### 1.3 設計原則

1. **正確性優先於效能：** 我們不追求最高 RPS，而追求最可靠地重現競態並正確判定違規。
2. **零外部服務依賴：** 單一 CLI + jar，可離線跑，符合成本敏感與隱私需求。
3. **可重現：** 同樣的設定 + seed 應盡量產生一致的結論；報告需含足以複現的參數。
4. **只打你有權測的系統：** 工具內建授權確認機制（見 §10）。

---

## 2. 目標使用者與使用情境

### 2.1 使用者輪廓

- **U1 — 賞金獵人 / 資安研究員：** 想快速對授權範圍內的 API 掃 race condition，要能自訂請求、看清楚哪一發成功、產出可貼進報告的證據。
- **U2 — 後端 / QA 工程師：** 想在 CI 裡對自家 staging API 加一道「併發正確性」防線，抓回歸。
- **U3 — SRE / 平台團隊：** 想在上線前驗證關鍵金流端點在高併發下守得住不變式。

### 2.2 核心使用情境（User Stories）

- **US-1（超賣偵測）：** 身為 U2，我要對 `/withdraw` 同時發 N 個請求，工具告訴我「預期最多 3 筆成功，實際 7 筆成功 → 超賣 +4」。
- **US-2（單封包攻擊）：** 身為 U1，我要用 HTTP/2 single-packet attack 把 20 個請求壓進同一時刻，最大化命中極窄競態窗口。
- **US-3（冪等性測試）：** 身為 U2，我要對帶相同 `Idempotency-Key` 的重試併發，驗證只有一筆生效。
- **US-4（CI 閘門）：** 身為 U3，我要工具在偵測到違規時回傳非零 exit code，讓 pipeline 失敗。
- **US-5（證據產出）：** 身為 U1，我要一份 JSON + 文字報告，含每一發的狀態碼、回應、時間戳，可直接附進賞金報告。

---

## 3. 範圍

### 3.1 M2 範圍內（本規格書涵蓋）

- 併發 HTTP 客戶端（HTTP/1.1，可設定並發數、請求範本）。
- 不變式斷言引擎（超賣、負餘額、成功次數上限、自訂比對）。
- 對既有 `BuggyMockWithdrawServer` 的端到端測試（證明能抓到已知 bug）。
- CLI：可指向**任意** HTTP 端點（不再寫死自家 mock）。
- 文字 + JSON 雙格式報告（沿用並擴充 `ViolationReport`）。
- 基本授權確認機制。

### 3.2 M3+ 範圍（規劃，暫不實作）

- HTTP/2 單封包攻擊（single-packet attack）。
- 「探測 → 攻擊 → 驗證」三階段自動流程（先讀 baseline，攻擊，再讀 final）。
- 情境設定檔（YAML/JSON DSL 描述端點、範本、不變式）。
- GitHub Action 封裝。
- 選配雲端付費層（見 §11）。

### 3.3 非目標（Non-Goals）

- **不做**通用負載/效能基準測試（交給 k6）。
- **不做** UI 層測試或瀏覽器自動化。
- **不做**未授權掃描、繞過防護、DoS。工具刻意不最佳化為攻擊武器（見 §10）。
- **不做**分散式多機協同壓測（M2/M3 單機即可）。

---

## 4. 系統架構

### 4.1 元件圖

```
┌──────────────────────────────────────────────────────────┐
│                        CLI (main)                          │
│  解析參數 / 讀情境檔 / 授權確認 / 組裝執行計畫              │
└───────────────┬──────────────────────────┬────────────────┘
                │                          │
       ┌────────▼────────┐        ┌────────▼─────────┐
       │  RequestPlan     │        │  BaselineProbe   │
       │  請求範本 + 並發  │        │  攻擊前讀取狀態   │
       └────────┬────────┘        └──────────────────┘
                │
       ┌────────▼──────────────────────┐
       │      ConcurrentClient          │
       │  依模式發射併發請求：           │
       │   - barrier 同步齊發 (M2)       │
       │   - single-packet attack (M3)   │
       │  收集每發的 Outcome             │
       └────────┬──────────────────────┘
                │  List<Outcome>
       ┌────────▼──────────────────────┐
       │      InvariantEngine           │
       │  套用一組 Invariant 規則        │
       │  比對 expected vs actual        │
       └────────┬──────────────────────┘
                │  ViolationReport
       ┌────────▼──────────────────────┐
       │      Reporter                  │
       │  文字 (人讀) + JSON (機器讀)     │
       │  設定 exit code                 │
       └────────────────────────────────┘
```

### 4.2 資料流

1. CLI 解析參數，建立 `RequestPlan`（目標 URL、方法、標頭、body 範本、並發數 N）。
2. `BaselineProbe`（選配）讀取攻擊前狀態，例如 `GET /balance`。
3. `ConcurrentClient` 依選定模式齊發 N 個請求，收集 `List<Outcome>`。
4. `InvariantEngine` 讀取 baseline、final 狀態與所有 Outcome，套用不變式規則，產生 `ViolationReport`。
5. `Reporter` 輸出報告並設定 exit code（有違規 → 非零）。

---

## 5. 核心資料模型

### 5.1 `Outcome`（單發請求結果）

```
record Outcome(
    int    index,          // 第幾發（0..N-1）
    int    statusCode,     // HTTP 狀態碼；-1 表連線失敗
    String body,           // 回應內文（可截斷）
    long   sentAtNanos,    // 送出時間（System.nanoTime）
    long   recvAtNanos,    // 收到時間
    String error           // 例外訊息，成功則為 null
)
```

- **成功判定**由 Invariant 定義，而非硬編碼 2xx（有些 API 用 200 包 error）。預設規則：2xx 且 body 不含失敗標記。

### 5.2 `RequestPlan`

```
record RequestPlan(
    String method,                 // POST / GET / ...
    URI    target,                 // http://host:port/path
    Map<String,String> headers,    // 自訂標頭
    String bodyTemplate,           // 例如 {"amount":30}
    int    concurrency,            // N，齊發數量
    FireMode mode                  // BARRIER | SINGLE_PACKET
)
```

### 5.3 `Invariant`（不變式規則介面）

```
interface Invariant {
    String name();
    // 給定 baseline、final 狀態與所有 outcome，回傳違規描述（無違規則 empty）
    Optional<Violation> check(ProbeState baseline,
                              ProbeState finalState,
                              List<Outcome> outcomes);
}
```

內建實作：

| 規則 | M2 | 說明 |
|---|---|---|
| `MaxSuccessesInvariant` | ✅ | 成功數 ≤ ⌊initialBalance / amount⌋，否則超賣 |
| `NonNegativeStateInvariant` | ✅ | final 狀態的數值欄位（如 balance）不得 < 0 |
| `ConservationInvariant` | ✅ | initial − final == 成功數 × amount，否則帳不平 |
| `IdempotencyInvariant` | ⬜ M3+ | 帶相同冪等鍵的併發請求，最多一筆生效 |
| `UniqueResponseInvariant` | ⬜ M3+ | （race-the-web 風格）併發回應應唯一 |

**成功判定（M2）：** HTTP 2xx 且傳輸完成。自訂成功條件（body 標記）列 M2.1。

### 5.4 報告類別策略

- **`ViolationReport`（M1，保留）：** 超賣／負餘額的簡潔 expected-vs-actual 視圖，API 不變。
- **`AuditReport`（M2，新增）：** 多規則 `violations`、完整 `outcomes`、`formatText()` / `toJson()`。  
  透過 `AuditReport.toViolationReport()` 轉成舊視圖，供相容測試使用。

---

## 6. 併發客戶端設計

### 6.1 M2：Barrier 齊發模式

目標：讓 N 個請求盡量在同一時刻離開客戶端，最大化落入伺服器競態窗口的機率。

- 使用 `java.net.http.HttpClient`（JDK 內建，無外部依賴）。
- N 條虛擬執行緒（JDK 21 Virtual Threads）各自準備好請求。
- 用 `CyclicBarrier(N)` 或 `CountDownLatch` 讓所有執行緒在送出前對齊，`await()` 之後同時 `send()`。
- 每發記錄 `sentAtNanos` / `recvAtNanos`。

**限制（誠實揭露）：** Barrier 只對齊「客戶端送出時刻」，網路抖動與 TCP 排程仍會造成毫秒級散射。對付伺服器端故意 `sleep(5)` 放大的窗口綽綽有餘，但對極窄窗口（微秒級）命中率有限——這是 M3 單封包攻擊要解決的問題。

### 6.2 M3：HTTP/2 單封包攻擊（規劃）

- 技術原理：把多個 HTTP/2 請求的最後一個位元組緩存，湊齊後放進**同一個 TCP 封包**送出，消除網路抖動，讓伺服器幾乎同時開始處理，逼出微秒級競態窗口（Burp Turbo Intruder 的核心技術）。
- 實作評估：JDK `HttpClient` 不易精細控制 TCP 封包邊界，M3 可能需要較低階的 socket 控制或引入 Netty。此為 M3 決策點，本規格書不定案。

---

## 7. CLI 設計

### 7.1 呼叫方式

```bash
java -jar concurrency-guard.jar audit \
  --target      http://localhost:18080/withdraw \
  --method      POST \
  --body        '{"amount":30}' \
  --concurrency 10 \
  --baseline    http://localhost:18080/balance \
  --state-field balance \
  --initial     100 \
  --amount      30 \
  --invariant   max-successes,non-negative,conservation \
  --report      json \
  --out         report.json \
  --i-am-authorized
```

### 7.2 參數表

| 參數 | 必填 | 說明 |
|---|---|---|
| `--target` | ✓ | 受測端點 URL |
| `--method` | | HTTP 方法，預設 POST |
| `--body` | | 請求內文範本 |
| `--header` | | 可重複，`K: V` 格式 |
| `--concurrency` | ✓ | 齊發請求數 N |
| `--baseline` | | 攻擊前讀取狀態的 GET 端點 |
| `--state-field` | | baseline/final JSON 中代表狀態的欄位名（如 `balance`） |
| `--initial` | | 已知初始值（供 max-successes 計算） |
| `--amount` | | 每筆扣款額（供守恆/超賣計算） |
| `--invariant` | | 逗號分隔的規則清單 |
| `--report` | | `text`（預設）\| `json` \| `both` |
| `--out` | | 報告輸出檔（預設 stdout） |
| `--fire-mode` | | `barrier`（預設）\| `single-packet`（M3） |
| `--i-am-authorized` | ✓ | 授權確認旗標（見 §10） |

### 7.3 Exit Code

| Code | 意義 |
|---|---|
| 0 | 無違規 |
| 1 | 偵測到違規（CI 應視為失敗） |
| 2 | 使用錯誤（參數缺失、未帶授權旗標） |
| 3 | 執行錯誤（無法連線、逾時） |

---

## 8. 報告格式

### 8.1 文字（人讀，沿用現有風格）

```
=== ConcurrencyGuard Violation Report ===
Target          : POST http://localhost:18080/withdraw
Initial balance : 100
Withdraw amount : 30
Concurrent reqs : 10
Fire mode       : barrier
----------------------------------------
Expected successes : 3
Actual   successes : 7   << OVERSELL (+4)
Final balance      : -110  << NEGATIVE
----------------------------------------
Invariants:
  [FAIL] max-successes    : 7 > 3
  [FAIL] non-negative     : final balance -110 < 0
  [FAIL] conservation     : 100 - (-50) = 150 != 4*30 = 120
----------------------------------------
Verdict : VIOLATION
```

> 註：當超賣使 `successes * amount == initial - final` 時，conservation 可能單獨通過，
> 仍會被 max-successes / non-negative 抓到（這是預期行為，見單元測試）。

### 8.2 JSON（機器讀，供 CI 與賞金報告）

```json
{
  "target": "http://localhost:18080/withdraw",
  "method": "POST",
  "fireMode": "barrier",
  "initialBalance": 100,
  "withdrawAmount": 30,
  "concurrency": 10,
  "expectedSuccesses": 3,
  "actualSuccesses": 7,
  "finalBalance": -110,
  "verdict": "VIOLATION",
  "violations": [
    {"invariant": "max-successes", "detail": "7 > 3"},
    {"invariant": "non-negative", "detail": "final balance -110 < 0"}
  ],
  "outcomes": [
    {"index": 0, "status": 200, "latencyMs": 6, "success": true},
    {"index": 1, "status": 200, "latencyMs": 6, "success": true}
  ]
}
```

---

## 9. 里程碑與交付

| 里程碑 | 內容 | 產出 | 狀態 |
|---|---|---|---|
| **M1** | Mock 靶伺服器 + 報告骨架 | `BuggyMockWithdrawServer`、`ViolationReport` | ✅ 已完成 |
| **M2** | 併發客戶端 + 不變式引擎 + 通用 CLI + 端到端測試 | `ConcurrentClient`、`InvariantEngine`、`AuditCli`、`AuditReport`、JUnit | ✅ 已完成（2026-07-17） |
| **M3** | 單封包攻擊 + 三階段流程 + 情境設定檔 | HTTP/2 fire mode、YAML DSL | ⬜ 規劃 |
| **M4** | GitHub Action 封裝 + 文件網站 + 範例靶場 | Action、docs、多種 buggy 靶 | ⬜ 規劃 |
| **M5** | 選配雲端付費層 / 顧問接案素材 | 見 §11 | ⬜ 規劃 |

### M2 驗收準則（Definition of Done）

1. `ConcurrentClient` 能對任意 HTTP 端點齊發 N 個請求並收集 `Outcome`。 ✅
2. `InvariantEngine` 至少實作 `max-successes`、`non-negative`、`conservation` 三規則。 ✅
3. 端到端測試：啟動 `BuggyMockWithdrawServer`，齊發並**穩定重現超賣**，`ViolationReport.isViolation()` 為 true。 ✅
4. CLI 能透過參數指向外部端點並輸出 text/JSON 報告，exit code 正確。 ✅
5. `mvn test` 全綠；`mvn package` 產出可執行 jar。 ✅
6. README 更新使用範例；本 SPEC 標記 M2 完成。 ✅

### M2 實作偏差說明

- 報告採 **`AuditReport` 新增 + `ViolationReport` 保留**，而非直接膨脹 M1 類別。
- CLI 子命令：`audit` / `serve-target`（mock 降為子命令）。
- 支援可重複 `--header` 與 body `{{index}}`／`{{n}}` 替換（賞金／授權場景最小必要）。
- 預設並發上限 100；超過需 `--allow-high-concurrency`。
- JSON 採手寫序列化，零 runtime 依賴。

---

## 10. 安全與倫理（Responsible Use）

ConcurrencyGuard 是**防禦性與授權測試**工具，不是攻擊武器。

- **授權閘門：** 未帶 `--i-am-authorized` 旗標時拒絕對非 localhost 目標執行，並印出提醒。
- **範圍限制：** 預設並發上限（如 100），避免誤用為 DoS；超過需明確旗標。
- **不含規避技術：** 不實作繞過 WAF、隱藏來源、放大攻擊等功能。
- **文件聲明：** README 明列「僅可用於你擁有或已獲書面授權測試的系統」。
- **賞金使用：** 使用者須遵守各賞金計畫的範圍與規則；工具僅產出證據，不代表授權。

---

## 11. 變現對應（Monetization Mapping）

本專案的商業邏輯是**「開源引擎養名聲，名聲換現金」**，非直接賣工具。

| 階段 | 動作 | 收入來源 |
|---|---|---|
| 引擎期（M2–M4） | 開源、寫 writeup、經營 GitHub | 無直接收入；累積 star 與信任 |
| 自用期 | 用工具在授權賞金計畫獵取 race condition | 賞金 $500–25,000/筆（浮動） |
| 名聲期 | 以工具與 writeup 為名片接案 | 顧問 / 滲透測試專案費（Jepsen 模式，單人最穩） |
| 擴張期（M5，選配） | 雲端託管掃描 / 團隊版 | SaaS 訂閱（經常性收入） |

**風險揭露：** 這是慢錢，收入落後於投入數月。工具是引擎與名片，不是收銀機。最穩的單人變現是「名聲 → 顧問接案」，賞金為浮動補充，SaaS 為後期選項。

---

## 12. 技術選型

- **語言 / 執行環境：** Java 21（虛擬執行緒、record、無外部服務依賴），符合現有 pom。
- **HTTP 客戶端：** JDK `java.net.http.HttpClient`（M2）；M3 視需要評估 Netty。
- **JSON：** M2 以最小手寫序列化避免依賴；若複雜度上升再引入 Jackson。
- **測試：** JUnit 5（已配置）。
- **建置：** Maven（已配置），jar main class 改為 `AuditCli`（mock server 降為子命令或獨立 class）。
- **版控：** 已 `git init`（M2 開工時建立）。

---

## 13. 待決議事項（Open Questions）

1. **JSON 依賴：** M2 已手寫；M3 若 DSL／複雜報告再評估 Jackson。 ✅ 已決（M2）
2. **單封包攻擊實作路徑：** JDK HttpClient 能否勝任，或需 Netty？（M3 決策）
3. **情境 DSL 格式：** YAML vs JSON vs 純 CLI 參數？（M3 決策）
4. **mock server 定位：** `serve-target` 子命令。 ✅ 已決
5. **雲端層技術棧與成本：** M5 才評估，須符合成本敏感原則。
6. **成功判定可設定：** body 標記／自訂狀態碼（建議 M2.1）。
7. **開源授權：** ✅ 已選 **MIT**（見 [LICENSE](./LICENSE)）；repo 公開於 https://github.com/benson-code/concurrency-guard

---

*本規格書為活文件。M2 介面以 `src/main/java` 為準；重大偏差見 §9。使用說明以雙語 [README.md](./README.md) 為準。*
