## MODIFIED Requirements

### Requirement: Language level and toolchain
Production code SHALL target Java 8 (`options.release = 8`) and SHALL compile with `-Werror`.
The processor's `JavaCompile` task SHALL additionally pass `-Xlint:-options`, so that the
obsolete-option warning emitted by newer JDKs for `--release 8` does not fail the build.
The build SHALL run on the JDK pinned in `.mise.toml`.

#### Scenario: Release level is 8
- **WHEN** `JavaCompile` tasks are configured
- **THEN** `options.release` is 8 and `-Werror` is present in the compiler arguments

#### Scenario: Obsolete-option lint is suppressed for the processor
- **WHEN** the `compileJava` task of `processor` is configured
- **THEN** the compiler arguments contain `-Xlint:-options`

#### Scenario: Processor bytecode is Java 8
- **WHEN** the `processor` jar is built
- **THEN** every class file in it has major version 52
