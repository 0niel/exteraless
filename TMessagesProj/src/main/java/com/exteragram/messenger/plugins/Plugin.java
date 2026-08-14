package com.exteragram.messenger.plugins;

/**
 * Шим {@code com.exteragram.messenger.plugins.Plugin}.
 *
 * В каталоге этот класс используется исключительно как аннотация типа
 * ({@code Optional[Plugin]}, {@code List[Plugin]}) — его не создают и не
 * проверяют через isinstance. Поэтому достаточно, чтобы имя импортировалось;
 * сквозь API ходят настоящие {@link app.exteraless.plugins.Plugin}, что для
 * Python-аннотаций неразличимо.
 */
public class Plugin extends app.exteraless.plugins.Plugin {
}
