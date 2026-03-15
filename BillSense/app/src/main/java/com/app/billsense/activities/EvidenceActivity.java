package com.app.billsense.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityEvidenceBinding;
import com.app.billsense.fragments.EvidenceHistoryFragment;
import com.app.billsense.fragments.FaqFragment;
import com.app.billsense.fragments.TriviaFragment;
import com.app.billsense.fragments.TutorialFragment;
import com.app.billsense.fragments.WriteCaseFragment;
import com.google.android.material.appbar.MaterialToolbar;

public class EvidenceActivity extends AppCompatActivity {
    private ActivityEvidenceBinding binding;
    private MaterialToolbar toolbar;
    private LinearLayout selectedButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEvidenceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.evidence));

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        binding.history.setOnClickListener(v -> {
            openFragment(new EvidenceHistoryFragment());
            updateButtonSelection(binding.history);
        });
        binding.writeCase.setOnClickListener(v -> {
            openFragment(new WriteCaseFragment());
            updateButtonSelection(binding.writeCase);
        });
        binding.compareBill.setOnClickListener(v -> {
            startActivity(new Intent(EvidenceActivity.this, CompareBillActivity.class));
        });

        // Open TutorialFragment by default
        openFragment(new WriteCaseFragment());
        updateButtonSelection(binding.writeCase);
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