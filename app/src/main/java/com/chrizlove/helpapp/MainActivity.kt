package com.chrizlove.helpapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrizlove.helpapp.ShakeDetector.OnShakeListener
import com.chrizlove.helpapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contactViewModel: ContactViewModel
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var location: Location
    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var mShakeDetector: ShakeDetector? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        //requesting permissions
        if(!checkSMSPermission()){
            requestSMSPermission()
        }
        if(!checkLocationPermission()){
            requestLocationPermission()
        }

        //recycler view is setted up
        setUpReminderRecyclerView()

        //contactViewModel set up
        contactViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(ContactViewModel::class.java)

        contactViewModel.contacts.observe(this, Observer {

            //change visibility of no contacts yet textview
            if(contactViewModel.contacts.value?.isEmpty() == true)
            {
                binding.noContact.visibility = View.VISIBLE
            }
            else{
                binding.noContact.visibility = View.INVISIBLE
            }

            //updating the recyclerview on any change
            contactAdapter.updateContacts(it)

        })

        //adding new contact
        addContact()

        //swipe to delete functionality
        swipeToDelete()

        //send sos
        sendSOS()

        // ShakeDetector initialization
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mShakeDetector = ShakeDetector()
        mShakeDetector!!.setOnShakeListener(object : OnShakeListener {
            @SuppressLint("MissingPermission")
            override fun onShake(count: Int) {
                // check if the user has shacked
                // the phone for 3 time in a row
                if (count == 3) {
                    // vibrate the phone
                    vibrate()
                    fusedLocationProviderClient=LocationServices.getFusedLocationProviderClient(applicationContext)
                    getCurrentLocationAndSendSMS()
                    Toast.makeText(applicationContext,"Shaken",Toast.LENGTH_SHORT).show()
                }
            }
        })
        // register the listener
        mSensorManager!!.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI)
}

    // method to vibrate the phone
    fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val vibEff: VibrationEffect

        // Android Q and above have some predefined vibrating patterns
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibEff = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            vibrator.cancel()
            vibrator.vibrate(vibEff)
        } else {
            vibrator.vibrate(500)
        }
    }

    private fun sendSOS() {
        binding.buttonSOS.setOnClickListener {
            fusedLocationProviderClient=LocationServices.getFusedLocationProviderClient(this)
            getCurrentLocationAndSendSMS()
        }
    }

    private fun sendSMS() {
        Log.d(TAG,"2")
        if(checkSMSPermission()){
            val smsManager: SmsManager
            smsManager = SmsManager.getDefault()
//            if (Build.VERSION.SDK_INT>=23) {
//                //if SDK is greater that or equal to 23 then
//                //this is how we will initialize the SmsManager
//                smsManager = this.getSystemService(SmsManager::cla   NN ss.java)
//            }
//            else{
//                //if user's SDK is less than 23 then
//                //SmsManager will be initialized like this
//                smsManager = SmsManager.getDefault()
//            }
            for (contact in contactViewModel.contacts.value!!){
                smsManager.sendTextMessage(contact.c_number, null,
                    "Hi, I am in an emergency! This is my location https://www.google.com/maps/?q="+location.latitude+","+location.longitude,
                    null, null)
            }
        }
        else{
            requestSMSPermission()
        }
    }

    private fun requestSMSPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.SEND_SMS), PERMISSION_REQUEST_SMS)
    }

    private fun checkSMSPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this,Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocationAndSendSMS() {
        Log.d(TAG,"1")
        if(checkLocationPermission()){
            if(locationEnabled()){
                fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) {task->
                    location=task.result
                    if(location==null){

                    }
                    else{
                        //SEND sms when gotten location
                        sendSMS()
                    }
                }
            }
            else{
                //send last known location
            }
        }
        else{
            //request permission
            requestLocationPermission()
        }
    }

    companion object{
        private const val PERMISSION_REQUEST_ACCESS_LOCATION=100
        private const val  PERMISSION_REQUEST_SMS=99
    }

    private fun requestLocationPermission() {
            ActivityCompat.requestPermissions(this,
                arrayOf( Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_ACCESS_LOCATION)

    }

    private fun locationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkLocationPermission(): Boolean {
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
        {
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode== PERMISSION_REQUEST_ACCESS_LOCATION){
            if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_DENIED){
                Toast.makeText(applicationContext, "Permissions Required to Operate.", Toast.LENGTH_SHORT).show()
                //again request permission
                requestLocationPermission()
            }
        }
        if(requestCode== PERMISSION_REQUEST_SMS){
            if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_DENIED){
                Toast.makeText(applicationContext, "Permissions Required to Operate.", Toast.LENGTH_SHORT).show()
                //again request permission
                requestSMSPermission()
            }
        }
    }


    private fun swipeToDelete() {
        val item= object: SwipeToDelete(this,0, ItemTouchHelper.RIGHT){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                //remove contact from room db
                contactViewModel.delete(contactAdapter.getContact(viewHolder.adapterPosition))
                //remove reminder from recyclerview
                contactAdapter.deleteContacts(viewHolder.adapterPosition)

            }
        }
        val itemTouchHelper = ItemTouchHelper(item)
        itemTouchHelper.attachToRecyclerView(binding.contactRecyclerView)
    }

    private fun addContact() {

        //adding contact manually
        binding.manualContactButton.setOnClickListener {

            //checking if contact list size is less than 3
            if(checkContactListSize()) {
                val builder = AlertDialog.Builder(this)
                val inflater = layoutInflater
                val dialoglayout = inflater.inflate(R.layout.alert_edit_lyt, null)
                val dialogName = dialoglayout.findViewById<EditText>(R.id.alert_name)
                val dialogNumber = dialoglayout.findViewById<EditText>(R.id.alert_number)

                with(builder) {
                    setTitle("Add Contact Details")
                    setPositiveButton("ADD") { dialog, which ->
                        contactViewModel.insert(
                            Contact(
                                dialogName.text.toString(),
                                dialogNumber.text.toString()
                            )
                        )
                    }
                    setNegativeButton("Cancel") { dialog, which ->
                    }
                    setView(dialoglayout)
                    show()
                }
            }
        }

        //adding contact from contacts list
        binding.contactListButton.setOnClickListener {

            //checking if contact list size is less than 3
            if(checkContactListSize()) {
                var i = Intent(Intent.ACTION_PICK)
                i.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                startActivityForResult(i, 111)
            }
        }
    }

    private fun checkContactListSize(): Boolean {
        if(contactAdapter.itemCount>=3){
            Toast.makeText(applicationContext, "Maximum Contact Limit Reached!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun setUpReminderRecyclerView() {
        contactAdapter = ContactAdapter(this)
        val layoutManager =  LinearLayoutManager(this)
        binding.contactRecyclerView.layoutManager = layoutManager
        binding.contactRecyclerView.adapter = contactAdapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //code when requestcode is 111 that is when we want to add contact from contacts list
        if (requestCode==111 && resultCode==Activity.RESULT_OK){
            var contacturi=data?.data ?: return
            var cols= arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            var rs=contentResolver.query(contacturi,cols,null,null,null)
            if(rs?.moveToFirst()!!){
                //Log.d(TAG, rs.getString(1))
                contactViewModel.insert(Contact(rs.getString(1), rs.getString(0)))
            }
        }
    }
}