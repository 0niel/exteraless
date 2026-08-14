package app.exteraless.appearance;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Превью закругления аватарок со слайдером «Квадрат ↔ Круг».
 * Порт AvatarCornersPreviewCell из exteraGram.
 */
@SuppressLint("ViewConstructor")
public class AvatarCornersPreviewCell extends FrameLayout {

    public interface OnChanged {
        void run();
    }

    private final FrameLayout preview;
    private final AvatarCornersSeekBar seekBar;

    private final RectF rect = new RectF();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean needDivider;

    public AvatarCornersPreviewCell(Context context, OnChanged onChanged) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int start = 0, end = AppearanceConfig.AVATAR_CORNERS_MAX;
        seekBar = new AvatarCornersSeekBar(context, value -> {
            AppearanceConfig.avatarCorners.setConfigInt(value);
            invalidate();
            if (onChanged != null) {
                onChanged.run();
            }
        }, start, end,
                LocaleController.getString(R.string.OEAppearanceAvatarCorners),
                LocaleController.getString(R.string.OEAppearanceAvatarCornersLeft),
                LocaleController.getString(R.string.OEAppearanceAvatarCornersRight));
        seekBar.setValue(AppearanceConfig.avatarCorners());
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
        outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(1f)));

        preview = new FrameLayout(context) {
            private final Path onlinePath = new Path();

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
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Theme.dialogs_onlineCirclePaint);

                float stroke = outlinePaint.getStrokeWidth() / 2;
                rect.set(stroke, stroke, w - stroke, h - stroke);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), outlinePaint);

                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(AndroidUtilities.dp(68), h / 2.0f + AndroidUtilities.dpf2(20.5f), AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(204, r, g, b));
                canvas.drawRoundRect(AndroidUtilities.dp(92), h / 2.0f - AndroidUtilities.dpf2(15.5f),
                        w - AndroidUtilities.dp(90), h / 2.0f - AndroidUtilities.dpf2(7.5f),
                        w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);

                onlinePath.rewind();
                onlinePath.addCircle(AndroidUtilities.dp(68), h / 2.0f + AndroidUtilities.dpf2(20.5f), AndroidUtilities.dp(12), Path.Direction.CCW);
                canvas.save();
                canvas.clipPath(onlinePath, Region.Op.DIFFERENCE);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(90, r, g, b));
                canvas.drawRoundRect(AndroidUtilities.dp(92), h / 2.0f + AndroidUtilities.dpf2(7.5f),
                        w - AndroidUtilities.dp(50), h / 2.0f + AndroidUtilities.dp(15.5f),
                        w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);

                float corners = AppearanceConfig.getAvatarCorners(AndroidUtilities.dp(56));
                canvas.drawRoundRect(AndroidUtilities.dp(20), h / 2.0f - AndroidUtilities.dp(28),
                        AndroidUtilities.dp(76), h / 2.0f + AndroidUtilities.dp(28),
                        corners, corners, Theme.dialogs_onlineCirclePaint);
                canvas.restore();
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER, 21, 112, 21, 21));
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
        if (seekBar != null) {
            seekBar.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1,
                    getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1,
                    Theme.dividerPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(112 + 89 + 21), MeasureSpec.EXACTLY)
        );
    }
}
