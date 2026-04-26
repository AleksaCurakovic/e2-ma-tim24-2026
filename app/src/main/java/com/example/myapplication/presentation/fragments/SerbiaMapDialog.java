package com.example.myapplication.presentation.fragments;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.example.myapplication.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SerbiaMapDialog extends DialogFragment {

    public interface OnRegionConfirmedListener {
        void onRegionConfirmed(String region);
    }

    private static final String TAG = "SerbiaMap";

    private static final List<String> KNOWN_REGIONS = Arrays.asList(
            "Srem",
            "Južni Banat",
            "Severni Banat",
            "Severna Bačka",
            "Centralni Banat",
            "Zapadna Bačka",
            "Južna Bačka",
            "Beograd",
            "Bor",
            "Mačva",
            "Pčinja",
            "Kolubara",
            "Podunavlje",
            "Branićevo",
            "Sumadija",
            "Pomoravlje",
            "Moravica",
            "Zaječar",
            "Zlatibor",
            "Raška",
            "Pirot",
            "Jablanica",
            "Toplica",
            "Nišava",
            "Rasina"
    );

    private OnRegionConfirmedListener listener;
    private MapView mapView;
    private TextView tvSelectedLabel;
    private MaterialButton btnConfirm;

    private final Map<String, Polygon> regionPolygons = new HashMap<>();
    private Polygon selectedPolygon = null;
    private String selectedRegionName = null;

    private final int colorDefault  = Color.parseColor("#804FC3E8");
    private final int colorSelected = Color.parseColor("#80E67E22");
    private final int colorStroke   = Color.parseColor("#FF2C3E50");

    public void setOnRegionConfirmedListener(OnRegionConfirmedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_serbia_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Configuration.getInstance().load(
                requireContext(),
                requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        );

        tvSelectedLabel = view.findViewById(R.id.tvSelectedRegionLabel);
        btnConfirm = view.findViewById(R.id.btnConfirmRegion);
        btnConfirm.setEnabled(false);
        mapView = view.findViewById(R.id.osmMapView);

        mapView.post(() -> {
            setupMap();
            loadRegionsFromGeoJson();
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedRegionName != null && listener != null) {
                listener.onRegionConfirmed(selectedRegionName);
                dismiss();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new GeoPoint(44.0, 21.0));
        mapView.setScrollableAreaLimitLatitude(47.0, 41.0, 0);
        mapView.setScrollableAreaLimitLongitude(18.0, 24.0, 0);
        mapView.setMinZoomLevel(6.0);
        mapView.setMaxZoomLevel(10.0);
    }

    private void loadRegionsFromGeoJson() {
        try {
            InputStream is = requireContext().getResources()
                    .openRawResource(R.raw.map);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            String geoJson = new String(buffer, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(geoJson);
            JSONArray features = root.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject props = feature.getJSONObject("properties");
                String name = props.optString("shapeName", "Unknown");

                if (!KNOWN_REGIONS.contains(name)) {
                    Log.w(TAG, "Skipping: " + name);
                    continue;
                }

                Log.d(TAG, "Loading region: " + name);

                JSONObject geometry = feature.getJSONObject("geometry");
                String type = geometry.getString("type");
                JSONArray coordinates = geometry.getJSONArray("coordinates");

                List<GeoPoint> points = new ArrayList<>();

                if (type.equals("Polygon")) {
                    points = parseRing(coordinates.getJSONArray(0));
                } else if (type.equals("MultiPolygon")) {
                    int maxSize = 0;
                    for (int j = 0; j < coordinates.length(); j++) {
                        JSONArray ring = coordinates.getJSONArray(j).getJSONArray(0);
                        if (ring.length() > maxSize) {
                            maxSize = ring.length();
                            points = parseRing(ring);
                        }
                    }
                }

                if (!points.isEmpty()) {
                    addPolygon(name, points);
                }
            }

            mapView.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error loading GeoJSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<GeoPoint> parseRing(JSONArray ring) throws Exception {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < ring.length(); i++) {
            JSONArray coord = ring.getJSONArray(i);
            double lon = coord.getDouble(0);
            double lat = coord.getDouble(1);
            points.add(new GeoPoint(lat, lon));
        }
        return points;
    }

    private void addPolygon(String name, List<GeoPoint> points) {
        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(points);
        polygon.getFillPaint().setColor(colorDefault);
        polygon.getOutlinePaint().setColor(colorStroke);
        polygon.getOutlinePaint().setStrokeWidth(3f);
        polygon.setTitle(name);

        polygon.setOnClickListener((p, mapV, eventPos) -> {
            selectRegion(name, p);
            return true;
        });

        mapView.getOverlays().add(polygon);
        regionPolygons.put(name, polygon);
    }

    private void selectRegion(String name, Polygon tapped) {
        if (selectedPolygon != null) {
            selectedPolygon.getFillPaint().setColor(colorDefault);
        }
        tapped.getFillPaint().setColor(colorSelected);
        selectedPolygon = tapped;
        selectedRegionName = name;
        mapView.invalidate();

        // This was missing — update the label and enable the button
        tvSelectedLabel.setText("Selected: " + name);
        btnConfirm.setEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}