package app.exteraless.plugins;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.exteraless.plugins.ui.PluginPermissionsActivity;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import java.util.LinkedHashMap;
import java.util.Map;

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
    /**
     * Каналы, которые считаем известным источником плагинов.
     *
     * У exteraGram этот список приезжает с сервера (BadgesController:109,
     * CachedRemoteSet "trusted_plugins" со значением по умолчанию 2562664432 —
     * @exteraPlugins). Сервера у нас нет, поэтому остаётся то же значение по
     * умолчанию, зашитое в код. Флаг ничего не разрешает: он лишь пишет в
     * диалоге, откуда файл — устанавливается плагин всё равно в изоляции.
     */
    private static final long[] KNOWN_PLUGIN_CHANNELS = {2562664432L};

    private static boolean isKnownSource(MessageObject message) {
        if (message == null) {
            return false;
        }
        long dialogId = message.getDialogId();
        long channelId = dialogId < 0 ? -dialogId : dialogId;
        for (long known : KNOWN_PLUGIN_CHANNELS) {
            if (channelId == known) {
                return true;
            }
        }
        return false;
    }

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
        final boolean known = isKnownSource(message);
        AndroidUtilities.runOnUIThread(() -> confirmAndInstall(activity, file, known));
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
        // file:// ContentResolver не открывает — интент из файлового менеджера
        // приходил именно так и упирался в «не удалось прочитать файл».
        // Такой путь читаем напрямую, если он доступен процессу.
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            File direct = new File(uri.getPath());
            if (direct.canRead()) {
                try (InputStream in = new java.io.FileInputStream(direct);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    return target;
                } catch (Throwable t) {
                    FileLog.e("PluginInstallHelper: cannot read " + direct, t);
                    return null;
                }
            }
        }
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
     * (имя, версия, автор) и что плагин просит уметь. Метаданные читаются
     * AST-разбором, без выполнения кода плагина, — до подтверждения ничего
     * чужого не запускается.
     *
     * Нажатие «Установить» и есть согласие на перечисленное: оно пишется
     * в prefs (PluginPermissions.setGranted) прежде установки. Отказ —
     * установки нет, ничего не записывается.
     */
    /**
     * Показать согласие и установить. Публичный, потому что через него обязаны
     * идти ВСЕ пути установки: тап по файлу в чате, внешний интент и выбор
     * файла на экране плагинов. Установка мимо этого метода означала бы выдачу
     * разрешений без ведома пользователя.
     */
    public static void confirmAndInstall(Activity activity, File file) {
        confirmAndInstall(activity, file, false);
    }

    /**
     * @param knownSource файл пришёл из известного канала плагинов. Показывается
     *                    в диалоге и ни на что больше не влияет.
     */
    public static void confirmAndInstall(Activity activity, File file, boolean knownSource) {
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
                                confirmAndInstall(activity, file, knownSource);
                            })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        }
        controller.readMetadataAsync(file, plugin -> {
            // Разбор исходника — на фоновом потоке: это чтение файла и AST.
            final Map<String, List<String>> capabilities = scanCapabilities(file);
            AndroidUtilities.runOnUIThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                showConsentDialog(activity, file, plugin, knownSource, capabilities);
            });
        });
    }

    /**
     * Диалог установки с галочками.
     *
     * Раньше он перечислял `__permissions__`, но их объявляет меньшинство: из
     * 512 плагинов двух каталогов большинство молчит, и человек видел либо
     * пустой список, либо «получит всё». Теперь показывается то, что нашлось в
     * исходнике, — и каждая находка это галочка, которую можно снять.
     *
     * Галочки по умолчанию сняты. Плагин ставится ровно с тем, что отметили:
     * ничего не отметили — уровень «Изоляция», отметили что-то — «Ограниченный»,
     * отметили переписывание кода — «Доверенный».
     */
    private static void showConsentDialog(Activity activity, File file, Plugin plugin,
                                          boolean knownSource, Map<String, List<String>> capabilities) {
        final List<String> permissions = orderedPermissions(capabilities);
        final List<CheckBoxCell> cells = new ArrayList<>();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView summary = new TextView(activity);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        summary.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        summary.setText(buildConfirmMessage(plugin, knownSource, permissions.isEmpty()));
        summary.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(4));
        content.addView(summary);

        for (String permission : permissions) {
            CheckBoxCell cell = new CheckBoxCell(activity, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, null);
            // Улики — то, по чему разбор так решил: человеку видно основание,
            // а не только вывод.
            cell.setText(PluginPermissionsActivity.titleOf(permission),
                    evidenceOf(capabilities, permission), false, false);
            cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));
            cell.setTag(permission);
            cells.add(cell);
            content.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);

        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.PluginsInstallTitle))
                .setView(scroll)
                .setPositiveButton(LocaleController.getString(R.string.PluginsInstallAction),
                        (dialog, which) -> {
                            List<String> granted = new ArrayList<>();
                            for (CheckBoxCell cell : cells) {
                                if (cell.isChecked() && cell.getTag() instanceof String) {
                                    granted.add((String) cell.getTag());
                                }
                            }
                            grantOnConsent(plugin, granted);
                            install(activity, file, plugin != null ? plugin.id : null);
                        })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    /** Разрешения в порядке экрана плагина, чтобы список читался одинаково везде. */
    private static List<String> orderedPermissions(Map<String, List<String>> capabilities) {
        List<String> out = new ArrayList<>();
        if (capabilities == null) {
            return out;
        }
        for (String permission : PluginPermissions.REQUESTABLE) {
            if (capabilities.containsKey(permission)) {
                out.add(permission);
            }
        }
        return out;
    }

    private static String evidenceOf(Map<String, List<String>> capabilities, String permission) {
        List<String> evidence = capabilities == null ? null : capabilities.get(permission);
        if (evidence == null || evidence.isEmpty()) {
            return "";
        }
        // Двух признаков хватает: строка не должна вытеснять сам заголовок.
        return evidence.size() <= 2
                ? TextUtils.join(", ", evidence)
                : TextUtils.join(", ", evidence.subList(0, 2)) + "…";
    }

    /** Статический разбор исходника; пустая карта, если движок молчит. */
    private static Map<String, List<String>> scanCapabilities(File file) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String json = PythonPluginsEngine.getInstance().scanCapabilitiesJson(file.getAbsolutePath());
            if (TextUtils.isEmpty(json)) {
                return result;
            }
            JSONObject parsed = new JSONObject(json);
            for (java.util.Iterator<String> keys = parsed.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!PluginPermissions.isKnown(key)) {
                    continue;  // "error" и всё незнакомое
                }
                JSONArray array = parsed.optJSONArray(key);
                List<String> evidence = new ArrayList<>();
                for (int i = 0; array != null && i < array.length(); i++) {
                    String item = JsonUtils.optStringOrNull(array, i);
                    if (item != null) {
                        evidence.add(item);
                    }
                }
                result.put(key, evidence);
            }
        } catch (Throwable t) {
            FileLog.e("PluginInstallHelper: capability scan failed", t);
        }
        return result;
    }

    /**
     * Текст согласия: кто ставится и что сможет делать.
     *
     * Ключи разрешений человеку ничего не говорят, поэтому перечисляем
     * последствия ({@link PluginPermissionsActivity#titleOf}); строка про hooks
     * идёт отдельным абзацем и красным — обладая ей, плагин может всё
     * перечисленное выше независимо от остальных ключей.
     */
    /**
     * Шапка диалога: откуда файл, кто автор, и что означают галочки ниже.
     *
     * Перечисление разрешений отсюда ушло в сами галочки: список, который
     * нельзя тронуть, человек пролистывает, а отметку — принимает или снимает.
     */
    private static CharSequence buildConfirmMessage(Plugin plugin, boolean knownSource,
                                                    boolean nothingFound) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        // Откуда файл — первым делом: это то, на что человек реально опирается,
        // решая ставить или нет.
        sb.append(LocaleController.getString(knownSource
                ? R.string.PluginsSourceKnown
                : R.string.PluginsSourceUnknown)).append("\n\n");
        if (plugin == null || TextUtils.isEmpty(plugin.id)) {
            // Метаданные не прочитались (файл битый или движок не поднялся):
            // обещать что-либо про его поведение нечестно.
            sb.append(LocaleController.getString(R.string.PluginsInstallUnknownConfirm));
            return sb;
        }
        sb.append(LocaleController.formatString(R.string.PluginsInstallConfirm,
                plugin.getDisplayName(),
                plugin.version != null ? plugin.version : "1.0",
                plugin.author != null ? plugin.author : "—"));
        sb.append("\n\n").append(LocaleController.getString(nothingFound
                ? R.string.PluginsInstallNothingFound
                : R.string.PluginsInstallScanned));
        return sb;
    }

    /**
     * Зафиксировать выбор пользователя: отмеченные разрешения и уровень под них.
     *
     * Запись делается всегда, даже пустая: именно её наличие отличает плагин,
     * поставленный при модели разрешений, от старого, которому иначе достался
     * бы режим совместимости со всеми правами сразу.
     */
    private static void grantOnConsent(Plugin plugin, List<String> granted) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) {
            return;
        }
        PluginPermissions.setGranted(plugin.id, granted);
        final int level;
        if (granted.isEmpty()) {
            level = PluginTrustLevel.ISOLATED;
        } else if (granted.contains(PluginPermissions.HOOKS)) {
            // Хуки живут только на доверенном уровне: там про это и сказано.
            level = PluginTrustLevel.TRUSTED;
        } else {
            level = PluginTrustLevel.GATED;
        }
        PluginTrustLevel.setLevel(plugin.id, level);
    }

    private static void install(Activity activity, File file, String consentedId) {
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
                        // Установка сорвалась — согласие, записанное авансом, ни к чему
                        // не относится. Стираем, но только если плагина и правда нет:
                        // при перезаписи существующего файл мог уже подмениться.
                        if (consentedId != null
                                && PluginsController.getInstance().getPlugin(consentedId) == null) {
                            PluginPermissions.clear(consentedId);
                        }
                        showError(activity, humanError(error, consentedId));
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

    /**
     * Ошибка установки человеческим языком.
     *
     * Плагин, которому не хватило разрешения, падал с текстом вида
     * «PermissionError: plugin 'quotecreate' is not allowed to modify files
     * (/storage/.../cache/quotecreate): missing the 'files' permission» — это
     * сообщение для разработчика, а не для того, кто ставит плагин. Разбираем
     * его обратно в понятное: чего не хватило и что с этим делать.
     *
     * Остальные ошибки оставляем как есть: там текст обычно и есть суть
     * (битый архив, нет метаданных), а прятать её было бы хуже.
     */
    private static CharSequence humanError(CharSequence error, String pluginId) {
        if (error == null) {
            return LocaleController.getString(R.string.PluginsInstallError);
        }
        String text = error.toString();
        if (!text.contains("PermissionError") && !text.contains("missing the")) {
            return error;
        }
        String permission = null;
        for (String candidate : PluginPermissions.ALL) {
            if (text.contains("'" + candidate + "'")) {
                permission = candidate;
                break;
            }
        }
        if (permission == null) {
            return LocaleController.getString(R.string.PluginsInstallDeniedGeneric);
        }
        return LocaleController.formatString(R.string.PluginsInstallDenied,
                PluginPermissionsActivity.titleOf(permission));
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
