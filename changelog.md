# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

## [1.0.0] - 2026-07-13

- Initial release: full TOML 1.0.0 parsing and serialization via `tomlDeserialize()`, `tomlDeserializeFile()`, `tomlSerialize()`, and `tomlSerializeFile()`, backed by tomlj for parsing and a hand-written writer for serialization. Validated against the official toml-test 1.0.0 conformance suite. Best-effort TOML 1.1 opt-in via the `specVersion` setting.

[unreleased]: https://github.com/ortus-boxlang/bx-toml/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ortus-boxlang/bx-toml/compare/c1160815897cffaa1807acfe20f9783581adbaf7...v1.0.0
