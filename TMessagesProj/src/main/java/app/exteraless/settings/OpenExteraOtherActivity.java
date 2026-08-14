package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.PrivacySettingsActivity;

import app.exteraless.general.GeneralConfig;
import app.exteraless.general.GeneralHelper;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.AndroidUtil;
import xyz.nextalone.nagram.NaConfig;

/**
 * Экран «Other» раздела openExtera — повторяет Other из exteraGram
 * (секция Google + управление настройками).
 */
public class OpenExteraOtherActivity extends BaseNekoSettingsActivity {

    private int googleHeaderRow;
    private int crashlyticsRow;
    private int analyticsRow;
    private int googleDividerRow;

    private int exportSettingsRow;
    private int resetSettingsRow;
    private int deleteAccountRow;
    private int bottomDividerRow;

    public OpenExteraOtherActivity() {
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

        googleHeaderRow = addRow("googleHeader");
        crashlyticsRow = addRow("crashlytics");
        analyticsRow = addRow("analytics");
        googleDividerRow = addRow();

        exportSettingsRow = addRow("exportSettings");
        resetSettingsRow = addRow("resetSettings");
        deleteAccountRow = addRow("deleteAccount");
        bottomDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEGeneralOtherTitle);
    }

    @Override
    protected String getKey() {
        return "exteraless_other";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == crashlyticsRow) {
            // NaConfig.disableCrashlyticsCollection инвертирована относительно подписи «Crashlytics».
            boolean disabled = NaConfig.INSTANCE.getDisableCrashlyticsCollection().toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!disabled);
            }
            try {
                FirebaseCrashlytics.getInstance()
                        .setCrashlyticsCollectionEnabled(AndroidUtil.shouldEnableCrashlytics());
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (position == analyticsRow) {
            boolean value = GeneralConfig.analyticsCollection.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(value);
            }
        } else if (position == exportSettingsRow) {
            if (getParentActivity() == null) {
                return;
            }
            SettingsBackupHelper.backupSettings(getParentActivity(), resourcesProvider);
        } else if (position == resetSettingsRow) {
            if (getParentActivity() == null) {
                return;
            }
            AlertUtil.showConfirm(getParentActivity(),
                    getString(R.string.OEGeneralResetSettings),
                    getString(R.string.OEGeneralResetSettingsInfo),
                    R.drawable.msg_reset,
                    getString(R.string.OEGeneralResetSettings),
                    true,
                    () -> {
                        GeneralHelper.resetSettings();
                        LocaleController.getInstance().recreateFormatters();
                        if (getParentLayout() != null) {
                            getParentLayout().rebuildAllFragmentViews(false, false);
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                        BulletinFactory.of(OpenExteraOtherActivity.this)
                                .createSimpleBulletin(R.raw.info, getString(R.string.OEGeneralResetSettingsDone))
                                .show();
                    });
        } else if (position == deleteAccountRow) {
            // Штатный путь Telegram: удаление аккаунта живёт в настройках приватности.
            presentFragment(new PrivacySettingsActivity());
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == googleHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralGoogleHeader));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == crashlyticsRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralCrashlytics),
                                !NaConfig.INSTANCE.getDisableCrashlyticsCollection().Bool(), true);
                    } else if (position == analyticsRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralAnalytics),
                                GeneralConfig.analyticsCollection.Bool(), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == exportSettingsRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.OEGeneralExportSettings), R.drawable.msg_settings, true);
                    } else if (position == resetSettingsRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.OEGeneralResetSettings), R.drawable.msg_reset, true);
                    } else if (position == deleteAccountRow) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedBold);
                        cell.setTextAndIcon(getString(R.string.OEGeneralDeleteAccount), R.drawable.msg_clearcache, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    boolean bottom = position == bottomDividerRow;
                    if (position == googleDividerRow) {
                        cell.setText(getString(R.string.OEGeneralAnalyticsInfo));
                    } else {
                        cell.setText(null);
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
            if (position == googleHeaderRow) {
                return TYPE_HEADER;
            } else if (position == googleDividerRow || position == bottomDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == exportSettingsRow || position == resetSettingsRow
                    || position == deleteAccountRow) {
                return TYPE_TEXT;
            }
            return TYPE_CHECK;
        }
    }
}
