## MODIFIED Requirements

### Requirement: Language level and toolchain
Production code SHALL target Java 8 (`options.release = 8`) and SHALL compile with `-Werror`,
except the processor's `JavaCompile` task, which SHALL compile with `-nowarn` instead of `-Werror`.
Under `--release 8` two warnings cannot be disabled individually and are accepted: JDKs 21 and later
report that release 8 is obsolete, and jspecify's `@NullMarked` class file names
`ElementType.MODULE`, which release 8 does not have. Errors SHALL still fail the build.
The build SHALL run on the JDK pinned in `.mise.toml`.

#### Scenario: Release level is 8
- **WHEN** `JavaCompile` tasks are configured
- **THEN** `options.release` is 8 and `-Werror` is present in the compiler arguments of every task except the processor's

#### Scenario: Processor compiles without -Werror
- **WHEN** the `compileJava` task of `processor` is configured
- **THEN** the compiler arguments contain `-nowarn` and do not contain `-Werror`

#### Scenario: Processor bytecode is Java 8
- **WHEN** the `processor` jar is built
- **THEN** every class file in it has major version 52
