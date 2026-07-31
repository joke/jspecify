package io.github.joke.jspecify.processor

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * Records whether it was still handed its annotation. If {@link NullMarkedProcessor} ever claimed
 * {@code "*"} by returning {@code true}, this processor would see nothing.
 */
@SupportedAnnotationTypes('java.lang.Deprecated')
class AnnotationObservingProcessor extends AbstractProcessor {

    boolean sawAnnotatedElements

    @Override
    SourceVersion getSupportedSourceVersion() {
        SourceVersion.latestSupported()
    }

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!annotations.empty) {
            sawAnnotatedElements = true
        }
        false
    }
}
