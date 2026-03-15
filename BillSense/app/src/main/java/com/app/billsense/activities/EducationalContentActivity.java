package com.app.billsense.activities;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityEducationalContentBinding;
import com.app.billsense.fragments.FaqFragment;
import com.app.billsense.fragments.TriviaFragment;
import com.app.billsense.fragments.TutorialFragment;
import com.google.android.material.appbar.MaterialToolbar;

public class EducationalContentActivity extends AppCompatActivity {
    private ActivityEducationalContentBinding binding;
    private MaterialToolbar toolbar;
    private LinearLayout selectedButton;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEducationalContentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.educational_content));

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        binding.tutorial.setOnClickListener(v -> {
            openFragment(new TutorialFragment());
            updateButtonSelection(binding.tutorial);
        });
        binding.trivia.setOnClickListener(v -> {
            openFragment(new TriviaFragment());
            updateButtonSelection(binding.trivia);
        });
        binding.faq.setOnClickListener(v -> {
            openFragment(new FaqFragment());
            updateButtonSelection(binding.faq);
        });

        // Open TutorialFragment by default
        openFragment(new TutorialFragment());
        updateButtonSelection(binding.tutorial);
    }

    private void openFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(binding.fragmentContainer.getId(), fragment);
//        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void updateButtonSelection(LinearLayout button) {
        // Reset the previously selected button
        if (selectedButton != null) {
            resetButton(selectedButton);
        }

        // Highlight the newly selected button
        highlightButton(button);

        // Update the selectedButton reference
        selectedButton = button;
    }

    private void highlightButton(LinearLayout button) {
        button.setBackground(ContextCompat.getDrawable(this, R.drawable.rounded_bg_selected));
        ImageView imageView = (ImageView) button.getChildAt(0);
        TextView textView = (TextView) button.getChildAt(1);
        textView.setTextColor(Color.parseColor("#FFFFFF"));
    }

    private void resetButton(LinearLayout button) {
        button.setBackground(ContextCompat.getDrawable(this, R.drawable.rounded_bg));
        ImageView imageView = (ImageView) button.getChildAt(0);
        TextView textView = (TextView) button.getChildAt(1);
        textView.setTextColor(Color.parseColor("#000000"));
    }

}