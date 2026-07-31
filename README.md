# jspecify-generator

An annotation processor that generates the `package-info.java` files a project-wide
"everything is null-marked" policy would otherwise require you to write by hand.

## Why

JSpecify has no syntax for "this whole codebase is null-marked". `@NullMarked` targets a module, a
package, a type, a method or a constructor — so a non-modular project has no altitude above
`package` at which to state the rule once. The rule has to be transcribed into one
`package-info.java` per package, which is boilerplate in the same sense that writing the Java
language version at the top of every source file would be boilerplate: a global build-level fact,
repeated N times because the language offers nowhere else to put it.

This processor does the transcription. You state the policy by applying the processor; it produces
the files.

### Why not `-XepOpt:NullAway:AnnotatedPackages`?

For a closed application checked only by NullAway, that flag *is* the better answer and you do not
need this tool.

It is not enough for a published library. The flag is a fact known only to one javac invocation; it
is not carried in the class files. That makes it invisible to Error Prone's
`RequireExplicitNullMarking`, to IDE inspections, to Kotlin consumers deciding platform-type versus
definite-type, and to downstream Java consumers running their own NullAway. A consumer *can*
restate `AnnotatedPackages=your.lib` in their own build, but that asserts a property of code they
do not control and cannot verify — if the library never actually added its `@Nullable` annotations,
NullAway will confidently conclude that nullable returns are non-null.

Producer-side markers in the class file are the only sound mechanism, and the only source of a
`package-info.class` is a `package-info.java`.

## Usage

```groovy
dependencies {
    annotationProcessor     'io.github.joke.jspecify:processor:<version>'
    testAnnotationProcessor 'io.github.joke.jspecify:processor:<version>'

    // Supply JSpecify yourself, at a version you choose. This project deliberately
    // publishes no dependencies and does not push a JSpecify version on you.
    compileOnly     'org.jspecify:jspecify:<version>'
    testCompileOnly 'org.jspecify:jspecify:<version>'
}
```

Wire `testAnnotationProcessor` too, or your test sources stay unmarked.

The generated files land in the annotation processor output directory and are compiled into your
jar. Nothing is written into your source tree, and nothing needs committing.

## What it generates

For every package it discovers that does not already have a `package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package com.example.foo;
```

## Overriding a package

Write the `package-info.java` yourself. An existing file always wins, whatever it contains — a
`@NullUnmarked`, a different annotation, or javadoc alone. There is no exclusion list and no
processor option, because a hand-written file is the better escape hatch: it lives next to the code
it affects, carries a comment explaining itself, survives renames, and shows up in the pull request
that needs it.

```java
// This example demonstrates the fallback for an absent-capable reference source, which requires
// it resolve as UNKNOWN nullness rather than the NON_NULL a @NullMarked package would default to.
@org.jspecify.annotations.NullUnmarked
package com.example.legacy;
```

## Opting a whole module out

The per-package override above is for exceptions inside a module that has opted in. To exclude an
entire module — vendored third-party sources, a legacy module you are not ready to mark — simply
do not apply the processor to it, the same way you opt out of any annotation processor:

```groovy
// in a shared convention plugin
if (project.path != ':lib:vendored') {
    dependencies { annotationProcessor 'io.github.joke.jspecify:processor:<version>' }
}
```

This matters because the missing-JSpecify diagnostic is an error, not a warning. Wiring the
processor into a convention plugin applied to every module makes JSpecify mandatory in every
module. That is deliberate — a processor that quietly did nothing when JSpecify was absent would
leave exactly the silent holes this tool exists to close — but it means module-level exclusion is a
build-wiring decision, not something the processor can infer.

Be aware of what opting *in* means for a module of third-party code: blanket `@NullMarked` changes
its API contract, so unannotated parameters become non-null and callers passing nullable values
start failing. That is the correct result, but it is a migration, not a no-op.

## What it skips

- Packages that already have a `package-info.java`, in this compilation or on the classpath.
- Every package in a module whose `module-info.java` is already `@NullMarked` — JPMS can state the
  rule once, and NullAway honours it, so per-package files would be redundant.
- The unnamed (default) package, which cannot have a `package-info.java`. This produces a warning.

If `org.jspecify:jspecify` is missing from the compile classpath the processor emits one clear
error naming the missing dependency rather than generating a file that cannot compile.

## Recommended companion

Pair this with Error Prone's `RequireExplicitNullMarking` set to error:

```groovy
options.errorprone.error('RequireExplicitNullMarking')
```

The division of labour is that this processor fills the gaps and that check proves none remain. It
catches the one case the processor deliberately stays out of: a hand-written `package-info.java`
that exists for some other reason (javadoc, a legacy annotation) and silently leaves the package
unmarked.

## Incremental builds

The processor is declared `AGGREGATING`, so Gradle keeps incremental compilation enabled in the
modules that apply it.

## Licence

Apache 2.0.
