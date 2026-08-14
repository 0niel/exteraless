package app.exteraless.drawer;

/**
 * Реестр пунктов главного меню: один и тот же список используется меню «⋮»
 * в {@code DialogsActivity} и боковой шторкой {@link DrawerContainer}.
 *
 * там это
 * kotlin-enum, значения id хранятся в настройках, поэтому менять их нельзя.
 *
 * Из exteraGram не переносятся {@code FEED(106)} и {@code PLUGINS(102)}: фида и движка
 * плагинов в форке нет. Их id зарезервированы — не переиспользовать.
 */
public enum MainMenuItem {

    /** Разделитель между группами пунктов, а не сам пункт. */
    DIVIDER(-1),
    PROFILE(18),
    ARCHIVE(14),
    /** Разворачивается в список attach-menu-ботов, у которых {@code show_in_side_menu}. */
    BOTS(105),
    NEW_GROUP(2),
    CONTACTS(6),
    NEW_CHANNEL(3),
    CALLS(10),
    SAVED(11),
    SETTINGS(8),
    BROWSER(101),
    QR(17);

    private final int id;

    MainMenuItem(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /** exteraGram: {@code MainMenuItem$Companion.getById} — линейный поиск по значениям. */
    public static MainMenuItem getById(int id) {
        for (MainMenuItem item : values()) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }
}
