package app.exteraless.plugins;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Установка плагина из файла, открытого снаружи: тап по .plugin в чате, файловый
 * менеджер, «Поделиться».
 *
 * Раньше такого пути не было вовсе — система показывала обычный выбор
 * приложения, и поставить плагин было неоткуда, кроме как через
 * «Установить из файла» в настройках. exteraGram объявляет intent-filter на
 * {@code .plugin} и разбирает интент в IntentsController; здесь то же самое,
 * только точка входа — {@code LaunchActivity.handleIntent}.
 */
public final class PluginInstallHelper {

    /** Расширения, которые движок умеет ставить. */
    private static final String[] EXTENSIONS = {
            PluginsConstants.PLUGIN_EXT,       // .plugin
            PluginsConstants.PLUGIN_EXT_ELYX,  // .elyx
            PluginsConstants.PLUGIN_EXT_EAF,   // .eaf
    };

    private PluginInstallHelper() {
    }

    /** Расширение файла из ссылки: сперва имя из ContentResolver, потом сам путь. */
    private static String extensionOf(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
            // content://-провайдер может не отдавать метаданные — упадём на путь.
        }
        if (TextUtils.isEmpty(name)) {
            name = uri.getLastPathSegment();
        }
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        name = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    /** Похоже ли содержимое ссылки на файл плагина. */
    public static boolean isPluginUri(Context context, Uri uri) {
        return context != null && uri != null && extensionOf(context, uri) != null;
    }

    /**
     * Тап по документу в чате или в общих файлах.
     *
     * Обязан вызываться ДО встроенного просмотрщика: в этом форке
     * {@code MarkdownUtils} регистрирует {@code .plugin} как исходник Python
     * ({@code addLanguage("python", "py", "pyw", "plugin")}), поэтому файл
     * плагина открывался в подсветке кода, и установить его было неоткуда.
     * У exteraGram порядок такой же — {@code isPlugin} проверяется раньше
     * {@code canPreviewDocument} и {@code MarkdownParser.isMarkdown}
     * (SharedMediaLayout.java:7999).
     *
     * @return true, если сообщение содержит плагин и показан диалог установки.
     */
    public static boolean handleMessageTap(Activity activity, MessageObject message) {
        if (activity == null || message == null) {
            return false;
        }
        String name = message.getDocumentName();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        boolean isPlugin = false;
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                isPlugin = true;
                break;
            }
        }
        if (!isPlugin) {
            return false;
        }
        File file = FileLoader.getInstance(UserConfig.selectedAccount)
                .getPathToMessage(message.messageOwner);
        if (file == null || !file.exists() || file.length() == 0) {
            // Ещё не скачан — пусть отработает штатная загрузка.
            return false;
        }
        AndroidUtilities.runOnUIThread(() -> confirmAndInstall(activity, file));
        return true;
    }

    /**
     * Обработать открытие файла плагина.
     *
     * @return true, если ссылка вела на плагин и обработка взята на себя.
     */
    public static boolean handleViewIntent(Activity activity, Uri uri) {
        if (activity == null || uri == null) {
            return false;
        }
        String ext = extensionOf(activity, uri);
        if (ext == null) {
            return false;
        }
        File cached = copyToCache(activity, uri, ext);
        if (cached == null) {
            AndroidUtilities.runOnUIThread(() -> showError(activity,
                    LocaleController.getString(R.string.PluginsInstallReadError)));
            return true;
        }
        AndroidUtilities.runOnUIThread(() -> confirmAndInstall(activity, cached));
        return true;
    }

    private static File copyToCache(Activity activity, Uri uri, String ext) {
        File target = new File(activity.getCacheDir(), "plugin_incoming" + ext);
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return target;
        } catch (Throwable t) {
            FileLog.e("PluginInstallHelper: cannot read " + uri, t);
            return null;
        }
    }

    /**
     * Спросить подтверждение, показав то, что удалось прочитать из метаданных
     * (имя, версия, автор). Метаданные читаются AST-разбором, без выполнения
     * кода плагина, — до подтверждения ничего чужого не запускается.
     */
    private static void confirmAndInstall(Activity activity, File file) {
        PluginsController controller = PluginsController.getInstance();
        if (!controller.isEngineEnabled()) {
            // Не отказываем молча: движок выключен по умолчанию, и пользователю
            // иначе неоткуда узнать, где его включить.
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.PluginsInstallTitle))
                    .setMessage(LocaleController.getString(R.string.PluginsEngineDisabledHint))
                    .setPositiveButton(LocaleController.getString(R.string.PluginsEngineEnableAndInstall),
                            (dialog, which) -> {
                                controller.setEngineEnabled(true);
                                confirmAndInstall(activity, file);
                            })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        }
        controller.readMetadataAsync(file, plugin -> AndroidUtilities.runOnUIThread(() -> {
            if (activity.isFinishing()) {
                return;
            }
            CharSequence message;
            if (plugin == null || TextUtils.isEmpty(plugin.id)) {
                message = LocaleController.getString(R.string.PluginsInstallUnknownConfirm);
            } else {
                message = LocaleController.formatString(R.string.PluginsInstallConfirm,
                        plugin.getDisplayName(),
                        plugin.version != null ? plugin.version : "1.0",
                        plugin.author != null ? plugin.author : "—");
            }
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.PluginsInstallTitle))
                    .setMessage(message)
                    .setPositiveButton(LocaleController.getString(R.string.PluginsInstallAction),
                            (dialog, which) -> install(activity, file))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        }));
    }

    private static void install(Activity activity, File file) {
        AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage(LocaleController.getString(R.string.PluginsInstalling));
        progress.setCanCancel(false);
        progress.show();
        PluginsController.getInstance().installPlugin(file, (ok, error, plugin) ->
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progress.dismiss();
                    } catch (Throwable ignored) {
                    }
                    if (activity.isFinishing()) {
                        return;
                    }
                    if (!ok) {
                        showError(activity, error != null ? error
                                : LocaleController.getString(R.string.PluginsInstallError));
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(LocaleController.getString(R.string.PluginsInstallTitle))
                            .setMessage(LocaleController.formatString(R.string.PluginsInstalled,
                                    plugin != null ? plugin.getDisplayName() : ""))
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                }));
    }

    private static void showError(Activity activity, CharSequence message) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.PluginsInstallError))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }
}
