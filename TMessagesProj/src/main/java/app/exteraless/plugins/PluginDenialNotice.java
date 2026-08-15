package app.exteraless.plugins;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.exteraless.plugins.ui.PluginPermissionsActivity;

/**
 * Сообщить пользователю, что плагину чего-то не хватило.
 *
 * Отказ не роняет плагин: команда просто ничего не делает, и со стороны это
 * неотличимо от поломки — «нажал, и тишина». Здесь появляется единственный
 * ответ на вопрос «почему не работает»: короткое сообщение с именем плагина и
 * тем, чего ему не дали.
 *
 * Показываем не чаще одного раза на пару (плагин, разрешение) за запуск: хуки
 * срабатывают часто, и повторять одно и то же на каждый вызов — верный способ
 * сделать сообщение фоновым шумом, который перестают читать.
 */
public final class PluginDenialNotice {

    private static final Set<String> SHOWN = ConcurrentHashMap.newKeySet();

    private PluginDenialNotice() {
    }

    public static void note(String pluginId, String permission) {
        if (TextUtils.isEmpty(pluginId) || TextUtils.isEmpty(permission)) {
            return;
        }
        // ui выдан всегда; отказ по нему означал бы, что плагина нет в реестре —
        // сообщать пользователю тут нечего.
        if (PluginPermissions.UI.equals(permission)) {
            return;
        }
        if (!SHOWN.add(pluginId + "|" + permission)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> show(pluginId, permission));
    }

    private static void show(String pluginId, String permission) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) {
            // Приложение не на экране — сообщение всё равно некому прочитать,
            // а показывать его при следующем открытии уже поздно и непонятно.
            return;
        }
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        String name = plugin != null ? plugin.getDisplayName() : pluginId;
        CharSequence text = LocaleController.formatString(R.string.PluginDeniedNotice,
                name, PluginPermissionsActivity.titleOf(permission));
        BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.error, text,
                        LocaleController.getString(R.string.PluginDeniedOpen),
                        () -> fragment.presentFragment(new PluginPermissionsActivity(pluginId)))
                .show();
    }

    /** Забыть показанное (удаление плагина, смена разрешений). */
    public static void reset(String pluginId) {
        if (TextUtils.isEmpty(pluginId)) {
            return;
        }
        SHOWN.removeIf(mark -> mark.startsWith(pluginId + "|"));
    }
}
