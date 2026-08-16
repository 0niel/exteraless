package app.exteraless.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
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
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
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

    private final ArrayList<JSONObject> items = new ArrayList<>();

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

    public static PluginSettingsActivity newSubPage(String pluginId, String json, String title) {
        PluginSettingsActivity fragment = new PluginSettingsActivity();
        fragment.pluginId = pluginId;
        fragment.subPageJson = json;
        fragment.subPageTitle = title;
        return fragment;
    }

    @Override
    public boolean onFragmentCreate() {
        // Слушатель один на pluginId: подстраница показывает зафиксированный JSON
        // и перерегистрацией только сломала бы слушатель родительского экрана.
        if (subPageJson == null) {
            PluginsController.getInstance().setSettingsReloadListener(pluginId, this::rebuildFromEngine);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (subPageJson == null) {
            PluginsController.getInstance().setSettingsReloadListener(pluginId, null);
        }
        super.onFragmentDestroy();
    }

    /** Движок просит перестроить экран (плагин изменил настройки из кода). Зовётся на UI-потоке. */
    private void rebuildFromEngine() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        items.clear();
        String json = subPageJson != null
                ? subPageJson
                : PluginsController.getInstance().getPluginSettingsJson(pluginId);
        if (json != null && !"null".equals(json)) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj != null) {
                        items.add(obj);
                    }
                }
            } catch (JSONException e) {
                FileLog.e(e);
            }
        }

        if (items.isEmpty()) {
            itemsStartRow = -1;
            notLoadedRow = addRow();
        } else {
            notLoadedRow = -1;
            itemsStartRow = rowCount;
            rowCount += items.size();
        }

        // Вход в разрешения — только на корневом экране плагина: подстраница
        // (sub_page) принадлежит самому плагину, разрешениям там не место.
        if (subPageJson == null) {
            // Разделитель нужен, только когда выше стоит карточка настроек плагина:
            // строка «плагин не загружен» уже рисует тень под собой сама.
            permissionsShadowRow = notLoadedRow == -1 ? addRow() : -1;
            permissionsRow = addRow();
        } else {
            permissionsShadowRow = -1;
            permissionsRow = -1;
        }
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
        if (itemsStartRow == -1 || index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
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

    private static String optNonEmpty(JSONObject item, String key) {
        String value = app.exteraless.plugins.JsonUtils.optStringOrNull(item, key);
        return TextUtils.isEmpty(value) ? null : value;
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
                if (callbackId != null && key != null) {
                    controller.notifySettingChanged(pluginId, key, String.valueOf(newValue));
                }
                break;
            }
            case "selector":
                showSelectorDialog(position, item, key, callbackId);
                break;
            case "input":
                showInputDialog(position, item, key, callbackId, false);
                break;
            case "edittext":
                showInputDialog(position, item, key, callbackId, true);
                break;
            case "custom": {
                JSONArray customSubPage = item.optJSONArray("sub_page");
                if (customSubPage != null) {
                    presentFragment(newSubPage(pluginId, customSubPage.toString(), getActionBarTitle()));
                } else if (callbackId != null) {
                    controller.dispatchSettingClick(pluginId, callbackId);
                }
                break;
            }
            case "text": {
                JSONArray subPage = item.optJSONArray("sub_page");
                if (subPage != null) {
                    presentFragment(newSubPage(pluginId, subPage.toString(), item.optString("text")));
                } else if (callbackId != null) {
                    controller.dispatchSettingClick(pluginId, callbackId);
                }
                break;
            }
        }
    }

    private void showSelectorDialog(int position, JSONObject item, String key, String callbackId) {
        Activity activity = getParentActivity();
        JSONArray options = item.optJSONArray("items");
        if (activity == null || options == null) {
            return;
        }
        CharSequence[] labels = new CharSequence[options.length()];
        for (int i = 0; i < options.length(); i++) {
            labels[i] = options.optString(i);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(item.optString("text"));
        builder.setItems(labels, (dialog, which) -> {
            try {
                item.put("value", which);
            } catch (JSONException ignore) {
            }
            if (callbackId != null && key != null) {
                PluginsController.getInstance().notifySettingChanged(pluginId, key, String.valueOf(which));
            }
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
        });
        showDialog(builder.create());
    }

    private void showInputDialog(int position, JSONObject item, String key, String callbackId, boolean multiline) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final EditText input = new EditText(activity);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        String hint = optNonEmpty(item, "hint");
        input.setHint(hint != null ? hint : item.optString("text"));
        input.setText(item.optString("value"));
        input.setSelection(input.getText().length());
        if (multiline) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine();
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        int maxLength = item.optInt("max_length", 0);
        if (maxLength > 0) {
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(item.optString("text"));
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String value = input.getText().toString();
            try {
                item.put("value", value);
            } catch (JSONException ignore) {
            }
            if (callbackId != null && key != null) {
                PluginsController.getInstance().notifySettingChanged(pluginId, key, JSONObject.quote(value));
            }
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
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
                    String value;
                    if ("selector".equals(item.optString("type"))) {
                        JSONArray options = item.optJSONArray("items");
                        int selected = item.optInt("value");
                        value = options != null && selected >= 0 && selected < options.length()
                                ? options.optString(selected) : "";
                    } else {
                        value = item.optString("value");
                    }
                    cell.setTextAndValue(item.optString("text"), value, needDivider(position));
                    cell.setIcon(resolveIcon(item));
                    break;
                }
                case TYPE_TEXT: {
                    JSONObject item = itemAt(position);
                    if (item == null) {
                        break;
                    }
                    TextCell cell = (TextCell) holder.itemView;
                    String text = item.optString("text");
                    String subtext = optNonEmpty(item, "subtext");
                    int icon = resolveIcon(item);
                    boolean divider = needDivider(position);
                    if (icon != 0 && subtext != null) {
                        cell.setTextAndValueAndIcon(text, subtext, icon, divider);
                    } else if (icon != 0) {
                        cell.setTextAndIcon(text, icon, divider);
                    } else if (subtext != null) {
                        cell.setTextAndValue(text, subtext, divider);
                    } else {
                        cell.setText(text, divider);
                    }
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
                    return TYPE_SETTINGS;
                case "custom":
                    return TYPE_CUSTOM;
                default:
                    return TYPE_TEXT;
            }
        }
    }
}
