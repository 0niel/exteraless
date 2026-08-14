package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import app.exteraless.OpenExteraConfig;
import app.exteraless.chats.ChatsConfig;
import app.exteraless.chats.DoubleTapCell;
import app.exteraless.chats.StickerShapeCell;
import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.ui.cells.StickerSizePreviewMessagesCell;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.DoubleTap;

/**
 * Экран «Chats» раздела openExtera — визуальный порт экрана exteraGram
 * (com.exteragram.messenger.preferences.ChatsPreferencesActivity, читаемый исходник 10.10.1).
 *
 * Настройки, которые уже есть в NagramX, переиспользуются из {@link NekoConfig} / {@link NaConfig};
 * то, чего нет — новые ConfigItem в {@link ChatsConfig} (помечены «только UI»).
 * Группы-мультивыбор (Replies, Hide Reactions, Quick Swipe Transition, Message Menu,
 * Extended Settings, Auto-Pause) сделаны разворачивающимися: строка «x/y» + чекбоксы.
 */
public class OpenExteraChatsActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_STICKER_SIZE = 100;
    private static final int TYPE_STICKER_SHAPE = 101;
    private static final int TYPE_DOUBLE_TAP = 102;
    private static final int TYPE_SET_REACTION = 103;

    private StickerSizeCell stickerSizeCell;
    private StickerShapeCell stickerShapeCell;
    private DoubleTapCell doubleTapCell;

    private boolean repliesExpanded;
    private boolean hideReactionsExpanded;
    private boolean quickTransitionExpanded;
    private boolean messageMenuExpanded;
    private boolean extendedSettingsExpanded;
    private boolean pauseExpanded;

    // Sticker Size
    private int stickerSizeRow;
    private int hideTimeOnStickersRow;
    private int repliesGroupRow;
    private int replyColorsRow;
    private int replyEmojiRow;
    private int replyBackgroundRow;
    private int stickerSizeDividerRow;

    // Sticker Shape
    private int stickerShapeHeaderRow;
    private int stickerShapeRow;
    private int stickerShapeDividerRow;

    // Links
    private int aiChatRow;
    private int chatSettingsRow;
    private int linksDividerRow;

    // Stickers and Emoji
    private int stickersHeaderRow;
    private int unlimitedRecentStickersRow;
    private int hideReactionsGroupRow;
    private int hideReactionsChannelsRow;
    private int hideReactionsGroupsRow;
    private int hideReactionsPrivateRow;
    private int stickersDividerRow;

    // Double Tap
    private int doubleTapHeaderRow;
    private int doubleTapRow;
    private int doubleTapIncomingRow;
    private int doubleTapOutgoingRow;
    private int doubleTapReactionRow;
    private int doubleTapDividerRow;

    // Chats
    private int chatsHeaderRow;
    private int bottomButtonRow;
    private int adminShortcutsRow;
    private int quickTransitionGroupRow;
    private int quickTransitionChannelsRow;
    private int quickTransitionTopicsRow;
    private int disableGreetingRow;
    private int hideKeyboardOnScrollRow;
    private int addCommaRow;
    private int hideSendAsPeerRow;
    private int chatsDividerRow;

    // Messages
    private int messagesHeaderRow;
    private int removeMessageTailRow;
    private int replaceEditedRow;
    private int showOnlineStatusRow;
    private int hideShareButtonRow;
    private int showResultsBeforeVotingRow;
    private int messageMenuGroupRow;
    private int menuCopyPhotoRow;
    private int menuSaveRow;
    private int menuRepeatRow;
    private int menuClearRow;
    private int menuHistoryRow;
    private int menuReportRow;
    private int menuDetailsRow;
    private int groupedMessageMenuRow;
    private int messagesDividerRow;

    // Camera
    private int cameraHeaderRow;
    private int cameraTypeRow;
    private int extendedSettingsGroupRow;
    private int seamlessSwitchingRow;
    private int extendedFpsRow;
    private int cameraStabilizationRow;
    private int cameraMirrorRow;
    private int wideAngleCameraRow;
    private int videoMessagesCameraRow;
    private int rememberLastUsedCameraRow;
    private int staticZoomRow;
    private int cameraDividerRow;

    // Photos
    private int photoHeaderRow;
    private int alwaysSendHdRow;
    private int hideCameraTileRow;
    private int photoDividerRow;

    // Videos
    private int videosHeaderRow;
    private int doubleTapSeekDurationRow;
    private int preferOriginalQualityRow;
    private int swipeToPipRow;
    private int unmuteWithVolumeButtonsRow;
    private int pauseGroupRow;
    private int pauseVideoRow;
    private int pauseVoiceRow;
    private int pauseRoundRow;
    private int videosDividerRow;

    public OpenExteraChatsActivity() {
        super();
        ChatsConfig.ensureLoaded();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        stickerSizeRow = addRow("stickerSize");
        hideTimeOnStickersRow = addRow("hideTimeOnStickers");
        repliesGroupRow = addRow("replies");
        if (repliesExpanded) {
            replyColorsRow = addRow();
            replyEmojiRow = addRow();
            replyBackgroundRow = addRow();
        } else {
            replyColorsRow = replyEmojiRow = replyBackgroundRow = -1;
        }
        stickerSizeDividerRow = addRow();

        stickerShapeHeaderRow = addRow("stickerShapeHeader");
        stickerShapeRow = addRow("stickerShape");
        stickerShapeDividerRow = addRow();

        aiChatRow = addRow("aiChat");
        chatSettingsRow = addRow("chatSettings");
        linksDividerRow = addRow();

        stickersHeaderRow = addRow("stickersHeader");
        unlimitedRecentStickersRow = addRow("unlimitedRecentStickers");
        hideReactionsGroupRow = addRow("hideReactions");
        if (hideReactionsExpanded) {
            hideReactionsChannelsRow = addRow();
            hideReactionsGroupsRow = addRow();
            hideReactionsPrivateRow = addRow();
        } else {
            hideReactionsChannelsRow = hideReactionsGroupsRow = hideReactionsPrivateRow = -1;
        }
        stickersDividerRow = addRow();

        doubleTapHeaderRow = addRow("doubleTapHeader");
        doubleTapRow = addRow("doubleTapPreview");
        doubleTapIncomingRow = addRow("doubleTapIncoming");
        doubleTapOutgoingRow = addRow("doubleTapOutgoing");
        if (NaConfig.INSTANCE.getDoubleTapAction().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS
                || NaConfig.INSTANCE.getDoubleTapActionOut().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS) {
            doubleTapReactionRow = addRow("doubleTapReaction");
        } else {
            doubleTapReactionRow = -1;
        }
        doubleTapDividerRow = addRow();

        chatsHeaderRow = addRow("chatsHeader");
        bottomButtonRow = addRow("bottomButton");
        adminShortcutsRow = addRow("adminShortcuts");
        quickTransitionGroupRow = addRow("quickTransition");
        if (quickTransitionExpanded) {
            quickTransitionChannelsRow = addRow();
            quickTransitionTopicsRow = addRow();
        } else {
            quickTransitionChannelsRow = quickTransitionTopicsRow = -1;
        }
        disableGreetingRow = addRow("disableGreeting");
        hideKeyboardOnScrollRow = addRow("hideKeyboardOnScroll");
        addCommaRow = addRow("addCommaAfterMention");
        hideSendAsPeerRow = addRow("hideSendAsPeer");
        chatsDividerRow = addRow();

        messagesHeaderRow = addRow("messagesHeader");
        removeMessageTailRow = addRow("removeMessageTail");
        replaceEditedRow = addRow("replaceEdited");
        showOnlineStatusRow = addRow("showOnlineStatus");
        hideShareButtonRow = addRow("hideShareButton");
        showResultsBeforeVotingRow = addRow("showResultsBeforeVoting");
        messageMenuGroupRow = addRow("messageMenu");
        if (messageMenuExpanded) {
            menuCopyPhotoRow = addRow();
            menuSaveRow = addRow();
            menuRepeatRow = addRow();
            menuClearRow = addRow();
            menuHistoryRow = addRow();
            menuReportRow = addRow();
            menuDetailsRow = addRow();
        } else {
            menuCopyPhotoRow = menuSaveRow = menuRepeatRow = menuClearRow = menuHistoryRow = menuReportRow = menuDetailsRow = -1;
        }
        groupedMessageMenuRow = addRow("groupedMessageMenu");
        messagesDividerRow = addRow();

        cameraHeaderRow = addRow("cameraHeader");
        cameraTypeRow = addRow("cameraType");
        extendedSettingsGroupRow = addRow("extendedSettings");
        if (extendedSettingsExpanded) {
            seamlessSwitchingRow = addRow();
            extendedFpsRow = addRow();
            cameraStabilizationRow = addRow();
            cameraMirrorRow = addRow();
            wideAngleCameraRow = addRow();
        } else {
            seamlessSwitchingRow = extendedFpsRow = cameraStabilizationRow = cameraMirrorRow = wideAngleCameraRow = -1;
        }
        videoMessagesCameraRow = addRow("videoMessagesCamera");
        rememberLastUsedCameraRow = addRow("rememberLastUsedCamera");
        staticZoomRow = addRow("staticZoom");
        cameraDividerRow = addRow();

        photoHeaderRow = addRow("photoHeader");
        alwaysSendHdRow = addRow("alwaysSendInHD");
        hideCameraTileRow = addRow("hideCameraTile");
        photoDividerRow = addRow();

        videosHeaderRow = addRow("videosHeader");
        doubleTapSeekDurationRow = addRow("doubleTapSeekDuration");
        preferOriginalQualityRow = addRow("preferOriginalQuality");
        swipeToPipRow = addRow("swipeToPip");
        unmuteWithVolumeButtonsRow = addRow("unmuteWithVolumeButtons");
        pauseGroupRow = addRow("pauseOnMinimize");
        if (pauseExpanded) {
            pauseVideoRow = addRow();
            pauseVoiceRow = addRow();
            pauseRoundRow = addRow();
        } else {
            pauseVideoRow = pauseVoiceRow = pauseRoundRow = -1;
        }
        videosDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExteraChats);
    }

    @Override
    protected String getKey() {
        return "exteraless_chats";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private void reloadList() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    // ---- Значения для строк с выбором ----

    private CharSequence[] bottomButtonOptions() {
        return new CharSequence[]{
                getString(R.string.Hide),
                getString(R.string.ChannelMuteNoCaps),
                getString(R.string.OEChatsBottomButtonDiscuss)
        };
    }

    private CharSequence[] cameraTypeOptions() {
        return new CharSequence[]{
                getString(R.string.Default),
                getString(R.string.OEChatsCameraTypeCamera2),
                getString(R.string.OEChatsCameraTypeCameraX)
        };
    }

    private CharSequence[] videoMessagesCameraOptions() {
        return new CharSequence[]{
                getString(R.string.CameraInVideoMessagesFront),
                getString(R.string.CameraInVideoMessagesRear),
                getString(R.string.CameraInVideoMessagesAsk)
        };
    }

    private CharSequence[] seekDurationOptions() {
        int[] durations = ChatsConfig.SEEK_DURATIONS;
        CharSequence[] result = new CharSequence[durations.length];
        for (int a = 0; a < durations.length; a++) {
            result[a] = LocaleController.formatPluralString("Seconds", durations[a]);
        }
        return result;
    }

    private static int clampIndex(int value, int size) {
        return value >= 0 && value < size ? value : 0;
    }

    private static int count(boolean... values) {
        int c = 0;
        for (boolean v : values) {
            if (v) c++;
        }
        return c;
    }

    private static String ratio(int selected, int total) {
        return String.format(Locale.getDefault(), "%d/%d", selected, total);
    }

    private void showOptions(View view, int position, CharSequence[] options, ConfigItem item) {
        PopupBuilder builder = new PopupBuilder(view);
        builder.setItems(new ArrayList<CharSequence>(Arrays.asList(options)), (index, text) -> {
            item.setConfigInt(index);
            listAdapter.notifyItemChanged(position);
            return Unit.INSTANCE;
        });
        builder.show();
    }

    private void showDoubleTapOptions(View view, int position, boolean outgoing) {
        ArrayList<CharSequence> titles = new ArrayList<>();
        ArrayList<Integer> types = new ArrayList<>();

        titles.add(getString(R.string.Disable));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_NONE);
        titles.add(getString(R.string.SendReactions));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS);
        titles.add(getString(R.string.ShowReactions));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_SHOW_REACTIONS);
        titles.add(getString(R.string.TranslateMessage));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE);
        titles.add(getString(R.string.TranslateMessageLLM));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE_LLM);
        titles.add(getString(R.string.Reply));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_REPLY);
        titles.add(getString(R.string.AddToSavedMessages));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_SAVE);
        titles.add(getString(R.string.Repeat));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT);
        titles.add(getString(R.string.RepeatAsCopy));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT_AS_COPY);
        if (outgoing) {
            titles.add(getString(R.string.Edit));
            types.add(DoubleTap.DOUBLE_TAP_ACTION_EDIT);
        }
        titles.add(getString(R.string.Delete));
        types.add(DoubleTap.DOUBLE_TAP_ACTION_DELETE);

        PopupBuilder builder = new PopupBuilder(view);
        builder.setItems(titles, (index, text) -> {
            boolean hadReaction = doubleTapReactionRow != -1;
            if (outgoing) {
                NaConfig.INSTANCE.getDoubleTapActionOut().setConfigInt(types.get(index));
            } else {
                NaConfig.INSTANCE.getDoubleTapAction().setConfigInt(types.get(index));
            }
            if (doubleTapCell != null) {
                doubleTapCell.updateIcons(0, true);
            }
            boolean hasReaction = NaConfig.INSTANCE.getDoubleTapAction().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS
                    || NaConfig.INSTANCE.getDoubleTapActionOut().Int() == DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS;
            if (hadReaction != hasReaction) {
                reloadList();
            } else {
                listAdapter.notifyItemChanged(position);
            }
            return Unit.INSTANCE;
        });
        builder.show();
    }

    /**
     * «Безлимит недавних стикеров» из exteraGram — один тумблер поверх двух рабочих ключей
     * NagramX: лимита недавних ({@link NekoConfig#maxRecentStickerCount}, шкала 20…200) и
     * безлимитных избранных ({@link NekoConfig#unlimitedFavedStickers}).
     */
    private static final int RECENT_STICKERS_DEFAULT = 20;
    private static final int RECENT_STICKERS_MAX = 200;

    private static boolean isUnlimitedRecentStickers() {
        return NekoConfig.maxRecentStickerCount.Int() > RECENT_STICKERS_DEFAULT
                || NekoConfig.unlimitedFavedStickers.Bool();
    }

    private void toggleUnlimitedRecentStickers(View view) {
        boolean value = !isUnlimitedRecentStickers();
        NekoConfig.maxRecentStickerCount.setConfigInt(value ? RECENT_STICKERS_MAX : RECENT_STICKERS_DEFAULT);
        NekoConfig.unlimitedFavedStickers.setConfigBool(value);
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
    }

    /**
     * «Быстрые действия администратора» из exteraGram — один тумблер поверх пяти пунктов меню чата
     * NagramX ({@code NaConfig.shortcuts*}) и пункта «Права администратора» в меню сообщения
     * ({@link NekoConfig#showAdminActions}). Включён, пока включён хотя бы один пункт.
     */
    private static ConfigItem[] adminShortcutItems() {
        return new ConfigItem[]{
                NaConfig.INSTANCE.getShortcutsAdministrators(),
                NaConfig.INSTANCE.getShortcutsRecentActions(),
                NaConfig.INSTANCE.getShortcutsStatistics(),
                NaConfig.INSTANCE.getShortcutsPermissions(),
                NaConfig.INSTANCE.getShortcutsMembers(),
                NekoConfig.showAdminActions
        };
    }

    private static boolean isQuickAdminShortcuts() {
        for (ConfigItem item : adminShortcutItems()) {
            if (item.Bool()) {
                return true;
            }
        }
        return false;
    }

    private void toggleQuickAdminShortcuts(View view) {
        boolean value = !isQuickAdminShortcuts();
        for (ConfigItem item : adminShortcutItems()) {
            item.setConfigBool(value);
        }
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
    }

    private void toggleHighQualityPhoto(View view) {
        boolean value = !SharedConfig.photoHighQualityDefault;
        SharedConfig.photoHighQualityDefault = value;
        SharedPreferences prefs = ApplicationLoader.applicationContext
                .getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("photoHighQualityDefault", value).apply();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        // Разворачивающиеся группы
        if (position == repliesGroupRow) {
            repliesExpanded = !repliesExpanded;
            reloadList();
            return;
        } else if (position == hideReactionsGroupRow) {
            hideReactionsExpanded = !hideReactionsExpanded;
            reloadList();
            return;
        } else if (position == quickTransitionGroupRow) {
            quickTransitionExpanded = !quickTransitionExpanded;
            reloadList();
            return;
        } else if (position == messageMenuGroupRow) {
            messageMenuExpanded = !messageMenuExpanded;
            reloadList();
            return;
        } else if (position == extendedSettingsGroupRow) {
            extendedSettingsExpanded = !extendedSettingsExpanded;
            reloadList();
            return;
        } else if (position == pauseGroupRow) {
            pauseExpanded = !pauseExpanded;
            reloadList();
            return;
        }

        // Строки с popup-выбором
        if (position == doubleTapIncomingRow) {
            showDoubleTapOptions(view, position, false);
            return;
        } else if (position == doubleTapOutgoingRow) {
            showDoubleTapOptions(view, position, true);
            return;
        } else if (position == doubleTapReactionRow) {
            DoubleTapCell.SetReactionCell.showSelectStatusDialog((DoubleTapCell.SetReactionCell) view, this);
            return;
        } else if (position == bottomButtonRow) {
            showOptions(view, position, bottomButtonOptions(), NaConfig.INSTANCE.getLeftBottomButton());
            return;
        } else if (position == cameraTypeRow) {
            showOptions(view, position, cameraTypeOptions(), ChatsConfig.cameraType);
            return;
        } else if (position == videoMessagesCameraRow) {
            showOptions(view, position, videoMessagesCameraOptions(), NaConfig.INSTANCE.getCameraInVideoMessages());
            return;
        } else if (position == doubleTapSeekDurationRow) {
            showOptions(view, position, seekDurationOptions(), ChatsConfig.doubleTapSeekDuration);
            return;
        } else if (position == alwaysSendHdRow) {
            toggleHighQualityPhoto(view);
            return;
        } else if (position == unlimitedRecentStickersRow) {
            toggleUnlimitedRecentStickers(view);
            return;
        } else if (position == adminShortcutsRow) {
            toggleQuickAdminShortcuts(view);
            return;
        } else if (position == aiChatRow || position == chatSettingsRow) {
            // Заглушки: в openExtera отдельного экрана нет.
            return;
        }

        ConfigItem item = configForRow(position);
        if (item == null) {
            return;
        }
        boolean value = item.toggleConfigBool();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        } else if (view instanceof TextCheckbox2Cell) {
            ((TextCheckbox2Cell) view).setChecked(value);
            // обновить счётчик заголовка группы
            notifyGroupHeaderForMember(position);
        }
        if (position == hideTimeOnStickersRow && stickerSizeCell != null) {
            stickerSizeCell.invalidate();
        }
    }

    private void notifyGroupHeaderForMember(int position) {
        int header = -1;
        if (position == replyColorsRow || position == replyEmojiRow || position == replyBackgroundRow) {
            header = repliesGroupRow;
        } else if (position == hideReactionsChannelsRow || position == hideReactionsGroupsRow || position == hideReactionsPrivateRow) {
            header = hideReactionsGroupRow;
        } else if (position == quickTransitionChannelsRow || position == quickTransitionTopicsRow) {
            header = quickTransitionGroupRow;
        } else if (position == menuCopyPhotoRow || position == menuSaveRow || position == menuRepeatRow
                || position == menuClearRow || position == menuHistoryRow || position == menuReportRow || position == menuDetailsRow) {
            header = messageMenuGroupRow;
        } else if (position == seamlessSwitchingRow || position == extendedFpsRow || position == cameraStabilizationRow
                || position == cameraMirrorRow || position == wideAngleCameraRow) {
            header = extendedSettingsGroupRow;
        } else if (position == pauseVideoRow || position == pauseVoiceRow || position == pauseRoundRow) {
            header = pauseGroupRow;
        }
        if (header != -1) {
            listAdapter.notifyItemChanged(header);
        }
    }

    private ConfigItem configForRow(int position) {
        if (position == hideTimeOnStickersRow) return NekoConfig.hideTimeForSticker;
        if (position == replyColorsRow) return ChatsConfig.replyColors;
        if (position == replyEmojiRow) return ChatsConfig.replyEmoji;
        if (position == replyBackgroundRow) return ChatsConfig.replyBackground;
        if (position == hideReactionsChannelsRow) return ChatsConfig.hideReactionsInChannels;
        if (position == hideReactionsGroupsRow) return ChatsConfig.hideReactionsInGroups;
        if (position == hideReactionsPrivateRow) return ChatsConfig.hideReactionsInPrivate;
        if (position == quickTransitionChannelsRow) return ChatsConfig.quickTransitionForChannels;
        if (position == quickTransitionTopicsRow) return ChatsConfig.quickTransitionForTopics;
        if (position == disableGreetingRow) return NekoConfig.dontSendGreetingSticker;
        if (position == hideKeyboardOnScrollRow) return NekoConfig.hideKeyboardOnChatScroll;
        if (position == addCommaRow) return OpenExteraConfig.addCommaAfterMention;
        if (position == hideSendAsPeerRow) return NekoConfig.hideSendAsChannel;
        if (position == removeMessageTailRow) return ChatsConfig.removeMessageTail;
        if (position == replaceEditedRow) return ChatsConfig.replaceEditedWithIcon;
        if (position == showOnlineStatusRow) return NaConfig.INSTANCE.getShowOnlineStatus();
        if (position == hideShareButtonRow) return NaConfig.INSTANCE.getHideShareButtonInChannel();
        if (position == showResultsBeforeVotingRow) return ChatsConfig.showResultsBeforeVoting;
        if (position == menuCopyPhotoRow) return NaConfig.INSTANCE.getShowCopyPhoto();
        if (position == menuSaveRow) return NekoConfig.showAddToSavedMessages;
        if (position == menuRepeatRow) return NekoConfig.showRepeat;
        if (position == menuClearRow) return NekoConfig.showDeleteDownloadedFile;
        if (position == menuHistoryRow) return NekoConfig.showViewHistory;
        if (position == menuReportRow) return NekoConfig.showReport;
        if (position == menuDetailsRow) return NekoConfig.showMessageDetails;
        if (position == groupedMessageMenuRow) return NaConfig.INSTANCE.getGroupedMessageMenu();
        if (position == seamlessSwitchingRow) return ChatsConfig.cameraSeamlessSwitching;
        if (position == extendedFpsRow) return ChatsConfig.extendedFramesPerSecond;
        if (position == cameraStabilizationRow) return ChatsConfig.cameraStabilization;
        if (position == cameraMirrorRow) return ChatsConfig.cameraMirrorMode;
        if (position == wideAngleCameraRow) return ChatsConfig.startWithWideAngleCamera;
        if (position == rememberLastUsedCameraRow) return ChatsConfig.rememberLastUsedCamera;
        if (position == staticZoomRow) return ChatsConfig.staticZoom;
        if (position == hideCameraTileRow) return ChatsConfig.hideCameraTile;
        if (position == preferOriginalQualityRow) return ChatsConfig.preferOriginalQuality;
        if (position == swipeToPipRow) return ChatsConfig.swipeToPip;
        if (position == unmuteWithVolumeButtonsRow) return ChatsConfig.unmuteWithVolumeButtons;
        if (position == pauseVideoRow) return NekoConfig.autoPauseVideo;
        if (position == pauseVoiceRow) return ChatsConfig.pauseOnMinimizeVoice;
        if (position == pauseRoundRow) return ChatsConfig.pauseOnMinimizeRound;
        return null;
    }

    /** Слайдер размера стикеров с живым превью переписки. */
    /**
     * Слайдер размера стикеров. Оформление перенесено из exteraGram 12.9.0
     * (AltSeekbar): синий жирный заголовок 15sp, рядом плашка со значением
     * (12sp bold, фон — тот же цвет с alpha 0.15, скругление 4dp), под слайдером
     * серые подписи краёв 13sp. Диапазон 4..20, как у exteraGram.
     */
    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final TextView headerValue;
        private final int startStickerSize = 4;
        private final int endStickerSize = 20;

        public StickerSizeCell(Context context) {
            super(context);
            setWillNotDraw(false);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            title.setText(getString(R.string.StickerSize));
            header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            headerValue = new TextView(context);
            headerValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            headerValue.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            headerValue.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            headerValue.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2));
            headerValue.setBackground(Theme.createRoundRectDrawable(dp(4),
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f)));
            header.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

            addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 17, 21, 0));

            FrameLayout edges = new FrameLayout(context);
            TextView left = new TextView(context);
            left.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            left.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            left.setText(getString(R.string.OEStickerSizeSmall));
            edges.addView(left, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            TextView right = new TextView(context);
            right.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            right.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            right.setText(getString(R.string.OEStickerSizeLarge));
            edges.addView(right, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            addView(edges, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 52, 21, 0));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endStickerSize - startStickerSize + 1);
            sizeBar.setDelegate((stop, progress) -> {
                NekoConfig.stickerSize.setConfigFloat(startStickerSize
                        + (endStickerSize - startStickerSize) * progress);
                updateValueText();
                StickerSizeCell.this.invalidate();
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                    Gravity.LEFT | Gravity.TOP, 9, 78, 9, 0));

            messagesCell = new StickerSizePreviewMessagesCell(context, OpenExteraChatsActivity.this);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 126, 0, 0));

            updateValueText();
        }

        private void updateValueText() {
            headerValue.setText(String.valueOf(Math.round(NekoConfig.stickerSize.Float())));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((NekoConfig.stickerSize.Float() - startStickerSize)
                    / (float) (endStickerSize - startStickerSize));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (messagesCell != null) messagesCell.invalidate();
            if (sizeBar != null) sizeBar.invalidate();
            updateValueText();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_STICKER_SIZE:
                    stickerSizeCell = new StickerSizeCell(mContext);
                    stickerSizeCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = stickerSizeCell;
                    break;
                case TYPE_STICKER_SHAPE:
                    stickerShapeCell = new StickerShapeCell(mContext) {
                        @Override
                        protected void updateStickerPreview() {
                            if (stickerSizeCell != null) {
                                stickerSizeCell.invalidate();
                            }
                        }
                    };
                    view = stickerShapeCell;
                    break;
                case TYPE_DOUBLE_TAP:
                    doubleTapCell = new DoubleTapCell(mContext);
                    view = doubleTapCell;
                    break;
                case TYPE_SET_REACTION:
                    DoubleTapCell.SetReactionCell reactionCell = new DoubleTapCell.SetReactionCell(mContext);
                    view = reactionCell;
                    break;
                default:
                    return super.onCreateViewHolder(parent, viewType);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_STICKER_SIZE || type == TYPE_STICKER_SHAPE || type == TYPE_DOUBLE_TAP) {
                return false;
            }
            if (type == TYPE_SET_REACTION) {
                return true;
            }
            return super.isEnabled(holder);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_SET_REACTION:
                    ((DoubleTapCell.SetReactionCell) holder.itemView).update(false);
                    break;
                case TYPE_HEADER:
                    bindHeader((HeaderCell) holder.itemView, position);
                    break;
                case TYPE_CHECK:
                    bindCheck((TextCheckCell) holder.itemView, position);
                    break;
                case TYPE_CHECKBOX:
                    bindCheckbox((TextCheckbox2Cell) holder.itemView, position);
                    break;
                case TYPE_TEXT:
                    bindText((TextCell) holder.itemView, position);
                    break;
                case TYPE_SETTINGS:
                    bindSettings((TextSettingsCell) holder.itemView, position);
                    break;
                case TYPE_INFO_PRIVACY:
                    bindInfo((TextInfoPrivacyCell) holder.itemView, position);
                    break;
            }
        }

        private void bindHeader(HeaderCell cell, int position) {
            if (position == stickerShapeHeaderRow) {
                cell.setText(getString(R.string.OEChatsStickerShape));
            } else if (position == stickersHeaderRow) {
                cell.setText(getString(R.string.OEChatsStickersAndEmoji));
            } else if (position == doubleTapHeaderRow) {
                cell.setText(getString(R.string.OEChatsDoubleTap));
            } else if (position == chatsHeaderRow) {
                cell.setText(getString(R.string.OpenExteraChats));
            } else if (position == messagesHeaderRow) {
                cell.setText(getString(R.string.OEChatsMessages));
            } else if (position == cameraHeaderRow) {
                cell.setText(getString(R.string.VoipCamera));
            } else if (position == photoHeaderRow) {
                cell.setText(getString(R.string.OEChatsPhoto));
            } else if (position == videosHeaderRow) {
                cell.setText(getString(R.string.OEChatsVideos));
            }
        }

        private void bindCheck(TextCheckCell cell, int position) {
            if (position == hideTimeOnStickersRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideTimeOnStickers), NekoConfig.hideTimeForSticker.Bool(), true);
            } else if (position == unlimitedRecentStickersRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsUnlimitedRecentStickers), isUnlimitedRecentStickers(), true);
            } else if (position == adminShortcutsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsAdminShortcuts), isQuickAdminShortcuts(), true);
            } else if (position == disableGreetingRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsDisableGreetingSticker), NekoConfig.dontSendGreetingSticker.Bool(), true);
            } else if (position == hideKeyboardOnScrollRow) {
                cell.setTextAndCheck(getString(R.string.HideKeyboardOnChatScroll), NekoConfig.hideKeyboardOnChatScroll.Bool(), true);
            } else if (position == addCommaRow) {
                cell.setTextAndCheck(getString(R.string.AddCommaAfterMention), OpenExteraConfig.addCommaAfterMention.Bool(), true);
            } else if (position == hideSendAsPeerRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideSendAsPeer), NekoConfig.hideSendAsChannel.Bool(), false);
            } else if (position == removeMessageTailRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsRemoveMessageTail), ChatsConfig.removeMessageTail.Bool(), true);
            } else if (position == replaceEditedRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsReplaceEditedWithIcon), ChatsConfig.replaceEditedWithIcon.Bool(), true);
            } else if (position == showOnlineStatusRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsShowOnlineStatus), NaConfig.INSTANCE.getShowOnlineStatus().Bool(), true);
            } else if (position == hideShareButtonRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideShareButton), NaConfig.INSTANCE.getHideShareButtonInChannel().Bool(), true);
            } else if (position == showResultsBeforeVotingRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsShowResultsBeforeVoting), ChatsConfig.showResultsBeforeVoting.Bool(), true);
            } else if (position == groupedMessageMenuRow) {
                cell.setTextAndCheck(getString(R.string.GroupedMessageMenu), NaConfig.INSTANCE.getGroupedMessageMenu().Bool(), false);
            } else if (position == rememberLastUsedCameraRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsRememberLastUsedCamera), ChatsConfig.rememberLastUsedCamera.Bool(), true);
            } else if (position == staticZoomRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsStaticZoom), ChatsConfig.staticZoom.Bool(), false);
            } else if (position == alwaysSendHdRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsAlwaysSendInHD), SharedConfig.photoHighQualityDefault, true);
            } else if (position == hideCameraTileRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideCameraTile), ChatsConfig.hideCameraTile.Bool(), false);
            } else if (position == preferOriginalQualityRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsPreferOriginalQuality), ChatsConfig.preferOriginalQuality.Bool(), true);
            } else if (position == swipeToPipRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsSwipeToPip), ChatsConfig.swipeToPip.Bool(), true);
            } else if (position == unmuteWithVolumeButtonsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsUnmuteWithVolumeButtons), ChatsConfig.unmuteWithVolumeButtons.Bool(), true);
            }
        }

        private void bindCheckbox(TextCheckbox2Cell cell, int position) {
            if (position == replyColorsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsReplyColors), ChatsConfig.replyColors.Bool(), true);
            } else if (position == replyEmojiRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsReplyEmoji), ChatsConfig.replyEmoji.Bool(), true);
            } else if (position == replyBackgroundRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsReplyBackground), ChatsConfig.replyBackground.Bool(), false);
            } else if (position == hideReactionsChannelsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideReactionsChannels), ChatsConfig.hideReactionsInChannels.Bool(), true);
            } else if (position == hideReactionsGroupsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideReactionsGroups), ChatsConfig.hideReactionsInGroups.Bool(), true);
            } else if (position == hideReactionsPrivateRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsHideReactionsPrivate), ChatsConfig.hideReactionsInPrivate.Bool(), false);
            } else if (position == quickTransitionChannelsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsQuickTransitionChannels), ChatsConfig.quickTransitionForChannels.Bool(), true);
            } else if (position == quickTransitionTopicsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsQuickTransitionTopics), ChatsConfig.quickTransitionForTopics.Bool(), false);
            } else if (position == menuCopyPhotoRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuCopyPhoto), NaConfig.INSTANCE.getShowCopyPhoto().Bool(), true);
            } else if (position == menuSaveRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuSave), NekoConfig.showAddToSavedMessages.Bool(), true);
            } else if (position == menuRepeatRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuRepeat), NekoConfig.showRepeat.Bool(), true);
            } else if (position == menuClearRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuClear), NekoConfig.showDeleteDownloadedFile.Bool(), true);
            } else if (position == menuHistoryRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuHistory), NekoConfig.showViewHistory.Bool(), true);
            } else if (position == menuReportRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuReport), NekoConfig.showReport.Bool(), true);
            } else if (position == menuDetailsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsMenuDetails), NekoConfig.showMessageDetails.Bool(), false);
            } else if (position == seamlessSwitchingRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsSeamlessSwitching), ChatsConfig.cameraSeamlessSwitching.Bool(), true);
            } else if (position == extendedFpsRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsExtendedFps), ChatsConfig.extendedFramesPerSecond.Bool(), true);
            } else if (position == cameraStabilizationRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsCameraStabilization), ChatsConfig.cameraStabilization.Bool(), true);
            } else if (position == cameraMirrorRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsCameraMirrorMode), ChatsConfig.cameraMirrorMode.Bool(), true);
            } else if (position == wideAngleCameraRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsWideAngleCamera), ChatsConfig.startWithWideAngleCamera.Bool(), false);
            } else if (position == pauseVideoRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsPauseVideo), NekoConfig.autoPauseVideo.Bool(), true);
            } else if (position == pauseVoiceRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsPauseVoice), ChatsConfig.pauseOnMinimizeVoice.Bool(), true);
            } else if (position == pauseRoundRow) {
                cell.setTextAndCheck(getString(R.string.OEChatsPauseRound), ChatsConfig.pauseOnMinimizeRound.Bool(), false);
            }
        }

        private void bindText(TextCell cell, int position) {
            if (position == aiChatRow) {
                cell.setTextAndValueAndIcon(getString(R.string.OEChatsAiChat), getString(R.string.OEChatsAiChatInfo), R.drawable.input_bot2, true);
            } else if (position == chatSettingsRow) {
                cell.setTextAndValueAndIcon(getString(R.string.OEChatsChatSettings), getString(R.string.OEChatsChatSettingsInfo), R.drawable.msg_discussion, false);
            }
        }

        private void bindSettings(TextSettingsCell cell, int position) {
            if (position == repliesGroupRow) {
                cell.setTextAndValue(getString(R.string.OEChatsReplies),
                        ratio(count(ChatsConfig.replyColors.Bool(), ChatsConfig.replyEmoji.Bool(), ChatsConfig.replyBackground.Bool()), 3), true);
            } else if (position == hideReactionsGroupRow) {
                cell.setTextAndValue(getString(R.string.OEChatsHideReactions),
                        ratio(count(ChatsConfig.hideReactionsInChannels.Bool(), ChatsConfig.hideReactionsInGroups.Bool(), ChatsConfig.hideReactionsInPrivate.Bool()), 3), true);
            } else if (position == doubleTapIncomingRow) {
                cell.setTextAndValue(getString(R.string.DoubleTapIncoming),
                        DoubleTap.doubleTapActionMap.get(NaConfig.INSTANCE.getDoubleTapAction().Int()), true);
            } else if (position == doubleTapOutgoingRow) {
                cell.setTextAndValue(getString(R.string.DoubleTapOutgoing),
                        DoubleTap.doubleTapActionMap.get(NaConfig.INSTANCE.getDoubleTapActionOut().Int()), doubleTapReactionRow != -1);
            } else if (position == bottomButtonRow) {
                CharSequence[] options = bottomButtonOptions();
                cell.setTextAndValue(getString(R.string.OEChatsBottomButton),
                        options[clampIndex(NaConfig.INSTANCE.getLeftBottomButton().Int(), options.length)], true);
            } else if (position == quickTransitionGroupRow) {
                cell.setTextAndValue(getString(R.string.OEChatsQuickTransitions),
                        ratio(count(ChatsConfig.quickTransitionForChannels.Bool(), ChatsConfig.quickTransitionForTopics.Bool()), 2), true);
            } else if (position == messageMenuGroupRow) {
                cell.setTextAndValue(getString(R.string.MessageMenu),
                        ratio(count(NaConfig.INSTANCE.getShowCopyPhoto().Bool(), NekoConfig.showAddToSavedMessages.Bool(),
                                NekoConfig.showRepeat.Bool(), NekoConfig.showDeleteDownloadedFile.Bool(), NekoConfig.showViewHistory.Bool(),
                                NekoConfig.showReport.Bool(), NekoConfig.showMessageDetails.Bool()), 7), true);
            } else if (position == cameraTypeRow) {
                CharSequence[] options = cameraTypeOptions();
                cell.setTextAndValue(getString(R.string.OEChatsCameraType),
                        options[clampIndex(ChatsConfig.cameraType.Int(), options.length)], true);
            } else if (position == extendedSettingsGroupRow) {
                cell.setTextAndValue(getString(R.string.OEChatsExtendedSettings),
                        ratio(count(ChatsConfig.cameraSeamlessSwitching.Bool(), ChatsConfig.extendedFramesPerSecond.Bool(),
                                ChatsConfig.cameraStabilization.Bool(), ChatsConfig.cameraMirrorMode.Bool(), ChatsConfig.startWithWideAngleCamera.Bool()), 5), true);
            } else if (position == videoMessagesCameraRow) {
                CharSequence[] options = videoMessagesCameraOptions();
                cell.setTextAndValue(getString(R.string.CameraInVideoMessages),
                        options[clampIndex(NaConfig.INSTANCE.getCameraInVideoMessages().Int(), options.length)], true);
            } else if (position == doubleTapSeekDurationRow) {
                CharSequence[] options = seekDurationOptions();
                cell.setTextAndValue(getString(R.string.OEChatsDoubleTapSeekDuration),
                        options[clampIndex(ChatsConfig.doubleTapSeekDuration.Int(), options.length)], true);
            } else if (position == pauseGroupRow) {
                cell.setTextAndValue(getString(R.string.OEChatsPauseOnMinimize),
                        ratio(count(NekoConfig.autoPauseVideo.Bool(), ChatsConfig.pauseOnMinimizeVoice.Bool(),
                                ChatsConfig.pauseOnMinimizeRound.Bool()), 3), false);
            }
        }

        private void bindInfo(TextInfoPrivacyCell cell, int position) {
            boolean bottom = position == videosDividerRow;
            if (position == doubleTapDividerRow) {
                cell.setText(getString(R.string.OEChatsDoubleTapInfo));
            } else if (position == chatsDividerRow) {
                cell.setText(getString(R.string.OEChatsHideSendAsPeerInfo));
            } else if (position == stickersDividerRow) {
                cell.setText(getString(R.string.OEChatsHideReactionsInfo));
            } else if (position == messagesDividerRow) {
                cell.setText(getString(R.string.OEChatsGlassMessageMenuInfo));
            } else if (position == cameraDividerRow) {
                cell.setText(getString(R.string.OEChatsStaticZoomInfo));
            } else if (position == photoDividerRow) {
                cell.setText(getString(R.string.OEChatsHideCameraTileInfo));
            } else if (position == videosDividerRow) {
                cell.setText(getString(R.string.OEChatsPauseOnMinimizeInfo));
            } else {
                cell.setText(null);
            }
            cell.setBackground(Theme.getThemedDrawable(mContext,
                    bottom ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                    Theme.key_windowBackgroundGrayShadow));
        }

        @Override
        public int getItemViewType(int position) {
            if (position == stickerSizeRow) return TYPE_STICKER_SIZE;
            if (position == stickerShapeRow) return TYPE_STICKER_SHAPE;
            if (position == doubleTapRow) return TYPE_DOUBLE_TAP;
            if (position == doubleTapReactionRow) return TYPE_SET_REACTION;
            if (isHeader(position)) return TYPE_HEADER;
            if (isDivider(position)) return TYPE_INFO_PRIVACY;
            if (position == aiChatRow || position == chatSettingsRow) return TYPE_TEXT;
            if (isSettings(position)) return TYPE_SETTINGS;
            if (isCheckbox(position)) return TYPE_CHECKBOX;
            return TYPE_CHECK;
        }

        private boolean isHeader(int position) {
            return position == stickerShapeHeaderRow
                    || position == stickersHeaderRow || position == doubleTapHeaderRow
                    || position == chatsHeaderRow || position == messagesHeaderRow
                    || position == cameraHeaderRow
                    || position == photoHeaderRow || position == videosHeaderRow;
        }

        private boolean isDivider(int position) {
            return position == stickerSizeDividerRow || position == stickerShapeDividerRow
                    || position == linksDividerRow || position == stickersDividerRow
                    || position == doubleTapDividerRow || position == chatsDividerRow
                    || position == messagesDividerRow
                    || position == cameraDividerRow || position == photoDividerRow
                    || position == videosDividerRow;
        }

        private boolean isSettings(int position) {
            return position == repliesGroupRow || position == hideReactionsGroupRow
                    || position == doubleTapIncomingRow || position == doubleTapOutgoingRow
                    || position == bottomButtonRow || position == quickTransitionGroupRow
                    || position == messageMenuGroupRow
                    || position == cameraTypeRow || position == extendedSettingsGroupRow
                    || position == videoMessagesCameraRow || position == doubleTapSeekDurationRow
                    || position == pauseGroupRow;
        }

        private boolean isCheckbox(int position) {
            return position == replyColorsRow || position == replyEmojiRow || position == replyBackgroundRow
                    || position == hideReactionsChannelsRow || position == hideReactionsGroupsRow || position == hideReactionsPrivateRow
                    || position == quickTransitionChannelsRow || position == quickTransitionTopicsRow
                    || position == menuCopyPhotoRow || position == menuSaveRow || position == menuRepeatRow
                    || position == menuClearRow || position == menuHistoryRow || position == menuReportRow || position == menuDetailsRow
                    || position == seamlessSwitchingRow || position == extendedFpsRow || position == cameraStabilizationRow
                    || position == cameraMirrorRow || position == wideAngleCameraRow
                    || position == pauseVideoRow || position == pauseVoiceRow || position == pauseRoundRow;
        }
    }
}
