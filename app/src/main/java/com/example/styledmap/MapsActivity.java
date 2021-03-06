package com.example.styledmap;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.styledmap.Adapters.CustomInfoWindowAdapter;
import com.example.styledmap.data.PlacesContract;
import com.example.styledmap.data.PlacesDBHelper;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * A styled map using JSON styles from a raw resource.
 */
public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ArrayList<MarkerOptions> markers = new ArrayList<>();
    private Geocoder gcd;
    private static final String TAG = MapsActivity.class.getSimpleName();
    private LatLng mLatLong;
    private String city;
    private Location location;
    private SQLiteDatabase mDb;
    private TextView tv_delete;
    private LinkedHashMap<Integer, MarkerOptions> markerHashMap;
    private Cursor mCursor;
    private Toolbar mActionBar;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private final int REQUEST_CODE_SEARCH_ACTIVITY = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);


        mActionBar = (Toolbar) findViewById(R.id.tb_actionbar);
        setSupportActionBar(mActionBar);


        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(MapsActivity.this, mDrawerLayout, R.string.open_drawer, R.string.closed_drawer);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Set up views
        tv_delete = (TextView) findViewById(R.id.tv_delete_marker);

        //Set up Database
        PlacesDBHelper placesDBHelper = new PlacesDBHelper(this);
        mDb = placesDBHelper.getWritableDatabase();


        // Get the SupportMapFragment and register for the callback
        // when the map is ready for use.
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        markerHashMap = new LinkedHashMap<>();

        //Set up location manager
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider;

        try {
            provider = locationManager.getBestProvider(criteria, false);
            location = locationManager.getLastKnownLocation(provider);

        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (SecurityException exception) {
            exception.printStackTrace();
        }

        try {
            mLatLong = new LatLng(location.getLatitude(), location.getLongitude());
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Retrieve markers from db
        mCursor = getAllPlaces();
        retrieveMarkers(mCursor);



    }


    /**
     * Manipulates the map when it's available.
     * The API invokes this callback when the map is ready for use.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        //Initialize the map
        mMap = googleMap;

        //Setting default map rules
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setRotateGesturesEnabled(false);

        //Disable Map Toolbar:
        mMap.getUiSettings().setMapToolbarEnabled(false);

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                marker.showInfoWindow();
                return true;
            }
        });




        Button btnAddPlace = (Button) findViewById(R.id.btn_add_place);
        btnAddPlace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    AutocompleteFilter filter = new AutocompleteFilter.Builder().setTypeFilter(5).build();

                    Intent intent =
                            new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                                    .setFilter(filter)
                                    .build(MapsActivity.this);
                    startActivityForResult(intent, REQUEST_CODE_SEARCH_ACTIVITY);
                } catch (GooglePlayServicesRepairableException e) {
                    // TODO: Handle the error.
                } catch (GooglePlayServicesNotAvailableException e) {
                    // TODO: Handle the error.
                }
            }
        });


        //Configure custom infowindow through InfoWindowAdapter
        CustomInfoWindowAdapter adapter = new CustomInfoWindowAdapter(MapsActivity.this);
        mMap.setInfoWindowAdapter(adapter);


        //Set onclicklistener for InfoWindow
        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Intent intent = new Intent(MapsActivity.this, MarkerActivity.class);
                startActivity(intent);
            }
        });


        //Set onclicklistener for Marker dragging to delete it
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                tv_delete.setVisibility(View.VISIBLE);
                tv_delete.setTextColor(Color.BLACK);
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                LatLng position = marker.getPosition();
                Projection projection = mMap.getProjection();
                Point screenPosition = projection.toScreenLocation(position);
                int tv_delete_top_boundry = tv_delete.getTop();
                if (screenPosition.y >= tv_delete_top_boundry) {
                    tv_delete.setTextColor(Color.RED);
                } else {
                    tv_delete.setTextColor(Color.BLACK);
                }
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                String string_id = marker.getSnippet();
                int id = Integer.parseInt(string_id);
                LatLng position = marker.getPosition();
                Projection projection = mMap.getProjection();
                Point screenPosition = projection.toScreenLocation(position);
                int tv_delete_top_boundry = tv_delete.getTop();

                if (screenPosition.y >= tv_delete_top_boundry) {
                    boolean test = removeMarker(id);
                    marker.remove();
                }
                mMap.clear();
                mCursor = getAllPlaces();
                retrieveMarkers(mCursor);
                setMarkers();
                tv_delete.setVisibility(View.INVISIBLE);
            }
        });


        //Set markers retrieved from database on now available map
        setMarkers();

        // Set the button for retrieving the current location and moving camera to it
        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            e.printStackTrace();
        }


        //Style the map
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_style));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }


        // Position the map's camera
        if (location != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(mLatLong)      // Sets the center of the map to location user
                    .zoom(3)                   // Sets the zoom
                    .build();                   // Creates a CameraPosition from the builder
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }


    // Database function for retrieving current database data in a cursor
    private Cursor getAllPlaces() {
        return mDb.query(
                PlacesContract.PlacesEntry.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                PlacesContract.PlacesEntry._ID
        );
    }


    //Function to add new places into the database
    private long addNewPlace(String name, double latitude, double longitude) {
        ContentValues cv = new ContentValues();
        cv.put(PlacesContract.PlacesEntry.COLUMN_CITY, name);
        cv.put(PlacesContract.PlacesEntry.COLUMN_LATITUDE, latitude);
        cv.put(PlacesContract.PlacesEntry.COLUMN_LONGITUDE, longitude);
        return mDb.insert(PlacesContract.PlacesEntry.TABLE_NAME, null, cv);
    }

    //Remove marker from db
    private boolean removeMarker(int id) {
        return mDb.delete(PlacesContract.PlacesEntry.TABLE_NAME, PlacesContract.PlacesEntry._ID + "=" + id, null) > 0;
    }


    //Function to set all markers retrieved from database
    private void setMarkers() {
        ArrayList<MarkerOptions> markerOptionsList = new ArrayList<>(markerHashMap.values());

        for (int i = 0; i < markerOptionsList.size(); i++) {
            MarkerOptions option = markerOptionsList.get(i);
            mMap.addMarker(option);
        }
    }


    //Retrieve all markers from database
    private void retrieveMarkers(Cursor cursor) {
        markers.clear();
        markerHashMap.clear();
        cursor = getAllPlaces();
        for (int i = 0; i < cursor.getCount(); i++) {
            if (!cursor.moveToPosition(i)) {
                Log.d("Cursor", "Cursor is empty");
            } else {
                String cCity = cursor.getString(cursor.getColumnIndex(PlacesContract.PlacesEntry.COLUMN_CITY));
                double cLatitude = cursor.getDouble(cursor.getColumnIndex(PlacesContract.PlacesEntry.COLUMN_LATITUDE));
                double cLongitude = cursor.getDouble(cursor.getColumnIndex(PlacesContract.PlacesEntry.COLUMN_LONGITUDE));
                int cId = cursor.getInt(cursor.getColumnIndex(PlacesContract.PlacesEntry._ID));
                LatLng cLatLng = new LatLng(cLatitude, cLongitude);
                MarkerOptions cMarkerOptions = new MarkerOptions().title(cCity).snippet("" + cId).position(cLatLng).draggable(true).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                markerHashMap.put(cId, cMarkerOptions);
            }
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1) {
            if(resultCode == Activity.RESULT_OK){
                Place place = PlaceAutocomplete.getPlace(this, data);


                //Retrieve information about selected place and zoom into it
                mLatLong = place.getLatLng();
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(mLatLong.latitude, mLatLong.longitude))      // Sets the center of the map to location user
                        .zoom(4)                   // Sets the zoom
                        .build();                   // Creates a CameraPosition from the builder
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                Log.d("Maps", "Place selected: " + place.getName());


                //Retrieve location information from latitude and longitude
                gcd = new Geocoder(MapsActivity.this, Locale.getDefault());
                try {
                    List<Address> address = gcd.getFromLocation(mLatLong.latitude, mLatLong.longitude, 1);
                    city = address.get(0).getLocality();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                //Check if selected place was already added

                mCursor = getAllPlaces();
                retrieveMarkers(mCursor);
                for (int i = 0; i < markerHashMap.size(); i++) {
                    MarkerOptions marker = (new ArrayList<>(markerHashMap.values()).get(i));
                    if (marker.getPosition().latitude == mLatLong.latitude && marker.getPosition().longitude == mLatLong.longitude) {
                        Snackbar duplicate = Snackbar.make(findViewById(R.id.drawer_layout), R.string.snackbar_duplicate, Snackbar.LENGTH_LONG);
                        View sb_duplicateView = duplicate.getView();
                        TextView tv_sb_duplicate = (TextView) sb_duplicateView.findViewById(android.support.design.R.id.snackbar_text);
                        tv_sb_duplicate.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        sb_duplicateView.setBackgroundColor(getColor(R.color.colorAccent));
                        duplicate.show();
                        return;
                    }

                }


                Snackbar placeAdded = Snackbar.make(findViewById(R.id.drawer_layout), R.string.snackbar_place_added, Snackbar.LENGTH_LONG);
                View sb_placeAddedView = placeAdded.getView();
                sb_placeAddedView.setBackgroundColor(getColor(R.color.colorPrimary));
                TextView tv_sb_placeAdded = (TextView) sb_placeAddedView.findViewById(android.support.design.R.id.snackbar_text);
                tv_sb_placeAdded.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                placeAdded.show();

                MarkerOptions currentMarker = new MarkerOptions()
                        .position(mLatLong)
                        .title(city)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                mMap.addMarker(currentMarker);

                //Add place into database
                addNewPlace(city, mLatLong.latitude, mLatLong.longitude);




            }
            if (resultCode == Activity.RESULT_CANCELED) {
                //Write your code if there's no result
            }
        }
    }




}
