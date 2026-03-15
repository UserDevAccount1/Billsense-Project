package com.app.billsense.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityDispatchBinding;
import com.google.android.material.appbar.MaterialToolbar;

public class DispatchActivity extends AppCompatActivity {
    private ActivityDispatchBinding binding;
    private MaterialToolbar toolbar;
    private CountDownTimer countdownTimer;
    private long totalTimeInSeconds = 60; //60 sec. 1 minute

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDispatchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.dispatch));

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        binding.cases.setOnClickListener(view -> {
            startActivity(new Intent(DispatchActivity.this, CasesActivity.class));
        });

        startAgentAnimation();
    }

    private void startAgentAnimation() {
        ImageView agentImageView = binding.leftImageView;
        ImageView locationImageView = binding.rightImageView;
        TextView estimateTimeTextView = binding.estimateTime;
        RelativeLayout parentLayout = (RelativeLayout) agentImageView.getParent();

        // Use ViewTreeObserver to get the layout positions after the layout is complete
        ViewTreeObserver viewTreeObserver = parentLayout.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove the listener to avoid multiple calls
                parentLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Get positions relative to the parent
                int[] locationPosition = new int[2];
                locationImageView.getLocationOnScreen(locationPosition);
                int locationX = locationPosition[0];

                int[] agentPosition = new int[2];
                agentImageView.getLocationOnScreen(agentPosition);
                int agentX = agentPosition[0];

                int[] parentPosition = new int[2];
                parentLayout.getLocationOnScreen(parentPosition);
                int parentX = parentPosition[0];

                // Calculate the distance to move in pixels
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                float agentWidthPx = 50 * displayMetrics.density;
                float distanceToMovePx = locationX - agentX - agentWidthPx;

                // Set up the countdown timer
                countdownTimer = new CountDownTimer(totalTimeInSeconds * 1000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        long minutesRemaining = millisUntilFinished / 1000 / 60;
                        long secondsRemaining = millisUntilFinished / 1000 % 60;
                        String formattedTime = String.format("%02d:%02d", minutesRemaining, secondsRemaining);
                        String text = getString(R.string.agent_is_minutes_away, formattedTime);
                        estimateTimeTextView.setText(text);
                    }

                    @Override
                    public void onFinish() {
                        estimateTimeTextView.setText(getString(R.string.agent_arrived_at_location));
                    }
                }.start();

                // Set up the animator
                ObjectAnimator animator = ObjectAnimator.ofFloat(agentImageView, View.TRANSLATION_X, 0f, distanceToMovePx);
                animator.setDuration(totalTimeInSeconds * 1000);
                animator.setInterpolator(new LinearInterpolator());
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Animation finished
                        estimateTimeTextView.setText(getString(R.string.agent_arrived_at_location));
                    }
                });
                animator.start();
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
    }

}