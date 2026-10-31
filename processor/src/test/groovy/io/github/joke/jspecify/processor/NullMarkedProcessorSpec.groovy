package io.github.joke.jspecify.processor

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.FilerException
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Elements
import javax.tools.Diagnostic
import javax.tools.JavaFileObject

/**
 * Unit-level contract of {@link NullMarkedProcessor}. Every method is exercised directly against a
 * mocked {@link ProcessingEnvironment}; the end-to-end behaviour through a real javac lives in
 * {@link NullMarkedProcessorCompilationSpec}.
 */
@Tag('unit')
class NullMarkedProcessorSpec extends Specification {

    ProcessingEnvironment processingEnv = Mock()
    Elements elements = Mock()
    Filer filer = Mock()
    Messager messager = Mock()
    RoundEnvironment roundEnv = Mock()

    @Subject
    NullMarkedProcessor processor = new NullMarkedProcessor()

    def setup() {
        processor.init(processingEnv)
    }

    def 'reports the latest supported source version'() {
        expect:
        processor.supportedSourceVersion == SourceVersion.latestSupported()
    }

    def 'claims every annotation'() {
        when:
        def claimed = processor.supportedAnnotationTypes

        then:
        // AbstractProcessor consults the source version to decide whether module prefixes are legal.
        _ * processingEnv.sourceVersion >> SourceVersion.latestSupported()
        0 * _

        expect:
        claimed == ['*'] as Set
    }

    def 'process generates and never claims the annotations'() {
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def claimed = subject.process([] as Set, roundEnv)

        then:
        1 * roundEnv.processingOver() >> false
        1 * subject.generate(roundEnv) >> {}
        1 * subject._
        0 * _

        expect:
        !claimed
    }

    def 'process generates nothing in the final round'() {
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def claimed = subject.process([] as Set, roundEnv)

        then:
        1 * roundEnv.processingOver() >> true
        1 * subject._
        0 * _

        expect:
        !claimed
    }

    def 'generate writes an uncovered package'() {
        Element type = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        subject.generate(roundEnv)

        then:
        1 * subject.jspecifyMissing() >> false
        1 * subject.existingPackageInfos(roundEnv) >> (['com.other'] as Set)
        1 * subject.candidates(roundEnv) >> ['com.example': [type]]
        1 * subject.writeUnlessCovered('com.example', [type], ['com.other'] as Set) >> {}
        1 * subject._
        0 * _
    }

    def 'generate stops when JSpecify is absent'() {
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        subject.generate(roundEnv)

        then:
        1 * subject.jspecifyMissing() >> true
        1 * subject.reportMissingJspecify() >> {}
        1 * subject._
        0 * _
    }

    def 'writeUnlessCovered writes only when the package is not covered'() {
        Element type = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        subject.writeUnlessCovered('com.example', [type], [] as Set)

        then:
        1 * subject.covered('com.example', [] as Set) >> covered
        writes * subject.write('com.example', [type]) >> {}
        1 * subject._
        0 * _

        where:
        covered | writes
        false | 1
        true | 0
    }

    def 'typeRoots keeps types and drops package-info root elements'() {
        Element type = Mock()
        PackageElement packageInfo = Mock()

        when:
        def roots = processor.typeRoots(roundEnv)

        then:
        1 * roundEnv.rootElements >> ([type, packageInfo] as Set)
        1 * type.kind >> ElementKind.CLASS
        1 * packageInfo.kind >> ElementKind.PACKAGE
        0 * _

        expect:
        roots == [type]
    }

    def 'packageOf resolves the enclosing package'() {
        Element type = Mock()
        PackageElement pkg = Mock()

        when:
        def resolved = processor.packageOf(type)

        then:
        1 * processingEnv.elementUtils >> elements
        1 * elements.getPackageOf(type) >> pkg
        0 * _

        expect:
        resolved.is(pkg)
    }

    def 'packageName renders the qualified name'() {
        Element type = Mock()
        PackageElement pkg = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def name = subject.packageName(type)

        then:
        1 * subject.packageOf(type) >> pkg
        1 * pkg.qualifiedName >> Names.of('com.example')
        1 * subject._
        0 * _

        expect:
        name == 'com.example'
    }

    def 'inUnnamedPackage delegates to the package element'() {
        Element type = Mock()
        PackageElement pkg = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def unnamed = subject.inUnnamedPackage(type)

        then:
        1 * subject.packageOf(type) >> pkg
        1 * pkg.unnamed >> true
        1 * subject._
        0 * _

        expect:
        unnamed
    }

    def 'inUnnamedPackage reports a named package as named'() {
        Element type = Mock()
        PackageElement pkg = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def unnamed = subject.inUnnamedPackage(type)

        then:
        1 * subject.packageOf(type) >> pkg
        1 * pkg.unnamed >> false
        1 * subject._
        0 * _

        expect:
        !unnamed
    }

    def 'inNullMarkedModule delegates to the module check'() {
        Element type = Mock()
        PackageElement pkg = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def marked = subject.inNullMarkedModule(type)

        then:
        1 * subject.packageOf(type) >> pkg
        1 * subject.moduleIsNullMarked(pkg) >> true
        1 * subject._
        0 * _

        expect:
        marked
    }

    def 'inNullMarkedModule reports an unmarked module as unmarked'() {
        Element type = Mock()
        PackageElement pkg = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def marked = subject.inNullMarkedModule(type)

        then:
        1 * subject.packageOf(type) >> pkg
        1 * subject.moduleIsNullMarked(pkg) >> false
        1 * subject._
        0 * _

        expect:
        !marked
    }

    def 'candidates groups types by package, warning on the default package'() {
        Element named = Mock()
        Element unnamed = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def grouped = subject.candidates(roundEnv)

        then:
        1 * subject.typeRoots(roundEnv) >> [named, unnamed]
        2 * subject.inUnnamedPackage(named) >> false
        2 * subject.inUnnamedPackage(unnamed) >> true
        1 * subject.warnUnnamedPackage(unnamed) >> {}
        1 * subject.inNullMarkedModule(named) >> false
        1 * subject.packageName(named) >> 'com.example'
        1 * subject._
        0 * _

        expect:
        grouped == ['com.example': [named]]
    }

    def 'candidates drops packages already covered by a null-marked module'() {
        Element type = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def grouped = subject.candidates(roundEnv)

        then:
        1 * subject.typeRoots(roundEnv) >> [type]
        2 * subject.inUnnamedPackage(type) >> false
        1 * subject.inNullMarkedModule(type) >> true
        1 * subject._
        0 * _

        expect:
        // Not `grouped.empty`: on a Map that reads the 'empty' key, which is always null.
        grouped == [:]
    }

    def 'existingPackageInfos collects package root elements'() {
        Element type = Mock()
        PackageElement packageInfo = Mock()

        when:
        def existing = processor.existingPackageInfos(roundEnv)

        then:
        1 * roundEnv.rootElements >> ([type, packageInfo] as Set)
        1 * type.kind >> ElementKind.CLASS
        1 * packageInfo.kind >> ElementKind.PACKAGE
        1 * packageInfo.qualifiedName >> Names.of('com.example')
        0 * _

        expect:
        existing == ['com.example'] as Set
    }

    def 'covered recognises a package-info in this compilation'() {
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def result = subject.covered('com.example', ['com.example'] as Set)

        then:
        1 * subject._
        0 * _

        expect:
        result
    }

    def 'covered recognises a package-info on the classpath'() {
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def result = subject.covered('com.example', [] as Set)

        then:
        1 * subject.annotatedOnClasspath('com.example') >> true
        1 * subject._
        0 * _

        expect:
        result
    }

    def 'covered reports an untouched package as uncovered'() {
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def result = subject.covered('com.example', [] as Set)

        then:
        1 * subject.annotatedOnClasspath('com.example') >> false
        1 * subject._
        0 * _

        expect:
        !result
    }

    def 'annotatedOnClasspath detects a package that already carries annotations'() {
        PackageElement pkg = Mock()

        when:
        def found = processor.annotatedOnClasspath('com.example')

        then:
        1 * processingEnv.elementUtils >> elements
        1 * elements.getPackageElement('com.example') >> pkg
        1 * pkg.annotationMirrors >> mirrors
        0 * _

        expect:
        found == expected

        where:
        mirrors | expected
        [Mock(AnnotationMirror)] | true
        [] | false
    }

    def 'annotatedOnClasspath tolerates a package unknown to this compilation'() {
        when:
        def found = processor.annotatedOnClasspath('com.example')

        then:
        1 * processingEnv.elementUtils >> elements
        1 * elements.getPackageElement('com.example') >> null
        0 * _

        expect:
        !found
    }

    def 'moduleIsNullMarked consults the enclosing module'() {
        PackageElement pkg = Mock()
        Element module = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def marked = subject.moduleIsNullMarked(pkg)

        then:
        1 * pkg.enclosingElement >> module
        1 * subject.isNullMarked(module) >> true
        1 * subject._
        0 * _

        expect:
        marked
    }

    def 'moduleIsNullMarked reports a module without the annotation as unmarked'() {
        PackageElement pkg = Mock()
        Element module = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def marked = subject.moduleIsNullMarked(pkg)

        then:
        1 * pkg.enclosingElement >> module
        1 * subject.isNullMarked(module) >> false
        1 * subject._
        0 * _

        expect:
        !marked
    }

    def 'moduleIsNullMarked tolerates a package with no enclosing module'() {
        PackageElement pkg = Mock()

        when:
        def marked = processor.moduleIsNullMarked(pkg)

        then:
        1 * pkg.enclosingElement >> null
        0 * _

        expect:
        !marked
    }

    def 'isNullMarked scans the module annotations'() {
        Element module = Mock()
        AnnotationMirror mirror = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        def marked = subject.isNullMarked(module)

        then:
        1 * module.annotationMirrors >> [mirror]
        1 * subject.isNullMarkedAnnotation(mirror) >> found
        1 * subject._
        0 * _

        expect:
        marked == found

        where:
        found << [true, false]
    }

    def 'isNullMarkedAnnotation matches the JSpecify annotation by qualified name'() {
        AnnotationMirror mirror = Mock()
        DeclaredType annotationType = Mock()
        TypeElement annotation = Mock()

        when:
        def matched = processor.isNullMarkedAnnotation(mirror)

        then:
        1 * mirror.annotationType >> annotationType
        1 * annotationType.asElement() >> annotation
        1 * annotation.qualifiedName >> Names.of(qualifiedName)
        0 * _

        expect:
        matched == expected

        where:
        qualifiedName | expected
        'org.jspecify.annotations.NullMarked' | true
        'org.jspecify.annotations.NullUnmarked' | false
    }

    def 'isNullMarkedAnnotation ignores an annotation that is not a type element'() {
        AnnotationMirror mirror = Mock()
        DeclaredType annotationType = Mock()
        Element notAType = Mock()

        when:
        def matched = processor.isNullMarkedAnnotation(mirror)

        then:
        1 * mirror.annotationType >> annotationType
        1 * annotationType.asElement() >> notAType
        0 * _

        expect:
        !matched
    }

    def 'write emits the package-info and records the package'() {
        Element type = Mock()
        JavaFileObject file = Mock()
        StringWriter written = new StringWriter()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        subject.write('com.example', [type])

        then:
        1 * processingEnv.filer >> filer
        1 * filer.createSourceFile('com.example.package-info', type) >> file
        1 * file.openWriter() >> written
        1 * subject.content('com.example') >> 'CONTENT'
        1 * subject._
        0 * _

        expect:
        written.toString() == 'CONTENT'
        subject.covered('com.example', [] as Set)
    }

    def 'write treats an already-created file as covered'() {
        Element type = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        subject.write('com.example', [type])

        then:
        1 * processingEnv.filer >> filer
        1 * filer.createSourceFile('com.example.package-info', type) >> {
            throw new FilerException('already created')
        }
        1 * subject._
        0 * _

        expect:
        subject.covered('com.example', [] as Set)
    }

    def 'write wraps an IO failure'() {
        Element type = Mock()
        NullMarkedProcessor subject = Spy()
        subject.init(processingEnv)

        when:
        subject.write('com.example', [type])

        then:
        1 * processingEnv.filer >> filer
        1 * filer.createSourceFile('com.example.package-info', type) >> {
            throw new IOException('disk full')
        }
        def error = thrown(IllegalStateException)
        1 * subject._
        0 * _

        expect:
        error.message == 'Failed to generate com.example.package-info'
    }

    def 'content carries the JDK 9 Generated annotation from source 9 onwards'() {
        given:
        processingEnv.sourceVersion >> SourceVersion.RELEASE_11

        expect:
        processor.content('com.example') ==
                '@javax.annotation.processing.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")\n' +
                '@org.jspecify.annotations.NullMarked\n' +
                'package com.example;\n'
    }

    def 'content carries the JDK 8 Generated annotation at source 8'() {
        given:
        processingEnv.sourceVersion >> SourceVersion.RELEASE_8

        expect:
        processor.content('com.example') ==
                '@javax.annotation.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")\n' +
                '@org.jspecify.annotations.NullMarked\n' +
                'package com.example;\n'
    }

    def 'jspecifyMissing reports an unresolvable annotation as missing'() {
        when:
        def missing = processor.jspecifyMissing()

        then:
        1 * processingEnv.elementUtils >> elements
        1 * elements.getTypeElement('org.jspecify.annotations.NullMarked') >> null
        0 * _

        expect:
        missing
    }

    def 'jspecifyMissing reports a resolvable annotation as present'() {
        TypeElement nullMarked = Mock()

        when:
        def missing = processor.jspecifyMissing()

        then:
        1 * processingEnv.elementUtils >> elements
        1 * elements.getTypeElement('org.jspecify.annotations.NullMarked') >> nullMarked
        0 * _

        expect:
        !missing
    }

    def 'reportMissingJspecify errors exactly once'() {
        when:
        processor.reportMissingJspecify()
        processor.reportMissingJspecify()

        then:
        1 * processingEnv.messager >> messager
        1 * messager.printMessage(Diagnostic.Kind.ERROR, NullMarkedProcessor.MISSING_JSPECIFY)
        0 * _
    }

    def 'warnUnnamedPackage warns against the offending element'() {
        Element type = Mock()

        when:
        processor.warnUnnamedPackage(type)

        then:
        1 * processingEnv.messager >> messager
        1 * messager.printMessage(Diagnostic.Kind.WARNING, NullMarkedProcessor.UNNAMED_PACKAGE, type)
        0 * _
    }
}
