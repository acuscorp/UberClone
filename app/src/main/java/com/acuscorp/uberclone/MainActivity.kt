package com.acuscorp.uberclone

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.Button
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.acuscorp.uberclone.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.rengwuxian.materialedittext.MaterialEditText
import uk.co.chrisjenx.calligraphy.CalligraphyConfig
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper
import com.google.android.material.snackbar.Snackbar


class MainActivity : AppCompatActivity() {
    private lateinit var btnSignIn: Button
    private lateinit var btnRegister: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var users: DatabaseReference

    private lateinit var rootLayout: RelativeLayout


    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CalligraphyConfig.initDefault(
            CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/uber_move.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        )

        // init firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()
        users = db.getReference("Users")

        // init view
        setContentView(R.layout.activity_main)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnRegister = findViewById(R.id.btnRegister)
        rootLayout = findViewById(R.id.rootLayout)

        // event listener
        btnRegister.setOnClickListener {
            showRegisterDialog()
        }
    }

    private fun showRegisterDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("REGISTER")
            .setMessage("Please use email to register")

        val inflater = LayoutInflater.from(this)
        val registerLayout = inflater.inflate(R.layout.layout_register, null)

        val edtEmail: MaterialEditText = registerLayout.findViewById(R.id.edtEmail)
        val edtPassword: MaterialEditText = registerLayout.findViewById(R.id.edtPassword)
        val edtName: MaterialEditText = registerLayout.findViewById(R.id.edtName)
        val edtPhone: MaterialEditText = registerLayout.findViewById(R.id.edtPhone)

        dialog.setPositiveButton("REGISTER") { dialog, which ->
            dialog.dismiss()

            // check validation
            if (TextUtils.isEmpty(edtEmail.text)) {
                Snackbar.make(rootLayout, "Please enter email address", Snackbar.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }

            if (TextUtils.isEmpty(edtPhone.text)) {
                Snackbar.make(rootLayout, "Please enter your phone number", Snackbar.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }
            if (edtPassword.text.toString().length < 6) {
                Snackbar.make(rootLayout, "Password too short", Snackbar.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            //register new user
            val email = edtEmail.text.toString()
            val password = edtPassword.text.toString()
            val phone = edtPhone.text.toString()
            val name = edtName.text.toString()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    // save user to db
                    val user: User = User(
                        name = name,
                        email = email,
                        password = password,
                        phone = phone
                    )

                }

        }
        dialog.setNegativeButton("CANCEL") { dialog, which ->
            dialog.dismiss()
        }
    }
}