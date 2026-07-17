# ConcurrencyGuard

[![Java 21](https://img.shields.io/badge/Java-21-orange)](#requirements--系統需求)
[![Maven](https://img.shields.io/badge/build-Maven-blue)](#build--建置)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)
[![Status: M2](https://img.shields.io/badge/status-M2%20complete-success)](#roadmap--路線圖)

**Languages / 語言：** [繁體中文](#繁體中文) · [English](#english)

---

開源 **HTTP API race-condition 稽核 CLI**  
Open-source **HTTP API race-condition audit CLI**

對目標端點齊發併發請求，以不變式（invariant）比對「預期 vs 實際」，偵測超賣、負餘額、帳目不平衡等競態問題。

Fires aligned concurrent requests at an HTTP endpoint and checks *expected vs actual* invariants to detect oversell, negative balance, broken conservation, and similar race bugs.

| | 繁中 | English |
|---|---|---|
| **一句話 / Tagline (M2)** | barrier 齊發 + 內建不變式斷言的授權測試工具 | Authorized testing tool: barrier-aligned fire + built-in invariant assertions |
| **非目標 / Not (yet)** | HTTP/2 單封包攻擊（M3 roadmap） | HTTP/2 single-packet attack (M3 roadmap) |
| **規格書 / Spec** | [SPEC.md](./SPEC.md)（繁體中文） | [SPEC.md](./SPEC.md) (Traditional Chinese) |

---

# 繁體中文

## 目錄

- [這是什麼](#這是什麼)
- [解決什麼問題](#解決什麼問題)
- [功能特色（M2）](#功能特色m2)
- [系統需求](#requirements--系統需求)
- [建置](#build--建置)
- [快速開始](#快速開始-繁中)
- [CLI 完整說明](#cli-完整說明-繁中)
- [不變式引擎](#不變式引擎-繁中)
- [報告格式](#報告格式-繁中)
- [架構與目錄](#架構與目錄-繁中)
- [開發與測試](#開發與測試-繁中)
- [安全與倫理](#安全與倫理-繁中)
- [路線圖](#roadmap--路線圖)
- [授權](#license--授權)

## 這是什麼

ConcurrencyGuard 是一個**零 runtime 依賴**的 Java 21 CLI 工具，專門稽核 HTTP API 在**併發**下是否守住正確性不變式。

它**不是**壓力測試工具（k6 / JMeter 測的是 RPS 與延遲）。它測的是：

> 「在同一時刻附近打 N 次請求後，系統的帳／庫存／額度還對不對？」

典型場景：提款、支付、庫存扣減、優惠券領取、冪等重試等 **TOCTOU（check-then-act）** 競態。

## 解決什麼問題

| 問題 | 說明 |
|------|------|
| **超賣 (oversell)** | 餘額只夠 3 次成功，卻有 7 次回 200 |
| **負餘額** | 併發扣款後 `balance < 0` |
| **帳目不平衡** | `initial − final ≠ 成功次數 × 單筆金額` |
| **一般測試抓不到** | 單執行緒、CI 綠燈、code review 都可能完全看不到 |

市面通用壓測工具不負責「併發正確性」；企業級確定性模擬（如 DST）門檻高。ConcurrencyGuard 走**輕量、可自助、可進 CI** 的路線。

## 功能特色（M2）

- **通用 CLI**：`--target` 指向任意 HTTP 端點（不再寫死自家 mock）
- **Barrier 齊發**：JDK 21 虛擬執行緒 + `CyclicBarrier`，盡量對齊送出時刻
- **三條內建不變式**：`max-successes` / `non-negative` / `conservation`
- **Baseline 探測**：攻擊前後 `GET` 狀態（如 `/balance`）
- **自訂標頭**：可重複 `--header`（`Authorization`、`Cookie` 等）
- **Body 範本**：`{{index}}` / `{{n}}` 替換
- **雙格式報告**：人類可讀 text + 機器可讀 JSON
- **CI 友善 exit code**：違規回傳 `1`
- **授權閘門**：非 localhost 需 `--i-am-authorized`
- **內建 buggy mock**：`serve-target` 可本地重現超賣
- **零 runtime 依賴**：僅 JDK + 測試用 JUnit

---

# English

## Table of contents

- [What it is](#what-it-is)
- [Problem it solves](#problem-it-solves)
- [Features (M2)](#features-m2)
- [Requirements](#requirements--系統需求)
- [Build](#build--建置)
- [Quick start](#quick-start-english)
- [CLI reference](#cli-reference-english)
- [Invariant engine](#invariant-engine-english)
- [Report formats](#report-formats-english)
- [Architecture & layout](#architecture--layout-english)
- [Development & tests](#development--tests-english)
- [Responsible use](#responsible-use-english)
- [Roadmap](#roadmap--路線圖)
- [License](#license--授權)

## What it is

ConcurrencyGuard is a **zero runtime-dependency** Java 21 CLI that audits whether an HTTP API preserves **correctness invariants under concurrency**.

It is **not** a load / performance tester (k6, JMeter measure RPS and latency). It answers:

> “After ~N requests fire near the same moment, is the account / stock / quota still consistent?”

Typical targets: withdrawals, payments, inventory decrements, coupon claims, idempotent retries — classic **TOCTOU (check-then-act)** races.

## Problem it solves

| Issue | Meaning |
|------|---------|
| **Oversell** | Balance allows 3 successes, but 7 requests return 200 |
| **Negative balance** | Concurrent debits leave `balance < 0` |
| **Broken conservation** | `initial − final ≠ successes × amount` |
| **Hard to catch** | Single-thread tests, green CI, and review can all miss it |

Generic load tools do not assert concurrency correctness; enterprise deterministic simulation is heavy. ConcurrencyGuard aims for **lightweight, self-serve, CI-friendly** auditing.

## Features (M2)

- **Generic CLI**: point `--target` at any HTTP endpoint
- **Barrier fire**: JDK 21 virtual threads + `CyclicBarrier`
- **Three built-in invariants**: `max-successes`, `non-negative`, `conservation`
- **Baseline probe**: `GET` state before/after the attack (e.g. `/balance`)
- **Custom headers**: repeatable `--header` (`Authorization`, `Cookie`, …)
- **Body templates**: `{{index}}` / `{{n}}`
- **Dual reports**: human text + machine JSON
- **CI exit codes**: violation → `1`
- **Auth gate**: non-localhost requires `--i-am-authorized`
- **Buggy mock**: `serve-target` reproduces oversell locally
- **Zero runtime deps**: JDK only (+ JUnit for tests)

---

## Requirements / 系統需求

| Item / 項目 | Version / 版本 |
|-------------|----------------|
| JDK | **21+** (virtual threads) |
| Maven | **3.8+** (3.9+ recommended) |
| OS | Linux / macOS / Windows（有 JDK 即可） |

Runtime **no** third-party libraries.  
執行期**無**第三方相依套件。

---

## Build / 建置

```bash
git clone https://github.com/benson-code/concurrency-guard.git
cd concurrency-guard

# Run unit + end-to-end tests / 跑測試
mvn test

# Build executable jar / 產出可執行 jar
mvn -q package
```

| Artifact | Path |
|----------|------|
| JAR | `target/concurrency-guard-0.1.0-SNAPSHOT.jar` |
| Main class | `com.concurrencyguard.cli.AuditCli` |

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar help
```

---

## 快速開始 (繁中)

### 1. 啟動故意有 race 的 mock 靶機

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar serve-target 18080 100
```

- 埠：`18080`（可改）
- 初始餘額：`100`
- 端點：
  - `POST /withdraw` body `{"amount":30}` → `200` ok / `409` insufficient
  - `GET /balance` → `{"balance":...}`
- 伺服器在 `balance` 上做**無鎖 check-then-act**，並 `sleep(5ms)` 放大競態窗口（這是故意的 bug）。

### 2. 對 mock 發動稽核

另開一個終端：

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  --target      http://127.0.0.1:18080/withdraw \
  --method      POST \
  --body        '{"amount":30}' \
  --concurrency 10 \
  --baseline    http://127.0.0.1:18080/balance \
  --state-field balance \
  --amount      30 \
  --invariant   max-successes,non-negative,conservation \
  --report      text
```

**預期結果：**

- 畫面上 `Verdict : VIOLATION`
- `Actual successes` > 3（`⌊100/30⌋ = 3`）和／或 `Final balance` 為負
- **Exit code = 1**

> localhost **不需要** `--i-am-authorized`。

### 3. 稽核任意（已授權）HTTP 端點

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  --target      https://staging.example/api/withdraw \
  --method      POST \
  --body        '{"amount":30}' \
  --header      'Authorization: Bearer YOUR_TOKEN' \
  --header      'Cookie: session=...' \
  --concurrency 20 \
  --baseline    https://staging.example/api/balance \
  --state-field balance \
  --amount      30 \
  --report      json \
  --out         report.json \
  --i-am-authorized
```

---

## Quick start (English)

### 1. Start the intentional racy mock

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar serve-target 18080 100
```

- Port: `18080`
- Initial balance: `100`
- Endpoints:
  - `POST /withdraw` with `{"amount":30}` → `200` ok / `409` insufficient
  - `GET /balance` → `{"balance":...}`
- The server deliberately uses **unlocked check-then-act** on `balance` plus a `5ms` sleep to widen the race window.

### 2. Audit the mock

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  --target      http://127.0.0.1:18080/withdraw \
  --method      POST \
  --body        '{"amount":30}' \
  --concurrency 10 \
  --baseline    http://127.0.0.1:18080/balance \
  --state-field balance \
  --amount      30 \
  --invariant   max-successes,non-negative,conservation \
  --report      text
```

**Expected:**

- `Verdict : VIOLATION`
- More than 3 HTTP successes and/or negative final balance
- **Exit code `1`**

> localhost does **not** require `--i-am-authorized`.

### 3. Audit any authorized HTTP endpoint

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  --target      https://staging.example/api/withdraw \
  --method      POST \
  --body        '{"amount":30}' \
  --header      'Authorization: Bearer YOUR_TOKEN' \
  --header      'Cookie: session=...' \
  --concurrency 20 \
  --baseline    https://staging.example/api/balance \
  --state-field balance \
  --amount      30 \
  --report      json \
  --out         report.json \
  --i-am-authorized
```

---

## CLI 完整說明 (繁中)

### 子命令

| 子命令 | 說明 |
|--------|------|
| `audit` | 對目標端點齊發請求並套用不變式（主功能） |
| `serve-target` | 啟動 buggy mock 提款伺服器 |
| `help` | 顯示用法 |

亦可省略 `audit`，直接以 `--target ...` 開頭（視為 audit）。

### `serve-target`

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar serve-target [port] [initialBalance]
```

| 參數 | 預設 | 說明 |
|------|------|------|
| `port` | `18080` | 監聽埠 |
| `initialBalance` | `100` | 初始餘額 |

### `audit` 參數表

| 參數 | 必填 | 預設 | 說明 |
|------|------|------|------|
| `--target <url>` | ✅ | — | 要齊發的端點 |
| `--concurrency <n>` | ✅ | — | 齊發請求數 N（≥ 1） |
| `--method <M>` | | `POST` | HTTP 方法 |
| `--body <text>` | | 空 | 請求 body；支援 `{{index}}`、`{{n}}` |
| `--header <K: V>` | | — | 可重複；自訂標頭 |
| `--baseline <url>` | * | — | 攻擊前／後 GET 狀態的 URL |
| `--state-field <name>` | | `balance` | baseline JSON 中的數值欄位名 |
| `--initial <n>` | * | — | 已知初始值（無 `--baseline` 時） |
| `--amount <n>` | * | — | 每筆扣款額（超賣／守恆計算） |
| `--invariant <list>` | | 三條全開 | 逗號分隔規則名 |
| `--report text\|json\|both` | | `text` | 報告格式 |
| `--out <file>` | | stdout | 輸出路徑；`-` 亦為 stdout |
| `--fire-mode barrier` | | `barrier` | `single-packet` 保留給 M3（會拒跑） |
| `--timeout <sec>` | | `30` | 單請求逾時秒數 |
| `--i-am-authorized` | 非本機必填 | off | 確認你有權測試該目標 |
| `--allow-high-concurrency` | N>100 時 | off | 允許超過預設並發上限 100 |

\* 不變式數學需要初始狀態：`--baseline` **或** `--initial` 至少其一。  
選了 `max-successes` / `conservation` 時需要 `--amount`。  
`non-negative` / `conservation` 需要能讀到 final 狀態（建議有 `--baseline`）。

### Body 範本

```text
{"amount":30,"clientReqId":"{{index}}"}
```

| 占位符 | 意義 |
|--------|------|
| `{{index}}` | 第幾發（0 … N-1） |
| `{{n}}` | 同 `{{index}}` |

### Exit codes

| Code | 意義 | CI 建議 |
|------|------|---------|
| **0** | 無違規 | 通過 |
| **1** | 偵測到違規 | **失敗** |
| **2** | 參數錯誤／缺少授權旗標 | 失敗 |
| **3** | 執行錯誤（連線失敗、探測失敗等） | 失敗 |

### 成功判定（M2）

目前：**HTTP 狀態碼 2xx 且傳輸完成** → 計為一次 success。  
自訂 body 成功標記列在後續版本（見 SPEC 待決議）。

---

## CLI reference (English)

### Subcommands

| Command | Purpose |
|---------|---------|
| `audit` | Fire concurrent requests and evaluate invariants (main) |
| `serve-target` | Start the intentional buggy mock withdraw server |
| `help` | Print usage |

You may omit `audit` and start with `--target ...` (treated as audit).

### `serve-target`

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar serve-target [port] [initialBalance]
```

| Arg | Default | Meaning |
|-----|---------|---------|
| `port` | `18080` | Listen port |
| `initialBalance` | `100` | Starting balance |

### `audit` flags

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--target <url>` | ✅ | — | Endpoint to fire |
| `--concurrency <n>` | ✅ | — | Number of aligned requests (N ≥ 1) |
| `--method <M>` | | `POST` | HTTP method |
| `--body <text>` | | empty | Body; supports `{{index}}`, `{{n}}` |
| `--header <K: V>` | | — | Repeatable custom headers |
| `--baseline <url>` | * | — | GET URL for pre/post state |
| `--state-field <name>` | | `balance` | Numeric JSON field name |
| `--initial <n>` | * | — | Known initial value if no baseline |
| `--amount <n>` | * | — | Per-request debit for oversell/conservation |
| `--invariant <list>` | | all three | Comma-separated rule names |
| `--report text\|json\|both` | | `text` | Report format |
| `--out <file>` | | stdout | Output path (`-` = stdout) |
| `--fire-mode barrier` | | `barrier` | `single-packet` is M3-only (rejected) |
| `--timeout <sec>` | | `30` | Per-request timeout |
| `--i-am-authorized` | non-local required | off | Confirms authorization to test |
| `--allow-high-concurrency` | when N>100 | off | Override default max concurrency 100 |

\* Provide `--baseline` **or** `--initial` for invariant math.  
`--amount` is required when using `max-successes` / `conservation`.  
Prefer `--baseline` so final state can be read for `non-negative` / `conservation`.

### Body templates

| Placeholder | Meaning |
|-------------|---------|
| `{{index}}` | Request index (0 … N-1) |
| `{{n}}` | Same as `{{index}}` |

### Exit codes

| Code | Meaning | CI |
|------|---------|-----|
| **0** | No violation | pass |
| **1** | Violation found | **fail** |
| **2** | Usage / missing auth flag | fail |
| **3** | Runtime error | fail |

### Success definition (M2)

**HTTP 2xx with a completed response** counts as success.  
Custom body-based success markers are planned later (see SPEC open questions).

---

## 不變式引擎 (繁中)

| 名稱 | 規則 | 失敗代表 |
|------|------|----------|
| `max-successes` | 成功數 ≤ ⌊initial / amount⌋ | **超賣** |
| `non-negative` | final 狀態欄位 ≥ 0 | **負餘額／負庫存** |
| `conservation` | initial − final == 成功數 × amount | **帳不平**（與成功次數不一致） |

**注意：** 在極端超賣下，有時 `conservation` 單獨仍可能成立（成功數 × amount 剛好等於 initial − final），但 `max-successes` / `non-negative` 仍會抓到問題。這是預期行為。

### 範例：只開超賣檢查

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  --target http://127.0.0.1:18080/withdraw \
  --body '{"amount":30}' \
  --concurrency 10 \
  --baseline http://127.0.0.1:18080/balance \
  --amount 30 \
  --invariant max-successes
```

---

## Invariant engine (English)

| Name | Rule | Failure means |
|------|------|----------------|
| `max-successes` | successes ≤ ⌊initial / amount⌋ | **Oversell** |
| `non-negative` | final state field ≥ 0 | **Negative balance/stock** |
| `conservation` | initial − final == successes × amount | **Books don't balance** |

**Note:** Under heavy oversell, `conservation` alone may still hold while `max-successes` / `non-negative` fail. That is expected.

---

## 報告格式 (繁中)

### 文字（預設）

```text
=== ConcurrencyGuard Violation Report ===
Target          : POST http://127.0.0.1:18080/withdraw
Initial balance : 100
Withdraw amount : 30
Concurrent reqs : 10
Fire mode       : barrier
----------------------------------------
Expected successes : 3
Actual   successes : 9  << OVERSELL (+6)
Final balance      : -170  << NEGATIVE
----------------------------------------
Invariants:
  [FAIL] max-successes   : 9 > 3 (oversell +6)
  [FAIL] non-negative    : final balance -170 < 0
----------------------------------------
Verdict : VIOLATION
```

### JSON（`--report json`）

機器可讀，適合 CI artifact 或賞金報告附件。欄位包含：

- `target`, `method`, `fireMode`
- `initialBalance`, `withdrawAmount`, `concurrency`
- `expectedSuccesses`, `actualSuccesses`, `finalBalance`
- `verdict`：`OK` | `VIOLATION`
- `violations[]`：`{ invariant, detail }`
- `outcomes[]`：每發的 `index`, `status`, `latencyMs`, `success`, 可選 `error`

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  ... \
  --report both \
  --out report.txt
# text → report.txt ； JSON 另寫 report.txt.json（當 --out 有指定時）
```

---

## Report formats (English)

Text report is the default human summary (see sample above).

JSON (`--report json`) is machine-readable for CI artifacts or bounty evidence:

- Plan: `target`, `method`, `fireMode`, balances, concurrency
- Result: `expectedSuccesses`, `actualSuccesses`, `finalBalance`, `verdict`
- `violations[]` and per-request `outcomes[]`

---

## 架構與目錄 (繁中)

```text
ConcurrencyGuard audit flow
───────────────────────────
CLI (AuditCli)
  │  parse flags / auth gate
  ▼
RequestPlan ──► ConcurrentClient (barrier + virtual threads)
  │                    │
  │                    ▼
  │              List<Outcome>
  │                    │
BaselineProbe ──► InvariantEngine ──► AuditReport (text / JSON)
  (StateProbe)         │                    │
                       ▼                    ▼
                 List<Violation>      exit code 0/1
```

```text
src/main/java/com/concurrencyguard/
├── cli/AuditCli.java                 # 入口：audit / serve-target
├── client/ConcurrentClient.java      # barrier 齊發
├── invariant/                        # 不變式介面、引擎、三規則
├── mock/BuggyMockWithdrawServer.java # 故意 racy 的靶機
├── model/                            # Outcome, RequestPlan, ProbeState, FireMode
├── probe/StateProbe.java             # GET 狀態欄位
└── report/
    ├── AuditReport.java              # M2 完整報告（text+JSON）
    └── ViolationReport.java          # M1 相容超賣視圖
```

詳細設計見 **[SPEC.md](./SPEC.md)**（繁體中文規格書，含市場定位、里程碑、倫理）。

---

## Architecture & layout (English)

See the diagram above. Packages:

| Package | Role |
|---------|------|
| `cli` | Entry point, flags, exit codes |
| `client` | Barrier-aligned concurrent HTTP fire |
| `invariant` | Rules + engine |
| `mock` | Intentional TOCTOU target server |
| `model` | Immutable data records |
| `probe` | Baseline/final numeric state reader |
| `report` | `AuditReport` + legacy `ViolationReport` |

Full design doc: **[SPEC.md](./SPEC.md)** (Traditional Chinese).

---

## 開發與測試 (繁中)

```bash
# 全測試（含對 mock 的 E2E 超賣重現）
mvn test

# 只跑 E2E
mvn -Dtest=OversellE2ETest test
```

主要測試：

| 測試類別 | 涵蓋 |
|----------|------|
| `OversellE2ETest` | 真的打 buggy mock，穩定偵測 VIOLATION |
| `AuditCliTest` | CLI 參數、授權閘門、exit code、JSON |
| `ConcurrentClientTest` | barrier 齊發、body 替換、M3 mode 拒絕 |
| `InvariantEngineTest` | 三規則通過／失敗案例 |
| `StateProbeTest` | JSON 欄位解析（含負數） |
| `AuditReportJsonTest` | JSON 轉義與結構 |

貢獻建議：先開 issue 討論 API／範圍；PR 請保持 **JDK 21、零 runtime 依賴**（除非討論通過）。

---

## Development & tests (English)

```bash
mvn test
mvn -Dtest=OversellE2ETest test
```

Key suites: E2E oversell against the mock, CLI flags/auth/exit codes, client barrier fire, invariant unit cases, probe parsing, JSON report shape.

Contributions: open an issue first for API/scope changes; keep **JDK 21 + zero runtime deps** unless agreed otherwise.

---

## 安全與倫理 (繁中)

ConcurrencyGuard 是**防禦性／授權測試**工具，**不是**攻擊武器。

1. **僅測你擁有或已獲書面授權的系統**（含賞金計畫的明確範圍）。
2. 對 **非 localhost** 目標必須加上 `--i-am-authorized`，否則拒跑。
3. 預設並發上限 **100**；更高需 `--allow-high-concurrency`。
4. **不實作** WAF 繞過、來源隱藏、流量放大等功能。
5. 工具產出的報告只是**證據素材**，不代表你已獲得授權。

濫用造成的任何後果由使用者自負。

---

## Responsible use (English)

ConcurrencyGuard is for **defensive / authorized** testing only.

1. Only test systems you **own** or have **written permission** to test (including in-scope bounty programs).
2. Non-localhost targets **require** `--i-am-authorized`.
3. Default concurrency cap is **100** (`--allow-high-concurrency` to raise).
4. **No** WAF bypass, source hiding, or amplification features.
5. Reports are evidence materials, **not** authorization.

You are responsible for how you use this tool.

---

## Roadmap / 路線圖

| Milestone | Content / 內容 | Status |
|-----------|----------------|--------|
| **M1** | Mock target + report skeleton | ✅ Done |
| **M2** | Concurrent client + invariants + generic CLI + E2E | ✅ Done |
| **M3** | HTTP/2 single-packet attack + scenario DSL | ⬜ Planned |
| **M4** | GitHub Action packaging + more target samples | ⬜ Planned |
| **M5** | Optional cloud tier / consulting materials | ⬜ Optional |

M2 **does not** claim single-packet attack capability. That is M3.

---

## License / 授權

[MIT License](./LICENSE) — Copyright (c) 2026 benson-code

---

## Links / 連結

| | |
|---|---|
| Repository | https://github.com/benson-code/concurrency-guard |
| Spec (繁中) | [SPEC.md](./SPEC.md) |
| Issues | https://github.com/benson-code/concurrency-guard/issues |

---

## Disclaimer / 免責

本軟體按「現況」提供，不附帶任何明示或默示擔保。  
This software is provided “as is”, without warranty of any kind. See [LICENSE](./LICENSE).
