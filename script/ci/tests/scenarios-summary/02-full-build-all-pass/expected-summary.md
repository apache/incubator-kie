## Test Results

> 🎉 **All tests passed.**

| ✅ Passed | ❌ Failed | 💥 Errors | ⏭️ Skipped | TOTAL |
|---:|---:|---:|---:|---:|
| **2** | **0** | **0** | **0** | **2** |

## Reproduce This Build Locally

> Built on **ubuntu-latest** with **Java 21**

**Full build:**

```bash
mvn --batch-mode --no-transfer-progress -fae -Dsurefire.redirectTestOutputToFile=true -Dfull -Dreproducible install
```

