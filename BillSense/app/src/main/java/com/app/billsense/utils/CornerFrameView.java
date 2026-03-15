package com.app.billsense.utils;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.billsense.R; // Replace with your R class import

public class CornerFrameView extends View {

    private Paint cornerPaint;
    private float cornerRadius = 24f; // Default 8dp equivalent, adjust as needed
    private float strokeWidth = 9f;   // Default 3dp equivalent, adjust as needed
    private int frameColor = Color.parseColor("#CCFFFFFF"); // Default 80% White

    private Path path;
    private RectF rectF;

    public CornerFrameView(Context context) {
        super(context);
        init(null);
    }

    public CornerFrameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CornerFrameView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        path = new Path();
        rectF = new RectF();

        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.CornerFrameView, 0, 0);
            cornerRadius = typedArray.getDimension(R.styleable.CornerFrameView_cfv_cornerRadius, 24f);
            strokeWidth = typedArray.getDimension(R.styleable.CornerFrameView_cfv_strokeWidth, 9f);
            frameColor = typedArray.getColor(R.styleable.CornerFrameView_cfv_frameColor, Color.parseColor("#CCFFFFFF"));
            typedArray.recycle();
        }

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(strokeWidth);
        cornerPaint.setColor(frameColor);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND); // For rounded line ends, optional
        cornerPaint.setStrokeJoin(Paint.Join.ROUND); // For rounded joins, optional
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        // Adjust multiplier for longer/shorter corner lines from the rounded part
        float cornerLengthExtension = cornerRadius * 1.5f; // This is the length of the straight part extending from the arc

        // Top-Left Corner
        path.reset();
        path.moveTo(strokeWidth / 2, cornerRadius + cornerLengthExtension); // Start of vertical line
        path.lineTo(strokeWidth / 2, cornerRadius + strokeWidth / 2);      // To beginning of arc
        rectF.set(strokeWidth / 2, strokeWidth / 2, cornerRadius * 2 + strokeWidth / 2, cornerRadius * 2 + strokeWidth / 2);
        path.arcTo(rectF, 180f, 90f, false);
        path.lineTo(cornerRadius + cornerLengthExtension, strokeWidth / 2); // End of horizontal line
        canvas.drawPath(path, cornerPaint);

        // Top-Right Corner
        path.reset();
        path.moveTo(viewWidth - cornerRadius - cornerLengthExtension, strokeWidth / 2); // Start of horizontal line
        path.lineTo(viewWidth - cornerRadius - strokeWidth / 2, strokeWidth / 2);       // To beginning of arc
        rectF.set(viewWidth - cornerRadius * 2 - strokeWidth / 2, strokeWidth / 2, viewWidth - strokeWidth / 2, cornerRadius * 2 + strokeWidth / 2);
        path.arcTo(rectF, 270f, 90f, false);
        path.lineTo(viewWidth - strokeWidth / 2, cornerRadius + cornerLengthExtension);  // End of vertical line
        canvas.drawPath(path, cornerPaint);

        // Bottom-Left Corner
        path.reset();
        path.moveTo(strokeWidth / 2, viewHeight - cornerRadius - cornerLengthExtension); // Start of vertical line
        path.lineTo(strokeWidth / 2, viewHeight - cornerRadius - strokeWidth / 2);      // To beginning of arc
        rectF.set(strokeWidth / 2, viewHeight - cornerRadius * 2 - strokeWidth / 2, cornerRadius * 2 + strokeWidth / 2, viewHeight - strokeWidth / 2);
        path.arcTo(rectF, 180f, -90f, false);
        path.lineTo(cornerRadius + cornerLengthExtension, viewHeight - strokeWidth / 2); // End of horizontal line
        canvas.drawPath(path, cornerPaint);

        // Bottom-Right Corner
        path.reset();
        path.moveTo(viewWidth - cornerRadius - cornerLengthExtension, viewHeight - strokeWidth / 2); // Start of horizontal line
        path.lineTo(viewWidth - cornerRadius - strokeWidth / 2, viewHeight - strokeWidth / 2);      // To beginning of arc
        rectF.set(viewWidth - cornerRadius * 2 - strokeWidth / 2, viewHeight - cornerRadius * 2 - strokeWidth / 2, viewWidth - strokeWidth / 2, viewHeight - strokeWidth / 2);
        path.arcTo(rectF, 90f, -90f, false);
        path.lineTo(viewWidth - strokeWidth / 2, viewHeight - cornerRadius - cornerLengthExtension); // End of vertical line
        canvas.drawPath(path, cornerPaint);
    }


    // --- Optional: Methods to update properties programmatically ---
    public void setFrameColor(int color) {
        this.frameColor = color;
        cornerPaint.setColor(this.frameColor);
        invalidate(); // Redraw the view
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
        cornerPaint.setStrokeWidth(this.strokeWidth);
        invalidate();
    }
}
