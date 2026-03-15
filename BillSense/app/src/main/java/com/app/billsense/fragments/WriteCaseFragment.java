package com.app.billsense.fragments;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.billsense.R;
import com.app.billsense.databinding.FragmentWriteCaseBinding;
import com.app.billsense.fcm.FcmNotificationSender;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.utils.FBStorageUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.MapDialogFragment;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.Utils;
import com.bumptech.glide.Glide;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class WriteCaseFragment extends Fragment implements MapDialogFragment.AddressSelectionListener {
    private FragmentWriteCaseBinding binding;
    private final Calendar calendar = Calendar.getInstance();
    private FBUtils fbUtils;
    private String userId, userName, title, description, caseDate, caseTime, date, address, downloadImageUrl;
    private Double latitude, longitude;
    private ActivityResultLauncher<Intent> imagePickerActivityResultLauncher;
    private Uri imageUri;

    public WriteCaseFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the ActivityResultLauncher for Image picker
        imagePickerActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        imageUri = result.getData().getData();
                        if (imageUri != null) {
                            if (getContext() != null) {
                                Glide.with(getContext()).load(imageUri)
                                        .placeholder(R.drawable.add_photo)
                                        .into(binding.addEvidenceImg); // Or binding.caseImageView
                            }
                        }
//                        uploadProfileImage();
                    } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                        showToast(requireContext(), ImagePicker.getError(result.getData()));
                    } else {
                        showToast(requireContext(), "Task Cancelled");
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentWriteCaseBinding.inflate(inflater, container, false);
        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();
        userName = PrefManager.getInstance().getUserName();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.dateEditText.setOnClickListener(v -> showDatePicker());

        binding.timeEditText.setOnClickListener(v -> showTimePicker());

        binding.addEvidenceImg.setOnClickListener(view1 -> {
            ImagePicker.with(requireActivity())
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

        // Handle click on Address EditText
        binding.addressEditText.setOnClickListener(v -> {
            binding.evidenceTitleEdt.clearFocus();
            binding.evidenceDescEdt.clearFocus();
            MapDialogFragment mapDialog = MapDialogFragment.newInstance();
            // mapDialog.setTargetFragment(WriteCaseFragment.this, 0); // Not needed if using getParentFragment() in dialog
            mapDialog.show(getChildFragmentManager(), "MAP_DIALOG_TAG");
        });

        binding.submitBtn.setOnClickListener(view1 -> {
            title = binding.evidenceTitleEdt.getText().toString();
            description = binding.evidenceDescEdt.getText().toString();
            caseDate = binding.dateEditText.getText().toString();
            caseTime = binding.timeEditText.getText().toString();
            date = Utils.getCurrentDateTime();
            address = binding.addressEditText.getText().toString();
            if (imageUri == null) {
                showToast(requireContext(), "Please select image");
            } else if (!InputValidator.isValidTitle(title)) {
                InputValidator.setEditTextError(binding.evidenceTitleEdt, "Please input valid title.");
            } else if (!InputValidator.isValidDescription(description)) {
                InputValidator.setEditTextError(binding.evidenceDescEdt, "Please input valid description.");
            } else if (!InputValidator.isValidDate(caseDate)) {
                showToast(requireContext(), "Please select valid date.");
            } else if (!InputValidator.isValidTime(caseTime)) {
                showToast(requireContext(), "Please select valid time.");
            } else if (!InputValidator.isValidAddress(address)) {
                showToast(requireContext(), "Please select valid address.");
            } else if (!InputValidator.isValidLatLng(latitude, longitude)) {
                showToast(requireContext(), "Please select valid location.");
            } else {
                showProgressDialog(requireContext());
                FBStorageUtils.uploadImage(FBStorageUtils.CASE_IMAGE_PATH, imageUri, new FBInterface.ImageUploadCallback() {
                    @Override
                    public void onImageUploadSuccess(String downloadUrl) {
                        downloadImageUrl = downloadUrl;
                        fbUtils.saveCaseEvidenceData(fbUtils.CASES_PATH, new FBInterface.OnCaseDataSaveCallBack() {
                                    @Override
                                    public void onCaseDataSaveSuccess() {
                                        hideProgressDialog();
                                        FcmNotificationSender.get().sendNotificationToTopic(
                                                null, "New Case Reported",
                                                "New case has been reported. Please check your dashboard.",
                                                userId, "1", "case"
                                        );
                                        showToast(requireContext(), "Case data saved successfully");
                                        imageUri = null;
                                        binding.evidenceTitleEdt.setText("");
                                        binding.evidenceDescEdt.setText("");
                                        binding.dateEditText.setText("");
                                        binding.timeEditText.setText("");
                                        binding.addressEditText.setText("");
                                        binding.addEvidenceImg.setImageResource(R.drawable.add_photo);
                                        latitude = null;
                                        longitude = null;
                                        downloadImageUrl = null;
                                    }

                                    @Override
                                    public void onCaseDataSaveFailure(Exception e) {
                                        hideProgressDialog();
                                        showToast(requireContext(), "Failed to save case data: " + e.getMessage());
                                    }
                                },
                                new Pair<>("userId", userId),
                                new Pair<>("userName", userName),
                                new Pair<>("title", title),
                                new Pair<>("description", description),
                                new Pair<>("caseDate", caseDate),
                                new Pair<>("caseTime", caseTime),
                                new Pair<>("date", date),
                                new Pair<>("ticketNo", Utils.generateTicketNo()),
                                new Pair<>("address", address),
                                new Pair<>("latitude", latitude),
                                new Pair<>("longitude", longitude),
                                new Pair<>("image", downloadImageUrl),
                                new Pair<>("status", "Pending"),
                                new Pair<>("isArchived", false)
                        );
                    }

                    @Override
                    public void onImageUploadFailure(Exception exception) {
                        hideProgressDialog();
                        showToast(requireContext(), "Failed to upload image: " + exception.getMessage());
                    }
                });
            }
        });
    }

    private void showDatePicker() {
        binding.evidenceTitleEdt.clearFocus();
        binding.evidenceDescEdt.clearFocus();
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();
        builder.setTitleText("Select date");
        builder.setSelection(MaterialDatePicker.todayInUtcMilliseconds()); // Default to today
        builder.setSelection(calendar.getTimeInMillis()); // Default to current calendar instance

        final MaterialDatePicker<Long> datePicker = builder.build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            // The selection is in UTC milliseconds.
            // Calendar.setTimeInMillis() should handle the local time zone conversion.
            calendar.setTimeInMillis(selection);

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            binding.dateEditText.setText(dateFormat.format(calendar.getTime()));
        });

        // Show the date picker dialog
        datePicker.show(getChildFragmentManager(), "DATE_PICKER_TAG");
    }

    private void showTimePicker() {
        binding.evidenceTitleEdt.clearFocus();
        binding.evidenceDescEdt.clearFocus();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);

        MaterialTimePicker.Builder builder = new MaterialTimePicker.Builder();
        builder.setTimeFormat(TimeFormat.CLOCK_12H); // Or TimeFormat.CLOCK_24H
        builder.setHour(currentHour);
        builder.setMinute(currentMinute);
        builder.setTitleText("Select time");

        final MaterialTimePicker timePicker = builder.build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            int selectedHour = timePicker.getHour();
            int selectedMinute = timePicker.getMinute();

            calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
            calendar.set(Calendar.MINUTE, selectedMinute);

            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault()); // e.g., 03:30 PM
            binding.timeEditText.setText(timeFormat.format(calendar.getTime()));
        });

        // Show the time picker dialog
        timePicker.show(getChildFragmentManager(), "TIME_PICKER_TAG");
    }

    @Override
    public void onAddressSelected(LatLng latLng, String addressString) {
        binding.addressEditText.setText(addressString);
        latitude = latLng.latitude;
        longitude = latLng.longitude;
        Log.d("WriteCaseFragment", "Address selected: " + addressString + " (LatLng: " + latLng + ")" +
                "Latitude: " + latLng.latitude + ", Longitude: " + latLng.longitude);
    }
}