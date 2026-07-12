# Vendored toml-test conformance fixtures

Source: https://github.com/toml-lang/toml-test
Pinned commit: 9eef1b959e0449d41a31d4e4e0a839faee534b36

These are the TOML 1.0.0-scoped fixtures selected by `files-toml-1.0.0` from the upstream repo
(`tests/valid/**.toml` + matching `.json`, `tests/invalid/**.toml`), vendored verbatim as static,
license-compatible (MIT) test data. Consumed by `OfficialConformanceTest`.

To refresh: re-clone the upstream repo, diff `tests/files-toml-1.0.0` against this pin, and re-copy
the listed files. Do this deliberately/periodically, not automatically - conformance suite churn
should not silently change what a build asserts.
