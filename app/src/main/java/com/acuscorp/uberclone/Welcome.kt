package com.acuscorp.uberclone

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.os.HandlerCompat.postDelayed
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation

import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.ghyeok.stickyswitch.widget.StickySwitch
import io.ghyeok.stickyswitch.widget.StickySwitch.OnSelectedChangeListener


private const val My_PERMISSION_REQUEST_CODE = 7000
private const val PLAY_SERVICE_RES_REQUEST = 7001
private const val UPDATE_INTERVAL = 5000L
private const val FASTEST_INTERVAL = 3000L
private const val DISPLACEMENT = 10F

class Welcome : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var mMap: GoogleMap
    private lateinit var locationRequest: LocationRequest
    private lateinit var location: Location

    private lateinit var drivers: DatabaseReference
    private lateinit var geoFire: GeoFire
    private lateinit var mCurrent: Marker
    private lateinit var locationSwitcher: StickySwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        locationSwitcher = findViewById(R.id.locationSwitch)


        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        drivers = FirebaseDatabase.getInstance().getReference("Drivers")
        geoFire = GeoFire(drivers)

        val listener = object : OnSelectedChangeListener {
            override fun onSelectedChange(direction: StickySwitch.Direction, text: String) {
                when (direction) {
                    StickySwitch.Direction.LEFT -> {
                        stopLocationUpdates()
                        mCurrent.remove()
                        Snackbar.make(mapFragment.view!!, "You are offline", Snackbar.LENGTH_SHORT)
                            .show()
                    }
                    StickySwitch.Direction.RIGHT -> {
                        displayLocation()
                        startLocationUpdates()

                        Snackbar.make(mapFragment.view!!, "You are online", Snackbar.LENGTH_SHORT)
                            .show()
                    }


                }


            }
        }
        locationSwitcher.setA(listener)
        setupLocation()
        Snackbar.make(mapFragment.view!!, "You are online", Snackbar.LENGTH_SHORT).show()
        mapFragment.getMapAsync(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            My_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    createLocationRequest()
//                    if(locationSwitcher.getDirection() == StickySwitch.Direction.RIGHT){
                    displayLocation()
//                    }
                }
            }
        }
    }

    private fun setupLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), My_PERMISSION_REQUEST_CODE
            )
        } else {
            createLocationRequest()
//            if (locationSwitcher.getDirection() == StickySwitch.Direction.RIGHT) {
                displayLocation()
//            }

        }

    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()
            .setInterval(UPDATE_INTERVAL)
            .setFastestInterval(FASTEST_INTERVAL)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setSmallestDisplacement(DISPLACEMENT)
    }

    private fun stopLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .removeLocationUpdates(locationCallback)

    }

    private fun displayLocation() {


        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation.addOnSuccessListener {
                it?.let { location ->
                    val latitude = location.latitude
                    val longitude = location.longitude

                    val latLng = LatLng(latitude, longitude)


                    geoFire.setLocation(
                        FirebaseAuth.getInstance().currentUser?.uid,
                        GeoLocation(latitude, longitude)
                    ) { _, _ ->

                        mCurrent = mMap.addMarker(
                            MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                                .position(latLng)
                                .title("You")
                        )

                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0F))
                        rotateMarker(mCurrent, -360F, mMap)

                    }

                }


            }

    }

    private fun rotateMarker(mCurrent: Marker, i: Float, mMap: GoogleMap) {
        val start = SystemClock.uptimeMillis()
        val startRotation = mCurrent.rotation
        val duration = 1500L
        val interpolator = LinearInterpolator()
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val t = interpolator.getInterpolation(elapsed.toFloat() / duration)
                val rot = t * i + (1 - t) * startRotation
                mCurrent.rotation = if (-rot > 180) rot / 2 else rot

                if (i < 1.0) {

                    postDelayed(handler, this, null, 16L)
                }
            }
        }
        runnable.run()


    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
            locationRequest, locationCallback,
            Looper.myLooper()
        )


    }

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            location = locationResult.lastLocation
            val latitude = location.latitude
            val longitude = location.longitude

            val latLng = LatLng(latitude, longitude)


            geoFire.setLocation(
                FirebaseAuth.getInstance().currentUser?.uid, GeoLocation(latitude, longitude)
            ) { _, _ ->
                mMap.clear()
                mCurrent = mMap.addMarker(
                    MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                        .position(latLng)
                        .title("You")
                )

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0F))
                rotateMarker(mCurrent, -360F, mMap)

            }


            Snackbar.make(
                mapFragment.view!!,
                "Location has change ${locationResult?.lastLocation?.latitude}",
                Snackbar.LENGTH_SHORT
            )
                .show()
            super.onLocationResult(locationResult)
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability?) {
            Snackbar.make(
                mapFragment.view!!,
                "Is location available ${locationAvailability?.isLocationAvailable}",
                Snackbar.LENGTH_SHORT
            )
                .show()
            super.onLocationAvailability(locationAvailability)


        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be
     * used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        displayLocation()

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }


}