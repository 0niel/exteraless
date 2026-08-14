package app.exteraless.plugins.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import app.exteraless.plugins.Plugin;

/**
 * Ячейка плагина: название, подпись («v1.0 • автор», красной при loadError)
 * и переключатель включения справа. Клик по телу ячейки обрабатывает список
 * (onItemClick активити), переключатель живёт сам через OnPluginToggleListener.
 */
public class PluginCell extends FrameLayout {

    public interface OnPluginToggleListener {
        void onPluginToggle(Plugin plugin, boolean enabled);
    }

    private final TextView nameTextView;
    private final TextView subtitleTextView;
    private final Switch switchView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Plugin plugin;
    private boolean needDivider;
    private OnPluginToggleListener toggleListener;

    public PluginCell(Context context) {
        super(context);

        setMinimumHeight(AndroidUtilities.dp(64));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        addView(textContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                16, 0, 76, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setSingleLine();
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        textContainer.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleTextView.setSingleLine();
        subtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
        textContainer.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        switchView = new Switch(context);
        switchView.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked(), true));
        addView(switchView, LayoutHelper.createFrame(38, 22,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                14, 0, 14, 0));

        updateColors();
    }

    private void updateColors() {
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        dividerPaint.setStrokeWidth(1);
    }

    public void setOnPluginToggleListener(OnPluginToggleListener listener) {
        toggleListener = listener;
    }

    public void setPlugin(Plugin p, boolean divider) {
        plugin = p;
        needDivider = divider;
        nameTextView.setText(p.getDisplayName());
        subtitleTextView.setText(p.getSubtitle());
        if (p.loadError != null) {
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        } else {
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
        // setChecked дёргает listener при смене состояния — на время бинда отключаем.
        switchView.setOnCheckedChangeListener(null);
        switchView.setChecked(p.enabled, false);
        switchView.setOnCheckedChangeListener((view, isChecked) -> {
            if (toggleListener != null && plugin != null) {
                toggleListener.onPluginToggle(plugin, isChecked);
            }
        });
        setWillNotDraw(!needDivider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            int inset = AndroidUtilities.dp(16);
            canvas.drawLine(LocaleController.isRTL ? 0 : inset, getMeasuredHeight() - 1,
                    LocaleController.isRTL ? getMeasuredWidth() - inset : getMeasuredWidth(),
                    getMeasuredHeight() - 1, dividerPaint);
        }
    }
}
