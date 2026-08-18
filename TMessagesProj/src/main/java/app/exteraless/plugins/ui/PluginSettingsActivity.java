package app.exteraless.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginPermissions;
import app.exteraless.plugins.PluginsController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Экран настроек плагина, строящийся из JSON-описания (ui.settings Python SDK).
 * Поддерживает вложенные подстраницы (sub_page) — тогда JSON приходит готовым,
 * без обращения к движку.
 */
public class PluginSettingsActivity extends BaseNekoSettingsActivity {

    /** Строка, которую рисует сам плагин ({@code ui.settings.Custom}). */
    private static final int TYPE_CUSTOM = 100;

    private String pluginId;
    private String subPageJson;
    private String subPageTitle;
    private int[] subPageIndex;
    private String[] subPageOwners;

    /**
     * Строки экрана. Имя нарочно не {@code items}: плагины, написанные под
     * exteraGram, ищут у фрагмента приватное поле с этим именем, чистят его
     * рефлексией и заполняют своим {@code fillItems} — у нас такого метода нет,
     * и экран после этого оставался пустым.
     */
    private final ArrayList<JSONObject> rows = new ArrayList<>();

    private int itemsStartRow;
    private int notLoadedRow;
    private int permissionsShadowRow;
    private int permissionsRow;

    public PluginSettingsActivity() {
    }

    /** Конструктор для шима внешнего API: {@code PluginSettingsActivity(plugin)}. */
    public PluginSettingsActivity(String pluginId) {
        this.pluginId = pluginId;
    }

    public static PluginSettingsActivity newInstance(String pluginId) {
        PluginSettingsActivity fragment = new PluginSettingsActivity();
        fragment.pluginId = pluginId;
        return fragment;
    }

    public static PluginSettingsActivity newSubPage(String pluginId, String json, String title,
                                                    int[] index, String[] owners) {
        PluginSettingsActivity fragment = new PluginSettingsActivity();
        fragment.pluginId = pluginId;
        fragment.subPageJson = json;
        fragment.subPageTitle = title;
        fragment.subPageIndex = index;
        fragment.subPageOwners = owners;
        return fragment;
    }

    private final Runnable reloadListener = this::rebuildFromEngine;
    private ActionBarMenuItem resetItem;

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // Кнопка сброса — как у exteraGram: только на корневом экране плагина и
        // только когда есть что сбрасывать.
        if (subPageIndex == null) {
            resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
            resetItem.setContentDescription(getString(R.string.PluginsResetSettings));
            resetItem.setOnClickListener(v -> showResetDialog());
            updateResetVisibility(false);
        }
        return view;
    }

    private void updateResetVisibility(boolean animated) {
        if (resetItem == null) {
            return;
        }
        AndroidUtilities.updateViewVisibilityAnimated(resetItem,
                PluginsController.getInstance().hasPluginSettingsPreferences(pluginId),
                0.5f, animated);
    }

    private void showResetDialog() {
        Activity activity = getParentActivity();
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        if (activity == null || plugin == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(R.string.PluginsResetSettings));
        builder.setMessage(AndroidUtilities.replaceTags(
                LocaleController.formatString(R.string.PluginsResetSettingsInfo,
                        plugin.getDisplayName())));
        builder.setPositiveButton(getString(R.string.Reset), (dialog, which) -> {
            PluginsController.getInstance().clearPluginSettingsPreferences(pluginId);
            rebuildFromEngine();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            ((TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    @Override
    public boolean onFragmentCreate() {
        PluginsController.getInstance().addSettingsReloadListener(pluginId, reloadListener);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        PluginsController.getInstance().removeSettingsReloadListener(pluginId, reloadListener);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Возврат с подстраницы: там могли переключить то, от чего зависит состав
        // строк на этом экране.
        rebuildFromEngine();
    }

    /**
     * Перестроить экран из движка. Публичный метод — его зовут и плагины:
     * экранов у exteraGram два (свой и наш), и плагины ищут у фрагмента именно
     * {@code updateItems()}, а не найдя — лезут чистить приватные поля рефлексией.
     */
    public void updateItems() {
        AndroidUtilities.runOnUIThread(this::rebuildFromEngine);
    }

    /** Движок просит перестроить экран (плагин изменил настройки из кода). Зовётся на UI-потоке. */
    private void rebuildFromEngine() {
        if (listAdapter == null) {
            return;
        }
        // Плагин удалили или выгрузили — экран его настроек больше ни о чём.
        if (subPageIndex == null && PluginsController.getInstance().getPlugin(pluginId) == null) {
            finishFragment();
            return;
        }
        updateRows();
        updateResetVisibility(true);
        listAdapter.notifyDataSetChanged();
        ensureListVisible();
    }

    /**
     * Вернуть список в видимое состояние.
     *
     * Плагины обновляют экран с анимацией: гасят список в прозрачность, в
     * withEndAction перестраивают его и возвращают обратно. Если их код между
     * этими шагами бросит исключение (а ловят они молча), список навсегда
     * остаётся прозрачным — экран выглядит пустым, хотя строки на месте.
     */
    /** Анимации плагинов короткие (десятые доли секунды) — проверяем после них. */
    private void scheduleVisibilityCheck() {
        AndroidUtilities.runOnUIThread(this::ensureListVisible, 600);
    }

    private void ensureListVisible() {
        if (listView == null) {
            return;
        }
        if (listView.getAlpha() < 1f || listView.getTranslationY() != 0f) {
            listView.animate().cancel();
            listView.setAlpha(1f);
            listView.setTranslationY(0f);
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        ArrayList<JSONObject> previous = new ArrayList<>(rows);
        rows.clear();
        String json;
        if (subPageIndex != null) {
            String resolved = resolveSubPageJson();
            if (resolved != null) {
                subPageJson = resolved;
            }
            json = subPageJson;
        } else {
            json = PluginsController.getInstance().getPluginSettingsJson(pluginId);
        }
        if (json != null && !"null".equals(json)) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj != null) {
                        rows.add(obj);
                    }
                }
            } catch (JSONException e) {
                FileLog.e(e);
            }
        }
        // Пересборка вернула пустоту там, где только что были строки: движок мог
        // не ответить, плагин — не пережить перезагрузку. Стереть экран хуже, чем
        // оставить прошлый список.
        if (rows.isEmpty() && !previous.isEmpty()) {
            FileLog.e("PluginSettingsActivity: empty rebuild for " + pluginId
                    + ", keeping " + previous.size() + " previous rows");
            rows.addAll(previous);
        }

        if (rows.isEmpty()) {
            itemsStartRow = -1;
            notLoadedRow = addRow();
        } else {
            notLoadedRow = -1;
            itemsStartRow = rowCount;
            rowCount += rows.size();
        }

        // Вход в разрешения — только на корневом экране плагина: подстраница
        // (sub_page) принадлежит самому плагину, разрешениям там не место.
        if (subPageIndex == null) {
            // Разделитель нужен, только когда выше стоит карточка настроек плагина:
            // строка «плагин не загружен» уже рисует тень под собой сама.
            permissionsShadowRow = notLoadedRow == -1 ? addRow() : -1;
            permissionsRow = addRow();
        } else {
            permissionsShadowRow = -1;
            permissionsRow = -1;
        }
    }

    /**
     * Свежий JSON этой подстраницы: она рисует срез корневого списка, а он
     * пересобирается при каждом изменении настроек. Идём по запомненному пути
     * от корня, сверяя заголовки, — состав строк по дороге мог измениться.
     */
    private String resolveSubPageJson() {
        String rootJson = PluginsController.getInstance().getPluginSettingsJson(pluginId);
        if (rootJson == null || "null".equals(rootJson)) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(rootJson);
            for (int step = 0; step < subPageIndex.length; step++) {
                String owner = subPageOwners != null && step < subPageOwners.length
                        ? subPageOwners[step] : null;
                JSONObject holder = subPageHolder(array, subPageIndex[step], owner);
                if (holder == null) {
                    return null;
                }
                array = holder.optJSONArray("sub_page");
                if (array == null) {
                    return null;
                }
            }
            return array.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    private static JSONObject subPageHolder(JSONArray array, int index, String text) {
        JSONObject candidate = array.optJSONObject(index);
        if (holdsSubPage(candidate, text)) {
            return candidate;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (holdsSubPage(obj, text)) {
                return obj;
            }
        }
        return null;
    }

    private static boolean holdsSubPage(JSONObject obj, String text) {
        return obj != null && obj.optJSONArray("sub_page") != null
                && (text == null || text.equals(obj.optString("text")));
    }

    /** Путь до строки на этом экране, считая от корневого списка плагина. */
    private int[] pathTo(int position) {
        int index = position - itemsStartRow;
        int[] parent = subPageIndex == null ? new int[0] : subPageIndex;
        int[] out = java.util.Arrays.copyOf(parent, parent.length + 1);
        out[parent.length] = index;
        return out;
    }

    private String[] ownersTo(String text) {
        String[] parent = subPageOwners == null ? new String[0] : subPageOwners;
        String[] out = java.util.Arrays.copyOf(parent, parent.length + 1);
        out[parent.length] = text;
        return out;
    }

    @Override
    protected String getActionBarTitle() {
        if (subPageTitle != null) {
            return subPageTitle;
        }
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        return plugin != null ? plugin.getDisplayName() : getString(R.string.PluginSettingsTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    /**
     * Значение строки «Разрешения»: сколько из запрошенного сейчас выдано.
     * Цифрами, а не словами, — строка справа узкая, а перевода не требует.
     */
    private String permissionsValue() {
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        List<String> requested = PluginPermissionsActivity.requestedFor(plugin);
        if (requested.isEmpty()) {
            return "";
        }
        int granted = 0;
        for (String perm : requested) {
            if (PluginPermissions.has(pluginId, perm)) {
                granted++;
            }
        }
        return granted + "/" + requested.size();
    }

    private JSONObject itemAt(int position) {
        int index = position - itemsStartRow;
        if (itemsStartRow == -1 || index < 0 || index >= rows.size()) {
            return null;
        }
        return rows.get(index);
    }

    /** Разделитель под строкой рисуем, только если следующая строка — тоже контентная. */
    private boolean needDivider(int position) {
        JSONObject next = itemAt(position + 1);
        if (next == null) {
            return false;
        }
        String nextType = next.optString("type");
        return !"header".equals(nextType) && !"divider".equals(nextType)
                && !"custom".equals(nextType);
    }

    /** Иконки приезжают именем drawable («msg_settings»); неизвестные молча пропускаем. */
    private static int resolveIcon(JSONObject item) {
        String name = app.exteraless.plugins.JsonUtils.optStringOrNull(item, "icon");
        if (TextUtils.isEmpty(name)) {
            return 0;
        }
        try {
            return R.drawable.class.getField(name).getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Заголовок строки: у EditText его нет — там подпись живёт в hint. */
    private static String rowTitle(JSONObject item) {
        String text = optNonEmpty(item, "text");
        if (text != null) {
            return text;
        }
        String hint = optNonEmpty(item, "hint");
        return hint != null ? hint : "";
    }

    /** Правая колонка строки: выбранный пункт для selector, значение для остальных. */
    private static String rowValue(JSONObject item) {
        if ("selector".equals(item.optString("type"))) {
            JSONArray options = item.optJSONArray("items");
            int selected = item.optInt("value");
            return options != null && selected >= 0 && selected < options.length()
                    ? options.optString(selected) : "";
        }
        return item.optString("value");
    }

    private static String optNonEmpty(JSONObject item, String key) {
        String value = app.exteraless.plugins.JsonUtils.optStringOrNull(item, key);
        return TextUtils.isEmpty(value) ? null : value;
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        JSONObject item = itemAt(position);
        String callbackId = item == null ? null : optNonEmpty(item, "long_callback_id");
        if (callbackId == null) {
            return false;
        }
        PluginsController.getInstance().dispatchSettingClick(pluginId, callbackId, view);
        return true;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == permissionsRow) {
            presentFragment(new PluginPermissionsActivity(pluginId));
            return;
        }
        JSONObject item = itemAt(position);
        if (item == null) {
            return;
        }
        String type = item.optString("type");
        String key = optNonEmpty(item, "key");
        String callbackId = optNonEmpty(item, "callback_id");
        PluginsController controller = PluginsController.getInstance();
        switch (type) {
            case "switch": {
                boolean newValue = !item.optBoolean("value");
                try {
                    item.put("value", newValue);
                } catch (JSONException ignore) {
                }
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
                if (key != null) {
                    controller.notifySettingChanged(pluginId, key, String.valueOf(newValue));
                    scheduleVisibilityCheck();
                }
                break;
            }
            case "selector":
                showSelectorDialog(position, item, key);
                break;
            case "input":
                showInputDialog(position, item, key, false);
                break;
            case "edittext":
                showInputDialog(position, item, key, true);
                break;
            case "custom": {
                JSONArray customSubPage = item.optJSONArray("sub_page");
                if (customSubPage != null) {
                    presentFragment(newSubPage(pluginId, customSubPage.toString(), getActionBarTitle(),
                            pathTo(position), ownersTo(null)));
                } else if (callbackId != null) {
                    controller.dispatchSettingClick(pluginId, callbackId, view);
                }
                break;
            }
            case "text": {
                JSONArray subPage = item.optJSONArray("sub_page");
                if (subPage != null) {
                    presentFragment(newSubPage(pluginId, subPage.toString(), item.optString("text"),
                            pathTo(position), ownersTo(item.optString("text"))));
                } else if (callbackId != null) {
                    controller.dispatchSettingClick(pluginId, callbackId, view);
                }
                break;
            }
        }
    }

    /**
     * Выбор из списка — как у exteraGram
     * ({@code plugins/ui/PluginSettingsActivity.showSelectorDialog}): строки с
     * радиокнопками и отмеченным текущим значением, а не голый список.
     */
    private void showSelectorDialog(int position, JSONObject item, String key) {
        Activity activity = getParentActivity();
        JSONArray options = item.optJSONArray("items");
        if (activity == null || options == null) {
            return;
        }
        int selected = item.optInt("value");
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialog = new AlertDialog[1];
        for (int i = 0; i < options.length(); i++) {
            final int index = i;
            RadioColorCell cell = new RadioColorCell(activity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground),
                    Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options.optString(i), selected == i);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            cell.setOnClickListener(v -> {
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                try {
                    item.put("value", index);
                } catch (JSONException ignore) {
                }
                if (key != null) {
                    PluginsController.getInstance().notifySettingChanged(pluginId, key,
                            String.valueOf(index));
                    scheduleVisibilityCheck();
                }
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(position);
                }
            });
            content.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(rowTitle(item));
        builder.setView(content);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        dialog[0] = builder.create();
        showDialog(dialog[0]);
    }

    /**
     * Диалог ввода значения — как у exteraGram
     * ({@code plugins/ui/PluginSettingsActivity.showStringInputDialog}):
     * подпись строки над полем, поле EditTextBoldCursor с подчёркиванием и
     * курсором темы, ширина 292dp, кнопка «Готово» и клавиатура сразу.
     */
    private void showInputDialog(int position, JSONObject item, String key, boolean multiline) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        String subtext = optNonEmpty(item, "subtext");
        if (subtext != null) {
            TextView description = new TextView(activity);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            description.setText(subtext);
            content.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));
        }

        final EditTextBoldCursor input = new EditTextBoldCursor(activity);
        input.lineYFix = true;
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setText(item.optString("value"));
        input.setSelection(input.getText().length());
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        String hint = optNonEmpty(item, "hint");
        input.setHintText(hint != null ? hint : getString(R.string.PluginsEnterValue));
        input.setFocusable(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (multiline) {
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        input.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        input.setBackground(null);
        input.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        int maxLength = item.optInt("max_length", 0);
        if (maxLength > 0) {
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        content.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(rowTitle(item));
        builder.makeCustomMaxHeight();
        builder.setView(content);
        builder.setWidth(AndroidUtilities.dp(292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> {
            String value = input.getText().toString();
            try {
                item.put("value", value);
            } catch (JSONException ignore) {
            }
            if (key != null) {
                PluginsController.getInstance().notifySettingChanged(pluginId, key, JSONObject.quote(value));
                scheduleVisibilityCheck();
            }
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.setDismissDialogByButtons(false);
        dialog.setOnDismissListener(d -> AndroidUtilities.hideKeyboard(input));
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        showDialog(dialog);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    JSONObject item = itemAt(position);
                    if (item == null) {
                        break;
                    }
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(item.optString("text"));
                    break;
                }
                case TYPE_CHECK: {
                    JSONObject item = itemAt(position);
                    if (item == null) {
                        break;
                    }
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    String subtext = optNonEmpty(item, "subtext");
                    boolean checked = item.optBoolean("value");
                    if (subtext != null) {
                        cell.setTextAndValueAndCheck(item.optString("text"), subtext,
                                checked, true, needDivider(position));
                    } else {
                        cell.setTextAndCheck(item.optString("text"), checked, needDivider(position));
                    }
                    cell.setIcon(resolveIcon(item));
                    break;
                }
                // TYPE_SHADOW не биндим: ShadowSectionCell рисует свой фон сам
                // (updateBackground в конструкторе), а перекрашивание его здесь
                // только сломало бы тень.
                case TYPE_SETTINGS: {
                    if (position == permissionsRow) {
                        ((TextSettingsCell) holder.itemView).setTextAndValue(
                                getString(R.string.PluginPermissions), permissionsValue(), false);
                        ((TextSettingsCell) holder.itemView).setIcon(0);
                        break;
                    }
                    JSONObject item = itemAt(position);
                    if (item == null) {
                        break;
                    }
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextAndValue(rowTitle(item), rowValue(item), needDivider(position));
                    cell.setIcon(resolveIcon(item));
                    break;
                }
                case TYPE_TEXT: {
                    JSONObject item = itemAt(position);
                    if (item == null) {
                        break;
                    }
                    TextCell cell = (TextCell) holder.itemView;
                    String text = rowTitle(item);
                    boolean isText = "text".equals(item.optString("type"));
                    String subtext = isText ? optNonEmpty(item, "subtext") : null;
                    String value = isText ? null : rowValue(item);
                    int icon = resolveIcon(item);
                    boolean divider = needDivider(position);
                    if (value != null && icon != 0) {
                        cell.setTextAndValueAndIcon(text, value, icon, divider);
                    } else if (value != null) {
                        cell.setTextAndValue(text, value, divider);
                    } else if (icon != 0) {
                        cell.setTextAndIcon(text, icon, divider);
                    } else {
                        cell.setText(text, divider);
                    }
                    // Подпись живёт под заголовком, а не справа: у плагинов это
                    // предложение целиком, справа от него остаётся многоточие.
                    // Геометрия иконки — как у TextSettingsCell и TextCheckCell:
                    // на одном экране строки разных типов идут вперемешку, и
                    // штатные 58dp у TextCell дают рваный левый край.
                    cell.setImageLeft(21);
                    cell.setOffsetFromImage(71);
                    cell.setSubtitle(subtext);
                    cell.heightDp = subtext != null ? 60 : 50;
                    if (item.optBoolean("red")) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    } else if (item.optBoolean("accent")) {
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon,
                                Theme.key_windowBackgroundWhiteBlueText4);
                    } else {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon,
                                Theme.key_windowBackgroundWhiteBlackText);
                    }
                    break;
                }
                case TYPE_CUSTOM: {
                    FrameLayout container = (FrameLayout) holder.itemView;
                    container.removeAllViews();
                    JSONObject item = itemAt(position);
                    String viewId = item == null ? null : optNonEmpty(item, "view_id");
                    if (viewId == null) {
                        break;
                    }
                    View custom = PluginsController.getInstance()
                            .getPluginSettingsCustomView(pluginId, viewId, mContext);
                    if (custom == null) {
                        break;
                    }
                    // Вьюха живёт в объекте плагина и переживает переработку
                    // строки: тот же экземпляр может ещё висеть в прошлом
                    // контейнере, и addView без этого бросит IllegalState.
                    AndroidUtilities.removeFromParent(custom);
                    container.addView(custom, LayoutHelper.createFrame(
                            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == notLoadedRow) {
                        cell.setText(getString(R.string.PluginsNotLoaded));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        break;
                    }
                    JSONObject item = itemAt(position);
                    if (item == null) {
                        break;
                    }
                    String text = optNonEmpty(item, "text");
                    cell.setText(text);
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            position == rowCount - 1 ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                            Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_CUSTOM) {
                FrameLayout container = new FrameLayout(mContext);
                container.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(container);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == TYPE_CUSTOM) {
                JSONObject item = itemAt(holder.getAdapterPosition());
                return item != null && (optNonEmpty(item, "callback_id") != null
                        || item.optJSONArray("sub_page") != null);
            }
            return super.isEnabled(holder);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == notLoadedRow) {
                return TYPE_INFO_PRIVACY;
            }
            if (position == permissionsShadowRow) {
                return TYPE_SHADOW;
            }
            if (position == permissionsRow) {
                return TYPE_SETTINGS;
            }
            JSONObject item = itemAt(position);
            if (item == null) {
                return TYPE_INFO_PRIVACY;
            }
            switch (item.optString("type")) {
                case "header":
                    return TYPE_HEADER;
                case "divider":
                    return TYPE_INFO_PRIVACY;
                case "switch":
                    return TYPE_CHECK;
                case "selector":
                case "input":
                case "edittext":
                    return TYPE_TEXT;
                case "custom":
                    return TYPE_CUSTOM;
                default:
                    return TYPE_TEXT;
            }
        }
    }
}
