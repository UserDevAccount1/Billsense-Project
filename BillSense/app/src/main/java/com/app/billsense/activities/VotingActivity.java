package com.app.billsense.activities;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.PostCommentAdapter;
import com.app.billsense.adapters.VotingPostAdapter;
import com.app.billsense.databinding.ActivityVotingBinding;
import com.app.billsense.databinding.DialogPostCommentsBinding;
import com.app.billsense.interfaces.CommentInterface;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.interfaces.VotingPostInterface;
import com.app.billsense.model.Comments;
import com.app.billsense.model.VotingPosts;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.PrefManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.database.DataSnapshot;
import com.google.gson.Gson;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class VotingActivity extends AppCompatActivity implements VotingPostInterface, CommentInterface {
    private ActivityVotingBinding binding;
    private FBUtils fbUtils;
    private VotingPostAdapter postAdapter;
    private final ArrayList<VotingPosts> votingPostsArrayList = new ArrayList<>();
    private PostCommentAdapter commentAdapter;
    private final ArrayList<Comments> commentsArrayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVotingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.voting_system));

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        fbUtils = new FBUtils();

        binding.postFab.setOnClickListener(view -> {
            startActivity(new Intent(VotingActivity.this, AddVotingPostActivity.class));
        });

        getAllVotingPost();
        setupSearchListener();

    }

    private void setupSearchListener() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (postAdapter != null) {
                    postAdapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void getAllVotingPost() {
        binding.votingRv.setHasFixedSize(true);
        binding.votingRv.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false));
        postAdapter = new VotingPostAdapter(this, votingPostsArrayList, this);
        binding.votingRv.setAdapter(postAdapter);
        fbUtils.getAllDataFromPath(fbUtils.VOTING_POST, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                votingPostsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    VotingPosts posts = snapshot.getValue(VotingPosts.class);
                    votingPostsArrayList.add(posts);
                }

                votingPostsArrayList.sort((post1, post2) -> {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                        String dateTime1 = post1.getDate() + " " + post1.getTime();
                        String dateTime2 = post2.getDate() + " " + post2.getTime();

                        Date date1 = dateFormat.parse(dateTime1);
                        Date date2 = dateFormat.parse(dateTime2);

                        int dateComparison = date2.compareTo(date1); // Latest first
                        if (dateComparison != 0) {
                            return dateComparison; // Dates are different, use date order
                        } else {
                            // Dates are equal, break tie using ID
                            // Assuming your VotingPosts class has a getId() method that returns a unique identifier (e.g., a long or String)
                            return post2.getId().compareTo(post1.getId()); // You might need to adjust depending on the type of your ID
                        }
                    } catch (ParseException e) {
                        Log.e("VotingPostAdapter", "Error parsing dates for sorting", e);
                        return 0; // Treat as equal for now
                    }
                });

                if (postAdapter != null) {
                    postAdapter.updateData(new ArrayList<>(votingPostsArrayList)); // Update adapter with new master list
                    // Re-apply current filter if any
                    String currentQuery = binding.searchEditText.getText().toString();
                    if (!currentQuery.isEmpty()) {
                        postAdapter.getFilter().filter(currentQuery);
                    }
                }

                Log.d("==post size", votingPostsArrayList.size() + "");
            }

            @Override
            public void onDataNotFound() {
                votingPostsArrayList.clear();
                if (postAdapter != null){
                    postAdapter.updateData(new ArrayList<>(votingPostsArrayList));
                }
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                votingPostsArrayList.clear();
                if (postAdapter != null){
                    postAdapter.updateData(new ArrayList<>(votingPostsArrayList));
                }
            }
        });
    }

    @Override
    public void onPostReal(VotingPosts post) {
        String userId = PrefManager.getInstance().getUserId();

        if (userId == null || (post.getVoters() != null && post.getVoters().containsKey(userId))) {
            showToast(VotingActivity.this, "You have already voted.");
            return;
        }

        int totalVotesInt;
        int realVotesInt = 0;
        int fakeVotesInt = 0;
        if (post.getRealVotes() != null && !post.getRealVotes().isEmpty()) {
            realVotesInt = Integer.parseInt(post.getRealVotes());
        }
        if (post.getFakeVotes() != null && !post.getFakeVotes().isEmpty()) {
            fakeVotesInt = Integer.parseInt(post.getFakeVotes());
        }
        realVotesInt = realVotesInt + 1;
        totalVotesInt = realVotesInt + fakeVotesInt;
        String voteResult = ""; // Initialize with a default value

        if (totalVotesInt > 0) {
            double realVotePercentage = (double) realVotesInt / totalVotesInt * 100;
            double fakeVotePercentage = (double) fakeVotesInt / totalVotesInt * 100;
            voteResult = String.format(Locale.getDefault(), "Real: %.2f%%, Fake: %.2f%%", realVotePercentage, fakeVotePercentage);
        } else {
            voteResult = "No votes";  // Or some other appropriate message
        }
        String realVote = String.valueOf(realVotesInt);
        String totalVote = String.valueOf(totalVotesInt);
        String fakeVote = String.valueOf(fakeVotesInt);
        fbUtils.updateVotingPostData(fbUtils.VOTING_POST, post.getId(), new FBInterface.OnVotingPostDataSaveCallBack() {
                    @Override
                    public void onVotingPostDataSaveSuccess() {
                        showToast(VotingActivity.this, "Vote Added");
                    }

                    @Override
                    public void onVotingPostDataSaveFailure(Exception exception) {
                        showToast(VotingActivity.this, "Failed to vote");
                    }
                },
                new Pair<>("totalVotes", totalVote),
                new Pair<>("realVotes", realVote),
                new Pair<>("votesResult", voteResult),
                new Pair<>("fakeVotes", fakeVote),
                new Pair<>("voters/" + userId, true)
        );
    }

    @Override
    public void onPostFake(VotingPosts post) {
        String userId = PrefManager.getInstance().getUserId();

        if (userId == null || (post.getVoters() != null && post.getVoters().containsKey(userId))) {
            showToast(VotingActivity.this, "You have already voted.");
            return;
        }
        int totalVotesInt;
        int realVotesInt = 0;
        int fakeVotesInt = 0;
        if (post.getRealVotes() != null && !post.getRealVotes().isEmpty()) {
            realVotesInt = Integer.parseInt(post.getRealVotes());
        }
        if (post.getFakeVotes() != null && !post.getFakeVotes().isEmpty()) {
            fakeVotesInt = Integer.parseInt(post.getFakeVotes());
        }
        fakeVotesInt = fakeVotesInt + 1;
        totalVotesInt = realVotesInt + fakeVotesInt;
        String voteResult = ""; // Initialize with a default value

        if (totalVotesInt > 0) {
            double realVotePercentage = (double) realVotesInt / totalVotesInt * 100;
            double fakeVotePercentage = (double) fakeVotesInt / totalVotesInt * 100;
            voteResult = String.format(Locale.getDefault(), "Real: %.2f%%, Fake: %.2f%%", realVotePercentage, fakeVotePercentage);
        } else {
            voteResult = "No votes";  // Or some other appropriate message
        }
        String realVote = String.valueOf(realVotesInt);
        String totalVote = String.valueOf(totalVotesInt);
        String fakeVote = String.valueOf(fakeVotesInt);
        fbUtils.updateVotingPostData(fbUtils.VOTING_POST, post.getId(), new FBInterface.OnVotingPostDataSaveCallBack() {
                    @Override
                    public void onVotingPostDataSaveSuccess() {
                        showToast(VotingActivity.this, "Vote Added");
                    }

                    @Override
                    public void onVotingPostDataSaveFailure(Exception exception) {
                        showToast(VotingActivity.this, "Failed to vote");
                    }
                },
                new Pair<>("totalVotes", totalVote),
                new Pair<>("realVotes", realVote),
                new Pair<>("votesResult", voteResult),
                new Pair<>("fakeVotes", fakeVote),
                new Pair<>("voters/" + userId, true)
        );
    }

    @Override
    public void onPostComments(VotingPosts post) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogPostCommentsBinding commentsBinding = DialogPostCommentsBinding.inflate(
                LayoutInflater.from(this));
        dialog.setContentView(commentsBinding.getRoot());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        commentsBinding.closeIv.setOnClickListener(view -> dialog.dismiss());

        commentsBinding.commentsRv.setHasFixedSize(true);
        commentsBinding.commentsRv.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false));
        commentAdapter = new PostCommentAdapter(this, commentsArrayList, this);
        commentsBinding.commentsRv.setAdapter(commentAdapter);
        fbUtils.getAllDataFromPath(fbUtils.VOTING_POST + "/" + post.getId() +
                "/" + fbUtils.VOTING_POST_COMMENTS, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                commentsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Comments comments = snapshot.getValue(Comments.class);
                    commentsArrayList.add(comments);
                    commentAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onDataNotFound() {
                commentsArrayList.clear();
                commentAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                commentsArrayList.clear();
                commentAdapter.notifyDataSetChanged();
            }
        });

        commentsBinding.postComment.setOnClickListener(view -> {
            String comment = commentsBinding.commentInput.getText().toString();
            String userId = PrefManager.getInstance().getUserId();
            String userName = PrefManager.getInstance().getUserName();
            if (!InputValidator.isValidComment(comment)) {
                InputValidator.setInputError(commentsBinding.commentInputLayout,
                        getString(R.string.invalid_comment));
            } else {
                showProgressDialog(VotingActivity.this);
                fbUtils.savePostComment(fbUtils.VOTING_POST, post.getId(), userId, new FBInterface.OnCommentSaveCallBack() {
                            @Override
                            public void onCommentSaveSuccess() {
                                hideProgressDialog();
                                showToast(VotingActivity.this, "Comment Added");
                                dialog.dismiss();
                            }

                            @Override
                            public void onCommentSaveFailure(Exception e) {
                                hideProgressDialog();
                                showToast(VotingActivity.this, "Comment failed to post");
                            }
                        },
                        new Pair<>("userId", userId),
                        new Pair<>("postId", post.getId()),
                        new Pair<>("userName", userName),
                        new Pair<>("comment", comment),
                        new Pair<>("likes", "0")

                );
            }
        });

        dialog.show();

    }

    @Override
    public void onEditPost(VotingPosts post) {
        Intent intent = new Intent(VotingActivity.this, AddVotingPostActivity.class);
        Gson gson = new Gson();
        String billJson = gson.toJson(post);
        intent.putExtra("post_json", billJson);
        startActivity(intent);
    }

    @Override
    public void onLikedComment(Comments comments) {
        int likesCount = 0;
        if (comments.getLikes() != null && !comments.getLikes().isEmpty()){
            likesCount = Integer.parseInt(comments.getLikes());
        }
        likesCount = likesCount + 1;
        String likes = String.valueOf(likesCount);
        fbUtils.savePostCommentLikes(fbUtils.VOTING_POST, comments.getPostId(),
                comments.getUserId(), comments.getId(), new FBInterface.OnCommentSaveCallBack() {
                    @Override
                    public void onCommentSaveSuccess() {
                        showToast(VotingActivity.this, "Comment Liked");
                    }

                    @Override
                    public void onCommentSaveFailure(Exception e) {
                        showToast(VotingActivity.this, e.getMessage());

                    }
                }, new Pair<>("likes", likes));
    }
}