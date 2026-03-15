package com.app.billsense.utils;

import android.app.Dialog;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.app.billsense.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


public class MapDialogFragment extends DialogFragment implements OnMapReadyCallback {

    private static final String TAG = "MapDialogFragment";
    private GoogleMap googleMap;
    private Marker currentMarker;
    private LatLng selectedLatLng;
    private String selectedAddressString;

    private AddressSelectionListener addressListener;

    // Interface to send data back
    public interface AddressSelectionListener {
        void onAddressSelected(LatLng latLng, String addressString);
    }

    public static MapDialogFragment newInstance() {
        return new MapDialogFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof AddressSelectionListener) {
            addressListener = (AddressSelectionListener) getParentFragment();
        } else if (context instanceof AddressSelectionListener) {
            addressListener = (AddressSelectionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement AddressSelectionListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_map, container, false); // Ensure this layout is updated

        Button confirmButton = view.findViewById(R.id.confirm_address_button);
        Button cancelButton = view.findViewById(R.id.cancel_button);

        // Places API initialization and AutocompleteSupportFragment setup are removed.
        // Ensure your API key for Google Maps SDK is correctly set in the AndroidManifest.xml

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_view_dialog);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "SupportMapFragment not found. Check your layout file.");
            Toast.makeText(getContext(), "Error loading map.", Toast.LENGTH_LONG).show();
            // Optionally dismiss the dialog or disable functionality
        }

        confirmButton.setOnClickListener(v -> {
            if (selectedLatLng != null && addressListener != null) {
                if (TextUtils.isEmpty(selectedAddressString)) {
                    // This fallback will always be used if user only tapped the map
                    selectedAddressString = getAddressFromLatLng(selectedLatLng);
                }
                if (TextUtils.isEmpty(selectedAddressString) || selectedAddressString.startsWith("Unable to") || selectedAddressString.equals("Address not found")) {
                    Toast.makeText(getContext(), "Could not determine address. Please try again or select a different point.", Toast.LENGTH_LONG).show();
                    return;
                }
                addressListener.onAddressSelected(selectedLatLng, selectedAddressString);
                dismiss();
            } else {
                Toast.makeText(getContext(), "Please select a location on the map.", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> dismiss());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            // You might want to make the dialog taller if there's no search bar
            int height = ViewGroup.LayoutParams.WRAP_CONTENT; // Or a fixed height like 500dp
            dialog.getWindow().setLayout(width, height);
            // Example for a fixed height:
            // int desiredHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.7); // 70% of screen height
            // dialog.getWindow().setLayout(width, desiredHeight);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        // Default location (e.g., center of a country or a default city)
//        LatLng defaultLocation = new LatLng(37.0902, -95.7129); // Center of USA
//        LatLng defaultLocation = new LatLng(14.5995, 120.9842); // Manila, Philippines
        LatLng defaultLocation = new LatLng(12.8797, 121.7740); // Approximate center of the Philippines
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 3));

        googleMap.setOnMapClickListener(latLng -> {
            selectedLatLng = latLng;
            selectedAddressString = getAddressFromLatLng(latLng); // Geocode on map click
            updateMapLocation(latLng, selectedAddressString); // Title will be the geocoded address or an error message
        });

        // Optionally, add controls like MyLocationButton if permission is granted
        // if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        // googleMap.setMyLocationEnabled(true);
        // }
    }

    private void updateMapLocation(LatLng latLng, String title) {
        if (googleMap == null) return;

        if (currentMarker != null) {
            currentMarker.remove();
        }
        MarkerOptions markerOptions = new MarkerOptions().position(latLng);
        if (!TextUtils.isEmpty(title) && !title.startsWith("Unable to") && !title.equals("Address not found")) {
            markerOptions.title(title); // Only show title if it's a valid address
        }
        currentMarker = googleMap.addMarker(markerOptions);
        if (markerOptions.getTitle() != null) {
            currentMarker.showInfoWindow();
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15)); // Zoom to the selected location
    }

    private String getAddressFromLatLng(LatLng latLng) {
        if (getContext() == null || latLng == null) return "Unknown location data"; // More specific error
        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
        try {
            // In some Eclair versions and above, getFromLocation will return null
            // On other devices, it can throw an IOException if the backend is unavailable
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressBuilder = new StringBuilder();

                // Construct the address string. You can customize this.
                // address.getMaxAddressLineIndex() can be used to iterate if available
                if (address.getAddressLine(0) != null) {
                    addressBuilder.append(address.getAddressLine(0));
                } else {
                    // Fallback if getAddressLine(0) is null
                    if (address.getFeatureName() != null) addressBuilder.append(address.getFeatureName()).append(", ");
                    if (address.getThoroughfare() != null) addressBuilder.append(address.getThoroughfare()).append(", ");
                    if (address.getLocality() != null) addressBuilder.append(address.getLocality()).append(", ");
                    if (address.getAdminArea() != null) addressBuilder.append(address.getAdminArea()).append(", ");
                    if (address.getCountryName() != null) addressBuilder.append(address.getCountryName());
                    // Remove trailing comma and space if any
                    if (addressBuilder.length() > 2 && addressBuilder.charAt(addressBuilder.length() - 2) == ',') {
                        addressBuilder.delete(addressBuilder.length() - 2, addressBuilder.length());
                    }
                }

                String result = addressBuilder.toString();
                return TextUtils.isEmpty(result) ? "Address details not found" : result;
            } else {
                Log.w(TAG, "No address found for LatLng: " + latLng.toString());
                return "Address not found";
            }
        } catch (IOException e) {
            // This is thrown if the backend service is not available
            Log.e(TAG, "Geocoder service not available or error for LatLng: " + latLng.toString(), e);
            return "Unable to get address (network or service error)";
        } catch (IllegalArgumentException e) {
            // This can be thrown if latitude or longitude is invalid
            Log.e(TAG, "Invalid LatLng provided to Geocoder: " + latLng.toString(), e);
            return "Unable to get address (invalid location)";
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        addressListener = null;
    }
}