package com.app.billsense.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.app.billsense.utils.InputValidator.isValidDescription;
import static com.app.billsense.utils.InputValidator.isValidQuestion;
import static com.app.billsense.utils.InputValidator.isValidTitle;
import static com.app.billsense.utils.InputValidator.setEditTextError;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.getCurrentDate;
import static com.app.billsense.utils.Utils.getCurrentTime;
import static com.app.billsense.utils.Utils.showToast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.CompoundButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityAddVotingPostBinding;
import com.app.billsense.fcm.FcmNotificationSender;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.VotingPosts;
import com.app.billsense.utils.FBStorageUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.bumptech.glide.Glide;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import java.util.Calendar;

public class AddVotingPostActivity extends AppCompatActivity {
    private ActivityAddVotingPostBinding binding;
    private String userId;
    private Calendar startDateTime;
    private Calendar endDateTime;
    private String startDateString;
    private String startTimeString;
    private String endDateString;
    private String endTimeString;
    private String votingDateTime;
    private FBUtils fbUtils;
    private String title, description, mediaType = "none", votingQuestion,
            downloadImageUrl, downloadVideoUrl, votingEnabled = "false";
    private ActivityResultLauncher<Intent> videoPickActivityResultLauncher;
    private ActivityResultLauncher<Intent> imagePickerActivityResultLauncher;
    private Uri imageUri, videoUri;
    private String id = null, date, time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddVotingPostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = PrefManager.getInstance().getUserId();

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.add_post));

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("post_json")) {
            String billJson = intent.getStringExtra("post_json");
            Gson gson = new Gson();
            VotingPosts post = gson.fromJson(billJson, VotingPosts.class);

            if (post != null) {
                // Populate UI
                id = post.getId();
                date = post.getDate();
                time = post.getTime();
                Log.d("AddPost", "Post ID: " + id);
                mediaType = post.getMediaType();
                binding.inputTitle.setText(post.getTitle());
                binding.inputTitle.setFocusable(false);
                binding.inputTitle.setEnabled(false);
                binding.inputDescription.setText(post.getDescription());
                binding.addMediaLyt.setVisibility(GONE);
                binding.addPhoto.setVisibility(VISIBLE);
                binding.updatePostContent.setVisibility(VISIBLE);
                binding.updatePost.setVisibility(VISIBLE);
                if (post.getMediaType().equals("image")) {
                    binding.postImg.setVisibility(VISIBLE);
                    binding.tutorialVid.setVisibility(GONE);
                    if (post.getDownloadImageUrl() != null) {
                        Glide.with(this).load(post.getDownloadImageUrl()).into(binding.postImg);
                    }
                } else if (post.getMediaType().equals("video")) {
                    binding.postImg.setVisibility(GONE);
                    binding.tutorialVid.setVisibility(VISIBLE);
                    if (post.getDownloadVideoUrl() != null) {
                        binding.tutorialVid.setVideoPath(post.getDownloadVideoUrl());
                    }
                }else {
                    binding.updatePostContent.setVisibility(GONE);
                }
                binding.votingSw.setChecked(post.getVotingEnabled().equals("true"));
                binding.votingLlMain.setVisibility(
                        post.getVotingEnabled().equals("true") ? VISIBLE : GONE
                );
                binding.inputVotingQues.setText(post.getVotingQuestion());

                if (post.getStartDate() != null && post.getStartTime() != null &&
                    post.getEndDate() != null && post.getEndTime() != null) {
                    binding.pickDate.setText(String.format("%s %s | %s %s",
                            post.getStartDate(), post.getStartTime(), post.getEndDate(), post.getEndTime()));
                }
                binding.addPost.setVisibility(GONE);
            }
        }

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        fbUtils = new FBUtils();

        initActivityResultLauncher();

        binding.pickDate.setOnClickListener(view -> showStartDateTimePicker());

        binding.addPhoto.setOnClickListener(view -> {
            ImagePicker.with(AddVotingPostActivity.this)
                    .galleryOnly()
                    .galleryMimeTypes(new String[]{
                            "image/png",
                            "image/jpg",
                            "image/jpeg"
                    })
                    .crop()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .createIntent(intent1 -> {
                        imagePickerActivityResultLauncher.launch(intent1);
                        return null;
                    });
        });

        binding.addVideo.setOnClickListener(view -> {
            pickVideo();
        });

        binding.addPost.setOnClickListener(view -> {
            title = binding.inputTitle.getText().toString();
            description = binding.inputDescription.getText().toString();
            votingQuestion = binding.inputVotingQues.getText().toString();

            if (!isValidTitle(title)) {
                setEditTextError(binding.inputTitle,
                        getString(R.string.invalid_title));
            } else if (!isValidDescription(description)) {
                setEditTextError(binding.inputDescription,
                        getString(R.string.invalid_description));
            } else {
                if (binding.votingSw.isChecked()) {
                    if (!isValidQuestion(votingQuestion)) {
                        setEditTextError(binding.inputVotingQues,
                                getString(R.string.invalid_question));
                    } else if (!checkDateTimeValidity()) {
                        showToast(this, "End date/time must be at least 30 minutes after start date/time");
                    } else {
                        showProgressDialog(AddVotingPostActivity.this);
                        saveVotingPostData(binding.votingSw.isChecked());
                    }
                } else {
                    showProgressDialog(AddVotingPostActivity.this);
                    saveVotingPostData(binding.votingSw.isChecked());
                }
            }

        });

        binding.updatePost.setOnClickListener(view -> {
            title = binding.inputTitle.getText().toString();
            description = binding.inputDescription.getText().toString();
            votingQuestion = binding.inputVotingQues.getText().toString();

            if (!isValidTitle(title)) {
                setEditTextError(binding.inputTitle,
                        getString(R.string.invalid_title));
            } else if (!isValidDescription(description)) {
                setEditTextError(binding.inputDescription,
                        getString(R.string.invalid_description));
            } else {
                if (binding.votingSw.isChecked()) {
                    if (!isValidQuestion(votingQuestion)) {
                        setEditTextError(binding.inputVotingQues,
                                getString(R.string.invalid_question));
                    } else if (!checkDateTimeValidity()) {
                        showToast(this, "End date/time must be at least 30 minutes after start date/time");
                    } else {
                        showProgressDialog(AddVotingPostActivity.this);
                        saveVotingPostData(binding.votingSw.isChecked());
                    }
                } else {
                    showProgressDialog(AddVotingPostActivity.this);
                    saveVotingPostData(binding.votingSw.isChecked());
                }
            }

        });


        binding.votingSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                binding.votingLlMain.setVisibility(b ? View.VISIBLE : View.GONE);
                votingEnabled = String.valueOf(b);
            }
        });

    }

    private void initActivityResultLauncher() {
        // Initialize the ActivityResultLauncher for Video
        videoPickActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            videoUri = data.getData();
                            // Use the videoUri here
                            Log.d("TAG", "Video URI: " + videoUri.toString());
                            showProgressDialog(AddVotingPostActivity.this);
                            uploadVotingPostVideo();
                        } else {
                            // No video selected
                            Log.d("TAG", "No video selected");
                            showToast(AddVotingPostActivity.this, "Task Cancelled");
                        }

                    }
                }
        );

        // Initialize the ActivityResultLauncher for Image picker
        imagePickerActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        imageUri = result.getData().getData();
                        showProgressDialog(AddVotingPostActivity.this);
                        uploadVotingPostImage();
                    } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                        showToast(AddVotingPostActivity.this, ImagePicker.getError(result.getData()));
                    } else {
                        showToast(AddVotingPostActivity.this, "Task Cancelled");
                    }
                }
        );
    }

    private void saveVotingPostData(boolean checked) {
        if (id == null){
            date = getCurrentDate();
            time = getCurrentTime();
        }
        fbUtils.saveVotingPostData(fbUtils.VOTING_POST, id, new FBInterface.OnVotingPostDataSaveCallBack() {
                    @Override
                    public void onVotingPostDataSaveSuccess() {
                        if (id == null) {
                            FcmNotificationSender.get().sendNotificationToAllCustomers(
                                    "New Post", "New Post Added" + " " + title + "." + (checked ? " Vote Now" : ""),
                                    userId, "posts"
                            );
                        }
                        showToast(AddVotingPostActivity.this, "Post Added.");
                        hideProgressDialog();
//                        startActivity(getIntent());
                        finish();
                    }

                    @Override
                    public void onVotingPostDataSaveFailure(Exception exception) {
                        hideProgressDialog();
                        showToast(AddVotingPostActivity.this, "Error: " + exception.getMessage());
                    }
                },
                new Pair<>("title", title),
                new Pair<>("description", description),
                new Pair<>("userId", userId),
                new Pair<>("userName", PrefManager.getInstance().getUserName()),
                new Pair<>("date", date),
                new Pair<>("time", time),
                new Pair<>("mediaType", mediaType),
                new Pair<>("votingQuestion", votingQuestion),
                new Pair<>("startDate", startDateString),
                new Pair<>("startTime", startTimeString),
                new Pair<>("endDate", endDateString),
                new Pair<>("endTime", endTimeString),
                new Pair<>("votingDateTime", votingDateTime),
                new Pair<>("status", "published"),
                new Pair<>("votingEnabled", votingEnabled),
                new Pair<>("downloadImageUrl", downloadImageUrl),
                new Pair<>("downloadVideoUrl", downloadVideoUrl)
        );
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        videoPickActivityResultLauncher.launch(intent);
    }

    private void uploadVotingPostVideo() {
        binding.orTv.setVisibility(GONE);
        binding.addPhoto.setVisibility(GONE);
        mediaType = "video";
        FBStorageUtils.uploadFile(FBStorageUtils.POST_VIDEO_PATH, videoUri, FBStorageUtils.FileType.VIDEO,
                new FBInterface.FileUploadCallback() {
                    @Override
                    public void onFileUploadSuccess(String downloadUrl) {
                        downloadVideoUrl = downloadUrl;
                        hideProgressDialog();
                    }

                    @Override
                    public void onFileUploadFailure(Exception exception) {
                        hideProgressDialog();
                        showToast(AddVotingPostActivity.this, "Error: " + exception.getMessage());
                    }

                    @Override
                    public void onFileUploadProgress(double progress) {
                        binding.addVideoProgress.setVisibility(VISIBLE);
                        binding.addVideoProgressPercentage.setVisibility(VISIBLE);
                        int progressInt = (int) progress;
                        binding.addVideoProgress.setProgress(progressInt);
                        binding.addVideoProgressPercentage.setText(progressInt + "%");
                    }
                });
    }

    private void uploadVotingPostImage() {
        binding.orTv.setVisibility(GONE);
        binding.addVideo.setVisibility(GONE);
        mediaType = "image";
        FBStorageUtils.uploadFile(FBStorageUtils.POST_IMAGE_PATH, imageUri, FBStorageUtils.FileType.IMAGE,
                new FBInterface.FileUploadCallback() {
                    @Override
                    public void onFileUploadSuccess(String downloadUrl) {
                        downloadImageUrl = downloadUrl;
                        hideProgressDialog();
                    }

                    @Override
                    public void onFileUploadFailure(Exception exception) {
                        hideProgressDialog();
                        showToast(AddVotingPostActivity.this, "Error: " + exception.getMessage());
                    }

                    @Override
                    public void onFileUploadProgress(double progress) {
                        binding.addPhotoProgress.setVisibility(VISIBLE);
                        binding.addPhotoProgressPercentage.setVisibility(VISIBLE);
                        int progressInt = (int) progress;
                        binding.addPhotoProgress.setProgress(progressInt);
                        binding.addPhotoProgressPercentage.setText(progressInt + "%");
                    }
                });
    }


    private void showStartDateTimePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog datePicker = DatePickerDialog.newInstance(
                this::onStartDateSetListener,
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.setTitle(getString(R.string.select_voting_start_date));
        datePicker.setMinDate(now);
        datePicker.show(getSupportFragmentManager(), "StartDatePickerDialog");
    }

    private void onStartDateSetListener(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        startDateTime = Calendar.getInstance();
        startDateTime.set(year, monthOfYear, dayOfMonth);
        startDateString = String.format("%02d/%02d/%d", dayOfMonth, monthOfYear + 1, year);
        // Update your TextView to show startDateString
        showStartTimePicker();
    }

    private void showStartTimePicker() {
        if (startDateTime == null) {
            startDateTime = Calendar.getInstance();
        }
        TimePickerDialog timePicker = TimePickerDialog.newInstance(
                this::onStartTimeSetListener,
                startDateTime.get(Calendar.HOUR_OF_DAY),
                startDateTime.get(Calendar.MINUTE),
                true
        );
        timePicker.setTitle(getString(R.string.select_voting_start_time));
        timePicker.show(getSupportFragmentManager(), "StartTimePickerDialog");
    }

    private void onStartTimeSetListener(TimePickerDialog view, int hourOfDay, int minute, int second) {
        if (startDateTime == null) {
            startDateTime = Calendar.getInstance();
        }
        startDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        startDateTime.set(Calendar.MINUTE, minute);
        startTimeString = String.format("%02d:%02d", hourOfDay, minute);
        // Update your TextView to show startTimeString
        showEndDateTimePicker();
    }

    private void showEndDateTimePicker() {
        if (startDateTime == null) {
            // You might want to show an error or prevent proceeding
            return;
        }
        Calendar now = Calendar.getInstance();
        DatePickerDialog endDatePicker = DatePickerDialog.newInstance(
                this::onEndDateSetListener,
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        );
        endDatePicker.setTitle(getString(R.string.select_voting_end_date));
        endDatePicker.setMinDate(startDateTime);
        endDatePicker.show(getSupportFragmentManager(), "EndDatePickerDialog");
    }

    private void onEndDateSetListener(DatePickerDialog view, int endYear, int endMonth, int endDay) {
        if (startDateTime == null) {
            // You might want to show an error or prevent proceeding
            return;
        }
        endDateTime = Calendar.getInstance();
        endDateTime.set(endYear, endMonth, endDay);
        endDateString = String.format("%02d/%02d/%d", endDay, endMonth + 1, endYear);
        // Update your TextView to show endDateString
        showEndTimePicker();
    }

    private void showEndTimePicker() {
        if (endDateTime == null) {
            endDateTime = Calendar.getInstance();
        }
        TimePickerDialog endTimePicker = TimePickerDialog.newInstance(
                this::onEndTimeSetListener,
                endDateTime.get(Calendar.HOUR_OF_DAY),
                endDateTime.get(Calendar.MINUTE),
                true
        );
        endTimePicker.setTitle(getString(R.string.select_voting_end_time));
        endTimePicker.show(getSupportFragmentManager(), "EndTimePickerDialog");
    }

    private void onEndTimeSetListener(TimePickerDialog view, int endHour, int endMinute, int second) {
        if (endDateTime == null) {
            endDateTime = Calendar.getInstance();
        }
        endDateTime.set(Calendar.HOUR_OF_DAY, endHour);
        endDateTime.set(Calendar.MINUTE, endMinute);
        endTimeString = String.format("%02d:%02d", endHour, endMinute);
        updateCombinedDateTimeText();
    }

    private void updateCombinedDateTimeText() {
        if (startDateTime != null && endDateTime != null) {
            String formattedDateTime = String.format("Start: %s %s  |  End: %s %s",
                    startDateString, startTimeString, endDateString, endTimeString);
            votingDateTime = String.format("%s %s  |  %s %s",
                    startDateString, startTimeString, endDateString, endTimeString);
            binding.pickDate.setText(formattedDateTime);
        } else {
            binding.pickDate.setText(R.string.select_voting_period);
        }
    }

    private boolean checkDateTimeValidity() {
        if (startDateTime != null && endDateTime != null) {
            long timeDiffMillis = endDateTime.getTimeInMillis() - startDateTime.getTimeInMillis();
            long minDiffMillis = 30 * 60 * 1000; // 30 minutes in milliseconds

            if (timeDiffMillis < minDiffMillis) {
                startDateString = endDateString = startTimeString = endTimeString = votingDateTime = null;
                binding.pickDate.setText(getString(R.string.select_valid_voting_period));
                return false;
            }
            return true; // Valid date/time range
        }
        return false; // Not both dates/times are set
    }


}