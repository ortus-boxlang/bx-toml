# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

* Initial release: full TOML 1.0.0 parsing and serialization via `tomlDeserialize()`, `tomlDeserializeFile()`, `tomlSerialize()`, and `tomlSerializeFile()`, backed by tomlj for parsing and a hand-written writer for serialization. Validated against the official toml-test 1.0.0 conformance suite. Best-effort TOML 1.1 opt-in via the `specVersion` setting.
