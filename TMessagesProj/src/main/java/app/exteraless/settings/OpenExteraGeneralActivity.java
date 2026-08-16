package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import app.exteraless.OpenExteraConfig;
import app.exteraless.general.GeneralConfig;
import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.NekoXConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.translate.TranslatorKt;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/**
 * Экран «General» раздела openExtera — визуально 1:1 повторяет General из exteraGram.
 * Настройки по возможности привязаны к уже существующим ConfigItem NagramX; новые заводятся
 * только там, где в NagramX нет аналога, и помечены «UI-only» (без бэкенда, как в exteraGram 12.9.0).
 */
public class OpenExteraGeneralActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_SLIDE = 100;

    /** Проверяется имя папки, а не путь. */
    private static final Pattern SAVE_PATH_PATTERN = Pattern.compile("^(?!\\.{1,2}$)[A-Za-z0-9._ -]{1,255}$");

    /**
     * «Зальго»-образец для подписи под переключателем фильтра. У нашего фильтра порог срабатывания —
     * четыре подряд идущих комбинирующих знака (MessageHelper.ZALGO_PATTERN, :887), поэтому у образца
     * их по четыре на букву, иначе строка выглядела бы одинаково при включённом и выключенном фильтре.
     */
    private static final String ZALGO_SAMPLE =
            "Z\u0334\u034d\u030c\u0301a\u0308\u0325\u0347\u0303l\u0302\u031e\u0356\u0300"
                    + "g\u0300\u035d\u0345\u0330o\u0304\u0353\u0359\u0306";

    private int translateHeaderRow;
    private int translateButtonRow;
    private int translateChatButtonRow;
    private int translationProviderRow;
    private int translateToLangRow;
    private int doNotTranslateRow;
    private int translateDividerRow;

    private int generalHeaderRow;
    private int disableNumberRoundingRow;
    private int formatTimeWithSecondsRow;
    private int inAppVibrationRow;
    private int filterZalgoRow;
    private int generalDividerRow;

    private int speedHeaderRow;
    private int downloadSpeedRow;
    private int uploadBoostRow;
    private int speedDividerRow;

    private int storageHeaderRow;
    private int savePathRow;
    private int storageDividerRow;

    private int profileHeaderRow;
    private int relativeLastSeenRow;
    private int hidePhoneRow;
    private int showIdAndDcRow;
    private int profileDividerRow;

    private int archiveHeaderRow;
    private int hideArchiveRow;
    private int archiveOnPullRow;
    private int disableUnarchiveSwipeRow;
    private int archiveDividerRow;

    /** Момент «пять минут назад» для живого примера в строке Relative Last Seen. */
    private int fiveMinutesAgo;

    public OpenExteraGeneralActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GeneralConfig.init();
        fiveMinutesAgo = getConnectionsManager().getCurrentTime() - 300;
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        translateHeaderRow = addRow("translateHeader");
        translateButtonRow = addRow("translateButton");
        translateChatButtonRow = addRow("translateChatButton");
        translationProviderRow = addRow("translationProvider");
        translateToLangRow = addRow("translateToLang");
        doNotTranslateRow = addRow("doNotTranslate");
        translateDividerRow = addRow();

        generalHeaderRow = addRow("generalHeader");
        disableNumberRoundingRow = addRow("disableNumberRounding");
        formatTimeWithSecondsRow = addRow("formatTimeWithSeconds");
        inAppVibrationRow = addRow("inAppVibration");
        filterZalgoRow = addRow("filterZalgo");
        generalDividerRow = addRow();

        speedHeaderRow = addRow("speedHeader");
        downloadSpeedRow = addRow("downloadSpeed");
        uploadBoostRow = addRow("uploadBoost");
        speedDividerRow = addRow();

        storageHeaderRow = addRow("storageHeader");
        savePathRow = addRow("savePath");
        storageDividerRow = addRow();

        profileHeaderRow = addRow("profileHeader");
        relativeLastSeenRow = addRow("relativeLastSeen");
        hidePhoneRow = addRow("hidePhone");
        showIdAndDcRow = addRow("showIdAndDc");
        profileDividerRow = addRow();

        archiveHeaderRow = addRow("archiveHeader");
        hideArchiveRow = addRow("hideArchive");
        // Когда архив скрыт, строка уходит целиком: «открывать архив потягиванием»
        // нечего, если папки архива нет в списке.
        archiveOnPullRow = NaConfig.INSTANCE.getHideArchive().Bool() ? -1 : addRow("archiveOnPull");
        disableUnarchiveSwipeRow = addRow("disableUnarchiveSwipe");
        archiveDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEGeneralTitle);
    }

    @Override
    protected String getKey() {
        return "exteraless_general";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    /** Перерисовать экраны под нами. */
    private void rebuildAll() {
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == translationProviderRow) {
            Translator.showProviderSelect(view, provider -> {
                NekoConfig.translationProvider.setConfigInt(provider);
                listAdapter.notifyItemChanged(translationProviderRow);
                listAdapter.notifyItemChanged(translateToLangRow);
                return Unit.INSTANCE;
            });
            return;
        }

        if (position == translateToLangRow) {
            Translator.showTargetLangSelect(view, false, locale -> {
                NekoConfig.translateToLang.setConfigString(TranslatorKt.getLocale2code(locale));
                listAdapter.notifyItemChanged(translateToLangRow);
                return Unit.INSTANCE;
            });
            return;
        }

        if (position == doNotTranslateRow) {
            presentFragment(new RestrictedLanguagesSelectActivity());
            return;
        }

        if (position == savePathRow) {
            showCustomSavePathDialog();
            return;
        }

        if (position == showIdAndDcRow) {
            showIdAndDcSelector();
            return;
        }

        ConfigItem item = null;
        boolean inverted = false;

        if (position == translateButtonRow) {
            item = NekoConfig.showTranslate;
        } else if (position == translateChatButtonRow) {
            item = NaConfig.INSTANCE.getTelegramUIAutoTranslate();
        } else if (position == disableNumberRoundingRow) {
            item = NekoConfig.disableNumberRounding;
        } else if (position == formatTimeWithSecondsRow) {
            item = NekoConfig.showSeconds;
        } else if (position == relativeLastSeenRow) {
            item = OpenExteraConfig.relativeLastSeen;
        } else if (position == inAppVibrationRow) {
            // NekoConfig.disableVibration инвертирована относительно подписи «In-App Vibration».
            item = NekoConfig.disableVibration;
            inverted = true;
        } else if (position == filterZalgoRow) {
            item = NaConfig.INSTANCE.getZalgoFilter();
        } else if (position == uploadBoostRow) {
            item = NekoConfig.uploadBoost;
        } else if (position == hidePhoneRow) {
            item = NekoConfig.hidePhone;
        } else if (position == hideArchiveRow) {
            item = NaConfig.INSTANCE.getHideArchive();
        } else if (position == archiveOnPullRow) {
            item = NekoConfig.openArchiveOnPull;
        } else if (position == disableUnarchiveSwipeRow) {
            item = NaConfig.INSTANCE.getDoNotUnarchiveBySwipe();
        }

        if (item == null) {
            return;
        }

        boolean raw = item.toggleConfigBool();
        boolean shown = inverted != raw;
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(shown);
        }

        if (position == translateButtonRow || position == translateChatButtonRow) {
            // Поиск по настройкам переиндексируется, экраны пересобираются — пункт
            // «Translate» появляется и исчезает в меню сообщения.
            getNotificationCenter().postNotificationName(NotificationCenter.updateSearchSettings);
            rebuildAll();
        }
        if (position == formatTimeWithSecondsRow) {
            LocaleController.getInstance().recreateFormatters();
            rebuildAll();
        }
        if (position == filterZalgoRow) {
            // Подпись секции показывает результат работы фильтра, значит меняется вместе с ним.
            listAdapter.notifyItemChanged(generalDividerRow);
            rebuildAll();
        }
        if (position == relativeLastSeenRow && view instanceof TextCheckCell) {
            // Подпись строки — живое превью самой настройки. Меняем только подпись:
            // пересборка ячейки оборвала бы анимацию переключателя, которая уже идёт.
            ((TextCheckCell) view).setValueText(
                    LocaleController.formatDateOnline(fiveMinutesAgo, new boolean[1]));
        }
        if (position == hidePhoneRow) {
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            rebuildAll();
        }
        if (position == hideArchiveRow) {
            // Папка архива пересобирается сразу.
            getMessagesController().checkArchiveFolder();
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            // Со скрытым архивом строке «открывать архив потягиванием» нечего открывать,
            // она уходит из списка — но с анимацией: notifyDataSetChanged её убивает,
            // и строка исчезает рывком.
            final int wasPullRow = archiveOnPullRow;
            updateRows();
            if (listAdapter != null) {
                if (archiveOnPullRow == -1) {
                    listAdapter.notifyItemRemoved(wasPullRow);
                } else {
                    listAdapter.notifyItemInserted(archiveOnPullRow);
                }
            }
        }
    }

    private CharSequence[] idOptions() {
        return new CharSequence[]{getString(R.string.Hide), "Telegram API", "Bot API"};
    }

    private void showIdAndDcSelector() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEGeneralShowIdAndDc));
        builder.setItems(idOptions(), (dialog, which) -> {
            NaConfig.INSTANCE.getIdDcType().setConfigInt(which);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(showIdAndDcRow);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            rebuildAll();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /**
     * Диалог имени папки сохранения: неподходящее имя не «чинится» молча, а отбивается
     * тряской поля, диалог остаётся открытым.
     */
    private void showCustomSavePathDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.lineYFix = true;
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(NekoConfig.customSavePath.String());
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintColor(getThemedColor(Theme.key_groupcreate_hintText));
        editText.setHintText(getString(R.string.OEGeneralSavePathHint));
        editText.setFocusable(true);
        editText.setSingleLine(true);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        editText.setBackground(null);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        editText.setPadding(0, dp(6), 0, dp(6));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 10f));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEGeneralSavePath));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(dp(292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> {
            String value = editText.getText() == null ? "" : editText.getText().toString().trim();
            if (!TextUtils.isEmpty(value) && !SAVE_PATH_PATTERN.matcher(value).matches()) {
                AndroidUtilities.shakeView(editText);
                return;
            }
            NekoConfig.customSavePath.setConfigString(value);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(savePathRow);
                // Подпись секции зависит от значения — обновляем вместе со строкой.
                listAdapter.notifyItemChanged(storageDividerRow);
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            editText.setSelection(editText.length());
            AndroidUtilities.showKeyboard(editText);
        });
        // Без этого диалог закрылся бы раньше проверки и «тряска» была бы не видна.
        dialog.setDismissDialogByButtons(false);
        // Слушателя закрытия ставим через showDialog: BaseFragment.showDialog (:834)
        // затирает тот, что назначен диалогу напрямую.
        showDialog(dialog, d -> AndroidUtilities.hideKeyboard(editText));
    }

    private String getProviderName(int providerConstant) {
        int resId;
        if (providerConstant == Translator.providerGoogle) {
            resId = R.string.ProviderGoogleTranslate;
        } else if (providerConstant == Translator.providerYandex) {
            resId = R.string.ProviderYandexTranslate;
        } else if (providerConstant == Translator.providerLingo) {
            resId = R.string.ProviderLingocloud;
        } else if (providerConstant == Translator.providerMicrosoft) {
            resId = R.string.ProviderMicrosoftTranslator;
        } else if (providerConstant == Translator.providerRealMicrosoft) {
            resId = R.string.ProviderRealMicrosoftTranslator;
        } else if (providerConstant == Translator.providerDeepL) {
            resId = R.string.ProviderDeepLTranslate;
        } else if (providerConstant == Translator.providerTelegram) {
            resId = R.string.ProviderTelegramAPI;
        } else if (providerConstant == Translator.providerTranSmart) {
            resId = R.string.ProviderTranSmartTranslate;
        } else if (providerConstant == Translator.providerLLMTranslator) {
            resId = R.string.ProviderLLMTranslator;
        } else {
            return "";
        }
        return getString(resId);
    }

    private String getRestrictedLanguagesValue() {
        HashSet<String> langCodes = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
        if (langCodes.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String lang : langCodes) {
            names.add(NekoXConfig.formatLang(lang));
        }
        return TextUtils.join(", ", names);
    }

    private String getSavePathInfo() {
        String path = NekoConfig.customSavePath.String();
        return TextUtils.isEmpty(path)
                ? getString(R.string.OEGeneralSavePathInfo)
                : LocaleController.formatString(R.string.OEGeneralSavePathInfoFolder, path);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_SLIDE) {
                SlideChooseView slide = new SlideChooseView(mContext, resourcesProvider);
                slide.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                slide.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new org.telegram.ui.Components.RecyclerListView.Holder(slide);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == translateHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralTranslateHeader));
                    } else if (position == generalHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralSectionHeader));
                    } else if (position == speedHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralSpeedHeader));
                    } else if (position == storageHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralStorageHeader));
                    } else if (position == profileHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralProfileHeader));
                    } else if (position == archiveHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralArchiveHeader));
                    }
                    break;
                }
                case TYPE_SLIDE: {
                    SlideChooseView slide = (SlideChooseView) holder.itemView;
                    slide.setCallback(index -> GeneralConfig.downloadSpeedBoost.setConfigInt(index));
                    slide.setOptions(GeneralConfig.downloadSpeedBoost.Int(),
                            getString(R.string.OEGeneralSpeedOff),
                            getString(R.string.OEGeneralSpeedFast),
                            getString(R.string.OEGeneralSpeedUltra));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == translateButtonRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralTranslateButton),
                                NekoConfig.showTranslate.Bool(), true);
                    } else if (position == translateChatButtonRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralTranslateWholeChat),
                                NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool(), true);
                    } else if (position == disableNumberRoundingRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OEGeneralDisableNumberRounding),
                                getString(R.string.OEGeneralDisableNumberRoundingValue),
                                NekoConfig.disableNumberRounding.Bool(), true, true);
                    } else if (position == formatTimeWithSecondsRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OEGeneralFormatTimeWithSeconds),
                                getString(R.string.OEGeneralFormatTimeWithSecondsValue),
                                NekoConfig.showSeconds.Bool(), true, true);
                    } else if (position == inAppVibrationRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralInAppVibration),
                                !NekoConfig.disableVibration.Bool(), true);
                    } else if (position == filterZalgoRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralFilterZalgo),
                                NaConfig.INSTANCE.getZalgoFilter().Bool(), false);
                    } else if (position == uploadBoostRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralUploadBoost),
                                NekoConfig.uploadBoost.Bool(), false);
                    } else if (position == relativeLastSeenRow) {
                        // Значение строки — живой пример «был(а) 5 минут назад».
                        cell.setTextAndValueAndCheck(getString(R.string.OEGeneralRelativeLastSeen),
                                LocaleController.formatDateOnline(fiveMinutesAgo, new boolean[1]),
                                OpenExteraConfig.relativeLastSeen.Bool(), false, true);
                    } else if (position == hidePhoneRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralHidePhone),
                                NekoConfig.hidePhone.Bool(), true);
                    } else if (position == hideArchiveRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralHideArchive),
                                NaConfig.INSTANCE.getHideArchive().Bool(), true);
                    } else if (position == archiveOnPullRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralArchiveOnPull),
                                NekoConfig.openArchiveOnPull.Bool(), true);
                    } else if (position == disableUnarchiveSwipeRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralDisableUnarchiveSwipe),
                                NaConfig.INSTANCE.getDoNotUnarchiveBySwipe().Bool(), false);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == translationProviderRow) {
                        cell.setTextAndValue(getString(R.string.OEGeneralTranslationProvider),
                                getProviderName(NekoConfig.translationProvider.Int()), true);
                    } else if (position == translateToLangRow) {
                        String lang = NekoConfig.translateToLang.String();
                        String value = TextUtils.isEmpty(lang)
                                ? getString(R.string.OEGeneralTranslationTargetDefault)
                                : NekoXConfig.formatLang(lang);
                        cell.setTextAndValue(getString(R.string.OEGeneralTranslationTarget), value, true);
                    } else if (position == doNotTranslateRow) {
                        cell.setTextAndValue(getString(R.string.OEGeneralDoNotTranslate),
                                getRestrictedLanguagesValue(), true, false);
                    } else if (position == savePathRow) {
                        String path = NekoConfig.customSavePath.String();
                        cell.setTextAndValue(getString(R.string.OEGeneralSavePath),
                                TextUtils.isEmpty(path)
                                        ? getString(R.string.OEGeneralSavePathDefault)
                                        : path,
                                false);
                    } else if (position == showIdAndDcRow) {
                        // Выбор из трёх режимов, а не переключатель: Bot API отличается
                        // от Telegram API префиксом -100 у чатов и каналов.
                        int type = NaConfig.INSTANCE.getIdDcType().Int();
                        CharSequence[] options = idOptions();
                        cell.setTextAndValue(getString(R.string.OEGeneralShowIdAndDc),
                                options[type < 0 || type >= options.length ? 0 : type], false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    boolean bottom = position == archiveDividerRow;
                    if (position == translateDividerRow) {
                        cell.setText(getString(R.string.OEGeneralTranslateInfo));
                    } else if (position == generalDividerRow) {
                        cell.setText(LocaleController.formatString(R.string.OEGeneralFilterZalgoInfo,
                                MessageHelper.zalgoFilter(ZALGO_SAMPLE)));
                    } else if (position == speedDividerRow) {
                        cell.setText(getString(R.string.OEGeneralSpeedBoostInfo));
                    } else if (position == storageDividerRow) {
                        cell.setText(getSavePathInfo());
                    } else if (position == profileDividerRow) {
                        cell.setText(getString(R.string.OEGeneralShowIdAndDcInfo));
                    } else if (position == archiveDividerRow) {
                        cell.setText(getString(R.string.OEGeneralDisableUnarchiveSwipeInfo));
                    }
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            bottom ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                            Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == translateHeaderRow || position == generalHeaderRow
                    || position == speedHeaderRow
                    || position == storageHeaderRow || position == profileHeaderRow
                    || position == archiveHeaderRow) {
                return TYPE_HEADER;
            } else if (position == translateDividerRow || position == generalDividerRow
                    || position == speedDividerRow
                    || position == storageDividerRow || position == profileDividerRow
                    || position == archiveDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == downloadSpeedRow) {
                return TYPE_SLIDE;
            } else if (position == translationProviderRow || position == translateToLangRow
                    || position == doNotTranslateRow || position == savePathRow
                    || position == showIdAndDcRow) {
                return TYPE_SETTINGS;
            }
            return TYPE_CHECK;
        }
    }
}
