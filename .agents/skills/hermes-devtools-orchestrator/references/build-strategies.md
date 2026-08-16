# Build Strategies Reference

> Detailed build strategies for hermes-pipeline binary across platforms.

## Table of Contents

- [Platform-Specific Builds](#platform-specific-builds)
- [Dependency Management](#dependency-management)
- [Binary Size Optimization](#binary-size-optimization)
- [Build Troubleshooting](#build-troubleshooting)
- [Smoke Test Checklist](#smoke-test-checklist)

---

## Platform-Specific Builds

### macOS (Apple Silicon / Intel)

```bash
# Apple Silicon (M1/M2/M3)
# Binary will be arm64 by default
./build.sh --test

# Cross-compile for Intel (if needed)
# Requires Rosetta: arch -x86_64 ./build.sh
```

**Known issues:**
- PyInstaller may need `--target-arch` for cross-compilation
- Some native extensions (e.g., `aiohttp`) need platform-specific wheels
- Code signing may be required for distribution: set `codesign_identity` in build.spec

### Linux (x86_64 / ARM)

```bash
# Standard Linux build
./build.sh --test

# Docker-based build for consistent environment
docker run --rm -v $(pwd):/app -w /app python:3.11-slim bash -c "
  pip install -r requirements.txt pyinstaller &&
  pyinstaller build.spec --clean
"
```

**Known issues:**
- `glibc` version compatibility — build on oldest target distro
- Static linking may be needed for portable binaries
- UPX compression may fail on some architectures

---

## Dependency Management

### Core Dependencies (Required)

| Package | Purpose | build.spec Reference |
|---------|---------|---------------------|
| `pyyaml` | Config parsing | `hiddenimports: yaml` |
| `aiohttp` | Async HTTP client | `hiddenimports: aiohttp` |
| `httpx` | HTTP client (optional) | Optional, graceful fallback |

### Optional Dependencies (Graceful Fallback)

| Package | Purpose | Fallback Behavior |
|---------|---------|-------------------|
| `chromadb` | Skill library storage | SkillLibrary disabled |
| `langfuse` | Observability tracing | LangfuseTracer disabled |
| `duckduckgo_search` | Web search tool | web_search tool unavailable |
| `trafilatura` | URL content extraction | read_url returns raw HTML |
| `mcp` | MCP protocol | MCP tools disabled |

### Dependency Verification

```bash
# Check which optional deps are available
python3 -c "
deps = ['chromadb', 'langfuse', 'duckduckgo_search', 'trafilatura', 'mcp']
for d in deps:
    try:
        __import__(d)
        print(f'  ✅ {d}')
    except ImportError:
        print(f'  ⚠️  {d} (optional, not installed)')
"
```

---

## Binary Size Optimization

### Current Excludes (build.spec)

The following are excluded to reduce binary size:

```python
excludes = [
    "src.tests",    # Test code
    "tkinter",      # GUI (not needed)
    "matplotlib",   # Plotting (not needed)
    "numpy",        # Numerical (not needed)
    "pandas",       # DataFrames (not needed)
    "PIL",          # Image processing (not needed)
    "cv2",          # Computer vision (not needed)
    "scipy",        # Scientific computing (not needed)
    "IPython",      # Interactive shell (not needed)
    "notebook",     # Jupyter (not needed)
    "pytest",       # Testing (not needed in binary)
]
```

### Size Benchmarks

| Configuration | Approximate Size |
|--------------|-----------------|
| Full build (no excludes) | ~40-60MB |
| Standard build (with excludes) | ~15-25MB |
| Minimal build (core only) | ~10-15MB |
| With UPX compression | ~8-15MB |

### Further Optimization Tips

1. **UPX compression**: Already enabled in build.spec (`upx=True`)
2. **Strip debug symbols**: Already enabled (`strip=True`)
3. **Exclude test data**: Ensure test fixtures are not bundled
4. **Single-file mode**: Current config uses single-file (`a.scripts, a.binaries, a.datas`)

---

## Build Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `ModuleNotFoundError` at runtime | Missing hiddenimport | Add to `hiddenimports` in build.spec |
| Binary too large (>30MB) | Unused packages bundled | Add to `excludes` list |
| `Permission denied` on binary | Not executable | `chmod +x dist/hermes-pipeline` |
| Segfault on startup | Python version mismatch | Rebuild with matching Python |
| `sqlite3` errors | System sqlite3 too old | Ensure Python bundles its own sqlite |

### Debug Build

```bash
# Build with debug info for troubleshooting
.venv/bin/pyinstaller build.spec --clean --log-level DEBUG 2>&1 | tee build-debug.log

# Analyze imports
.venv/bin/pyinstaller build.spec --clean --log-level DEBUG 2>&1 | grep "hidden import"
```

---

## Smoke Test Checklist

After every successful build, verify:

```bash
BINARY="dist/hermes-pipeline"

# 1. Binary exists and is executable
[ -f "$BINARY" ] && [ -x "$BINARY" ] && echo "✅ Binary exists"

# 2. Help command works
$BINARY --help > /dev/null 2>&1 && echo "✅ --help works"

# 3. Status command works
$BINARY status 2>&1 | grep -q "No pipeline progress\|PIPELINE STATUS" && echo "✅ status works"

# 4. Binary size within range
SIZE_MB=$(du -m "$BINARY" | awk '{print $1}')
if [ "$SIZE_MB" -ge 10 ] && [ "$SIZE_MB" -le 30 ]; then
    echo "✅ Size OK: ${SIZE_MB}MB"
else
    echo "⚠️ Size unusual: ${SIZE_MB}MB (expected 10-30MB)"
fi

# 5. No critical missing modules
$BINARY start --help > /dev/null 2>&1 && echo "✅ start subcommand available"
```

---

## Build Automation CI/CD

### GitHub Actions Example

```yaml
name: Build Hermes Pipeline
on:
  push:
    branches: [main]
    paths: ['src/**', 'build.spec', 'requirements.txt']

jobs:
  build:
    strategy:
      matrix:
        os: [macos-latest, ubuntu-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - run: |
          python -m venv .venv
          .venv/bin/pip install -r requirements.txt pyinstaller
          ./build.sh --test
      - uses: actions/upload-artifact@v4
        with:
          name: hermes-pipeline-${{ matrix.os }}
          path: dist/hermes-pipeline
```
