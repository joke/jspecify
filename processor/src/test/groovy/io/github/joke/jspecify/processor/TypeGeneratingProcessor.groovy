package io.github.joke.jspecify.processor

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * Stands in for any other processor in the build: it introduces a type in a brand new package
 * during round 0, so that package only becomes visible to {@link NullMarkedProcessor} in round 1.
 */
@SupportedAnnotationTypes('*')
class TypeGeneratingProcessor extends AbstractProcessor {

    private boolean generated

    @Override
    SourceVersion getSupportedSourceVersion() {
        SourceVersion.latestSupported()
    }

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!generated && !roundEnv.processingOver()) {
            generated = true
            processingEnv.filer.createSourceFile('com.generated.Thing').openWriter().withCloseable {
                it.write('package com.generated;\npublic class Thing {}\n')
            }
        }
        false
    }
}
