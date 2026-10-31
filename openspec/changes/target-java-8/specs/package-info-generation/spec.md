## MODIFIED Requirements

### Requirement: Generated file content
Each generated file SHALL consist of a `@Generated` annotation naming the processor, the
fully-qualified `@org.jspecify.annotations.NullMarked` annotation, and the `package` declaration.
The `@Generated` annotation SHALL be `@javax.annotation.Generated` when the consuming compilation's
source version is `RELEASE_8` or lower, and `@javax.annotation.processing.Generated` otherwise. The
file SHALL NOT contain an import statement.

#### Scenario: Content for source 9 and later is the fully-qualified three-line form
- **WHEN** `com.example.package-info` is generated in a compilation whose source version is 11
- **THEN** the source contains
  `@javax.annotation.processing.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")`
  followed by `@org.jspecify.annotations.NullMarked` followed by `package com.example;`
- **AND** it contains no `import` statement

#### Scenario: Content for source 8 uses the Java 8 Generated annotation
- **WHEN** `com.example.package-info` is generated in a compilation whose source version is 8
- **THEN** the source contains
  `@javax.annotation.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")`
  followed by `@org.jspecify.annotations.NullMarked` followed by `package com.example;`
- **AND** it contains no `import` statement

#### Scenario: Marking reaches the class file
- **WHEN** a compilation with a generated `com.example.package-info` completes
- **THEN** `com/example/package-info.class` is present in the compilation output
- **AND** it carries the `org.jspecify.annotations.NullMarked` annotation
