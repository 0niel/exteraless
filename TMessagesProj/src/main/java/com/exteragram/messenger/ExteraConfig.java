package com.exteragram.messenger;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.icons.BaseIconPacks;

/**
 * Шим {@code com.exteragram.messenger.ExteraConfig} — статика, к которой
 * обращаются плагины оформления.
 *
 * У exteraGram это поля, а не геттеры, но поле не может читать живое значение
 * настройки, поэтому здесь методы. Chaquopy различает вызов и чтение атрибута,
 * так что плагин, написанный под поле, получил бы объект метода вместо значения;
 * форму выравнивает обёртка {@code extera_utils.class_aliases._FieldShapedClass},
 * которой перечислены имена из {@code _FIELD_SHAPED}. Новый метод, который у
 * эталона поле, надо дописывать туда же.
 */
public final class ExteraConfig {

    /** Значения совпадают с exteraGram: 0 — стоковые, 1 — Solar, 2 — Remix. */
    public static final int ICON_PACK_DEFAULT = BaseIconPacks.BASE_DEFAULT;
    public static final int ICON_PACK_SOLAR = BaseIconPacks.BASE_SOLAR;
    public static final int ICON_PACK_REMIX = BaseIconPacks.BASE_REMIX;

    private ExteraConfig() {
    }

    /** Включён ли safe mode движка плагинов. */
    public static boolean pluginsSafeMode() {
        return app.exteraless.plugins.PluginsController.getInstance().isSafeMode();
    }

    /** Текущий набор иконок: одна из констант {@code ICON_PACK_*}. */
    public static int iconPack() {
        return BaseIconPacks.getSelected();
    }

    /**
     * Радиус скругления аватарки для стороны {@code size} в пикселях.
     * Внимание: именно в пикселях, не в dp — как и у exteraGram.
     */
    public static int getAvatarCorners(float size) {
        return AppearanceConfig.INSTANCE.getAvatarCorners(size);
    }

    /**
     * Вибро-отклик внутри приложения. В этом форке отдельного тумблера нет,
     * поэтому отклик считается разрешённым — плагин сам решает, вибрировать ли.
     */
    public static boolean inAppVibration() {
        return true;
    }
}
