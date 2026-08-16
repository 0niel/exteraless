package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;

import app.exteraless.backup.EtgBackup;
import app.exteraless.backup.EtgBackupUi;
import app.exteraless.general.GeneralConfig;
import app.exteraless.general.GeneralHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AlertUtil;

/**
 * Экран «Other» раздела openExtera — повторяет Other из exteraGram
 * (секция Google + управление настройками).
 */
public class OpenExteraOtherActivity extends BaseNekoSettingsActivity {

    /** Кнопка удаления остаётся заблокированной 30 секунд. */
    private static final long DELETE_ACCOUNT_DELAY = 30_000L;

    private static final int ETG_IMPORT_REQUEST_CODE = 22;

    private int nagramHeaderRow;
    private int nagramSettingsRow;
    private int nagramDividerRow;

    private int exportEtgRow;
    private int importEtgRow;
    private int etgDividerRow;
    private int resetSettingsRow;
    private int deleteAccountRow;
    private int bottomDividerRow;

    private CountDownTimer deleteAccountTimer;

    public OpenExteraOtherActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GeneralConfig.init();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        cancelDeleteAccountTimer();
        super.onFragmentDestroy();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // Секции Google (Analytics + Crashlytics) здесь больше нет. Отправлять
        // было нечего: shouldEnableCrashlytics требует applicationId
        // «nu.gpu.nagram», а у нас com.exteraless.app — переключатель стоял
        // мёртвым. На его месте вход в настройки NagramX, выключенный по
        // умолчанию.
        nagramHeaderRow = addRow("nagramHeader");
        nagramSettingsRow = addRow("nagramSettings");
        nagramDividerRow = addRow();

        exportEtgRow = addRow("exportEtgSettings");
        importEtgRow = addRow("importEtgSettings");
        etgDividerRow = addRow();

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
        if (position == nagramSettingsRow) {
            boolean enabled = GeneralConfig.showNagramSettings.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            // Строка появляется и исчезает в общем списке настроек, а он уже
            // построен и на свои уведомления её не пересобирает — поэтому
            // пересобираем вьюхи стека целиком, как это делают остальные
            // настройки, меняющие чужие экраны.
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
        } else if (position == exportEtgRow) {
            exportEtgSettings();
        } else if (position == importEtgRow) {
            openEtgFilePicker();
        } else if (position == resetSettingsRow) {
            showResetSettingsDialog();
        } else if (position == deleteAccountRow) {
            showDeleteAccountDialog();
        }
    }

    private void exportEtgSettings() {
        EtgBackupUi.export(this);
    }

    private void openEtgFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, ETG_IMPORT_REQUEST_CODE);
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
        }
    }

    /**
     * Выбранный файл копируется в кэш под своим расширением: провайдер отдаёт content://,
     * а читать бэкап удобнее обычным файлом.
     */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != ETG_IMPORT_REQUEST_CODE) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        File file = new File(AndroidUtilities.getCacheDir(),
                UUID.randomUUID().toString().replace("-", "") + EtgBackup.EXTENSION);
        try (InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return;
            }
            try (OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
            return;
        }
        EtgBackupUi.confirmImport(this, file);
    }

    private void showResetSettingsDialog() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        AlertUtil.showConfirm(activity,
                getString(R.string.OEGeneralResetSettings),
                getString(R.string.OEGeneralResetSettingsInfo),
                R.drawable.msg_reset,
                getString(R.string.OEGeneralResetSettings),
                true,
                () -> {
                    GeneralHelper.resetSettings();
                    LocaleController.getInstance().recreateFormatters();
                    // Ресурсы темы надо перечитать: иначе не подхватятся радиусы и цвета,
                    // сброшенные вместе с настройками.
                    Theme.reloadAllResources(activity);
                    if (getParentLayout() != null) {
                        getParentLayout().rebuildAllFragmentViews(false, false);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                    // У exteraGram бюллетень именно «ошибочный» (красный) — это предупреждение, а не успех.
                    BulletinFactory.of(OpenExteraOtherActivity.this)
                            .createErrorBulletin(getString(R.string.OEGeneralResetSettingsDone))
                            .show();
                });
    }

    /**
     * Удаление аккаунта: подтверждение с обратным отсчётом, затем
     * TL_account.deleteAccount и локальный выход.
     */
    private void showDeleteAccountDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEGeneralDeleteAccount));
        builder.setMessage(getString(R.string.TosDeclineDeleteAccount));
        builder.setPositiveButton(getString(R.string.Deactivate), (dialog, which) -> deleteAccount());
        builder.setNegativeButton(getString(R.string.Cancel), null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            View button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (!(button instanceof TextView)) {
                return;
            }
            TextView textView = (TextView) button;
            textView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            textView.setEnabled(false);
            CharSequence text = textView.getText();
            cancelDeleteAccountTimer();
            deleteAccountTimer = new CountDownTimer(DELETE_ACCOUNT_DELAY, 100L) {
                @Override
                public void onTick(long millisUntilFinished) {
                    textView.setText(String.format(Locale.getDefault(), "%s • %d",
                            text, (millisUntilFinished / 1000) + 1));
                }

                @Override
                public void onFinish() {
                    textView.setText(text);
                    textView.setEnabled(true);
                }
            };
            deleteAccountTimer.start();
        });
        // Слушателя ставим через showDialog: BaseFragment.showDialog (:834) затирает
        // тот, что назначен диалогу напрямую.
        showDialog(dialog, d -> cancelDeleteAccountTimer());
    }

    private void cancelDeleteAccountTimer() {
        if (deleteAccountTimer != null) {
            deleteAccountTimer.cancel();
            deleteAccountTimer = null;
        }
    }

    private void deleteAccount() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();
        // Пауза exteraGram перед запросом: спиннер успевает появиться, а не мигнуть.
        AndroidUtilities.runOnUIThread(() -> {
            TL_account.deleteAccount req = new TL_account.deleteAccount();
            req.reason = "openExtera";
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response instanceof TLRPC.TL_boolTrue) {
                    getMessagesController().performLogout(0);
                    return;
                }
                if (error != null && error.code == -1000) {
                    return;
                }
                if (getParentActivity() == null) {
                    return;
                }
                String message = getString(R.string.ErrorOccurred);
                if (error != null) {
                    message = message + "\n" + error.text;
                }
                AlertDialog.Builder alert = new AlertDialog.Builder(getParentActivity());
                alert.setTitle(getString(R.string.AppName));
                alert.setMessage(message);
                alert.setPositiveButton(getString(R.string.OK), null);
                showDialog(alert.create());
            }));
        }, 500);
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
                    if (position == nagramHeaderRow) {
                        cell.setText(getString(R.string.OEGeneralNagramHeader));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == nagramSettingsRow) {
                        cell.setTextAndCheck(getString(R.string.OEGeneralNagramSettings),
                                GeneralConfig.showNagramSettings(), false);
                        // setIcon после setTextAndCheck — тот сбрасывает отступы текста.
                        cell.setIcon(R.drawable.msg_settings);
                    } else {
                        cell.setIcon(0);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == exportEtgRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.OEGeneralExportEtgSettings), R.drawable.msg_shareout, true);
                    } else if (position == importEtgRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.OEGeneralImportEtgSettings), R.drawable.msg_download, false);
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
                    if (position == nagramDividerRow) {
                        cell.setText(getString(R.string.OEGeneralNagramSettingsInfo));
                    } else if (position == etgDividerRow) {
                        cell.setText(getString(R.string.OEGeneralEtgSettingsInfo));
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
            if (position == nagramHeaderRow) {
                return TYPE_HEADER;
            } else if (position == nagramDividerRow || position == etgDividerRow
                    || position == bottomDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == exportEtgRow || position == importEtgRow
                    || position == resetSettingsRow || position == deleteAccountRow) {
                return TYPE_TEXT;
            }
            return TYPE_CHECK;
        }
    }
}
