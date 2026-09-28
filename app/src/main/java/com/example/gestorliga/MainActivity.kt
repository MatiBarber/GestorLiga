package com.example.gestorliga

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegistro: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            irATorneos()
            return
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegistro = findViewById(R.id.btnRegistro)

        btnLogin.setOnClickListener {
            val datos = leerDatosValidos() ?: return@setOnClickListener
            setCargando(true)
            auth.signInWithEmailAndPassword(datos.first, datos.second)
                .addOnCompleteListener { tarea ->
                    setCargando(false)
                    if (tarea.isSuccessful) {
                        Toast.makeText(this, "Sesión iniciada", Toast.LENGTH_SHORT).show()
                        irATorneos()
                    } else {
                        Toast.makeText(
                            this,
                            "No se pudo iniciar sesión: ${tarea.exception?.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        btnRegistro.setOnClickListener {
            val datos = leerDatosValidos() ?: return@setOnClickListener
            setCargando(true)
            auth.createUserWithEmailAndPassword(datos.first, datos.second)
                .addOnCompleteListener { tarea ->
                    setCargando(false)
                    if (tarea.isSuccessful) {
                        Toast.makeText(this, "Cuenta creada", Toast.LENGTH_SHORT).show()
                        irATorneos()
                    } else {
                        Toast.makeText(
                            this,
                            "No se pudo registrar: ${tarea.exception?.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    /** Devuelve (email, password) si son válidos; si no, muestra un Toast y devuelve null. */
    private fun leerDatosValidos(): Pair<String, String>? {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (email.isEmpty()) {
            Toast.makeText(this, "Ingresá tu email", Toast.LENGTH_SHORT).show()
            return null
        }
        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return null
        }
        return email to password
    }

    /** Deshabilita los botones mientras se espera la respuesta de Firebase. */
    private fun setCargando(cargando: Boolean) {
        btnLogin.isEnabled = !cargando
        btnRegistro.isEnabled = !cargando
    }

    private fun irATorneos() {
        startActivity(Intent(this, TorneosActivity::class.java))
        finish()
    }
}