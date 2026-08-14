package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashMap;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.NekoXConfig;
import xyz.nextalone.nagram.NaConfig;

/**
 * Превью вкладок папок. Порт FoldersPreviewCell из exteraGram 10.10.1.
 * Стиль вкладок фиксирован как стандартный (у NagramX нет стилей табов),
 * поэтому анимация смены стиля опущена, а прогрессы стиля равны 0.
 * Привязки: заголовок/иконки -> NekoConfig.tabsTitleType,
 * счётчик -> NaConfig.ignoreUnreadCount, скрытие «All Chats» -> NekoConfig.hideAllTab.
 */
@SuppressLint("ViewConstructor")
public class FoldersPreviewCell extends FrameLayout {

    private final FrameLayout preview;

    private final RectF rect = new RectF();
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Стилевые прогрессы фиксированы (стандартные вкладки).
    private final float roundedStyleProgress = 0f;
    private final float chipsStyleProgress = 0f;
    private final float textStyleProgress = 0f;
    private final float pillsStyleProgress = 0f;

    private float hideAllChatsProgress = 1f;
    private float iconProgress = 0f, titleProgress = 1f;
    private float counterProgress = 1f;

    private ValueAnimator animator;

    private static final HashMap<String, Integer> ICONS = new HashMap<>();

    static {
        ICONS.put("💬", R.drawable.filter_all);
        ICONS.put("👥", R.drawable.filter_group);
        ICONS.put("🤖", R.drawable.filter_bots);
        ICONS.put("📢", R.drawable.filter_channels);
        ICONS.put("🔔", R.drawable.filter_unmuted);
        ICONS.put("🏠", R.drawable.filter_home);
        ICONS.put("✅", R.drawable.filter_unread);
        ICONS.put("🎭", R.drawable.filter_mask);
    }

    private final String[][] filters = new String[][]{
            {LocaleController.getString(R.string.FilterAllChats), "💬"},
            {LocaleController.getString(R.string.FilterGroups), "👥"},
            {LocaleController.getString(R.string.FilterBots), "🤖"},
            {LocaleController.getString(R.string.FilterChannels), "📢"},
            {LocaleController.getString(R.string.FilterNameNonMuted), "🔔"},
            {LocaleController.getString(R.string.FilterContacts), "🏠"},
            {LocaleController.getString(R.string.FilterNameUnread), "✅"},
            {LocaleController.getString(R.string.FilterNonContacts), "🎭"},
    };

    private String allChatsTabName;
    private String allChatsTabIcon;

    private static int iconRes(String emoji) {
        Integer res = ICONS.get(emoji);
        return res != null ? res : R.drawable.filter_all;
    }

    public FoldersPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
        outlinePaint.setStrokeWidth(Math.max(2, dp(1f)));

        preview = new FrameLayout(context) {
            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(Canvas canvas) {
                int color = Theme.getColor(Theme.key_switchTrack);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                float w = getMeasuredWidth();
                float h = getMeasuredHeight();

                rect.set(0, 0, w, h);
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));
                canvas.drawRoundRect(rect, dp(8), dp(8), Theme.dialogs_onlineCirclePaint);

                float stroke = outlinePaint.getStrokeWidth() / 2;
                rect.set(stroke, stroke, w - stroke, h - stroke);
                canvas.drawRoundRect(rect, dp(8), dp(8), outlinePaint);

                float startY = h - dp(4) - dpf2(4.5f * chipsStyleProgress) - stroke;

                Path tab = new Path();
                tab.addRect(0, startY + dp(4), getMeasuredWidth(), startY + dp(10), Path.Direction.CCW);
                canvas.save();
                canvas.clipPath(tab, Region.Op.DIFFERENCE);

                textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));

                float startX = dp(25);
                for (int i = 0; i < filters.length; i++) {
                    textPaint.setTextSize(dp(15));
                    if (i == 0) {
                        textPaint.setColor(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_windowBackgroundWhiteValueText), hideAllChatsProgress));
                        textPaint.setTextScaleX(hideAllChatsProgress * titleProgress);
                        Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), 0x1F), chipsStyleProgress));
                        Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(0x00, Theme.dialogs_onlineCirclePaint.getColor(), hideAllChatsProgress));
                    } else {
                        textPaint.setColor(ColorUtils.blendARGB(0x00, color, titleProgress));
                        textPaint.setTextScaleX(titleProgress);
                    }
                    String name = i == 0 ? allChatsTabName : filters[i][0];
                    Drawable icon = context.getDrawable(iconRes(i == 0 ? allChatsTabIcon : filters[i][1])).mutate();
                    icon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0x00, i == 0 ? textPaint.getColor() : color, iconProgress), PorterDuff.Mode.MULTIPLY));
                    float sw = textPaint.measureText(name) + dp(30 + 4) * iconProgress + (i == 0 ? dpf2(24) * counterProgress : 1) + 14 * (1 - iconProgress) * titleProgress - dp(4) * iconProgress * (1 - titleProgress) * counterProgress;
                    if (i == 0) {
                        canvas.drawRoundRect(
                                startX,
                                startY + dpf2(6) * textStyleProgress - dpf2(37.5f) * chipsStyleProgress,
                                startX + sw + dpf2(4) * (1 - titleProgress) * (1 - counterProgress) + dpf2(22) * chipsStyleProgress,
                                startY + dp(8) - dpf2(4) * roundedStyleProgress - dpf2(9.5f) * chipsStyleProgress,
                                dpf2(10 + 15 * pillsStyleProgress),
                                dpf2(10 + 15 * pillsStyleProgress),
                                Theme.dialogs_onlineCirclePaint);
                        float iconOffset = startX + dpf2(6) * (1 - titleProgress) * (1 - counterProgress) + dpf2(11) * chipsStyleProgress;
                        icon.setBounds((int) (iconOffset), (int) h / 2 - dp(13), (int) (dpf2(26) * iconProgress * hideAllChatsProgress + iconOffset), (int) h / 2 + dp(13));
                        canvas.drawText(name, startX + dp(30 * iconProgress) + dpf2(10) * chipsStyleProgress + 7f * (1 - iconProgress) * titleProgress, startY - dp(14), textPaint);
                        textPaint.setTextScaleX(counterProgress);
                        textPaint.setTextSize(dp(14 * hideAllChatsProgress * counterProgress));
                        textPaint.setColor(ColorUtils.blendARGB(0x00, Color.argb(20, r, g, b), counterProgress));
                        Path path = new Path();
                        textPaint.getTextPath("3", 0, 1, (int) (startX + sw - dpf2(15.5f) + dpf2(12) * chipsStyleProgress - dp(1) * (1 - titleProgress)), (int) (startY - dpf2(15f)), path);
                        canvas.clipPath(path, Region.Op.DIFFERENCE);

                        textPaint.setColor(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_windowBackgroundWhiteValueText), counterProgress * hideAllChatsProgress));
                        canvas.drawCircle(startX + sw - dpf2(11.5f) + dpf2(12) * chipsStyleProgress - dp(1) * (1 - titleProgress), h / 2, dp(10 * counterProgress * hideAllChatsProgress), textPaint);

                        startX += dp(25) + sw + dpf2(22) * chipsStyleProgress;
                    } else {
                        icon.setBounds((int) startX, (int) h / 2 - dp(13), (int) startX + dp(26 * iconProgress), (int) h / 2 + dp(13));
                        canvas.drawText(name, startX + dp(30) * iconProgress, startY - dp(14), textPaint);
                        startX += dp(25) + sw + dpf2(5) * chipsStyleProgress;
                    }
                    icon.draw(canvas);
                }
                canvas.restore();
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER, 21, 15, 21, 21));
        updateTabIcons(false);
        updateTabTitle(false);
        updateAllChatsTabName(false);
        updateTabCounter(false);
    }

    public void updateAllChatsTabName(boolean animate) {
        // hideAllTab меняет имя первой вкладки: All Chats -> Unread
        allChatsTabName = NekoConfig.hideAllTab.Bool() ? filters[6][0] : filters[0][0];
        allChatsTabIcon = NekoConfig.hideAllTab.Bool() ? filters[6][1] : filters[0][1];
        hideAllChatsProgress = 1f;
        invalidate();
    }

    public void updateTabTitle(boolean animate) {
        // Заголовок скрыт только в режиме «только иконки».
        float to = NekoConfig.tabsTitleType.Int() != NekoXConfig.TITLE_TYPE_ICON ? 1 : 0;
        animateFloat(animate, titleProgress, to, v -> titleProgress = v);
    }

    public void updateTabIcons(boolean animate) {
        // Иконки показаны во всех режимах, кроме «только текст».
        float to = NekoConfig.tabsTitleType.Int() != NekoXConfig.TITLE_TYPE_TEXT ? 1 : 0;
        animateFloat(animate, iconProgress, to, v -> iconProgress = v);
    }

    public void updateTabCounter(boolean animate) {
        float to = NaConfig.INSTANCE.getIgnoreUnreadCount().Int() != NekoConfig.DIALOG_FILTER_EXCLUDE_ALL ? 1 : 0;
        animateFloat(animate, counterProgress, to, v -> counterProgress = v);
    }

    private interface FloatSetter {
        void set(float v);
    }

    private void animateFloat(boolean animate, float from, float to, FloatSetter setter) {
        if (to == from && animate) {
            return;
        }
        if (animate) {
            animator = ValueAnimator.ofFloat(from, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                setter.set((Float) animation.getAnimatedValue());
                invalidate();
            });
            animator.start();
        } else {
            setter.set(to);
            invalidate();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(86), MeasureSpec.EXACTLY));
    }
}
