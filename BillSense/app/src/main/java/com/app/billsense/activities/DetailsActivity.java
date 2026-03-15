package com.app.billsense.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityDetailsBinding;
import com.app.billsense.databinding.ItemCasesBinding;
import com.app.billsense.databinding.ItemFaqBinding;
import com.app.billsense.databinding.ItemProcessDetectionBinding;
import com.app.billsense.databinding.ItemTriviaBinding;
import com.app.billsense.databinding.ItemTutorialsBinding;
import com.app.billsense.databinding.ItemVotingPostBinding;
import com.app.billsense.model.Cases;
import com.app.billsense.model.Detections;
import com.app.billsense.model.FAQs;
import com.app.billsense.model.Trivia;
import com.app.billsense.model.Tutorials;
import com.app.billsense.model.VotingPosts;
import com.app.billsense.utils.DialogUtils;
import com.app.billsense.utils.FBUtils;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DetailsActivity extends AppCompatActivity {
    private static final String TAG = "==DetailsActivity";
    private ActivityDetailsBinding binding;

    private FirebaseDatabase firebaseDatabase;
    private FBUtils fbUtils;

    private String itemId;
    private String itemType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        setSupportActionBar(binding.toolbarDetails);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        firebaseDatabase = FirebaseDatabase.getInstance();
        fbUtils = new FBUtils();

        itemId = getIntent().getStringExtra("ITEM_ID");
        itemType = getIntent().getStringExtra("ITEM_TYPE");
        // firebasePath = getIntent().getStringExtra("FIREBASE_PATH"); // Optional

        if (itemId == null || itemType == null) {
            Toast.makeText(this, "Error: Item data missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ITEM_ID or ITEM_TYPE is null.");
            finish();
            return;
        }

        binding.toolbarDetails.setTitle(itemType + " Details");
        fetchItemDetails();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Go back to the previous activity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchItemDetails() {
        binding.progressBarDetails.setVisibility(View.VISIBLE);
        binding.detailContentContainer.removeAllViews(); // Clear previous content

        String pathToFetch = getFirebasePathForItemType(itemType);
        if (pathToFetch == null) {
            Log.e(TAG, "Could not determine Firebase path for type: " + itemType);
            Toast.makeText(this, "Error: Invalid item type.", Toast.LENGTH_SHORT).show();
            binding.progressBarDetails.setVisibility(View.GONE);
            return;
        }
        Log.e(TAG, "Firebase path for type: " + itemType);

        DatabaseReference itemRef = firebaseDatabase.getReference(pathToFetch).child(itemId);

        itemRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                binding.progressBarDetails.setVisibility(View.GONE);
                if (snapshot.exists()) {
                    populateDetails(snapshot);
                } else {
                    Log.w(TAG, "Item not found at path: " + itemRef.toString());
                    Toast.makeText(DetailsActivity.this, "Details not found.", Toast.LENGTH_SHORT).show();
                    // Optionally show a "not found" message in the container
                    TextView notFoundView = new TextView(DetailsActivity.this);
                    notFoundView.setText("The requested details could not be found.");
                    notFoundView.setPadding(16, 16, 16, 16);
                    binding.detailContentContainer.addView(notFoundView);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                binding.progressBarDetails.setVisibility(View.GONE);
                Log.e(TAG, "Failed to fetch item details: " + error.getMessage(), error.toException());
                Toast.makeText(DetailsActivity.this, "Failed to load details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getFirebasePathForItemType(String type) {
        if (type == null) return null;
        switch (type) {
            case "Tutorials":
                return fbUtils.TUTORIAL_PATH;
            case "Trivia":
                return fbUtils.TRIVIA_PATH;
            case "FAQs":
                return fbUtils.FAQ_PATH;
            case "Cases":
                return fbUtils.CASES_PATH;
            case "Detections":
                return fbUtils.DETECTIONS_PATH;
            case "Voting Posts":
                return fbUtils.VOTING_POST;
            default:
                return null;
        }
    }

    private void populateDetails(DataSnapshot snapshot) {
        LayoutInflater inflater = LayoutInflater.from(this);

        switch (itemType) {
            case "Tutorials":
                Tutorials tutorial = snapshot.getValue(Tutorials.class);
                if (tutorial != null) {
                    ItemTutorialsBinding tutorialBinding = ItemTutorialsBinding.inflate(inflater, binding.detailContentContainer, false);
                    tutorialBinding.tutorialTitle.setText(tutorial.getTitle());
                    tutorialBinding.tutorialDesc.setText(tutorial.getDescription());

                    // Handle media (image/video)
                    if ("image".equalsIgnoreCase(tutorial.getMediaType()) && tutorial.getDownloadImageUrl() != null) {
                        tutorialBinding.tutorialImg.setVisibility(View.VISIBLE);
                        tutorialBinding.tutorialVid.setVisibility(View.GONE);
                        tutorialBinding.playVid.setVisibility(View.GONE);
                        Glide.with(this).load(tutorial.getDownloadImageUrl()).placeholder(R.drawable.ic_image_placeholder).into(tutorialBinding.tutorialImg); // Add a placeholder
                    } else if ("video".equalsIgnoreCase(tutorial.getMediaType()) && tutorial.getDownloadVideoUrl() != null) {
                        tutorialBinding.tutorialVid.setVisibility(View.VISIBLE);
                        tutorialBinding.playVid.setVisibility(View.VISIBLE);
                        tutorialBinding.tutorialImg.setVisibility(View.GONE);
                        // For VideoView, you typically set the URI and start it, or use a library like ExoPlayer for better control
                        tutorialBinding.tutorialVid.setVideoPath(tutorial.getDownloadVideoUrl());
                        MediaController mediaController = new MediaController(this);
                        mediaController.setAnchorView(tutorialBinding.tutorialVid);
                        tutorialBinding.tutorialVid.setMediaController(mediaController);
                        tutorialBinding.playVid.setOnClickListener(v -> tutorialBinding.tutorialVid.start());
                        // Consider auto-play or a play button
                    } else {
                        tutorialBinding.tutorialImg.setVisibility(View.GONE);
                        tutorialBinding.tutorialVid.setVisibility(View.GONE);
                        tutorialBinding.playVid.setVisibility(View.GONE);
                    }
                    binding.detailContentContainer.addView(tutorialBinding.getRoot());
                }
                break;

            case "Trivia":
                Trivia trivia = snapshot.getValue(Trivia.class);
                if (trivia != null) {
                    ItemTriviaBinding triviaBinding = ItemTriviaBinding.inflate(inflater, binding.detailContentContainer, false);
                    triviaBinding.triviaTitle.setText(trivia.getTitle());
                    triviaBinding.triviaDesc.setText(trivia.getDescription());
                    // Handle media similar to Tutorials
                    if ("image".equalsIgnoreCase(trivia.getMediaType()) && trivia.getDownloadImageUrl() != null) {
                        triviaBinding.triviaImg.setVisibility(View.VISIBLE);
                        triviaBinding.triviaVid.setVisibility(View.GONE);
                        triviaBinding.playVid.setVisibility(View.GONE);
                        Glide.with(this).load(trivia.getDownloadImageUrl()).placeholder(R.drawable.ic_image_placeholder).into(triviaBinding.triviaImg);
                    } else if ("video".equalsIgnoreCase(trivia.getMediaType()) && trivia.getDownloadVideoUrl() != null) {
                        triviaBinding.triviaVid.setVisibility(View.VISIBLE);
                        triviaBinding.playVid.setVisibility(View.VISIBLE);
                        triviaBinding.triviaImg.setVisibility(View.GONE);
                        triviaBinding.triviaVid.setVideoPath(trivia.getDownloadVideoUrl());
                        MediaController mediaController = new MediaController(this);
                        mediaController.setAnchorView(triviaBinding.triviaVid);
                        triviaBinding.triviaVid.setMediaController(mediaController);
                        triviaBinding.playVid.setOnClickListener(v -> triviaBinding.triviaVid.start());
                    } else {
                        triviaBinding.triviaImg.setVisibility(View.GONE);
                        triviaBinding.triviaVid.setVisibility(View.GONE);
                        triviaBinding.playVid.setVisibility(View.GONE);
                    }
                    binding.detailContentContainer.addView(triviaBinding.getRoot());
                }
                break;

            case "FAQs":
                FAQs faq = snapshot.getValue(FAQs.class);
                if (faq != null) {
                    ItemFaqBinding faqBinding = ItemFaqBinding.inflate(inflater, binding.detailContentContainer, false);
                    faqBinding.questionTv.setText(faq.getQuestion());
                    faqBinding.answerTv.setText(faq.getAnswer());
                    binding.detailContentContainer.addView(faqBinding.getRoot());
                }
                break;

            case "Cases":
                Cases caseItem = snapshot.getValue(Cases.class); // Ensure you have a Cases.java model
                if (caseItem != null) {
                    ItemCasesBinding caseBinding = ItemCasesBinding.inflate(inflater, binding.detailContentContainer, false);
                    caseBinding.textViewCaseTitle.setText(caseItem.getTitle());
                    caseBinding.textViewUserName.setText(caseItem.getUserName()); // Assuming Cases model has these
                    caseBinding.textViewAddress.setText(caseItem.getAddress());
                    caseBinding.textViewDate.setText(caseItem.getDate());
                    caseBinding.textViewTime.setText(caseItem.getCaseTime());
                    caseBinding.textViewDescription.setText(caseItem.getDescription());
                    if (caseItem.getImage() != null && !caseItem.getImage().isEmpty()) {
                        caseBinding.imageViewCaseImage.setVisibility(View.VISIBLE);
                        Glide.with(this).load(caseItem.getImage()).placeholder(R.drawable.ic_image_placeholder).into(caseBinding.imageViewCaseImage);
                    } else {
                        caseBinding.imageViewCaseImage.setVisibility(View.GONE);
                    }
                    binding.detailContentContainer.addView(caseBinding.getRoot());
                }
                break;

            case "Detections":
                Detections detection = snapshot.getValue(Detections.class); // Ensure you have Detections.java model
                if (detection != null) {
                    ItemProcessDetectionBinding detectionBinding = ItemProcessDetectionBinding.inflate(inflater, binding.detailContentContainer, false);
                    // The item_process_detection.xml has process_detection_title and process_detection_img
                    // Your Detections model might have 'type' and 'content', and an 'imageUrl'
                    detectionBinding.processDetectionTitle.setText(detection.getType() + ": " + detection.getContent()); // Example, adjust as per your model
                    if (detection.getImage() != null && !detection.getImage().isEmpty()) {
                        detectionBinding.processDetectionImg.setVisibility(View.VISIBLE);
                        Glide.with(this).load(detection.getImage())
                                .placeholder(R.drawable.ic_image_placeholder)
                                .into(detectionBinding.processDetectionImg);
                    } else {
                        detectionBinding.processDetectionImg.setVisibility(View.GONE);
                    }
                    detectionBinding.processDetectionImg.setOnClickListener(view -> {
                        DialogUtils.displayFullImageDialog(DetailsActivity.this, "Detection", detection.getImage(), null);
                    });
                    binding.detailContentContainer.addView(detectionBinding.getRoot());
                }
                break;

            case "Voting Posts":
                VotingPosts votingPost = snapshot.getValue(VotingPosts.class); // Ensure you have VotingPost.java model
                if (votingPost != null) {
                    ItemVotingPostBinding postBinding = ItemVotingPostBinding.inflate(inflater, binding.detailContentContainer, false);
                    postBinding.postTitle.setText(votingPost.getTitle());
                    postBinding.postDesc.setText(votingPost.getDescription());
                    postBinding.postDate.setText(votingPost.getDate()); // Assuming date field exists

                    // Hide voting UI elements if this is just a detail view without active voting
                    postBinding.votingLlMain.setVisibility(View.GONE); // Or configure as needed
                    postBinding.viewComment.setVisibility(View.GONE); // Or make it functional
                    postBinding.editPost.setVisibility(View.GONE);


                    // Handle media similar to Tutorials
                    if ("image".equalsIgnoreCase(votingPost.getMediaType()) && votingPost.getDownloadImageUrl() != null) {
                        postBinding.postImg.setVisibility(View.VISIBLE);
                        postBinding.postVid.setVisibility(View.GONE);
                        postBinding.playVid.setVisibility(View.GONE);
                        Glide.with(this).load(votingPost.getDownloadImageUrl()).placeholder(R.drawable.ic_image_placeholder).into(postBinding.postImg);
                    } else if ("video".equalsIgnoreCase(votingPost.getMediaType()) && votingPost.getDownloadVideoUrl() != null) {
                        postBinding.postVid.setVisibility(View.VISIBLE);
                        postBinding.playVid.setVisibility(View.VISIBLE);
                        postBinding.postImg.setVisibility(View.GONE);
                        postBinding.postVid.setVideoPath(votingPost.getDownloadVideoUrl());
                        MediaController mediaController = new MediaController(this);
                        mediaController.setAnchorView(postBinding.postVid);
                        postBinding.postVid.setMediaController(mediaController);
                        postBinding.playVid.setOnClickListener(v -> postBinding.postVid.start());
                    } else {
                        postBinding.postImg.setVisibility(View.GONE);
                        postBinding.postVid.setVisibility(View.GONE);
                        postBinding.playVid.setVisibility(View.GONE);
                    }
                    binding.detailContentContainer.addView(postBinding.getRoot());
                }
                break;

            default:
                Log.w(TAG, "No layout defined for item type: " + itemType);
                TextView errorView = new TextView(this);
                errorView.setText("Cannot display details for this item type.");
                binding.detailContentContainer.addView(errorView);
                break;
        }
    }
}
