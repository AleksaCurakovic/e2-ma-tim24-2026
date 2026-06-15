package com.example.myapplication.presentation.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.RegionRepository;
import com.example.myapplication.presentation.viewModel.HomeViewModel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class RegionFragment extends Fragment {

    private static final List<String> KNOWN_REGIONS = Arrays.asList(
            "Srem", "Juzni Banat", "Severni Banat", "Severna Backa", "Centralni Banat",
            "Zapadna Backa", "Juzna Backa", "Beograd", "Bor", "Macva", "Pcinja",
            "Kolubara", "Podunavlje", "Branicevo", "Sumadija", "Pomoravlje",
            "Moravica", "Zajecar", "Zlatibor", "Raska", "Pirot", "Jablanica",
            "Toplica", "Nisava", "Rasina",
            "Južni Banat", "Severna Bačka", "Zapadna Bačka", "Južna Bačka",
            "Mačva", "Pčinja", "Branićevo", "Zaječar", "Raška", "Nišava"
    );

    private final RegionRepository repository = new RegionRepository();
    private final Map<String, Polygon> regionPolygons = new HashMap<>();
    private final Map<String, List<GeoPoint>> regionPoints = new HashMap<>();

    private HomeViewModel homeVm;
    private MapView mapView;
    private LinearLayout rankingContainer;
    private TextView tvRegionName;
    private TextView tvRegionStats;
    private TextView tvRegionStars;
    private TextView tvRegionIcon;

    private RegionRepository.RegionOverview overview;
    private String myRegion = "";
    private String selectedRegion = "";
    private boolean mapReady = false;

    public RegionFragment() {
        super(R.layout.fragment_regions);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        homeVm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        mapView = view.findViewById(R.id.regionsMap);
        rankingContainer = view.findViewById(R.id.regionRankContainer);
        tvRegionName = view.findViewById(R.id.tvRegionName);
        tvRegionStats = view.findViewById(R.id.tvRegionStats);
        tvRegionStars = view.findViewById(R.id.tvRegionStars);
        tvRegionIcon = view.findViewById(R.id.tvRegionIcon);

        view.findViewById(R.id.btnOpenRegionChat).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.chatFragment));

        Configuration.getInstance().load(
                requireContext(),
                requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        );
        setupMap();
        loadRegionsFromGeoJson();

        User current = homeVm.currentUser.getValue();
        if (current != null) bindCurrentUser(current);
        homeVm.currentUser.observe(getViewLifecycleOwner(), this::bindCurrentUser);
        homeVm.loadUser();

        repository.loadOverview(data -> {
            overview = data;
            renderRanking();
            renderUserMarkers();
            if (!myRegion.isEmpty()) showRegion(myRegion);
        }, e -> Toast.makeText(requireContext(), "Ne mogu da ucitam regione.", Toast.LENGTH_SHORT).show());
    }

    private void bindCurrentUser(User user) {
        if (user == null) return;
        myRegion = user.getRegion() != null ? user.getRegion().trim() : "";
        if (mapReady) refreshPolygonColors();
        if (overview != null && selectedRegion.isEmpty() && !myRegion.isEmpty()) {
            showRegion(myRegion);
            renderRanking();
        }
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(6.8);
        mapView.getController().setCenter(new GeoPoint(44.0, 21.0));
        mapView.setMinZoomLevel(6.0);
        mapView.setMaxZoomLevel(10.0);
    }

    private void loadRegionsFromGeoJson() {
        try {
            InputStream is = requireContext().getResources().openRawResource(R.raw.map);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            JSONObject root = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            JSONArray features = root.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                String name = feature.getJSONObject("properties").optString("shapeName", "Unknown");
                if (!KNOWN_REGIONS.contains(name)) continue;

                JSONObject geometry = feature.getJSONObject("geometry");
                String type = geometry.getString("type");
                JSONArray coordinates = geometry.getJSONArray("coordinates");
                List<GeoPoint> points = new ArrayList<>();

                if ("Polygon".equals(type)) {
                    points = parseRing(coordinates.getJSONArray(0));
                } else if ("MultiPolygon".equals(type)) {
                    int maxSize = 0;
                    for (int j = 0; j < coordinates.length(); j++) {
                        JSONArray ring = coordinates.getJSONArray(j).getJSONArray(0);
                        if (ring.length() > maxSize) {
                            maxSize = ring.length();
                            points = parseRing(ring);
                        }
                    }
                }

                if (!points.isEmpty()) addPolygon(name, points);
            }
            mapReady = true;
            refreshPolygonColors();
            renderUserMarkers();
            mapView.invalidate();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Mapa regiona nije ucitana.", Toast.LENGTH_SHORT).show();
        }
    }

    private List<GeoPoint> parseRing(JSONArray ring) throws Exception {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < ring.length(); i++) {
            JSONArray coord = ring.getJSONArray(i);
            points.add(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
        }
        return points;
    }

    private void addPolygon(String name, List<GeoPoint> points) {
        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(points);
        polygon.getOutlinePaint().setColor(Color.parseColor("#334155"));
        polygon.getOutlinePaint().setStrokeWidth(2.2f);
        polygon.setTitle(name);
        polygon.setOnClickListener((p, mapV, eventPos) -> {
            showRegion(name);
            return true;
        });
        mapView.getOverlays().add(polygon);
        regionPolygons.put(name, polygon);
        regionPoints.put(name, points);
    }

    private void showRegion(String region) {
        selectedRegion = region;
        refreshPolygonColors();

        RegionRepository.RegionStats stats = overview != null ? overview.statsByRegion.get(region) : null;
        int stars = overview != null && overview.starsByRegion.containsKey(region)
                ? overview.starsByRegion.get(region) : 0;

        tvRegionName.setText(region);
        tvRegionIcon.setText(regionIcon(region));
        tvRegionStars.setText("Mesecne zvezde regiona: " + stars);
        int first = stats != null ? stats.firstPlaces : 0;
        int second = stats != null ? stats.secondPlaces : 0;
        int third = stats != null ? stats.thirdPlaces : 0;
        int active = stats != null ? stats.activePlayers : 0;
        int total = stats != null ? stats.totalPlayers : 0;
        tvRegionStats.setText(
                "Prva mesta: " + first
                        + "\nDruga mesta: " + second
                        + "\nTreca mesta: " + third
                        + "\nTrenutno aktivni igraci: " + active
                        + "\nUkupno registrovani igraci: " + total
        );
        mapView.invalidate();
    }

    private void refreshPolygonColors() {
        for (Map.Entry<String, Polygon> entry : regionPolygons.entrySet()) {
            String region = entry.getKey();
            int fill = Color.parseColor("#66E2E8F0");
            if (region.equals(myRegion)) fill = Color.parseColor("#80BFDBFE");
            if (region.equals(selectedRegion)) fill = Color.parseColor("#99FDE68A");
            entry.getValue().getFillPaint().setColor(fill);
        }
        if (mapView != null) mapView.invalidate();
    }

    private void renderUserMarkers() {
        if (!mapReady || overview == null) return;
        Set<String> polygonNames = new HashSet<>(regionPolygons.keySet());
        mapView.getOverlays().removeIf(overlay -> overlay instanceof Marker);

        for (User user : overview.users) {
            if (user.getRegion() == null || user.getRegion().isEmpty()) continue;
            String region = matchRegionName(user.getRegion(), polygonNames);
            List<GeoPoint> points = regionPoints.get(region);
            if (points == null || points.isEmpty()) continue;
            GeoPoint point = randomPointInside(points, user.getUid() != null ? user.getUid() : user.getUsername());

            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(user.getUsername() != null ? user.getUsername() : "Igrac");
            marker.setSubDescription(region);
            mapView.getOverlays().add(marker);
        }
        mapView.invalidate();
    }

    private String matchRegionName(String region, Set<String> polygonNames) {
        if (polygonNames.contains(region)) return region;
        String ascii = toAscii(region);
        for (String candidate : polygonNames) {
            if (toAscii(candidate).equals(ascii)) return candidate;
        }
        return region;
    }

    private GeoPoint randomPointInside(List<GeoPoint> polygon, String seedText) {
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : polygon) {
            minLat = Math.min(minLat, p.getLatitude());
            maxLat = Math.max(maxLat, p.getLatitude());
            minLon = Math.min(minLon, p.getLongitude());
            maxLon = Math.max(maxLon, p.getLongitude());
        }
        Random random = new Random(seedText != null ? seedText.hashCode() : 0);
        for (int i = 0; i < 80; i++) {
            double lat = minLat + random.nextDouble() * (maxLat - minLat);
            double lon = minLon + random.nextDouble() * (maxLon - minLon);
            GeoPoint p = new GeoPoint(lat, lon);
            if (insidePolygon(p, polygon)) return p;
        }
        return polygon.get(polygon.size() / 2);
    }

    private boolean insidePolygon(GeoPoint point, List<GeoPoint> polygon) {
        boolean inside = false;
        double x = point.getLongitude();
        double y = point.getLatitude();
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i).getLongitude(), yi = polygon.get(i).getLatitude();
            double xj = polygon.get(j).getLongitude(), yj = polygon.get(j).getLatitude();
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / ((yj - yi) == 0 ? 0.000001 : (yj - yi)) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    private void renderRanking() {
        if (rankingContainer == null || overview == null) return;
        rankingContainer.removeAllViews();

        List<String> regions = new ArrayList<>();
        for (String region : overview.statsByRegion.keySet()) if (!regions.contains(region)) regions.add(region);
        for (String region : overview.starsByRegion.keySet()) if (!regions.contains(region)) regions.add(region);
        regions.sort((a, b) -> Integer.compare(starsFor(b), starsFor(a)));

        if (regions.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("Jos nema registrovanih igraca po regionima.");
            empty.setTextColor(Color.parseColor("#64748B"));
            empty.setPadding(0, dp(10), 0, 0);
            rankingContainer.addView(empty);
            return;
        }

        for (int i = 0; i < regions.size(); i++) {
            rankingContainer.addView(buildRegionRow(i + 1, regions.get(i)));
        }
    }

    private View buildRegionRow(int rank, String region) {
        boolean mine = region.equals(myRegion);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackgroundColor(mine ? Color.parseColor("#E0F2FE") : Color.parseColor("#FFFFFF"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> showRegion(region));

        TextView place = new TextView(requireContext());
        place.setText(String.valueOf(rank));
        place.setTextColor(Color.parseColor("#111827"));
        place.setTextSize(15);
        place.setGravity(Gravity.CENTER);
        place.setWidth(dp(32));
        row.addView(place);

        TextView icon = new TextView(requireContext());
        icon.setText(regionIcon(region));
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.parseColor("#075985"));
        icon.setTextSize(12);
        icon.setBackgroundColor(Color.parseColor("#E0F2FE"));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconLp.setMargins(dp(8), 0, dp(10), 0);
        icon.setLayoutParams(iconLp);
        row.addView(icon);

        TextView name = new TextView(requireContext());
        name.setText(region + (mine ? "  (tvoj region)" : ""));
        name.setTextColor(Color.parseColor("#111827"));
        name.setTextSize(14);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView stars = new TextView(requireContext());
        stars.setText(starsFor(region) + " zvezda");
        stars.setTextColor(Color.parseColor("#92400E"));
        stars.setTextSize(13);
        row.addView(stars);
        return row;
    }

    private int starsFor(String region) {
        return overview != null && overview.starsByRegion.containsKey(region)
                ? overview.starsByRegion.get(region) : 0;
    }

    private String regionIcon(String region) {
        String ascii = toAscii(region).replace(" ", "");
        if (ascii.length() >= 2) return ascii.substring(0, 2).toUpperCase();
        return "RS";
    }

    private String toAscii(String value) {
        if (value == null) return "";
        return value
                .replace("ž", "z").replace("Ž", "Z")
                .replace("ć", "c").replace("Ć", "C")
                .replace("č", "c").replace("Č", "C")
                .replace("š", "s").replace("Š", "S")
                .replace("đ", "dj").replace("Đ", "Dj");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}
