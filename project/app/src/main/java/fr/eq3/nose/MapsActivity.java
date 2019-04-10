package fr.eq3.nose;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.eq3.nose.spot.items.DatabaseRequest;
import fr.eq3.nose.spot.items.Spot;
import fr.eq3.nose.spot.view.SpotActivity;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    private static final int MY_LOCATION_REQUEST_CODE = 101;

    private GoogleMap mMap;
    private LocationManager locationManager;
    private Location myLocation;
//    private LatLngBounds myArea;
    private static final long MIN_TIME = 0;
    private static final float MIN_DISTANCE = 0;
    private Set<Spot> spot_cache = new HashSet<>();
    //About the menu
    FloatingActionMenu menuMap;
    FloatingActionButton option_mapTerrain, option_mapNormal, option_mapSatelite, option_addSpot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        MapFragment map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map));
        map.getMapAsync(this);
        initializeMenu();
    }

    private void initializeMenu(){
        menuMap = findViewById(R.id.menuMap);
        option_mapTerrain = findViewById(R.id.option_mapTerrain);
        option_mapNormal = findViewById(R.id.option_mapNormal);
        option_mapSatelite = findViewById(R.id.option_mapSatelite);
        option_addSpot = findViewById(R.id.option_addSpot);

        option_mapTerrain.setOnClickListener(v -> {
            //TODO something when floating action menu first item clicked
            mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        });
        option_mapNormal.setOnClickListener(v -> {
            //TODO something when floating action menu second item clicked
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        });
        option_mapSatelite.setOnClickListener(v -> {
            //TODO something when floating action menu third item clicked
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        });
        option_addSpot.setOnClickListener(v -> {
            //TODO something when floating action menu third item clicked
            DatabaseRequest dbr = new DatabaseRequest(this);
            Spot spot = dbr.createSpot("Spot", "", new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
            addSpotOnMap(spot);
        });
    }

    /**
     * Map management
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        //Enable to track the location
        enableMyLocation();
        LatLng currentPosition = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
        refreshSpotsCache();
        //Map type
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        //Min Zoom
        mMap.setMinZoomPreference(14.0f);
        //Disable rotation and scrolling
        LatLngBounds myArea = new LatLngBounds(new LatLng(currentPosition.latitude - 0.03, currentPosition.longitude - 0.03), new LatLng(currentPosition.latitude + 0.03, currentPosition.longitude + 0.03));
        mMap.setLatLngBoundsForCameraTarget(myArea);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        //Move the camera to the current location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 17.0f));

        mMap.setOnMarkerClickListener(marker -> {
            Intent intent = new Intent(MapsActivity.this, SpotActivity.class);
            final long id = Long.parseLong(marker.getTitle());
            intent.putExtra(SpotActivity.SPOT_EXTRA, id);
            startActivity(intent);
            return true;
        });
    }

    /**
     * Listen to the location changes and refresh the Map according to them
     * @param location new location of person
     */
    @Override
    public void onLocationChanged(Location location) {
        myLocation=location;
        refreshSpotsCache();
    }

    @Override
    public void onResume(){
        super.onResume();

        if(mMap != null){
            mMap.clear();
        }
        refreshMap();
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        Toast.makeText(MapsActivity.this,
                "Provider disable: " + provider, Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        Toast.makeText(MapsActivity.this,
                "Provider enabled: " + provider, Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }



    //TODO//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //---------------------------------METHODS CREATED ESPACIALLY FOR THIS PROJECT------------------------------------------------------
    //TODO//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Check for the permissions and initialize myLocation variable with the last known location
     */
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_LOCATION_REQUEST_CODE);

        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
            myLocation=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
    }

    /**
     * Avoid the area around the current location to duplicate itself
     * Re-put all the spots and influence areas on the Map
     */
    public void refreshMap(){
        for(Spot saved : spot_cache){
            mMap.addMarker(getSpotMarker(saved));
            mMap.addCircle(getSpotInfluenceZone(saved));
        }
    }

    private void refreshSpotsCache(){
        DatabaseRequest dbr = new DatabaseRequest(this);
        List<Integer> spots = dbr.getSpotsBetween(myLocation.getLatitude(), myLocation.getLongitude(), 100);
        for(int id : spots) {
            Spot s = dbr.getSpot(id);
            if(!spot_cache.contains(s)){
                spot_cache.add(s);
                addSpotOnMap(s);
            }
        }
    }

    /**I/art: Rejecting re-init on previously-failed class java.lang.Class<fr.eq3.nose.-$$Lambda$MapsActivity$GXJAi3W9Z41rCnmXELYVVzl0h_4>
     * Put a spot on the map, at the current location
     */
    public void addSpotOnMap(Spot spot){
        mMap.addMarker(getSpotMarker(spot));
        mMap.addCircle(getSpotInfluenceZone(spot));
        spot_cache.add(spot);
    }

    private MarkerOptions getSpotMarker(Spot spot){
        return new MarkerOptions()
                .position(new LatLng(spot.getLat(), spot.getLong()))
                .title(spot.getId() + "");
    }

    private CircleOptions getSpotInfluenceZone(Spot spot){
        CircleColor circleColor = CircleColor.GREEN;
        return new CircleOptions()
                .center(new LatLng(spot.getLat(), spot.getLong()))
                .radius(radius(spot.getInfluenceLvl()))
                .strokeColor(circleColor.strokeColor)
                .fillColor(circleColor.fillColor);
    }

    /**
     * Get the radius in meters from a spot influence lvl
     * @param lvl the lvl of influence
     * @return the radius in meters (always positive)
     */
    private int radius(int lvl){
        return lvl > 0 ? lvl * 20 : 0;
    }

    private enum CircleColor{

        GREEN(Color.GREEN, 0x7093fc0a);

        private int strokeColor;
        private int fillColor;

        CircleColor(int strokeColor, int fillColor){
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
        }
    }


}