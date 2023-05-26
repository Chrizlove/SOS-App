package com.chrizlove.helpapp

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrizlove.helpapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contactViewModel: ContactViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //recycler view is setted up
        setUpReminderRecyclerView()

        //contactViewModel set up
        contactViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(ContactViewModel::class.java)

        contactViewModel.contacts.observe(this, Observer {

            //change visibility of no reminders yet textview
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
        contactAdapter = ContactAdapter(this,)
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