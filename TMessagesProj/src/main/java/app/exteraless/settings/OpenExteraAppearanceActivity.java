package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersPreviewCell;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.exteraless.appearance.ChatListPreviewCell;
import app.exteraless.appearance.FoldersPreviewCell;
import app.exteraless.icons.IconPacksActivity;
import app.exteraless.pillstack.PillStackSettingsActivity;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/**
 * Экран «Оформление» раздела openExtera — визуальный порт AppearancePreferencesActivity
 * из exteraGram. Живые превью (аватарки, список чатов, папки) портированы 1:1 из
 * exteraGram 10.10.1 в пакет {@link app.exteraless.appearance}.
 *
 * Настройки, у которых в NagramX уже есть аналог, привязаны к существующим ConfigItem
 * (NaConfig / NekoConfig). Чисто визуальные настройки, которых в NagramX нет,
 * хранятся в {@link AppearanceConfig} и помечены в отчёте как «только UI».
 */
public class OpenExteraAppearanceActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_AVATAR_CORNERS = 100;
    private static final int TYPE_CHAT_LIST = 101;
    private static final int TYPE_FOLDERS = 102;
    private static final int TYPE_SECTION_SLIDER = 103;

    // Appearance
    private int appearanceHeaderRow;
    private int useSystemFontsRow;
    private int useSystemEmojiRow;
    private int switchStyleRow;
    private int sliderStyleRow;
    private int gooeyAvatarRow;
    private int customThemesRow;
    private int appearanceDividerRow;

    // Sections (UI only)
    private int sectionsHeaderRow;
    private int sectionRadiusRow;
    private int separateHeadersRow;
    private int dividerStyleRow;
    private int sectionsDividerRow;

    // Blur
    private int blurHeaderRow;
    private int glassOutlineRow;
    private int glassMessageMenuRow;
    private int forceBlurRow;
    private int disableAvatarBlurRow;
    private int blurDividerRow;

    // Avatar corners
    private int avatarCornersPreviewRow;
    private int singleCornerRadiusRow;
    private int avatarsDividerRow;

    // Chat list
    private int chatListHeaderRow;
    private int chatListPreviewRow;
    private int forceSnowRow;
    private int centerTitleRow;
    private int hideStoriesRow;
    private int hideFloatingButtonRow;
    private int hideSearchBarRow;
    private int senderMiniAvatarsRow;
    private int titleTextRow;
    private int chatListDividerRow;

    // Folders
    private int foldersHeaderRow;
    private int foldersPreviewRow;
    private int tabTitleStyleRow;
    private int tabCounterRow;
    private int hideAllChatsRow;
    private int foldersDividerRow;

    // Links
    private int appNavigationRow;
    private int iconPacksRow;
    private int pillStackRow;
    private int linksDividerRow;

    private AvatarCornersPreviewCell avatarCornersPreviewCell;
    private ChatListPreviewCell chatListPreviewCell;
    private FoldersPreviewCell foldersPreviewCell;

    public OpenExteraAppearanceActivity() {
        super();
        AppearanceConfig.init();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        avatarCornersPreviewRow = addRow("avatarCorners");
        singleCornerRadiusRow = addRow("singleCornerRadius");
        avatarsDividerRow = addRow();

        chatListHeaderRow = addRow("chatListHeader");
        chatListPreviewRow = addRow("chatListPreview");
        forceSnowRow = addRow("forceSnow");
        centerTitleRow = addRow("centerTitle");
        hideStoriesRow = addRow("hideStories");
        hideFloatingButtonRow = addRow("hideFloatingButton");
        hideSearchBarRow = addRow("hideSearchBar");
        senderMiniAvatarsRow = addRow("senderMiniAvatars");
        titleTextRow = addRow("titleText");
        chatListDividerRow = addRow();

        foldersHeaderRow = addRow("foldersHeader");
        foldersPreviewRow = addRow("foldersPreview");
        tabTitleStyleRow = addRow("tabTitleStyle");
        tabCounterRow = addRow("tabCounter");
        hideAllChatsRow = addRow("hideAllChats");
        foldersDividerRow = addRow();

        // Порядок как в 12.9.0: строки-переходы идут сразу после «Chat Folders»,
        // до секции общего вида.
        appNavigationRow = addRow("appNavigation");
        iconPacksRow = addRow("iconPacks");
        pillStackRow = addRow("pillStack");
        linksDividerRow = addRow();

        appearanceHeaderRow = addRow("appearanceHeader");
        useSystemFontsRow = addRow("useSystemFonts");
        useSystemEmojiRow = addRow("useSystemEmoji");
        switchStyleRow = addRow("switchStyle");
        sliderStyleRow = addRow("sliderStyle");
        gooeyAvatarRow = addRow("gooeyAvatar");
        customThemesRow = addRow("customThemes");
        appearanceDividerRow = addRow();

        sectionsHeaderRow = addRow("sectionsHeader");
        sectionRadiusRow = addRow("sectionRadius");
        separateHeadersRow = addRow("separateHeaders");
        dividerStyleRow = addRow("dividerStyle");
        sectionsDividerRow = addRow();

        blurHeaderRow = addRow("blurHeader");
        glassOutlineRow = addRow("glassOutline");
        glassMessageMenuRow = addRow("glassMessageMenu");
        forceBlurRow = addRow("forceBlur");
        disableAvatarBlurRow = addRow("disableAvatarBlur");
        blurDividerRow = addRow();

    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEAppearanceTitle);
    }

    @Override
    protected String getKey() {
        return "exteraless_appearance";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private void rebuildAll() {
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }

    private void showRestartHint() {
        if (getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info, getString(R.string.OEAppearanceNeedRestart))
                .show();
    }

    private void showSelector(int position, String title, CharSequence[] items, ConfigItem item, Runnable after) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setItems(items, (dialog, which) -> {
            item.setConfigInt(which);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
            if (after != null) {
                after.run();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private CharSequence[] titleTextOptions() {
        return new CharSequence[]{
                getString(R.string.OEAppearanceTitleTextApp),
                getString(R.string.OEAppearanceTitleTextUsername),
                getString(R.string.OEAppearanceTitleTextName)
        };
    }

    private CharSequence[] styleOptions() {
        return new CharSequence[]{
                getString(R.string.Default),
                getString(R.string.StyleModern),
                getString(R.string.StyleMaterialDesign3)
        };
    }

    private void onDividerStyleChanged() {
        // 0 — скрыт, 1 — линия, 2 — сегменты. Скрытый привязываем к реальному NaConfig.hideDividers,
        // чтобы не разъезжался экран NekoGeneralSettingsActivity.
        NaConfig.INSTANCE.getHideDividers().setConfigBool(AppearanceConfig.dividerStyle.Int() == 0);
        // Theme.getColor читает закешированное значение — сбросить кэш обязательно.
        AppearanceConfig.invalidateDividerStyle();
        rebuildAll();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == iconPacksRow) {
            presentFragment(new IconPacksActivity());
            return;
        } else if (position == pillStackRow) {
            presentFragment(new PillStackSettingsActivity());
            return;
        } else if (position == appNavigationRow) {
            presentFragment(new OpenExteraAppNavigationActivity());
            return;
        } else if (position == switchStyleRow) {
            showSelector(position, getString(R.string.OEAppearanceSwitchStyle), styleOptions(),
                    NaConfig.INSTANCE.getSwitchStyle(), this::rebuildAll);
            return;
        } else if (position == sliderStyleRow) {
            showSelector(position, getString(R.string.OEAppearanceSliderStyle), styleOptions(),
                    NaConfig.INSTANCE.getSliderStyle(), this::rebuildAll);
            return;
        } else if (position == dividerStyleRow) {
            showSelector(position, getString(R.string.OEAppearanceDividerStyle), new CharSequence[]{
                    getString(R.string.OEAppearanceDividerHidden),
                    getString(R.string.OEAppearanceDividerLine),
                    getString(R.string.OEAppearanceDividerSegments)
            }, AppearanceConfig.dividerStyle, this::onDividerStyleChanged);
            return;
        } else if (position == glassOutlineRow) {
            showSelector(position, getString(R.string.OEAppearanceGlassOutline), new CharSequence[]{
                    getString(R.string.OEAppearanceGlassOutlineGlare),
                    getString(R.string.OEAppearanceGlassOutlineSolid),
                    getString(R.string.OEAppearanceGlassOutlineHidden)
            }, AppearanceConfig.glassOutlineStyle, null);
            return;
        } else if (position == tabTitleStyleRow) {
            showSelector(position, getString(R.string.OEAppearanceTabTitleStyle), new CharSequence[]{
                    getString(R.string.OEAppearanceTabTitleStyleTextOnly),
                    getString(R.string.OEAppearanceTabTitleStyleIconsOnly),
                    getString(R.string.OEAppearanceTabTitleStyleTextWithIcons)
            }, NekoConfig.tabsTitleType, () -> {
                if (foldersPreviewCell != null) {
                    foldersPreviewCell.updateTabTitle(true);
                    foldersPreviewCell.updateTabIcons(true);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            });
            return;
        } else if (position == tabCounterRow) {
            showSelector(position, getString(R.string.OEAppearanceTabCounter), new CharSequence[]{
                    getString(R.string.Disable),
                    getString(R.string.FilterMuted),
                    getString(R.string.FilterAllChatsShort)
            }, NaConfig.INSTANCE.getIgnoreUnreadCount(), () -> {
                if (foldersPreviewCell != null) {
                    foldersPreviewCell.updateTabCounter(true);
                }
                showRestartHint();
            });
            return;
        } else if (position == centerTitleRow) {
            showSelector(position, getString(R.string.OEAppearanceCenterTitle), new CharSequence[]{
                    getString(R.string.CenterActionBarTitleOff),
                    getString(R.string.CenterActionBarTitleOn),
                    getString(R.string.SettingsOnly),
                    getString(R.string.ChatsOnly)
            }, NaConfig.INSTANCE.getCenterActionBarTitleType(), () -> {
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.updateCentered(true);
                }
                rebuildAll();
            });
            return;
        } else if (position == titleTextRow) {
            showSelector(position, getString(R.string.OEAppearanceTitleText), titleTextOptions(),
                    AppearanceConfig.titleText, () -> {
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.updateTitle(true);
                }
            });
            return;
        } else if (position == forceSnowRow) {
            boolean enabled = NekoConfig.actionBarDecoration.Int() != 1;
            NekoConfig.actionBarDecoration.setConfigInt(enabled ? 1 : 0);
            NaConfig.INSTANCE.getChatDecoration().setConfigInt(enabled ? 1 : 0);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            rebuildAll();
            return;
        } else if (position == forceBlurRow) {
            SharedConfig.toggleChatBlur();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(SharedConfig.chatBlurEnabled());
            }
            rebuildAll();
            return;
        }

        ConfigItem item = null;
        boolean rebuild = false;
        boolean restart = false;

        if (position == useSystemFontsRow) {
            item = NekoConfig.typeface;
            restart = true;
        } else if (position == useSystemEmojiRow) {
            item = NekoConfig.useSystemEmoji;
            rebuild = true;
        } else if (position == gooeyAvatarRow) {
            item = AppearanceConfig.gooeyAvatarAnimation;
        } else if (position == customThemesRow) {
            item = AppearanceConfig.customThemes;
        } else if (position == separateHeadersRow) {
            item = AppearanceConfig.separateHeaders;
        } else if (position == glassMessageMenuRow) {
            item = AppearanceConfig.glassMessageMenu;
        } else if (position == disableAvatarBlurRow) {
            item = NaConfig.INSTANCE.getDisableAvatarBlur();
            rebuild = true;
        } else if (position == singleCornerRadiusRow) {
            item = AppearanceConfig.singleCornerRadius;
            rebuild = true;
        } else if (position == hideStoriesRow) {
            item = NaConfig.INSTANCE.getHideStoriesFromHeader();
            rebuild = true;
        } else if (position == hideFloatingButtonRow) {
            item = NaConfig.INSTANCE.getDisableDialogsFloatingButton();
            rebuild = true;
        } else if (position == hideSearchBarRow) {
            item = NaConfig.INSTANCE.getHideDialogsSearchField();
            rebuild = true;
        } else if (position == senderMiniAvatarsRow) {
            item = AppearanceConfig.senderMiniAvatars;
        } else if (position == hideAllChatsRow) {
            item = NekoConfig.hideAllTab;
            restart = true;
        }

        if (item == null) {
            return;
        }

        boolean value = item.toggleConfigBool();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
        if (position == hideAllChatsRow && foldersPreviewCell != null) {
            foldersPreviewCell.updateAllChatsTabName(true);
        }
        if (rebuild) {
            rebuildAll();
        }
        if (restart) {
            showRestartHint();
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
                case TYPE_AVATAR_CORNERS:
                    avatarCornersPreviewCell = new AvatarCornersPreviewCell(mContext,
                            OpenExteraAppearanceActivity.this::rebuildAll);
                    avatarCornersPreviewCell.setNeedDivider(true);
                    view = avatarCornersPreviewCell;
                    break;
                case TYPE_CHAT_LIST:
                    chatListPreviewCell = new ChatListPreviewCell(mContext);
                    view = chatListPreviewCell;
                    break;
                case TYPE_FOLDERS:
                    foldersPreviewCell = new FoldersPreviewCell(mContext);
                    view = foldersPreviewCell;
                    break;
                case TYPE_SECTION_SLIDER:
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            value -> AppearanceConfig.sectionRadius.setConfigInt(value),
                            0, AppearanceConfig.AVATAR_CORNERS_MAX,
                            getString(R.string.OEAppearanceSectionRadius),
                            getString(R.string.OEAppearanceSectionRadiusOff),
                            getString(R.string.OEAppearanceSectionRadiusMax));
                    slider.setValueSuffix("dp");
                    slider.setValue(AppearanceConfig.sectionRadius.Int());
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                default:
                    return super.onCreateViewHolder(parent, viewType);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == appearanceHeaderRow) {
                        cell.setText(getString(R.string.OEAppearanceGeneral));
                    } else if (position == sectionsHeaderRow) {
                        cell.setText(getString(R.string.OEAppearanceSections));
                    } else if (position == blurHeaderRow) {
                        cell.setText(getString(R.string.OEAppearanceBlur));
                    } else if (position == chatListHeaderRow) {
                        cell.setText(getString(R.string.OEAppearanceChatList));
                    } else if (position == foldersHeaderRow) {
                        cell.setText(getString(R.string.OEAppearanceFolders));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == useSystemFontsRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceUseSystemFonts), NekoConfig.typeface.Bool(), true);
                    } else if (position == useSystemEmojiRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceUseSystemEmoji), NekoConfig.useSystemEmoji.Bool(), true);
                    } else if (position == gooeyAvatarRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceGooeyAvatar), AppearanceConfig.gooeyAvatarAnimation.Bool(), true);
                    } else if (position == customThemesRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceCustomThemes), AppearanceConfig.customThemes.Bool(), false);
                    } else if (position == separateHeadersRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceSeparateHeaders), AppearanceConfig.separateHeaders.Bool(), true);
                    } else if (position == glassMessageMenuRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceGlassMessageMenu), AppearanceConfig.glassMessageMenu.Bool(), true);
                    } else if (position == forceBlurRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceForceBlur), SharedConfig.chatBlurEnabled(), true);
                    } else if (position == disableAvatarBlurRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceDisableAvatarBlur), NaConfig.INSTANCE.getDisableAvatarBlur().Bool(), false);
                    } else if (position == singleCornerRadiusRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceSingleCornerRadius), AppearanceConfig.singleCornerRadius.Bool(), false);
                    } else if (position == forceSnowRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.OEAppearanceForceSnow), getString(R.string.OEAppearanceForceSnowInfo), NekoConfig.actionBarDecoration.Int() == 1, true, true);
                    } else if (position == hideStoriesRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceHideStories), NaConfig.INSTANCE.getHideStoriesFromHeader().Bool(), true);
                    } else if (position == hideFloatingButtonRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceHideFloatingButton), NaConfig.INSTANCE.getDisableDialogsFloatingButton().Bool(), true);
                    } else if (position == hideSearchBarRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceHideSearchBar), NaConfig.INSTANCE.getHideDialogsSearchField().Bool(), true);
                    } else if (position == senderMiniAvatarsRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceSenderMiniAvatars), AppearanceConfig.senderMiniAvatars.Bool(), false);
                    } else if (position == hideAllChatsRow) {
                        cell.setTextAndCheck(LocaleController.formatString(R.string.OEAppearanceHideAllChats, getString(R.string.FilterAllChats)), NekoConfig.hideAllTab.Bool(), false);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == switchStyleRow) {
                        cell.setTextAndValue(getString(R.string.OEAppearanceSwitchStyle), styleName(NaConfig.INSTANCE.getSwitchStyle().Int()), true);
                    } else if (position == sliderStyleRow) {
                        cell.setTextAndValue(getString(R.string.OEAppearanceSliderStyle), styleName(NaConfig.INSTANCE.getSliderStyle().Int()), true);
                    } else if (position == dividerStyleRow) {
                        String[] v = {getString(R.string.OEAppearanceDividerHidden), getString(R.string.OEAppearanceDividerLine), getString(R.string.OEAppearanceDividerSegments)};
                        cell.setTextAndValue(getString(R.string.OEAppearanceDividerStyle), v[clamp(AppearanceConfig.dividerStyle.Int(), v.length)], false);
                    } else if (position == glassOutlineRow) {
                        String[] v = {getString(R.string.OEAppearanceGlassOutlineGlare), getString(R.string.OEAppearanceGlassOutlineSolid), getString(R.string.OEAppearanceGlassOutlineHidden)};
                        cell.setTextAndValue(getString(R.string.OEAppearanceGlassOutline), v[clamp(AppearanceConfig.glassOutlineStyle.Int(), v.length)], true);
                    } else if (position == centerTitleRow) {
                        String[] v = {getString(R.string.CenterActionBarTitleOff), getString(R.string.CenterActionBarTitleOn), getString(R.string.SettingsOnly), getString(R.string.ChatsOnly)};
                        cell.setTextAndValue(getString(R.string.OEAppearanceCenterTitle), v[clamp(NaConfig.INSTANCE.getCenterActionBarTitleType().Int(), v.length)], true);
                    } else if (position == tabTitleStyleRow) {
                        String[] v = {getString(R.string.OEAppearanceTabTitleStyleTextOnly), getString(R.string.OEAppearanceTabTitleStyleIconsOnly), getString(R.string.OEAppearanceTabTitleStyleTextWithIcons)};
                        cell.setTextAndValue(getString(R.string.OEAppearanceTabTitleStyle), v[clamp(NekoConfig.tabsTitleType.Int(), v.length)], true);
                    } else if (position == tabCounterRow) {
                        String[] v = {getString(R.string.Disable), getString(R.string.FilterMuted), getString(R.string.FilterAllChatsShort)};
                        cell.setTextAndValue(getString(R.string.OEAppearanceTabCounter), v[clamp(NaConfig.INSTANCE.getIgnoreUnreadCount().Int(), v.length)], true);
                    } else if (position == titleTextRow) {
                        String[] v = {getString(R.string.OEAppearanceTitleTextApp), getString(R.string.OEAppearanceTitleTextUsername), getString(R.string.OEAppearanceTitleTextName)};
                        cell.setTextAndValue(getString(R.string.OEAppearanceTitleText), v[clamp(AppearanceConfig.titleText.Int(), v.length)], false);
                    } else if (position == appNavigationRow) {
                        cell.setTextAndValue(getString(R.string.OEAppearanceNavigation), getString(R.string.OEAppearanceNavigationSub), true);
                    } else if (position == iconPacksRow) {
                        cell.setTextAndValue(getString(R.string.OEAppearanceIconPacks), getString(R.string.OEAppearanceIconPacksInfo), true);
                    } else if (position == pillStackRow) {
                        cell.setTextAndValue(getString(R.string.OEAppearancePillStack), getString(R.string.OEAppearancePillStackInfo), false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    boolean bottom = position == linksDividerRow;
                    if (position == appearanceDividerRow) {
                        cell.setText(getString(R.string.OEAppearanceCustomThemesInfo));
                    } else if (position == blurDividerRow) {
                        cell.setText(getString(R.string.OEAppearanceBlurInfo));
                    } else if (position == avatarsDividerRow) {
                        cell.setText(getString(R.string.OEAppearanceSingleCornerRadiusInfo));
                    } else if (position == chatListDividerRow) {
                        cell.setText(getString(R.string.OEAppearanceChatListInfo));
                    } else if (position == foldersDividerRow) {
                        cell.setText(getString(R.string.OEAppearanceFoldersInfo));
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
            if (position == avatarCornersPreviewRow) {
                return TYPE_AVATAR_CORNERS;
            } else if (position == chatListPreviewRow) {
                return TYPE_CHAT_LIST;
            } else if (position == foldersPreviewRow) {
                return TYPE_FOLDERS;
            } else if (position == sectionRadiusRow) {
                return TYPE_SECTION_SLIDER;
            } else if (position == appearanceHeaderRow || position == sectionsHeaderRow
                    || position == blurHeaderRow || position == chatListHeaderRow
                    || position == foldersHeaderRow) {
                return TYPE_HEADER;
            } else if (position == appearanceDividerRow || position == sectionsDividerRow
                    || position == blurDividerRow || position == avatarsDividerRow
                    || position == chatListDividerRow || position == foldersDividerRow
                    || position == linksDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == switchStyleRow || position == sliderStyleRow
                    || position == dividerStyleRow || position == glassOutlineRow
                    || position == centerTitleRow || position == tabTitleStyleRow
                    || position == tabCounterRow || position == titleTextRow
                    || position == appNavigationRow
                    || position == iconPacksRow || position == pillStackRow) {
                return TYPE_SETTINGS;
            }
            return TYPE_CHECK;
        }
    }

    private static int clamp(int value, int size) {
        if (value < 0) return 0;
        if (value >= size) return size - 1;
        return value;
    }

    private static String styleName(int value) {
        switch (value) {
            case 1:
                return getString(R.string.StyleModern);
            case 2:
                return getString(R.string.StyleMaterialDesign3);
            default:
                return getString(R.string.Default);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.runOnUIThread(() -> {
            if (avatarCornersPreviewCell != null) avatarCornersPreviewCell.invalidate();
            if (chatListPreviewCell != null) chatListPreviewCell.invalidate();
            if (foldersPreviewCell != null) foldersPreviewCell.invalidate();
        });
    }
}
