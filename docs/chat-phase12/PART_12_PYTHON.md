# CodeC Phase 12 — Multi-Language Support, Python & Code Intelligence

**Status:** Planned · **Cost:** `[repo-build]` (ONE planned CI package build ~1–2h) · **Depends on:** Phase 8 (Projects) + Phase 9 (Editor) + Phase 10 (Package Hub)  
**Target Files:** `codec-packages/packages/python/`, `MultiLanguageSyntaxHighlighter.kt`, `CodeCompletionEngine.kt`, `EditorScreen.kt`

---

## 1. Context & Motivation

CodeC's syntax highlighting and execution have historically been tailored to C. With the signed repository and package manager operational, Python 3 is the most requested language for mobile development (data analysis, scripts, web backends with Flask/FastAPI, automation).

Phase 12 delivers:
1. **Python 3 Package in Repository (`[repo-build]`):** The only planned CI repo build (~1–2 hours) compiles `python3` (with standard libraries and pip) for `aarch64` and `x86_64` and publishes to the CodeC repository channel.
2. **Multi-Language Syntax Engine:** Extend syntax highlighting to Python, JavaScript, HTML/CSS, JSON, and Shell scripts.
3. **Buffer & Snippet Autocomplete:** Lightweight in-app autocompletion scanning identifiers in the active buffer + standard library snippet templates without heavy external language servers.
4. **Python Project & Server Presets:** 1-tap run `python3 main.py` and automatic Web Preview for `python3 -m http.server` and Flask.

---

## 2. Architectural Design (Decision D1)

### 2.1 Repository Build Recipe (`codec-packages/packages/python/`)
- Build recipe based on upstream Termux Python 3 recipes targeting `/data/data/com.codeci.ide/files/usr`.
- Package artifacts: `python` (interpreter, standard library, sqlite3, ssl, zlib, ctypes).
- Preflight validation with `validate-repository.py` and `validate-bootstrap.py`.

### 2.2 Multi-Language Syntax Highlighter
```kotlin
enum class LanguageType(val extensions: List<String>) {
    C(listOf("c", "h")),
    CPP(listOf("cpp", "hpp", "cc", "cxx")),
    PYTHON(listOf("py", "pyw")),
    JAVASCRIPT(listOf("js", "jsx", "ts", "tsx")),
    HTML_CSS(listOf("html", "htm", "css")),
    JSON(listOf("json")),
    SHELL(listOf("sh", "bash")),
    MARKDOWN(listOf("md", "markdown")),
    TEXT(listOf("txt"))
}
```
- Tokenizer parses: Keywords, Types, String literals, Number literals, Function definitions, Decorators (`@`), Comments (`#`, `//`, `/* */`), and Operators.
- Theme-aware color token assignment (`colors.keyword`, `colors.string`, `colors.function`, `colors.number`, `colors.comment`).

### 2.3 Buffer & Snippet Autocompletion Engine
- **Identifier Scanner:** Regex-based scanner collecting all user-defined symbols in the buffer (variable names, function names, class names).
- **Snippet Presets:**
  - Python: `def function():`, `class Class:`, `if __name__ == "__main__":`, `for x in list:`, `try ... except Exception:`, `import `, `from ... import ...`.
  - C: `int main(int argc, char *argv[]) {`, `printf("...\n");`, `for (int i = 0; i < n; i++) {`, `#include <...>`.
- **Floating Suggestion Popup:**
  - Floats near cursor; tap or press `TAB` / `ENTER` to insert; `ESC` to dismiss.

---

## 3. Implementation Steps

1. **Step 1:** Add `python` recipe in `codec-packages/` and dispatch the GitHub Actions repository build.
2. **Step 2:** Implement `MultiLanguageSyntaxHighlighter.kt` supporting C, Python, JS, HTML, JSON, and Shell.
3. **Step 3:** Implement `CodeCompletionEngine.kt` with identifier scanning and snippet dictionary.
4. **Step 4:** Integrate autocompletion popup into `EditorScreen.kt`.
5. **Step 5:** Add Python project template and run preset (`project.json`: `{ "type": "python", "run": "python3 main.py" }`).
6. **Step 6:** Unit tests in `SyntaxHighlighterTest.kt` and `CodeCompletionTest.kt`.

---

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following recipe on device:

```sh
# Setup & Python Verification
# 1. Open Packages tab -> Tap INSTALL on "python" (pkg install -y python).
# 2. Open Terminal -> Run: python3 --version -> Verify Python 3.x prints.
# 3. In Files tab, create new file "script.py".
# 4. Open "script.py" in Editor -> Observe Python keywords (def, import, print, class) highlighted.
# 5. Type "def " -> Observe autocomplete popup appears with function template -> Press TAB to insert.
# 6. Type code:
#    import math
#    print(f"Pi is {math.pi:.4f}")
# 7. Tap "RUN ▶" in toolbar -> Observe Output Panel shows "Pi is 3.1416" with exit code 0.
# PASS
```

---

## 5. Invariants & Guardrails

- Python build must strictly follow CodeC's prefix `/data/data/com.codeci.ide/files/usr` (no `com.termux`).
- Signed repository metadata must be updated with `generate-repository.py` and OpenPGP subkey signing.
