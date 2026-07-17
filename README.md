# ConcurrencyGuard

開源 **HTTP API race-condition 稽核 CLI**。對目標端點齊發併發請求，用不變式（invariant）比對「預期 vs 實際」，偵測超賣、負餘額、帳目不平衡等競態問題。

> **一句話定位（M2）：** barrier 齊發 + 內建不變式斷言的授權測試工具。  
> HTTP/2 單封包攻擊列在 roadmap（M3），尚未實作。

**狀態：** M2 已完成 — 通用 CLI、`ConcurrentClient`、三條不變式、端到端測試。

## Requirements

- JDK 21+
- Maven 3.9+（本機 3.8+ 亦可）

## Build

```bash
mvn -q test
mvn -q package
```

可執行 jar：`target/concurrency-guard-0.1.0-SNAPSHOT.jar`  
Main class：`com.concurrencyguard.cli.AuditCli`

## Quick start — 打自家 buggy mock

```bash
# 終端 1：啟動故意有 race 的提款靶機（balance=100）
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar serve-target 18080 100

# 終端 2：稽核（localhost 不需 --i-am-authorized）
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

預期：`Verdict : VIOLATION`、exit code `1`（成功次數 > 3 或餘額為負）。

## 指向任意 HTTP 端點

```bash
java -jar target/concurrency-guard-0.1.0-SNAPSHOT.jar audit \
  --target      https://staging.example/api/withdraw \
  --method      POST \
  --body        '{"amount":30}' \
  --header      'Authorization: Bearer TOKEN' \
  --header      'Cookie: session=...' \
  --concurrency 20 \
  --baseline    https://staging.example/api/balance \
  --state-field balance \
  --amount      30 \
  --report      json \
  --out         report.json \
  --i-am-authorized
```

Body 範本可用 `{{index}}` / `{{n}}` 代入請求序號。

### Exit codes

| Code | 意義 |
|------|------|
| 0 | 無違規 |
| 1 | 偵測到違規（CI 應 fail） |
| 2 | 參數／授權錯誤 |
| 3 | 執行錯誤（連線失敗等） |

### M2 內建不變式

| 名稱 | 說明 |
|------|------|
| `max-successes` | 成功數 ≤ ⌊initial / amount⌋ |
| `non-negative` | final 狀態欄位 ≥ 0 |
| `conservation` | initial − final == 成功數 × amount |

## Project layout

```
src/main/java/com/concurrencyguard/
├── cli/AuditCli.java              # 通用 CLI（audit / serve-target）
├── client/ConcurrentClient.java   # barrier 齊發（虛擬執行緒）
├── invariant/                     # 不變式引擎與三規則
├── mock/BuggyMockWithdrawServer.java
├── model/                         # Outcome / RequestPlan / ProbeState
├── probe/StateProbe.java
└── report/                        # ViolationReport + AuditReport
```

詳見 [SPEC.md](./SPEC.md)（繁體中文規格書）。

## Responsible use

僅可用於**你擁有或已獲書面授權**測試的系統。對非 localhost 目標必須帶 `--i-am-authorized`。預設並發上限 100（`--allow-high-concurrency` 可覆寫）。工具不含 WAF 繞過或 DoS 功能。

## Roadmap

| 里程碑 | 內容 | 狀態 |
|--------|------|------|
| M1 | Mock 靶 + 報告骨架 | ✅ |
| M2 | 併發客戶端 + 不變式 + 通用 CLI + E2E | ✅ |
| M3 | HTTP/2 單封包 + 情境 DSL | ⬜ |
| M4 | GitHub Action / 文件 / 多靶範例 | ⬜ |

## License

尚未選定授權；開源釋出前會補上。
