package com.app.billsense.activities;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityCasesBinding;
import com.app.billsense.databinding.DialogCaseDetailsBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Cases;
import com.app.billsense.utils.DialogUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;

public class CasesActivity extends AppCompatActivity implements OnMapReadyCallback {
    private ActivityCasesBinding binding;
    private MaterialToolbar toolbar;
    private GoogleMap mMap;
    private String userId, userName;
    private FBUtils fbUtils;
    private ArrayList<Cases> casesArrayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCasesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = PrefManager.getInstance().getUserId();
        fbUtils = new FBUtils();

        // Force the LEGACY Maps renderer. The newer maps_core renderer fails to fetch
        // tiles on devices whose Google Play Services account is in a BAD_AUTHENTICATION
        // state (observed on-device: blank tiles, Google logo only, no auth-failure log).
        // The legacy renderer fetches tiles more directly and is robust to that.
        try {
            MapsInitializer.initialize(getApplicationContext(),
                    MapsInitializer.Renderer.LEGACY, renderer -> { });
        } catch (Exception ignored) { }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(com.app.billsense.R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            showMapError("Map fragment not available");
        }

        binding.mapRetryBtn.setOnClickListener(v -> {
            binding.mapErrorLayout.setVisibility(View.GONE);
            SupportMapFragment retryFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            if (retryFragment != null) {
                retryFragment.getMapAsync(this);
            }
        });

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.cases));
        }

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Check if the map actually loaded (invalid API key results in blank map)
        mMap.setOnMapLoadedCallback(() -> {
            Log.d("==Cases", "Map loaded successfully");
            binding.mapErrorLayout.setVisibility(View.GONE);
        });

        getAllCases();
        // Default camera position: Philippines
        LatLng philippines = new LatLng(16.413599, 120.591616);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(philippines, 6f));
    }

    private void showMapError(String message) {
        Log.e("==Cases", "Map error: " + message);
        binding.mapErrorLayout.setVisibility(View.VISIBLE);
        binding.mapErrorText.setText(message);
    }

    private void getAllCases() {
        fbUtils.getAllDataFromPath(fbUtils.CASES_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                casesArrayList.clear();
                mMap.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Cases cases = snapshot.getValue(Cases.class);
                    if (cases == null) continue;
                    casesArrayList.add(cases);
                    addCasesLocations(cases);
                    //                    LatLng latLng = new LatLng(cases.getLatitude(), cases.getLongitude());
//                    mMap.addMarker(new MarkerOptions().position(latLng)
//                            .title(cases.getTitle()));
                }
            }

            @Override
            public void onDataNotFound() {
                mMap.clear();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                mMap.clear();
            }
        });

        mMap.setOnMarkerClickListener(marker -> {
            for (Cases cases : casesArrayList){
                LatLng latLng = new LatLng(cases.getLatitude(), cases.getLongitude());
                if (marker.getPosition().equals(latLng)){
                    showCaseDetailsDialog(cases);
                    return true;
                }
            }
            return false;
        });

    }

    private void addCasesLocations(Cases cases) {
        Bitmap bmp = BitmapFactory.decodeResource(getResources(),
                R.drawable.logo_main);
        Bitmap bitmap = getResizedBitmap(bmp, 150, 150);
        LatLng latLng = new LatLng(cases.getLatitude(), cases.getLongitude());
        mMap.addMarker(new MarkerOptions().position(latLng)
                .title(cases.getTitle())
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)));
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return GetBitmapClippedCircle(resizedBitmap);
    }

    public static Bitmap GetBitmapClippedCircle(Bitmap bitmap) {

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        final Path path = new Path();
        path.addCircle(
                (float) (width / 2)
                , (float) (height / 2)
                , (float) Math.min(width, (height / 2))
                , Path.Direction.CCW);

        final Canvas canvas = new Canvas(outputBitmap);
        canvas.clipPath(path);
        canvas.drawBitmap(bitmap, 0, 0, null);
        return outputBitmap;
    }

    private void showCaseDetailsDialog(Cases cases) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        DialogCaseDetailsBinding detailsBinding = DialogCaseDetailsBinding.inflate(getLayoutInflater());
        dialog.setContentView(detailsBinding.getRoot());


        // Make the dialog background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

            // Set dialog to full width
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            // layoutParams.gravity = Gravity.BOTTOM;
            dialog.getWindow().setAttributes(layoutParams);
        }

        detailsBinding.textViewCaseTitle.setText(cases.getTitle());
        detailsBinding.textViewUserName.setText(cases.getUserName());
        detailsBinding.textViewAddress.setText(cases.getAddress());
        detailsBinding.textViewDate.setText(cases.getCaseDate());
        detailsBinding.textViewTime.setText(cases.getCaseTime());
        detailsBinding.textViewDescription.setText(cases.getDescription());
        Glide.with(this).load(cases.getImage()).into(detailsBinding.imageViewCaseImage);

        detailsBinding.imageViewCaseImage.setOnClickListener(view -> {
            DialogUtils.displayFullImageDialog(CasesActivity.this, cases.getTitle(), cases.getImage(), null);
        });

        detailsBinding.closeIv.setOnClickListener(view -> {
            dialog.dismiss();
        });


        dialog.show();

    }
}