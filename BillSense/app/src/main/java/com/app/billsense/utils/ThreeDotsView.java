package com.app.billsense.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.billsense.R;

public class ThreeDotsView extends View {

    private static final int DOT_COUNT = 3;
    private static final int ANIMATION_DURATION = 1200; // ms
    private static final int ANIMATION_DELAY_PER_DOT = 200; // ms

    private Paint dotPaint;
    private float dotRadius;
    private float dotSpacing;
    private float[] dotAnimatedRadius = new float[DOT_COUNT];
    private ValueAnimator[] animators = new ValueAnimator[DOT_COUNT];

    private int dotColor;

    public ThreeDotsView(Context context) {
        super(context);
        init(null);
    }

    public ThreeDotsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ThreeDotsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        // Default values
        dotRadius = dpToPx(4); // Default radius 4dp
        dotSpacing = dpToPx(6); // Default spacing 6dp
        dotColor = ContextCompat.getColor(getContext(), R.color.grey_medium); // Default color

        if (attrs != null) {
//            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ThreeDotsView);
//            dotRadius = a.getDimension(R.styleable.ThreeDotsView_dotRadius, dpToPx(4));
//            dotSpacing = a.getDimension(R.styleable.ThreeDotsView_dotSpacing, dpToPx(6));
//            dotColor = a.getColor(R.styleable.ThreeDotsView_dotColor, ContextCompat.getColor(getContext(), R.color.grey_medium));
//         // You could also make ANIMATION_DURATION customizable
//            a.recycle();
        }

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(dotColor);
        dotPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < DOT_COUNT; i++) {
            dotAnimatedRadius[i] = dotRadius * 0.5f; // Start smaller
        }
    }

    public void setDotColor(int color) {
        this.dotColor = color;
        dotPaint.setColor(this.dotColor);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float desiredWidth = (DOT_COUNT * dotRadius * 2) + ((DOT_COUNT - 1) * dotSpacing);
        float desiredHeight = dotRadius * 2 * 1.5f; // Allow some space for animation scaling

        int width = resolveSize((int) desiredWidth, widthMeasureSpec);
        int height = resolveSize((int) desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float startX = (getWidth() - ((DOT_COUNT * dotRadius * 2) + ((DOT_COUNT - 1) * dotSpacing))) / 2f + dotRadius;
        float y = getHeight() / 2f;

        for (int i = 0; i < DOT_COUNT; i++) {
            canvas.drawCircle(startX + i * (dotRadius * 2 + dotSpacing), y, dotAnimatedRadius[i], dotPaint);
        }
    }

    public void startAnimation() {
        stopAnimation(); // Stop any existing animations

        for (int i = 0; i < DOT_COUNT; i++) {
            final int index = i;
            animators[i] = ValueAnimator.ofFloat(dotRadius * 0.5f, dotRadius * 1.2f, dotRadius * 0.5f);
            animators[i].setDuration(ANIMATION_DURATION / 2); // Half duration for up, half for down (in repeat)
            animators[i].setInterpolator(new LinearInterpolator());
            animators[i].setRepeatCount(ValueAnimator.INFINITE);
            animators[i].setRepeatMode(ValueAnimator.RESTART); // Using RESTART, the delay handles the wave
            animators[i].setStartDelay((long) i * ANIMATION_DELAY_PER_DOT);

            animators[i].addUpdateListener(animation -> {
                dotAnimatedRadius[index] = (float) animation.getAnimatedValue();
                invalidate();
            });
            animators[i].start();
        }
    }

    public void stopAnimation() {
        for (int i = 0; i < DOT_COUNT; i++) {
            if (animators[i] != null && animators[i].isRunning()) {
                animators[i].cancel();
            }
            dotAnimatedRadius[i] = dotRadius * 0.5f; // Reset to initial small size
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            startAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}

