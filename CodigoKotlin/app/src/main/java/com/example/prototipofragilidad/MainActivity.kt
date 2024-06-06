package com.example.prototipofragilidad

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.EditText
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import java.io.FileInputStream
import android.media.MediaPlayer
import org.json.JSONObject

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Para gestionar los sensores
    private lateinit var sensorManager: SensorManager

    // Botones necesarios
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var calculateButton: Button

    // Impresión por pantalla
    private lateinit var timerTextView: TextView
    private lateinit var velocityTextView: TextView
    private lateinit var recordingTimeTextView: TextView
    private lateinit var resultTextView: TextView  // TextView para mostrar los resultados
    private lateinit var legLengthInput: EditText  // EditText para ingresar la longitud de la pierna


    // Variables para guardar los datos en CSV
    private lateinit var csvFile: File
    private lateinit var csvWriter: FileWriter

    private var recording = false
    private var csvFileName: String? = null
    private var startTime: Long = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializamos los TextView y los botones
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        calculateButton = findViewById(R.id.calculateButton)
        timerTextView = findViewById(R.id.timerTextView)
        velocityTextView = findViewById(R.id.tv_velocity)
        recordingTimeTextView = findViewById(R.id.recordingTimeTextView)
        resultTextView = findViewById(R.id.resultTextView)  // Inicializar el TextView para resultados
        legLengthInput = findViewById(R.id.legLengthInput)  // Inicializar el EditText para la longitud de la pierna


        startButton.setOnClickListener {
            startTimer()
            toggleButtons(false)
        }

        stopButton.setOnClickListener {
            stopRecording()
        }

        calculateButton.setOnClickListener {
            csvFileName?.let { fileName ->
                val file = File(getExternalFilesDir(null), fileName)
                val legLength = legLengthInput.text.toString().toDoubleOrNull()
                if (legLength == null) {
                    Toast.makeText(this, "Por favor, introduzca una longitud de pierna", Toast.LENGTH_SHORT).show();
                } else if (legLength < 0.1 || legLength > 1.5) {
                    Toast.makeText(this, "El valor de la longitud de pierna debe estar entre 0.1 y 1.5", Toast.LENGTH_SHORT).show();
                } else {
                    uploadCSV(file, legLength);
                }
            }
        }
    }

    private fun toggleButtons(enable: Boolean) {
        startButton.isEnabled = enable
        stopButton.isEnabled = enable
        calculateButton.isEnabled = enable
    }

    private fun setUpSensorStuff() {
        // Crear el sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Especificar el sensor que se quiere escuchar
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    private fun playBeepSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.beep)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener { mp ->
            mp.release()
        }
    }

    private fun startTimer() {
        object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerTextView.text = "Seconds remaining: ${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                timerTextView.text = "Start recording!"
                playBeepSound()
                createCSVFile()
                setUpSensorStuff()
                recording = true
                startTime = System.currentTimeMillis()
                startRecording()
            }
        }.start()
    }

    private fun startRecording() {
        object : CountDownTimer(20000, 1000) { //Este es el tiempo de prueba, deberia estar en 30 lo de la izq
            override fun onTick(millisUntilFinished: Long) {
                val elapsedSeconds = 20 - millisUntilFinished / 1000
                recordingTimeTextView.text = "Recording Time: $elapsedSeconds seconds"
            }

            override fun onFinish() {
                stopRecording()
            }
        }.start()
    }

    private fun createCSVFile() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AccelerometerData_Prueba.csv"
        csvFileName = fileName
        csvFile = File(getExternalFilesDir(null), fileName)
        try {
            csvWriter = FileWriter(csvFile)

            csvWriter.flush()
            csvWriter.close()

            Toast.makeText(
                this,
                "Archivo CSV creado: $fileName\nPath: ${csvFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show() // Mensaje de confirmación

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        recording = false
        sensorManager.unregisterListener(this) // Detener la lectura del acelerómetro
        try {
            csvWriter.flush()
            csvWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        timerTextView.text = "Medida finalizada"
        playBeepSound()
        calculateButton.visibility = Button.VISIBLE
        toggleButtons(true)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (recording && event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val currentTime = System.currentTimeMillis()
            val timeElapsed =
                (currentTime - startTime) / 1000.0  // Convertir milisegundos a segundos
            try {
                csvWriter = FileWriter(csvFile, true)  // Abrir el archivo en modo adición
                csvWriter.append("$timeElapsed,${event.values[0]},${event.values[1]},${event.values[2]}\n")
                csvWriter.flush()  // Para asegurarse de que los datos se escriben inmediatamente
                csvWriter.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            // Mostrar los valores del acelerómetro lineal
            velocityTextView.text = """
                Valores del acelerómetro lineal:
                x: ${"%.2f".format(event.values[0])} m/s²
                y: ${"%.2f".format(event.values[1])} m/s²
                z: ${"%.2f".format(event.values[2])} m/s²
            """.trimIndent()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No utilizado
    }

    override fun onDestroy() {
        super.onDestroy()

    }

    private fun uploadCSV(file: File, legLength: Double) {
        val url = "http://192.168.0.25:5000/process_csv" // Reemplaza <your-server-ip> con la IP de tu servidor
        val charset = "UTF-8"
        val boundary = System.currentTimeMillis().toString(16) // Generar valor único
        val CRLF = "\r\n" // Separador de línea requerido por multipart/form-data

        thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                connection.outputStream.use { output ->
                    PrintWriter(OutputStreamWriter(output, charset), true).use { writer ->
                        // Enviar archivo CSV
                        writer.append("--$boundary").append(CRLF)
                        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"").append(CRLF)
                        writer.append("Content-Type: text/csv; charset=$charset").append(CRLF)
                        writer.append(CRLF).flush()

                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(output)
                        }
                        output.flush()
                        writer.append(CRLF).flush()

                        // Enviar longitud de la pierna
                        writer.append("--$boundary").append(CRLF)
                        writer.append("Content-Disposition: form-data; name=\"leg_length\"").append(CRLF)
                        writer.append(CRLF).append(legLength.toString()).append(CRLF).flush()

                        // Fin de multipart/form-data
                        writer.append("--$boundary--").append(CRLF).flush()
                    }
                }

                // Obtener respuesta
                val responseCode = connection.responseCode
                val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }
                runOnUiThread {
                    if (responseCode == 200) {
                        Toast.makeText(this@MainActivity, "Archivo subido con éxito", Toast.LENGTH_LONG).show()
                        val responseJson = JSONObject(responseMessage)
                        handleServerResponse(responseJson)
                    } else {
                        Toast.makeText(this@MainActivity, "Error al subir el archivo: $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleServerResponse(responseJson: JSONObject) {
        if (responseJson.has("message")) {
            val message = responseJson.getString("message")
            resultTextView.text = message
        } else {
            val gaitVelocity = responseJson.optDouble("gait_velocity", Double.NaN)
            val numSteps = responseJson.optInt("num_steps", 0)
            val distanceTraveled = responseJson.optDouble("distance_traveled", Double.NaN)
            val meanStepLength = responseJson.optDouble("mean_step_length", Double.NaN)

            val resultBuilder = StringBuilder()
            resultBuilder.append("Velocidad de la marcha: %.2f m/s\n".format(gaitVelocity))
            resultBuilder.append("Numero de pasos: $numSteps\n")
            resultBuilder.append("Distancia Recorrida: %.2f m\n".format(distanceTraveled))
            resultBuilder.append("Media de longitud de pasos: %.2f m\n".format(meanStepLength))

            resultTextView.text = resultBuilder.toString()
        }
        resultTextView.visibility = TextView.VISIBLE
    }






}
