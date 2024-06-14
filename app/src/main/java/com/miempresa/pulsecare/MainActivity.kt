package com.miempresa.pulsecare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var reminderController: ReminderController
    private lateinit var remindersRecyclerView: RecyclerView
    private lateinit var remindersAdapter: RemindersAdapter
    private lateinit var closestReminderTextView: TextView
    private lateinit var addReminderButton: ImageButton

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val foregroundServiceGranted = permissions[Manifest.permission.FOREGROUND_SERVICE_LOCATION] ?: true
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: true

        if (foregroundServiceGranted && (fineLocationGranted || coarseLocationGranted)) {
            startUpdateRemindersService()
        } else {
            Toast.makeText(this, "Se requieren permisos de ubicación para el servicio en primer plano.", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        remindersRecyclerView = findViewById(R.id.remindersRecyclerView)
        closestReminderTextView = findViewById(R.id.closestReminderTextView)
        addReminderButton = findViewById(R.id.addReminderButton)

        // Configurar RecyclerView
        remindersRecyclerView.layoutManager = LinearLayoutManager(this)
        remindersAdapter = RemindersAdapter { reminder -> deleteReminder(reminder) }
        remindersRecyclerView.adapter = remindersAdapter

        // Inicializar controlador
        reminderController = ReminderController(this)
        reminderController.fetchAllReminders { remindersList ->
            remindersAdapter.setData(remindersList)
            updateClosestReminder(remindersList)
        }

        // Configurar botón para agregar recordatorio
        addReminderButton.setOnClickListener {
            val intent = Intent(this, AddReminderActivity::class.java)
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionsIfNecessary()
        } else {
            startUpdateRemindersService()
        }

        // Escuchar cambios en el campo 'state' de 'closestReminders'
        listenForStateChanges()
    }

    private fun deleteReminder(reminder: Reminder) {
        reminderController.deleteReminder(reminder) { success ->
            if (success) {
                remindersAdapter.removeReminder(reminder)
                Toast.makeText(this, "Recordatorio eliminado exitosamente", Toast.LENGTH_SHORT).show()
                reminderController.fetchAllReminders { remindersList ->
                    updateClosestReminder(remindersList)
                }
            } else {
                Toast.makeText(this, "Error al eliminar el recordatorio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateClosestReminder(remindersList: List<Reminder>) {
        if (remindersList.isNotEmpty()) {
            val closestReminder = remindersList.first()
            val closestReminderText = "${closestReminder.medicineName} a las ${formatTime(closestReminder.reminderTime)}"
            closestReminderTextView.text = closestReminderText

            val closestDatabaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("closestReminders")
            val closestReminderData = hashMapOf(
                "id" to closestReminder.id,
                "medicineName" to closestReminder.medicineName,
                "reminderTime" to closestReminder.reminderTime,
                "state" to "pending"
            )

            // Almacenar el recordatorio más cercano en la referencia "closestReminders/currentClosest"
            closestDatabaseReference.setValue(closestReminderData)
                .addOnSuccessListener {
                    Log.d(TAG, "Recordatorio más cercano guardado exitosamente.")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error al guardar el recordatorio más cercano", e)
                }

        } else {
            closestReminderTextView.text = "No hay recordatorios"
        }
    }

    private fun formatTime(timeInMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(timeInMillis)
    }

    private fun listenForStateChanges() {
        val closestDatabaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("closestReminders")

        closestDatabaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java)
                val reminderId = snapshot.child("id").getValue(String::class.java)
                Log.d(TAG, "State: $state, Reminder ID: $reminderId") // Añadido para depuración

                if (state == "confirmed") {
                    showMedicationConfirmedNotification()
                    if (reminderId != null) {
                        updatePillsCount(reminderId)
                    } else {
                        Log.w(TAG, "Reminder ID is null")
                    }
                } else if (state == "emergency") {
                    showMedicationEmergencyNotification()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read state value.", error.toException())
            }
        })
    }

    // Función para actualizar la cantidad de píldoras cuando se presiona el boton "confirmed"
    private fun updatePillsCount(reminderId: String) {
        val reminderReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("reminders").child(reminderId)

        reminderReference.child("pills").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentPillsCount = snapshot.getValue(Int::class.java)
                Log.d(TAG, "Current pills count: $currentPillsCount") // Añadido para depuración

                if (currentPillsCount != null) {
                    val newPillsCount = currentPillsCount - 1
                    Log.d(TAG, "New pills count: $newPillsCount") // Log para depuración

                    reminderReference.child("pills").setValue(newPillsCount)
                        .addOnSuccessListener {
                            Log.d(TAG, "Cantidad de píldoras actualizada correctamente.")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error al actualizar la cantidad de píldoras", e)
                        }
                } else {
                    Log.w(TAG, "currentPillsCount is null")
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error al leer la cantidad de píldoras", error.toException())
            }
        })
    }

    private fun showMedicationConfirmedNotification() {
        Toast.makeText(this, "La toma del medicamento fue confirmada", Toast.LENGTH_SHORT).show()
    }

    private fun showMedicationEmergencyNotification() {
        Toast.makeText(this, "Se presionó el botón de emergencia", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private fun requestPermissionsIfNecessary() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startUpdateRemindersService()
        }
    }

    private fun startUpdateRemindersService() {
        val serviceIntent = Intent(this, UpdateRemindersService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
