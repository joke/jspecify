package io.github.joke.jspecify.processor;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.lang.model.SourceVersion.latestSupported;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Generates a {@code package-info.java} carrying {@code @NullMarked} for every package in the
 * compilation that does not already have one.
 *
 * <p>The processor claims {@code "*"} but never consumes anything: {@link #process} always returns
 * {@code false} so every annotation stays available to the rest of the compilation.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
// Local variables carry explicit types because the processor compiles to Java 8, which has no var.
@SuppressWarnings("PMD.UseVarForLocalVariables")
public class NullMarkedProcessor extends AbstractProcessor {

    @VisibleForTesting
    static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";

    @VisibleForTesting
    static final String GENERATED = "javax.annotation.processing.Generated";

    /** The {@code @Generated} annotation available from source 8, where the JDK 9 one does not exist. */
    @VisibleForTesting
    static final String GENERATED_JDK8 = "javax.annotation.Generated";

    /** Value of the {@code @Generated} marker: the processor that wrote the file. */
    @VisibleForTesting
    static final String GENERATOR = NullMarkedProcessor.class.getName();

    @VisibleForTesting
    static final String PACKAGE_INFO = "package-info";

    @VisibleForTesting
    static final String MISSING_JSPECIFY = "JSpecify is not on the compile classpath, so no @NullMarked "
            + "package-info can be generated. Add a compile dependency on org.jspecify:jspecify.";

    @VisibleForTesting
    static final String UNNAMED_PACKAGE = "Types in the default package cannot be null-marked: a "
            + "package-info.java cannot exist for the unnamed package. Move this type into a named package.";

    /** Packages this processor has already written, so a later round never writes them twice. */
    private final Set<String> emitted = new HashSet<>();

    private boolean missingJspecifyReported;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            generate(roundEnv);
        }
        return false;
    }

    /**
     * Emits eagerly, in the round a package is discovered. Deferring to the final round would make
     * javac warn that the file will not be subject to annotation processing, which is fatal under
     * {@code -Werror}.
     */
    @VisibleForTesting
    void generate(final RoundEnvironment roundEnv) {
        if (jspecifyMissing()) {
            reportMissingJspecify();
            return;
        }
        final Set<String> existing = existingPackageInfos(roundEnv);
        candidates(roundEnv).forEach((pkg, originating) -> writeUnlessCovered(pkg, originating, existing));
    }

    @VisibleForTesting
    void writeUnlessCovered(final String pkg, final List<Element> originating, final Set<String> existing) {
        if (!covered(pkg, existing)) {
            write(pkg, originating);
        }
    }

    /** Root types grouped by the package that will need a {@code package-info}. */
    @VisibleForTesting
    Map<String, List<Element>> candidates(final RoundEnvironment roundEnv) {
        final List<Element> roots = typeRoots(roundEnv);
        roots.stream().filter(this::inUnnamedPackage).forEach(this::warnUnnamedPackage);
        return roots.stream()
                .filter(root -> !inUnnamedPackage(root))
                .filter(root -> !inNullMarkedModule(root))
                .collect(groupingBy(this::packageName, LinkedHashMap::new, toList()));
    }

    /** Root elements that are types; a {@link PackageElement} root is an existing package-info. */
    @VisibleForTesting
    List<Element> typeRoots(final RoundEnvironment roundEnv) {
        return roundEnv.getRootElements().stream()
                .filter(root -> root.getKind() != ElementKind.PACKAGE)
                .collect(toList());
    }

    @VisibleForTesting
    PackageElement packageOf(final Element root) {
        return processingEnv.getElementUtils().getPackageOf(root);
    }

    @VisibleForTesting
    String packageName(final Element root) {
        return packageOf(root).getQualifiedName().toString();
    }

    @VisibleForTesting
    boolean inUnnamedPackage(final Element root) {
        return packageOf(root).isUnnamed();
    }

    @VisibleForTesting
    boolean inNullMarkedModule(final Element root) {
        return moduleIsNullMarked(packageOf(root));
    }

    /**
     * Packages whose {@code package-info.java} is part of this compilation. javac surfaces those as
     * {@link PackageElement} root elements, which is also how this processor's own output from an
     * earlier round excludes itself.
     */
    @VisibleForTesting
    Set<String> existingPackageInfos(final RoundEnvironment roundEnv) {
        return roundEnv.getRootElements().stream()
                .filter(root -> root.getKind() == ElementKind.PACKAGE)
                .map(root -> ((PackageElement) root).getQualifiedName().toString())
                .collect(toSet());
    }

    /**
     * A package is covered when its {@code package-info} is in this compilation, was written by an
     * earlier round, or is already on the classpath — the last of which is what stops a test
     * compilation from duplicating the one its main source set already produced.
     */
    @VisibleForTesting
    boolean covered(final String pkg, final Set<String> existing) {
        return existing.contains(pkg) || emitted.contains(pkg) || annotatedOnClasspath(pkg);
    }

    /**
     * Detects a {@code package-info} that reached this compilation as a class file. It cannot be
     * looked up with {@code getTypeElement(pkg + ".package-info")} — {@code package-info} is not a
     * valid identifier, so that never resolves. What is observable is the effect: javac only emits a
     * {@code package-info.class} when the package declaration carries annotations, so a package
     * arriving with annotations already attached is one that has a {@code package-info} behind it.
     */
    @VisibleForTesting
    boolean annotatedOnClasspath(final String pkg) {
        final PackageElement existing = processingEnv.getElementUtils().getPackageElement(pkg);
        return existing != null && !existing.getAnnotationMirrors().isEmpty();
    }

    /**
     * A module-level {@code @NullMarked} already covers every package in it. On JDK 9 and later a
     * package's enclosing element is its module; on JDK 8 there is none. The lookup is typed as
     * {@link Element} rather than {@code ModuleElement}, which {@code --release 8} cannot see.
     */
    @VisibleForTesting
    boolean moduleIsNullMarked(final PackageElement pkg) {
        final Element module = pkg.getEnclosingElement();
        return module != null && isNullMarked(module);
    }

    @VisibleForTesting
    boolean isNullMarked(final Element module) {
        return module.getAnnotationMirrors().stream().anyMatch(this::isNullMarkedAnnotation);
    }

    /**
     * Compares the annotation's qualified name rather than calling {@code
     * getAnnotation(NullMarked.class)}, so JSpecify is not needed on this processor's own runtime
     * classpath.
     */
    @VisibleForTesting
    boolean isNullMarkedAnnotation(final AnnotationMirror mirror) {
        final Element annotation = mirror.getAnnotationType().asElement();
        return annotation instanceof TypeElement
                && NULL_MARKED.contentEquals(((TypeElement) annotation).getQualifiedName());
    }

    @VisibleForTesting
    void write(final String pkg, final List<Element> originating) {
        try {
            final JavaFileObject file = processingEnv
                    .getFiler()
                    .createSourceFile(
                            pkg + "." + PACKAGE_INFO, originating.stream().toArray(Element[]::new));
            try (Writer writer = file.openWriter()) {
                writer.write(content(pkg));
            }
            emitted.add(pkg);
        } catch (final FilerException alreadyCreated) {
            emitted.add(pkg);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to generate " + pkg + "." + PACKAGE_INFO, e);
        }
    }

    /**
     * Provenance is carried by an {@code @Generated} annotation rather than a comment, so tooling can
     * read it. The annotation has {@code SOURCE} retention, so it does not reach the
     * {@code package-info.class} and cannot interact with NullAway's {@code
     * treatGeneratedAsUnannotated}, which reads the annotation off the outermost class of the code
     * being analysed. Fully qualified and ASCII only, because there are no imports here and the
     * encoding of the {@link Writer} is the consuming compilation's, not ours.
     */
    @VisibleForTesting
    String content(final String pkg) {
        return "@" + generatedAnnotation() + "(\"" + GENERATOR + "\")\n" + "@" + NULL_MARKED + "\n" + "package " + pkg
                + ";\n";
    }

    /**
     * The {@code @Generated} annotation the consuming compilation can resolve: the JDK 9 one from source
     * 9 onwards, and the JDK 8 one for source 8 and below.
     */
    @VisibleForTesting
    String generatedAnnotation() {
        return processingEnv.getSourceVersion().compareTo(RELEASE_8) > 0 ? GENERATED : GENERATED_JDK8;
    }

    @VisibleForTesting
    boolean jspecifyMissing() {
        return processingEnv.getElementUtils().getTypeElement(NULL_MARKED) == null;
    }

    @VisibleForTesting
    void reportMissingJspecify() {
        if (!missingJspecifyReported) {
            missingJspecifyReported = true;
            processingEnv.getMessager().printMessage(ERROR, MISSING_JSPECIFY);
        }
    }

    @VisibleForTesting
    void warnUnnamedPackage(final Element root) {
        processingEnv.getMessager().printMessage(WARNING, UNNAMED_PACKAGE, root);
    }
}
