package app.exteraless.pillstack.pills;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/** Скруглённый фон пилюли курса: вертикальный градиент из двух цветов. */
public class ColoredBackground extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final int colorTop;
    private final int colorBottom;
    private int lastHeight = -1;

    public ColoredBackground(int colorTop, int colorBottom) {
        this.colorTop = colorTop;
        this.colorBottom = colorBottom;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int height = getBounds().height();
        if (height != lastHeight) {
            lastHeight = height;
            paint.setShader(new LinearGradient(0, 0, 0, height,
                    colorTop, colorBottom, Shader.TileMode.CLAMP));
        }
        rectF.set(getBounds());
        canvas.drawRoundRect(rectF, AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
