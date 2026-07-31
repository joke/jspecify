package io.github.joke.jspecify.processor

import javax.lang.model.element.Name

/**
 * {@link Name} is a {@link CharSequence} whose {@code toString()} and {@code contentEquals} the
 * processor relies on. Mockito cannot stub {@code toString()}, so specs use a real implementation
 * backed by a String rather than a mock.
 */
final class Names {

    private Names() {
    }

    static Name of(String value) {
        new Name() {

            @Override
            int length() {
                value.length()
            }

            @Override
            char charAt(int index) {
                value.charAt(index)
            }

            @Override
            CharSequence subSequence(int start, int end) {
                value.subSequence(start, end)
            }

            @Override
            boolean contentEquals(CharSequence other) {
                value.contentEquals(other)
            }

            @Override
            String toString() {
                value
            }
        }
    }
}
