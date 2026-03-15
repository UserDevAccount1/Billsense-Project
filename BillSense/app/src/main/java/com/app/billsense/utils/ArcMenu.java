package com.app.billsense.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import com.app.billsense.R;

//public class ArcMenu extends ViewGroup {
//
//    private int mRadius;
//    private float mStartAngle;
//    private float mSweepAngle;
//    private boolean isOpen;
//    private View fab;
//
//    public ArcMenu(Context context) {
//        this(context, null);
//    }
//
//    public ArcMenu(Context context, AttributeSet attrs) {
//        this(context, attrs, 0);
//    }
//
//    public ArcMenu(Context context, AttributeSet attrs, int defStyleAttr) {
//        super(context, attrs, defStyleAttr);
//        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ArcMenu);
//        mRadius = typedArray.getDimensionPixelSize(R.styleable.ArcMenu_radius, 100);
//        mStartAngle = typedArray.getFloat(R.styleable.ArcMenu_startAngle, 180f);
//        mSweepAngle = typedArray.getFloat(R.styleable.ArcMenu_sweepAngle, 160f);
//        typedArray.recycle();
//        // Initially, hide the menu itself
//        setVisibility(View.GONE);
//    }
//
//    public void setImages(int[] imageResIds) {
//        for (int imageResId : imageResIds) {
//            ImageView imageView = new ImageView(getContext());
//            imageView.setImageResource(imageResId);
//            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
//            imageView.setPadding(5, 5, 5, 5);
//            LayoutParams layoutParams = new LayoutParams(100, 100);
//            addView(imageView, layoutParams);
//        }
//        requestLayout();
//    }
//
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        int childCount = getChildCount();
//        for (int i = 0; i < childCount; i++) {
//            View child = getChildAt(i);
//            measureChild(child, widthMeasureSpec, heightMeasureSpec);
//        }
//    }
//
//    @Override
//    protected void onLayout(boolean changed, int l, int t, int r, int b) {
//        int childCount = getChildCount();
//        if (childCount == 0) {
//            return;
//        }
//
//        for (int i = 0; i < childCount; i++) {
//            View child = getChildAt(i);
//
//            float centerX = getWidth() / 2f;
//            float centerY = getHeight();
//
//            int childWidth = child.getMeasuredWidth();
//            int childHeight = child.getMeasuredHeight();
//            double angle = mStartAngle + (mSweepAngle / (childCount - 1)) * i;
//
//            double x = centerX + mRadius * Math.cos(Math.toRadians(angle)) - childWidth / 2;
//            double y = centerY + mRadius * Math.sin(Math.toRadians(angle)) - childHeight / 2;
//
//            child.layout((int) x, (int) y, (int) x + childWidth, (int) y + childHeight);
//            //Correct logic
//            child.setVisibility(isOpen ? View.VISIBLE : View.INVISIBLE);
//        }
//    }
//
//    public void toggleMenu() {
//        isOpen = !isOpen;
//        if(isOpen){
//            setVisibility(View.VISIBLE);
//        }else {
//            setVisibility(View.GONE);
//        }
//        int childCount = getChildCount();
//        float centerX = getWidth() / 2f;
//        float centerY = getHeight();
//
//        for (int i = 0; i < childCount; i++) {
//            View child = getChildAt(i);
//
//            int childWidth = child.getMeasuredWidth();
//            int childHeight = child.getMeasuredHeight();
//            double angle = mStartAngle + (mSweepAngle / (childCount - 1)) * i;
//
//            double x = centerX + mRadius * Math.cos(Math.toRadians(angle)) - childWidth / 2;
//            double y = centerY + mRadius * Math.sin(Math.toRadians(angle)) - childHeight / 2;
//            TranslateAnimation animation;
//
//            if (isOpen) {
//                animation = new TranslateAnimation(0f, (float) (x - child.getLeft()), 0f, (float) (y - child.getTop()));
//            } else {
//                animation = new TranslateAnimation((float) (x - child.getLeft()), 0f, (float) (y - child.getTop()), 0f);
//            }
//
//            animation.setDuration(300);
//            animation.setFillAfter(true);
//            animation.setAnimationListener(new Animation.AnimationListener() {
//                @Override
//                public void onAnimationStart(Animation animation) {
//                    if (isOpen) {
//                        child.setVisibility(View.VISIBLE);
//                    }
//                }
//
//                @Override
//                public void onAnimationEnd(Animation animation) {
//                    if (!isOpen) {
//                        child.setVisibility(View.INVISIBLE);
//                    }
//                }
//
//                @Override
//                public void onAnimationRepeat(Animation animation) {}
//            });
//
//            child.startAnimation(animation);
//        }
//    }
//
//    public boolean isOpen() {
//        return isOpen;
//    }
//}








public class ArcMenu extends ViewGroup {

    private int mRadius;
    private float mStartAngle;
    private float mSweepAngle;
    private boolean isOpen;

    public ArcMenu(Context context) {
        this(context, null);
    }

    public ArcMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArcMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ArcMenu);
        mRadius = typedArray.getDimensionPixelSize(R.styleable.ArcMenu_radius, 120);
        mStartAngle = typedArray.getFloat(R.styleable.ArcMenu_startAngle, 180f);
        mSweepAngle = typedArray.getFloat(R.styleable.ArcMenu_sweepAngle, 160f);
        typedArray.recycle();
        // Initially, hide the menu itself
        setVisibility(View.GONE);
    }

    public void setImages(int[] imageResIds) {
        for (int imageResId : imageResIds) {
            ImageView imageView = new ImageView(getContext());
            imageView.setImageResource(imageResId);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(5, 5, 5, 5);
            LayoutParams layoutParams = new LayoutParams(100, 100);
            addView(imageView, layoutParams);
        }
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        if (childCount == 0) {
            return;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight();

        //find child width and child height.
        View firstChild = getChildAt(0);
        int childWidth = firstChild.getMeasuredWidth();
        int childHeight = firstChild.getMeasuredHeight();

        // Calculate the maximum x offset for the leftmost children taking into account the child's width
        double maxChildOffset = mRadius * Math.cos(Math.toRadians(mStartAngle)) + childWidth / 2;
        double maxChildOffsetVertical = mRadius * Math.sin(Math.toRadians(mStartAngle)) ;
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);


            double angle;

            if (childCount == 3) {
                // Fixed positions for 3 children
                if (i == 0) {
                    angle = mStartAngle; // Left-bottom
                } else if (i == 1) {
                    angle = mStartAngle + mSweepAngle / 2; // Center-bottom
                } else {
                    angle = mStartAngle + mSweepAngle; // Right-bottom
                }
            } else {
                // Default behavior for other numbers of children
                angle = mStartAngle + (mSweepAngle / (childCount - 1)) * i;
            }

            // Adjust centerX based on the maximum child offset to ensure full visibility
            double adjustedCenterX = centerX;
            double adjustedCenterY = centerY;
            if (childCount == 3) {
                adjustedCenterX = centerX - (maxChildOffset) / 2;
            }
            if (i == 0 && childCount == 3){
                adjustedCenterY = centerY + (maxChildOffsetVertical / 2) - childHeight /2;
            }

            double x = adjustedCenterX + mRadius * Math.cos(Math.toRadians(angle)) - childWidth / 2;
            double y = adjustedCenterY + mRadius * Math.sin(Math.toRadians(angle)) - childHeight / 2;

            child.layout((int) x, (int) y, (int) x + childWidth, (int) y + childHeight);
            child.setVisibility(isOpen ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void toggleMenu() {
        isOpen = !isOpen;
        if (isOpen) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
        // No animation needed, just toggle visibility
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.setVisibility(isOpen ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public boolean isOpen() {
        return isOpen;
    }
}