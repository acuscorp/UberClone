package com.acuscorp.uberclone

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.os.HandlerCompat.postDelayed
import com.acuscorp.uberclone.common.Common
import com.acuscorp.uberclone.remote.IGoogleApi
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.ghyeok.stickyswitch.widget.StickySwitch
import io.ghyeok.stickyswitch.widget.StickySwitch.OnSelectedChangeListener
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Response
import kotlin.properties.Delegates


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

    //car animation
    private lateinit var polylineList : MutableList<LatLng>
    private lateinit var pickUpLocationMarker: Marker
    private var v by Delegates.notNull<Float>()
    private var lat by Delegates.notNull<Float>()
    private var lng by Delegates.notNull<Float>()
    private lateinit var handler: Handler
    private lateinit var startPosition: LatLng
    private lateinit var endPosition: LatLng
    private lateinit var currentPosition: LatLng
    private var index by Delegates.notNull<Int>()
    private var next by Delegates.notNull<Int>()
    private lateinit var btnGo: Button
    private lateinit var edtPlace: EditText
    private var destination: String = ""
    private lateinit var polylineOptions: PolylineOptions
    private lateinit var blackPolylineOptions: PolylineOptions
    private lateinit var blackPolyline: Polyline
    private lateinit var greyPolyline: Polyline

    private lateinit var myService : IGoogleApi
    private var mLastLocation: LatLng? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        locationSwitcher = findViewById(R.id.locationSwitch)


        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        polylineList = mutableListOf()
        btnGo = findViewById(R.id.btnGo)
        edtPlace = findViewById(R.id.edtPlace)

        btnGo.setOnClickListener {
            destination  = edtPlace.text.toString()
            destination = destination.replace(" ", "+") // replace space for fetch data
            getDirection()
            myService = Common.getGoogleAPI()
        }

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
        mapFragment.getMapAsync(this)
    }

    private fun getDirection() {
        if(mLastLocation == null){
            return
        }
        currentPosition = LatLng(mLastLocation!!.latitude, mLastLocation!!.longitude)
        var requestApi = ""

            try {
                "https://maps.googleapis.com/maps/api/directions/json?mode=driving&transit_routing_preference=less_driving&origin=19.4647668,-99.1554364&destination=zamora&key=AIzaSyBqUW10T7Gk8I8a_uYsPk2WgdgXGu7olO8"

                requestApi = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${currentPosition.latitude},${currentPosition.longitude}&" +
                        "mode=driving&" +
                        "transit_routing_preference=fewer_transfers&" +

                        "destination=$destination&" +
                        "key=${resources.getString(R.string.google_direction_api)}"
                println("Noe: $requestApi")

                myService.getPath(requestApi)
                    .enqueue(object : retrofit2.Callback<String> {
                        override fun onResponse(call: Call<String>, response: Response<String>) {
                            val jsonObject: JSONObject = JSONObject(response.body().toString())
                            val jsonArray: JSONArray = jsonObject.getJSONArray("routes")
                            for (i in 0 until jsonArray.length()) {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyline = poly.getString("points")
                                polylineList = decodePoly(polyline) as MutableList<LatLng>
                            }

                            val builder = LatLngBounds.Builder()
                            for (latLng in polylineList){
                                builder.include(latLng)
                            }
                            builder.build()

                            polylineOptions = PolylineOptions()
                                .color(Color.GRAY)
                                .width(5F)
                                .startCap(SquareCap())
                                .endCap(SquareCap())
                                .jointType(JointType.ROUND)
                                .addAll(polylineList)
                            greyPolyline = mMap.addPolyline(polylineOptions)

                            blackPolylineOptions = PolylineOptions()
                                .color(Color.BLACK)
                                .width(5F)
                                .startCap(SquareCap())
                                .endCap(SquareCap())
                                .jointType(JointType.ROUND)
                            blackPolyline = mMap.addPolyline(blackPolylineOptions)
                            mMap.addMarker(MarkerOptions()
                                .position(polylineList[polylineList.size-1])
                                .title("Pickup location"))


                        }

                        override fun onFailure(call: Call<String>, t: Throwable) {
                                println("Noe: Error on calling api")
                            println("Noe: Error $t")
                        }
                    })
            } catch (e: Exception) {

        }

    }


    private fun decodePoly(encoded: String): List<LatLng>? {
        val poly: MutableList<LatLng> = ArrayList()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(
                lat / 1E5,
                lng / 1E5
            )
            poly.add(p)
        }
        return poly
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
                    if (locationSwitcher.getDirection() == StickySwitch.Direction.RIGHT) {
                        displayLocation()
                    }
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
            if (locationSwitcher.getDirection() == StickySwitch.Direction.RIGHT) {
                displayLocation()
            }

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


                    mLastLocation = LatLng(latitude, longitude)


                    geoFire.setLocation(
                        FirebaseAuth.getInstance().currentUser?.uid,
                        GeoLocation(latitude, longitude)
                    ) { _, _ ->

                        mCurrent = mMap.addMarker(
                            MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                                .position(mLastLocation!!)
                                .title("You")
                        )

                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLastLocation, 15.0F))
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

             mLastLocation = LatLng(latitude, longitude)


            geoFire.setLocation(
                FirebaseAuth.getInstance().currentUser?.uid, GeoLocation(latitude, longitude)
            ) { _, _ ->
                mMap.clear()
                mCurrent = mMap.addMarker(
                    MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                        .position(mLastLocation!!)
                        .title("You")
                )

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLastLocation, 15.0F))


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
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.isTrafficEnabled = false
        mMap.isIndoorEnabled = false
        mMap.isBuildingsEnabled = false
        mMap.uiSettings.isZoomControlsEnabled = true


    }


}