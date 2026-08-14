package org.telegram.ui.Components.blur3;

import app.exteraless.appearance.AppearanceConfig;

/**
 * Стиль контура «стеклянных» поверхностей.
 * GLARE(0), SOLID(1), HIDDEN(2);
 * порядок значений совпадает с {@code AppearanceConfig.glassOutlineStyle}.
 */
public enum GlassOutlineStyle {
    /** Блик: тонкая светлая линия сверху и тёмная снизу — как было до появления настройки. */
    GLARE,
    /** Сплошной контур цветом разделителя, тень выключена. */
    SOLID,
    /** Контура и тени нет. */
    HIDDEN;

    private static final GlassOutlineStyle[] VALUES = values();

    /** Текущий стиль из настроек; неизвестное значение трактуется как {@link #GLARE}. */
    public static GlassOutlineStyle current() {
        final int index = AppearanceConfig.glassOutlineStyle();
        if (index < 0 || index >= VALUES.length) {
            return GLARE;
        }
        return VALUES[index];
    }
}
