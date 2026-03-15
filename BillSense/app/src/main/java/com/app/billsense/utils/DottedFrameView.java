package com.app.billsense.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class DottedFrameView extends View {
    private Paint paint;

    public DottedFrameView(Context context) {
        super(context);
        init();
    }

    public DottedFrameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DottedFrameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(ContextCompat.getColor(getContext(), android.R.color.white)); // Or a custom color
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density * 3); // 3dp
        paint.setPathEffect(new DashPathEffect(new float[]{20, 10}, 0)); // Dash, gap
        paint.setAlpha(178); // Approx 70% opacity (0.7 * 255)
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw a rounded rectangle if you want rounded corners like in the CSS
        // For simplicity, this draws a simple rectangle. Add corner radius if needed.
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        // For rounded corners:
        // float cornerRadius = getResources().getDisplayMetrics().density * 12; // 12dp
        // canvas.drawRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius, paint);
    }
}
