package com.exteragram.messenger.plugins;

/**
 * Шим строковых констант exteraGram. Значения — те же, что в
 * {@code com.exteragram.messenger.plugins.PluginsConstants} 12.9.0, а не наши
 * внутренние: плагин сравнивает их с тем, что приходит из SDK.
 */
public final class PluginsConstants {

    public static final String PYTHON = "python";

    private PluginsConstants() {
    }

    public static final class MenuItemTypes {
        public static final String MESSAGE_CONTEXT_MENU = "message_context_menu";
        public static final String DRAWER_MENU = "drawer_menu";
        public static final String MAIN_MENU = "main_menu";
        public static final String CHAT_ACTION_MENU = "chat_action_menu";
        public static final String PROFILE_ACTION_MENU = "profile_action_menu";

        private MenuItemTypes() {
        }
    }

    public static final class Strategy {
        public static final String MODIFY = "MODIFY";
        public static final String CANCEL = "CANCEL";
        public static final String DEFAULT = "DEFAULT";
        public static final String MODIFY_FINAL = "MODIFY_FINAL";

        private Strategy() {
        }
    }

    /** Самая используемая вложенность в каталоге: 17 обращений у одного плагина. */
    public static final class Xposed {
        public static final String REPLACE_HOOKED_METHOD = "replace_hooked_method";
        public static final String BEFORE_HOOKED_METHOD = "before_hooked_method";
        public static final String AFTER_HOOKED_METHOD = "after_hooked_method";
        public static final String HOOK_FILTERS = "__hook_filters__";

        private Xposed() {
        }
    }
}
