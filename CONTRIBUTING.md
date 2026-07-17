# Contributing / 貢獻指南

**Languages：** [繁體中文](#繁體中文) · [English](#english)

---

## 繁體中文

感謝你有興趣改進 ConcurrencyGuard。

### 開發原則

1. **JDK 21**，執行期盡量保持**零第三方依賴**。
2. 先做**正確性與可重現**，不要把工具做成壓測／DoS 武器。
3. 非 localhost 的授權閘門與並發上限不要拿掉。
4. 大功能（單封包、DSL、新不變式）請先開 Issue 對齊 [SPEC.md](./SPEC.md)。

### 開發流程

```bash
git clone https://github.com/benson-code/concurrency-guard.git
cd concurrency-guard
mvn test
```

1. Fork + 開分支（例如 `feat/m3-single-packet`）
2. 補上測試（尤其會改變 CLI／不變式行為時）
3. `mvn test` 全綠
4. 送 Pull Request，說明**做了什麼、為什麼、如何驗證**

### 回報問題

請在 Issue 附上：

- JDK / OS 版本
- 完整 CLI 指令（**遮罩 token / cookie**）
- 期望行為 vs 實際行為
- 文字或 JSON 報告片段

### 行為準則

請尊重他人；僅討論合法、授權範圍內的測試用途。

---

## English

Thanks for contributing to ConcurrencyGuard.

### Principles

1. **JDK 21**, prefer **zero runtime dependencies**.
2. Correctness and reproducibility over raw RPS; do not turn this into a DoS tool.
3. Keep the non-localhost authorization flag and concurrency caps.
4. Large features (single-packet, DSL, new invariants) should start as an Issue aligned with [SPEC.md](./SPEC.md).

### Workflow

```bash
git clone https://github.com/benson-code/concurrency-guard.git
cd concurrency-guard
mvn test
```

1. Fork and branch (e.g. `feat/m3-single-packet`)
2. Add tests when CLI/invariant behavior changes
3. Ensure `mvn test` is green
4. Open a PR describing **what / why / how verified**

### Bug reports

Please include:

- JDK / OS versions
- Full CLI command (**redact tokens/cookies**)
- Expected vs actual
- Text or JSON report snippet

### Conduct

Be respectful. Discuss only lawful, authorized testing use cases.
