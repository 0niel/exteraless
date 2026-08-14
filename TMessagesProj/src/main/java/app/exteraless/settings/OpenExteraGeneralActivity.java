package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import app.exteraless.OpenExteraConfig;
import app.exteraless.general.GeneralConfig;
import app.exteraless.general.GeneralHelper;
import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.NekoXConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
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

    private static final String PREF_TRANSLATE_BUTTON = "translate_button";
    private static final String PREF_TRANSLATE_CHAT_BUTTON = "translate_chat_button";

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
    private int predictiveBackRow;
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

    public OpenExteraGeneralActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GeneralConfig.init();
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
        predictiveBackRow = addRow("predictiveBack");
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
        archiveOnPullRow = addRow("archiveOnPull");
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

    private boolean getTranslatePref(String key) {
        return getMessagesController().getMainSettings().getBoolean(key, true);
    }

    private void setTranslatePref(String key, boolean value) {
        if (PREF_TRANSLATE_BUTTON.equals(key)) {
            getMessagesController().getTranslateController().setContextTranslateEnabled(value);
        } else {
            getMessagesController().getTranslateController().setChatTranslateEnabled(value);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == translateButtonRow || position == translateChatButtonRow) {
            String key = position == translateButtonRow ? PREF_TRANSLATE_BUTTON : PREF_TRANSLATE_CHAT_BUTTON;
            boolean value = !getTranslatePref(key);
            setTranslatePref(key, value);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(value);
            }
            return;
        }

        if (position == translationProviderRow) {
            Translator.showProviderSelect(view, provider -> {
                NekoConfig.translationProvider.setConfigInt(provider);
                listAdapter.notifyItemChanged(translationProviderRow);
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
            GeneralHelper.showTextInputDialog(this,
                    getString(R.string.OEGeneralSavePath),
                    getString(R.string.OEGeneralSavePathHint),
                    NekoConfig.customSavePath.String(),
                    value -> {
                        NekoConfig.customSavePath.setConfigString(GeneralHelper.sanitizeSavePath(value));
                        listAdapter.notifyItemChanged(savePathRow);
                    });
            return;
        }

        ConfigItem item = null;
        boolean inverted = false;

        if (position == disableNumberRoundingRow) {
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
        } else if (position == predictiveBackRow) {
            item = app.exteraless.utils.UtilsConfig.predictiveBack;
        } else if (position == uploadBoostRow) {
            item = NekoConfig.uploadBoost;
        } else if (position == hidePhoneRow) {
            item = NekoConfig.hidePhone;
        } else if (position == showIdAndDcRow) {
            item = NekoConfig.showIdAndDc;
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

        if (position == formatTimeWithSecondsRow) {
            LocaleController.getInstance().recreateFormatters();
        }
        if (position == hidePhoneRow || position == showIdAndDcRow) {
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        }
        if (position == hideArchiveRow) {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        }
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
                                getTranslatePref(PREF_TRANSLATE_BUTTON), true);
                    } else if (position == translateChatButtonRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralTranslateWholeChat),
                                getTranslatePref(PREF_TRANSLATE_CHAT_BUTTON), true);
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
                                NaConfig.INSTANCE.getZalgoFilter().Bool(), true);
                    } else if (position == predictiveBackRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OEGeneralPredictiveBack),
                                getString(R.string.OEGeneralPredictiveBackValue),
                                app.exteraless.utils.UtilsConfig.predictiveBack.Bool(), true, false);
                    } else if (position == uploadBoostRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralUploadBoost),
                                NekoConfig.uploadBoost.Bool(), false);
                    } else if (position == relativeLastSeenRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralRelativeLastSeen),
                                OpenExteraConfig.relativeLastSeen.Bool(), true);
                    } else if (position == hidePhoneRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralHidePhone),
                                NekoConfig.hidePhone.Bool(), true);
                    } else if (position == showIdAndDcRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralShowIdAndDc),
                                NekoConfig.showIdAndDc.Bool(), false);
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
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    boolean bottom = position == archiveDividerRow;
                    if (position == translateDividerRow) {
                        cell.setText(getString(R.string.OEGeneralTranslateInfo));
                    } else if (position == generalDividerRow) {
                        cell.setText(getString(R.string.OEGeneralFilterZalgoInfo));
                    } else if (position == speedDividerRow) {
                        cell.setText(getString(R.string.OEGeneralSpeedBoostInfo));
                    } else if (position == storageDividerRow) {
                        cell.setText(getString(R.string.OEGeneralSavePathInfo));
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
                    || position == doNotTranslateRow || position == savePathRow) {
                return TYPE_SETTINGS;
            }
            return TYPE_CHECK;
        }
    }
}
