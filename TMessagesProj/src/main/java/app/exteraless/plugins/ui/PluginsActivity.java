package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginInstallHelper;
import app.exteraless.plugins.PluginsController;
import app.exteraless.plugins.PythonPluginsEngine;
import app.exteraless.plugins.PluginsWatchdog;

/**
 * Экран «Plugins». Порт
 * {@code com/exteragram/messenger/plugins/ui/PluginsActivity.java}.
 *
 * Устройство повторяет exteraGram: в шапке — поиск и кнопка (i), ведущая на
 * {@link PluginsInfoActivity}; в списке — крупный переключатель движка
 * ({@code UItem.asRippleCheck}) и сами плагины, а при пустом списке —
 * подсказка со ссылкой на канал. Настройки движка (developer/safe/compatibility)
 * живут на втором экране, а не свалены сюда: раньше всё было одним списком, и
 * это ощутимо расходилось с оригиналом.
 */
public class PluginsActivity extends BaseFragment {

    private static final int MENU_SEARCH = 0;
    private static final int MENU_INFO = 1;

    private static final int ID_ENGINE_TOGGLE = -1;
    /** id строк плагинов начинаются отсюда — не пересекаются со служебными. */
    private static final int ID_PLUGIN_BASE = 1000;

    private static final int REQUEST_CODE_PICK_PLUGIN = 9781;

    private UniversalRecyclerView listView;
    private final List<Plugin> plugins = new ArrayList<>();
    /** Вьюхи карточек по id плагина: см. {@link #cellFor}. */
    private final java.util.HashMap<String, PluginCell> cells = new java.util.HashMap<>();
    private String searchQuery;

    @Override
    public View createView(Context context) {
        // Пересоздание вьюхи фрагмента бывает при смене темы: карточки забрали
        // цвета при постройке, поэтому старые больше не годятся.
        cells.clear();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OpenExteraPlugins));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_INFO) {
                    presentFragment(new PluginsInfoActivity());
                }
            }
        });

        org.telegram.ui.ActionBar.ActionBarMenuItem search =
                actionBar.createMenu().addItem(MENU_SEARCH, R.drawable.ic_ab_search_solar)
                        .setIsSearchField(true)
                        .setActionBarMenuItemSearchListener(
                                new org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                                    @Override
                                    public void onSearchCollapse() {
                                        searchQuery = null;
                                        update();
                                    }

                                    @Override
                                    public void onTextChanged(android.widget.EditText editText) {
                                        searchQuery = editText.getText().toString();
                                        update();
                                    }
                                });
        search.setSearchFieldHint(getString(R.string.Search));
        actionBar.createMenu().addItem(MENU_INFO, R.drawable.msg_info);

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick,
                this::onItemLongClick);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        fragmentView = contentView;
        return fragmentView;
    }

    // ---------- список ----------

    private void reloadPlugins() {
        PluginsController controller = PluginsController.getInstance();
        if (controller.isEngineEnabled()) {
            // Движок стартует асинхронно; rescan сам выйдет, если он ещё не поднялся.
            controller.rescanPlugins();
        }
        plugins.clear();
        plugins.addAll(controller.getPlugins());
        plugins.sort((a, b) -> {
            // Закреплённые — наверх: список плагинов растёт, и нужные иначе
            // тонут среди остальных по алфавиту.
            boolean pinnedA = controller.isPluginPinned(a.id);
            boolean pinnedB = controller.isPluginPinned(b.id);
            if (pinnedA != pinnedB) {
                return pinnedA ? -1 : 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.getDisplayName(), b.getDisplayName());
        });
    }

    private List<Plugin> visiblePlugins() {
        if (TextUtils.isEmpty(searchQuery)) {
            return plugins;
        }
        String query = searchQuery.toLowerCase(Locale.ROOT);
        List<Plugin> filtered = new ArrayList<>();
        for (Plugin plugin : plugins) {
            if (plugin.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                    || (plugin.id != null && plugin.id.toLowerCase(Locale.ROOT).contains(query))) {
                filtered.add(plugin);
            }
        }
        return filtered;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        reloadPlugins();
        PluginsController controller = PluginsController.getInstance();

        items.add(UItem.asRippleCheck(ID_ENGINE_TOGGLE, getString(R.string.EnablePluginsEngine))
                .setChecked(controller.isEngineEnabled()));
        items.add(UItem.asSpace(dp(8)));

        List<Plugin> visible = visiblePlugins();
        if (visible.isEmpty()) {
            items.add(UItem.asFullscreenCustom(createEmptyView(), dp(74), true).setTransparent(true));
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            Plugin plugin = visible.get(i);
            PluginCell cell = cellFor(plugin);
            // Прозрачным: фон карточка рисует сама, иначе поверх неё ложится
            // ещё и плашка секции — с другим скруглением.
            items.add(UItem.asCustom(ID_PLUGIN_BASE + i, cell).setTransparent(true));
        }
    }

    /**
     * Одна и та же вьюха на плагин между пересборками списка.
     *
     * UItem считает элементы разными, если у них разные view (UItem.itemEquals:
     * {@code view == item.view}), — на новую вьюху каждый раз DiffUtil отвечал
     * «строку удалили и вставили другую», и RecyclerView проигрывал анимацию
     * удаления с вставкой. Со стороны это выглядело как «карточка дёрнулась по
     * высоте и вернулась» на каждое действие: вход на экран, включение плагина.
     */
    private PluginCell cellFor(Plugin plugin) {
        PluginCell cell = cells.get(plugin.id);
        if (cell == null) {
            cell = new PluginCell(getContext());
            cell.setDelegate(cellDelegate);
            cells.put(plugin.id, cell);
        }
        cell.setPlugin(plugin, PluginsController.getInstance().isCompactView());
        return cell;
    }

    private View createEmptyView() {
        Context context = getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);

        android.widget.TextView emoji = new android.widget.TextView(context);
        emoji.setText("📁");
        emoji.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 48);
        emoji.setGravity(Gravity.CENTER);
        layout.addView(emoji, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 12));

        LinkSpanDrawable.LinksTextView hint = new LinkSpanDrawable.LinksTextView(context);
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        hint.setText(TextUtils.isEmpty(searchQuery)
                ? withUsernameLink(getString(R.string.PluginsEmptyHint))
                : getString(R.string.PluginsNotFound));
        layout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
        return layout;
    }

    /**
     * Делает @username в подсказке кликабельной ссылкой на канал.
     * exteraGram зовёт свой LocaleUtils.formatWithUsernames; у нас такого хелпера
     * нет, а тянуть его целиком ради одной строки незачем.
     */
    private CharSequence withUsernameLink(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Matcher matcher = Pattern.compile("@([A-Za-z][A-Za-z0-9_]{3,31})").matcher(text);
        while (matcher.find()) {
            final String username = matcher.group(1);
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    Browser.openUrl(getParentActivity(), "https://t.me/" + username);
                }

                @Override
                public void updateDrawState(android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }


    // ---------- действия карточки ----------

    /**
     * Кнопки под описанием плагина. Раньше всё это жило в меню по долгому
     * нажатию: пункт, о существовании которого нельзя догадаться.
     */
    private final PluginCell.Delegate cellDelegate = new PluginCell.Delegate() {
        @Override
        public void onToggle(Plugin plugin) {
            togglePlugin(plugin);
        }

        @Override
        public void onShare(Plugin plugin) {
            sharePlugin(plugin);
        }

        @Override
        public void onPin(Plugin plugin) {
            PluginsController controller = PluginsController.getInstance();
            boolean pinned = !controller.isPluginPinned(plugin.id);
            controller.setPluginPinned(plugin.id, pinned);
            update();
            if (getContext() != null) {
                org.telegram.ui.Components.BulletinFactory.of(PluginsActivity.this)
                        .createSimpleBulletin(pinned ? R.raw.ic_pin : R.raw.ic_unpin,
                                getString(pinned ? R.string.PluginsPinned : R.string.PluginsUnpinned))
                        .show();
            }
        }

        @Override
        public void onSettings(Plugin plugin) {
            presentFragment(PluginSettingsActivity.newInstance(plugin.id));
        }

        @Override
        public void onPermissions(Plugin plugin) {
            presentFragment(new PluginPermissionsActivity(plugin.id));
        }

        @Override
        public void onDelete(Plugin plugin) {
            showDeleteDialog(plugin);
        }
    };

    /**
     * Отдать файл плагина наружу.
     *
     * Копируем в кэш, а не отдаём из filesDir/plugins: FileProvider наружу
     * этот каталог не публикует, а расширять ему видимость ради «поделиться»
     * значит открыть чужим приложениям всю папку плагинов.
     */
    private void sharePlugin(Plugin plugin) {
        Activity activity = getParentActivity();
        if (activity == null || plugin == null || plugin.path == null) {
            return;
        }
        try {
            File source = new File(plugin.path);
            if (!source.exists()) {
                return;
            }
            File dir = new File(activity.getCacheDir(), "share");
            dir.mkdirs();
            String ext = source.getName().contains(".")
                    ? source.getName().substring(source.getName().lastIndexOf('.'))
                    : ".plugin";
            File copy = new File(dir, plugin.id + ext);
            try (InputStream in = new java.io.FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(copy)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(activity,
                    org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", copy);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent,
                    getString(R.string.PluginsShare)));
        } catch (Exception e) {
            FileLog.e("PluginsActivity: cannot share plugin", e);
        }
    }

    /**
     * Включить или выключить плагин.
     *
     * setPluginEnabled возвращает «получилось ли», а не новое состояние,
     * поэтому спрашиваем сам плагин: при неудачной загрузке он останется
     * выключенным, и тумблер должен показать именно это.
     */
    private void togglePlugin(Plugin plugin) {
        PluginsController controller = PluginsController.getInstance();
        if (!controller.isEngineEnabled()) {
            return;
        }
        controller.setPluginEnabled(plugin.id, !plugin.enabled);
        Plugin updated = controller.getPlugin(plugin.id);
        boolean enabled = updated != null && updated.enabled;
        update();
        if (enabled && updated.loadError != null) {
            // Плагин уже падал: покажем, на чём именно, иначе включение
            // выглядит как «щёлкнул и ничего».
            showPluginInfo(updated);
        }
    }

    private void update() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    // ---------- клики ----------

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        PluginsController controller = PluginsController.getInstance();
        if (item.id == ID_ENGINE_TOGGLE) {
            boolean enabling = !controller.isEngineEnabled();
            controller.setEngineEnabled(enabling);
            update();
            if (enabling) {
                // Python поднимается асинхронно (около 240 мс на Pixel 7), а
                // rescanPlugins до его старта выходит рано. Без обновления по
                // готовности пользователь видит пустой список при уже лежащих
                // плагинах, и помогает только выйти и вернуться.
                PythonPluginsEngine.getInstance().ensureStarted(
                        org.telegram.messenger.ApplicationLoader.applicationContext,
                        ok -> AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() != null) {
                                update();
                            }
                        }));
            }
            return;
        }
        // По карточке кликов не ждём: у неё свои кнопки и свой тумблер.
        // Когда здесь стояло переключение, один тап по тумблеру доходил и до
        // него, и до строки списка — плагин включался и тут же выключался
        // обратно, а в prefs оставалось false при уже загруженном модуле.
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        Plugin plugin = pluginOf(item);
        if (plugin == null) {
            return false;
        }
        showPluginMenu(plugin);
        return true;
    }

    private Plugin pluginOf(UItem item) {
        int index = item.id - ID_PLUGIN_BASE;
        List<Plugin> visible = visiblePlugins();
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    // ---------- диалоги ----------

    private void showPluginInfo(Plugin plugin) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        if (!TextUtils.isEmpty(plugin.description)) {
            message.append(plugin.description).append("\n\n");
        }
        message.append(plugin.getSubtitle());
        if (plugin.requirements != null && !plugin.requirements.isEmpty()) {
            message.append('\n').append(getString(R.string.PluginsInfoRequirements))
                    .append(": ").append(TextUtils.join(", ", plugin.requirements));
        }
        if (plugin.loadError != null) {
            message.append('\n').append(plugin.loadError);
        }
        showDialog(new AlertDialog.Builder(activity)
                .setTitle(plugin.getDisplayName())
                .setMessage(message.toString())
                .setPositiveButton(getString(R.string.OK), null)
                .create());
    }

    private void showPluginMenu(Plugin plugin) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        PluginsController controller = PluginsController.getInstance();
        ArrayList<CharSequence> labels = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        if (plugin.hasSettings && plugin.loaded) {
            labels.add(getString(R.string.PluginsMenuOpenSettings));
            actions.add(0);
        }
        if (controller.isDeveloperMode()) {
            labels.add(getString(R.string.PluginsMenuReload));
            actions.add(1);
        }
        // Разрешения — рядом с настройками плагина: это второе, что о плагине
        // хотят узнать после того, что он умеет настраивать.
        labels.add(getString(R.string.PluginPermissions));
        actions.add(4);
        labels.add(getString(R.string.PluginsMenuInfo));
        actions.add(5);
        labels.add(getString(R.string.PluginsMenuCopyId));
        actions.add(2);
        labels.add(getString(R.string.PluginsMenuDelete));
        actions.add(3);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(plugin.getDisplayName());
        builder.setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
            int action = actions.get(which);
            if (action == 0) {
                presentFragment(PluginSettingsActivity.newInstance(plugin.id));
            } else if (action == 1) {
                controller.reloadPlugin(plugin.id);
                update();
            } else if (action == 2) {
                AndroidUtilities.addToClipboard(plugin.id);
            } else if (action == 3) {
                showDeleteDialog(plugin);
            } else if (action == 4) {
                presentFragment(new PluginPermissionsActivity(plugin.id));
            } else if (action == 5) {
                showPluginInfo(plugin);
            }
        });
        showDialog(builder.create());
    }

    private void showDeleteDialog(Plugin plugin) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(R.string.PluginsMenuDelete));
        builder.setMessage(LocaleController.formatString(R.string.PluginsDeleteConfirm,
                plugin.getDisplayName()));
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            PluginsController.getInstance().uninstallPlugin(plugin.id);
            cells.remove(plugin.id);
            update();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    // ---------- установка из файла ----------

    /** Зовётся и отсюда, и с экрана движка: пункт «Установить из файла» живёт там. */
    static void openPluginPicker(BaseFragment fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            fragment.startActivityForResult(intent, REQUEST_CODE_PICK_PLUGIN);
        } catch (Exception e) {
            FileLog.e("PluginsActivity: no document picker", e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_PICK_PLUGIN || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        installFromUri(data.getData());
    }

    /** content:// из системного пикера копируем в кэш — движку нужен обычный читаемый файл. */
    private void installFromUri(Uri uri) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        // Расширение обязано пережить копирование: движок по нему отличает
        // .elyx/.eaf (ZIP-архивы) от обычного .py-модуля.
        String name = resolveFileName(activity, uri);
        String ext = ".py";
        for (String candidate : new String[]{".elyx", ".eaf", ".plugin", ".py"}) {
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(candidate)) {
                ext = candidate;
                break;
            }
        }
        File tmp = new File(activity.getCacheDir(), "plugin_upload" + ext);
        boolean copied = false;
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while (in != null && (read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            copied = true;
        } catch (Exception e) {
            FileLog.e("PluginsActivity: cannot read picked file", e);
        }
        if (!copied) {
            showDialog(new AlertDialog.Builder(activity)
                    .setTitle(getString(R.string.PluginsInstallError))
                    .setMessage(getString(R.string.PluginsInstallReadError))
                    .setPositiveButton(getString(R.string.OK), null)
                    .create());
            return;
        }
        // Через диалог согласия, а не installPlugin напрямую: иначе выбор файла
        // на этом экране выдавал бы плагину все объявленные разрешения молча,
        // в обход единственного места, где пользователь их видит.
        PluginInstallHelper.confirmAndInstall(activity, tmp);
    }

    /** Имя файла за content://-ссылкой; нужно только ради расширения. */
    private static String resolveFileName(Activity activity, Uri uri) {
        try (android.database.Cursor cursor = activity.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception e) {
            FileLog.e("PluginsActivity: cannot resolve file name", e);
        }
        return uri.getLastPathSegment();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Однократное уведомление о плагине, отключённом watchdog'ом после падений.
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        String crashed = watchdog != null ? watchdog.consumeCrashedPlugin() : null;
        if (crashed != null && getParentActivity() != null) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.PluginsCrashedTitle))
                    .setMessage(LocaleController.formatString(R.string.PluginsCrashedMessage, crashed))
                    .setPositiveButton(getString(R.string.OK), null)
                    .create());
        }
        update();
    }
}
