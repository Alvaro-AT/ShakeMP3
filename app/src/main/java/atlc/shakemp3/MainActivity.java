package atlc.shakemp3;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.view.GestureDetectorCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends VoiceActivity implements OnPlayerEventListener, GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener{

    private long startListeningTime = 0; // To skip errors (see processAsrError method)

    private static Integer ID_PROMPT_INFO = 1;
    private static Integer ID_PROMPT_QUERY = 0;

    private static final String LOGTAG = "SHAKEMP3";
    //Lista de los títulos de las canciones
    private ArrayList<String> songList = null;
    //Este array de long almacenará los albums ID para la posterior muestra de la carátula del álbum
    private long [] albums;
    //Referencia al reproductor personalizado
    private SimplePlayer player;
    //Referencia a la seekBar
    private SeekBar seekBar;
    //Variables de estado de la reproducción
    private int currentSongPosition = -1;
    private int currentSongState = -1;
    private int currentSongDuration = -1;
    //Referencias a los textview que mostrarán el estado de la reproducción
    private TextView songTitle;
    private TextView songStart;
    private TextView songDuration;
    //Referencia a los botones de los cuales después definiremos su funcionalidad
    private ImageButton playButton = null;
    private ImageButton pauseButton = null;
    private ImageButton backButton = null;
    private ImageButton rewindButton = null;
    private ImageButton forwardButton = null;
    private ImageButton nextButton = null;
    //Carátula del álbum
    private ImageView album_art = null;
    //Lista de los paths de las canciones en nuestro teléfono
    private ArrayList<String> songPathList = null;
    //Referencia al detector de gestos
    private GestureDetectorCompat gesturedetector = null;
    //Variables que usamos para el uso de los sensores
    private SensorManager sensorManager;
    private boolean proximitySensorActivated = true;
    private boolean gyroscopeSensorActivated = true;
    private boolean aux_gyroscope = false;
    private boolean aux_proximity = false;
    //Boolean que será true si la app está en segundo plano y si el reproductor está en pausa
    private boolean isBackgroundPaused = false;
    //Menú de opciones para desactivar funcionalidades
    private Menu menu = null;
    //Handler que nos ayudará a cambiar el estado de la seekbar con el avance de la canción
    Handler handler = null;
    Runnable timerRunnable = null;
    //Variables para controlar el comportamiento del giroscopio que más abajo explicamos.
    boolean right = false;
    boolean left = false;
    //Boolean que será true si el reproductor está pausado
    private boolean pause = false;
    //CONSTANTES DEL RECONOCIMIENTO DE GESTO
    private static final int SWIPE_MIN_DISTANCE = 420;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;
    //Variables para la gestión del gesto de Swipe para subir y bajar el volumen
    private AudioManager audioManager = null;
    private int aux_vol;
    private int scale;
    private ScaleGestureDetector sgd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ////INICIALIZACIÓN DE TODAS LAS VARIABLES/////
        songList = new ArrayList<>();
        songPathList = new ArrayList<>();

        //Inicializamos el reproductor
        player = new SimplePlayer(this);

        //Elementos gráficos de la app y su inicialización
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        songStart = (TextView) findViewById(R.id.songStart);
        songStart.setText("0:00");
        songDuration = (TextView) findViewById(R.id.songDuration);
        playButton = (ImageButton) findViewById(R.id.playButton);
        pauseButton = (ImageButton) findViewById(R.id.pauseButton);
        backButton = (ImageButton) findViewById(R.id.backButton);
        rewindButton = (ImageButton) findViewById(R.id.rewindButton);
        forwardButton = (ImageButton) findViewById(R.id.forwardButton);
        nextButton = (ImageButton) findViewById(R.id.nextButton);
        album_art = (ImageView) findViewById(R.id.imageView2);
        songTitle = (TextView) findViewById(R.id.songTitle);

        //Inicialización del handler que gestionará la seekBar
        handler = new Handler();
        handler.postDelayed(timerRunnable, 1000);

        //Inicialización del detector de gestos
        gesturedetector = new GestureDetectorCompat(this, this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        scale = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        //Con esta hebra que se ejecutará cada segundo, actualizamos la seekBar así como los textview
        //según el estado de la canción
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                seekBar.setProgress(player.getPosition());
                songStart.setText(String.format("%02d:%02d", (player.getPosition()/1000)/60, (player.getPosition()/1000) % 60));
                int aux = currentSongDuration - player.getPosition();
                songDuration.setText(String.format("%02d:%02d", (aux/1000)/60, (aux/1000) % 60));
                currentSongState = player.getPosition();
                //Volvemos ejecutar en un segundo
                handler.postDelayed(this, 1000);
            }
        };

        //Initialize the speech recognizer and synthesizer
        initSpeechInputOutput(this);

        ///////////FUNCIONALIDAD DE BOTONES Y SENSORES///////////////

        //Obtenemos el servicio de sensores
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        //Definimos la funcionalidad de los sensores
        final SensorEventListener mySensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                //Si el sensor es el giroscopio
                if(sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE)
                {
                    //right nos indicará si el primer movimiento ha sido a la derecha
                    //left nos indicará lo mismo pero a la izquierda
                        //Si aun no hemos hecho un primer movimiento
                        if (!right && !left) {
                            if (currentSongPosition != -1) {
                                //En este caso hemos movido a la derecha
                                if (sensorEvent.values[2] <= -2) {
                                    right = true;
                                    //hebra que se ejecutará en 0.5s. Esta hebra
                                    //hace que si en 0.5s no hemos hecho el movimiento
                                    //hacia la izquierda, right volverá a ser false
                                    Runnable isElapsed = new Runnable() {
                                        @Override
                                        public void run() {
                                            right = false;
                                        }
                                    };

                                    Handler h = new Handler();
                                    h.postDelayed(isElapsed, 500);
                                } else if (sensorEvent.values[2] >= 2) {
                                    //Mismo procedimiento que para la derecha pero ahora para izquierda
                                    left = true;

                                    Runnable isElapsed = new Runnable() {
                                        @Override
                                        public void run() {
                                            left = false;
                                        }
                                    };

                                    Handler h = new Handler();
                                    h.postDelayed(isElapsed, 500);
                                }
                            }
                            //Si hemos ido a la derecha y además tenemos tanto el giroscopio activado y
                            //no estamos en segundo plano y en pausa
                    }else if(right && !isBackgroundPaused && gyroscopeSensorActivated)
                    {
                        //Ahora vamos hacia la izquierda
                        if(sensorEvent.values[2] >= 2)
                        {
                            //Creamos vibración
                            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            v.vibrate(300);
                            //Cambiamos a la siguiente canción
                            nextSong();
                            right = false;
                        }
                        //Mismo procedimiento pero ahora con el primer movimiento a la izq
                    }else if(left && !isBackgroundPaused && gyroscopeSensorActivated)
                    {
                        if(sensorEvent.values[2] <= -2)
                        {
                            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            v.vibrate(300);
                            //Cambiamos a la canción anterior
                            previousSong();
                            left = false;
                        }
                    }

                } //Ahora sensor de proximidad. Queremos crear la sensación de que el móvil se pausa poniendolo boca abajo
                //y que se pondrá en play dándole la vuelta boca arriba. Comprobamos que el sensor de proximidad esté activado
                //en la configuración de usuario
                else if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY && proximitySensorActivated) {
                    if (sensorEvent.values[0] >= -0.01 && sensorEvent.values[0]<= 0.01) {
                        //Si tenemos algo cerca, pausamos
                        pause();
                    } else {
                        //reproducimos si no
                        play();
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }

        };

        //Registramos el listener para los 2 sensores
        sensorManager.registerListener(mySensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                sensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(mySensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
                SensorManager.SENSOR_DELAY_NORMAL);

        //Funcionalidad del botón de play
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
                pause = false;

            }
        });

        //Funcionalidad del botón de pausa
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(currentSongPosition != -1)
                {
                    pause();
                    pause = true;
                }
            }
        });

        //Funcionalidad para poner canción previa
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                previousSong();
            }
        });

        //Funcionalidad para rebobinar canción
        rewindButton.setOnTouchListener(new View.OnTouchListener() {
            private Handler mHandler;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mHandler != null) return true;
                        mHandler = new Handler();
                        mHandler.postDelayed(mAction, 500);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (mHandler == null) return true;
                        mHandler.removeCallbacks(mAction);
                        mHandler = null;
                        return true;
                }
                return false;
            }
            Runnable mAction = new Runnable() {
                @Override public void run() {
                    currentSongState -= 5000;
                    player.setSeekPosition(currentSongState);
                    mHandler.postDelayed(this, 1000);
                }
            };
        });

        //Funcionalidad para avanzar canción
        forwardButton.setOnTouchListener(new View.OnTouchListener() {
            private Handler mHandler;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mHandler != null) return true;
                        mHandler = new Handler();
                        mHandler.postDelayed(mAction, 500);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (mHandler == null) return true;
                        mHandler.removeCallbacks(mAction);
                        mHandler = null;
                        return true;
                }
                return false;
            }
            Runnable mAction = new Runnable() {
                @Override public void run() {
                    currentSongState += 5000;
                    player.setSeekPosition(currentSongState);
                    mHandler.postDelayed(this, 1000);
                }
            };
        });

        //Botón de siguiente canción
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nextSong();
            }
        });

        //Funcionalidad para cuando cambiemos con la seekbar el estado de la canción
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(timerRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                currentSongState = seekBar.getProgress();
                //Cambiamos la posición de la canción
                player.setSeekPosition(currentSongState);
                handler.postDelayed(timerRunnable, 1000);
            }
        });

        //Instanciamos el detector de gestos para Swipe
        sgd = new ScaleGestureDetector(this, new ScaleListener());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;

        return true;
    }

    /**
     * Explain to the user why we need their permission to record audio on the device
     * See the checkASRPermission in the VoiceActivity class
     */
    @Override
    public void showRecordPermissionExplanation() {
        Toast.makeText(getApplicationContext(), "Esta opción de ShakeMP3 necesita acceder al micrófono para realizar el reconocimiento de voz", Toast.LENGTH_SHORT).show();

    }

    /**
     * If the user does not grant permission to record audio on the device, a message is shown and the app finishes
     */
    @Override
    public void onRecordAudioPermissionDenied() {
        Toast.makeText(getApplicationContext(), "Lo siento, esta opción de ShakeMP3 no puede funcionar sin acceso al micrófono", Toast.LENGTH_SHORT).show();
        System.exit(0);
    }


    /**
     * Synthesizes the best recognition result
     */
    @Override
    public void processAsrResults(ArrayList<String> nBestList, float[] nBestConfidences) {
        if(nBestList!=null){
            if(nBestList.size()>0){
                String bestResult = nBestList.get(0); //We will use the best result
                try {
                    String recognised_word = bestResult;
                    voiceAction(recognised_word);
                } catch (Exception e) {  }

            }
        }
    }

    /**
     * Invoked when the ASR is ready to start listening. Provides feedback to the user to show that the app is listening:
     * 		* It changes the color and the message of the speech button
     *      * For us it has no action
     */
    @Override
    public void processAsrReadyForSpeech() {

    }


    /**
     * Provides feedback to the user (by means of a Toast and a synthesized message) when the ASR encounters an error
     *
     * Code taken from TalkBack app
     */
    @Override
    public void processAsrError(int errorCode) {
        //Possible bug in Android SpeechRecognizer: NO_MATCH errors even before the the ASR
        // has even tried to recognized. We have adopted the solution proposed in:
        // http://stackoverflow.com/questions/31071650/speechrecognizer-throws-onerror-on-the-first-listening
        long duration = System.currentTimeMillis() - startListeningTime;
        if (duration < 500 && errorCode == SpeechRecognizer.ERROR_NO_MATCH) {
            Log.e(LOGTAG, "Doesn't seem like the system tried to listen at all. duration = " + duration + "ms. Going to ignore the error");
            stopListening();
        }
        else {
            String errorMsg = "";
            switch (errorCode) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMsg = "Audio recording error";
                case SpeechRecognizer.ERROR_CLIENT:
                    errorMsg = "Unknown client side error";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMsg = "Insufficient permissions";
                case SpeechRecognizer.ERROR_NETWORK:
                    errorMsg = "Network related error";
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMsg = "Network operation timed out";
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMsg = "No recognition result matched";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMsg = "RecognitionService busy";
                case SpeechRecognizer.ERROR_SERVER:
                    errorMsg = "Server sends error status";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMsg = "No speech input";
                default:
                    errorMsg = ""; //Another frequent error that is not really due to the ASR, we will ignore it
            }
            if (errorMsg != "") {
                this.runOnUiThread(new Runnable() { //Toasts must be in the main thread
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error en el reconocimiento de voz", Toast.LENGTH_LONG).show();
                    }
                });

                Log.e(LOGTAG, "Error when attempting to listen: " + errorMsg);
                try { speak(errorMsg,"EN", ID_PROMPT_INFO); } catch (Exception e) { Log.e(LOGTAG, "TTS not accessible"); }
            }
        }
    }

    /**
     * Starts listening for any user input.
     * When it recognizes something, the <code>processAsrResult</code> method is invoked.
     * If there is any error, the <code>onAsrError</code> method is invoked.
     */
    @Override
    public void onTTSDone(String uttId) {
        if(uttId.equals(ID_PROMPT_QUERY.toString())) {
            runOnUiThread(new Runnable() {
                public void run() {
                    startListening();
                }
            });
        }
    }

    @Override
    public void onTTSError(String uttId) {

    }

    @Override
    public void onTTSStart(String uttId) {

    }

    /**
     * Checks whether the device is connected to Internet (returns true) or not (returns false)
     * From: http://developer.android.com/training/monitoring-device-state/connectivity-monitoring.html
     */
    public boolean deviceConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return (activeNetwork != null && activeNetwork.isConnectedOrConnecting());
    }

    /**
     * Starts listening for any user input.
     * When it recognizes something, the <code>processAsrResult</code> method is invoked.
     * If there is any error, the <code>onAsrError</code> method is invoked.
     */
    private void startListening(){

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, aux_vol, 0);

        if(deviceConnectedToInternet()){

            try {
				/*Start listening, with the following default parameters:
					* Language = Spanish
					* Recognition model = Free form,
					* Number of results = 1 (we will use the best result to perform the search)
					*/
                startListeningTime = System.currentTimeMillis();
                Locale l = new Locale("es", "ES");
                listen(l, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, 1); //Start listening
            } catch (Exception e) {
                this.runOnUiThread(new Runnable() {  //Toasts must be in the main thread
                    public void run() {
                        Toast.makeText(getApplicationContext(),"ASR no se pudo inicializar", Toast.LENGTH_SHORT).show();
                    }
                });

                try { speak("No se pudo inicialiar el reconocimiento de voz", "es-ES", ID_PROMPT_INFO); } catch (Exception ex) { }

            }
        } else {

            this.runOnUiThread(new Runnable() { //Toasts must be in the main thread
                public void run() {
                    Toast.makeText(getApplicationContext(),"Por favor, compruebe su conexión a internet", Toast.LENGTH_SHORT).show();
                }
            });
            try { speak("Por favor, compruebe su conexión a internet", "es-ES", ID_PROMPT_INFO); } catch (Exception ex) { }

        }
    }
    //Listener para subir y bajar el volumen con el gesto de swipe
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //Obtenemos el volumen actual del dispositivo
            scale = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            //Según el pinch realizado y su tamaño, se incrementará/decrementará el volumen, en
            //mayor o menor medida.
            if (detector.getScaleFactor() < 1)
                scale = (int) Math.floor(scale * detector.getScaleFactor() + 0.1);
            else
                scale = (int)Math.ceil(scale * detector.getScaleFactor() - 0.1);

            if (scale > audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                scale = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

            if (scale < 1)
                scale = 1;

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, scale, 0);

            return true;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.gesturedetector.onTouchEvent(event);
        sgd.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    //Métodos que no usamos pero que teníamos que implementar por extender de una clase abstracta
    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return true;
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        return false;
    }

    //Listener para cuando realizamos un doble tap. Con este gesto pausamos y reproducimos
    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        if(pause)
        {
            play();
            pause = false;
        }else
        {
            pause();
            pause = true;
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent motionEvent) {
        return false;
    }

    //Función para cambiar de canción haciendo un fling. Parte del código ha sido cogido de
    //stackoverflow. Si hacemos fling de derecha a izquierda entonces ponemos la canción siguiente
    //y si hacemos de izquierda a derecha ponemos la canción previa
    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        Log.d("---onFling---", motionEvent.toString() + motionEvent1.toString() + "");

        try {
            if (Math.abs(motionEvent.getY() - motionEvent1.getY()) > SWIPE_MAX_OFF_PATH)
                return false;
            // right to left swipe
            if (motionEvent.getX() - motionEvent1.getX() > SWIPE_MIN_DISTANCE
                    && Math.abs(v) > SWIPE_THRESHOLD_VELOCITY) {
                //do your code
                nextSong();

            } else if (motionEvent1.getX() - motionEvent.getX() > SWIPE_MIN_DISTANCE
                    && Math.abs(v) > SWIPE_THRESHOLD_VELOCITY) {
                //left to right flip
                previousSong();
            }

        } catch (Exception e) {}
        return false;
    }

    //Función que procesa lo que la aplicación ha escuchado
    private void voiceAction(String recognised_word)
    {
        //Pasamos a mayúscula toda la palabra para evitar confusiones en la comparación de los string
        String valor_recibido = recognised_word.toUpperCase();
        boolean aux = false;

        //Comprobamos con contains
        if(valor_recibido.contains("REPRODUCIR") || valor_recibido.contains("PLAY"))
        {
            aux = true;
            pause = false;
            play();
        }else if(valor_recibido.contains("SIGUIENTE") || valor_recibido.contains("NEXT") || valor_recibido.contains("PASAR"))
        {
            nextSong();
        }else if(valor_recibido.contains("ANTERIOR") || valor_recibido.contains("PREVIOUS") || valor_recibido.contains("VOLVER"))
        {
            previousSong();
        }else if(valor_recibido.contains("CERRAR") || valor_recibido.contains("CLOSE") || valor_recibido.contains("SALIR"))
        {
            System.exit(0);
        }else if(valor_recibido.contains("PAUSA") || valor_recibido.contains("PARAR")  || valor_recibido.contains("STOP"))
        {
            aux = true;
        }else if(valor_recibido.contains("BAJAR") || valor_recibido.contains("DISMINUIR") || valor_recibido.contains("DECREMENTAR"))
        {
            int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            vol-=3;
            if(vol < 3) vol=0;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol,0);
            try {
                speak("He disminuido el volumen.", "es-ES", ID_PROMPT_INFO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else if(valor_recibido.contains("SUBIR") || valor_recibido.contains("AUMENTAR") || valor_recibido.contains("INCREMENTAR"))
        {
            int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            vol+=3;
            if(vol > audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            {
                vol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol,0);
            try {
                speak("He aumentado el volumen.", "es-ES", ID_PROMPT_INFO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if(valor_recibido.contains("ACTIVAR") || valor_recibido.contains("SHAKING") || valor_recibido.contains("GIROSCOPIO"))
        {
            MenuItem gyroscopeSensorItem = menu.findItem(R.id.action_settings2);

            if (gyroscopeSensorActivated) {
                gyroscopeSensorItem.setTitle("Activar Shaking");
                gyroscopeSensorActivated = false;
                aux_gyroscope = false;
                try {
                    speak("Shaking desactivado.", "es-ES", ID_PROMPT_INFO);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                gyroscopeSensorItem.setTitle("Desactivar Shaking");
                gyroscopeSensorActivated = true;
                aux_gyroscope = true;
                try {
                    speak("Shaking activado.", "es-ES", ID_PROMPT_INFO);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }else
        {
            try{
                aux_vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                speak("No te he entendido, repítemelo, por favor", "es-ES", ID_PROMPT_QUERY);
                aux = true;
            }catch (Exception e){}
        }

        if(!aux && pause)
        {
            pause = false;
            play();
        }

        proximitySensorActivated = aux_proximity;
        gyroscopeSensorActivated = aux_gyroscope;

    }
    //Menú de opciones para activar/desactivar los sensores
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        MenuItem proximitySensorItem = menu.findItem(R.id.action_settings);
        MenuItem gyroscopeSensorItem = menu.findItem(R.id.action_settings2);

        switch (item.getItemId()) {

            case R.id.action_voice:

                aux_proximity = proximitySensorActivated;
                aux_gyroscope = gyroscopeSensorActivated;
                proximitySensorActivated = false;
                gyroscopeSensorActivated = false;

                if(!pause) {
                    pause();
                    pause = true;
                }

                aux_vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

                try {
                    speak("¿Qué acción deseas realizar?", "es-ES", ID_PROMPT_QUERY);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case R.id.action_list:
                Intent intent = new Intent(getBaseContext(), SongList.class);
                startActivity(intent);
                break;
            case R.id.action_settings:
                if (proximitySensorActivated) {
                    proximitySensorItem.setTitle("Activar sensor de proximidad");
                    proximitySensorActivated = false;
                }
                else {
                    proximitySensorItem.setTitle("Desactivar sensor de proximidad");
                    proximitySensorActivated = true;
                }
                break;
            case R.id.action_settings2:
                if (gyroscopeSensorActivated) {
                    gyroscopeSensorItem.setTitle("Activar Shaking");
                    gyroscopeSensorActivated = false;
                }
                else {
                    gyroscopeSensorItem.setTitle("Desactivar Shaking");
                    gyroscopeSensorActivated = true;
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    //Sobreescribimos la funcionalidad de cuando pulsamos atrás. Lo que hacemos es mover la actividad
    //a segundo plano
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);  // "Hide" your current Activity
    }

    //Si paramos la actividad (en segundo plano) y estamos en pausa, ponemos isBackgroundPaused a true
    //para no poder cambiar de canción mientras tanto.
    @Override
    protected void onStop() {
        if (pause)
            isBackgroundPaused = true;
        super.onStop();

    }

    //Función pausa
    private void pause()
    {
        player.pause();
        pauseButton.setVisibility(Button.INVISIBLE);
        playButton.setVisibility(Button.VISIBLE);
    }

    //Función play
    private void play()
    {
        if(currentSongPosition != -1)
        {
            playButton.setVisibility(Button.INVISIBLE);
            pauseButton.setVisibility(Button.VISIBLE);
            player.init(songList, songPathList, currentSongPosition);
            player.play();
            //Cambiamos la imagen para poner la portada del album
            try {
                album_art.setImageURI(getCoverArtPath(this, albums[currentSongPosition]));
            }catch (Exception e)
            {
                //Si no es posible, ponemos un icono por defecto
                album_art.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_name));
            }
        }
    }

    //Funcionalidad para cambiar de canción
    public void nextSong()
    {
        pause = false;
        currentSongPosition++;
        currentSongPosition = currentSongPosition % songList.size();
        player.nextSong();
        playButton.setVisibility(Button.INVISIBLE);
        pauseButton.setVisibility(Button.VISIBLE);
        currentSongState = 0;
        //Cambiamos la portada del disco igual que anteriormente
        try {
            album_art.setImageURI(getCoverArtPath(this, albums[currentSongPosition]));
        }catch (Exception e)
        {
            album_art.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_name));
        }

    }

    //Anterior canción
    public void previousSong()
    {
        pause=false;
        currentSongPosition--;
        if (currentSongPosition == 0) currentSongPosition = songList.size();
        player.previousSong();
        playButton.setVisibility(Button.INVISIBLE);
        pauseButton.setVisibility(Button.VISIBLE);
        currentSongState = 0;
        try {
            album_art.setImageURI(getCoverArtPath(this, albums[currentSongPosition]));
        }catch (Exception e)
        {
            album_art.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_name));
        }
    }

    @Override
    public void onPlayerSongComplete() {
        //need to be implement!
    }

    //Función que se ejecutará cuando comienza a reproducirse una canción
    @Override
    public void onPlayerSongStart(String Title, int songDuration, int songPosition) {
        songTitle.setText(Title);
        seekBar.setMax(songDuration);
        handler = new Handler();
        handler.postDelayed(timerRunnable, 1000);

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                seekBar.setProgress(player.getPosition());
                currentSongState = player.getPosition();
                handler.postDelayed(this, 1000);
            }
        };

        currentSongDuration = songDuration;
        songDuration =  songDuration/1000;
        String duration = String.format("%02d:%02d", songDuration/60, songDuration % 60);

        this.songDuration.setText(duration);
    }

    //Funcionalidad cuando volvemos a la activity
    @Override
    protected void onRestart() {
        super.onRestart();
        isBackgroundPaused = false;
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            //Recibe información desde el ListView, donde buscamos todas las canciones del móvil
            currentSongPosition = extras.getInt("SONG_INDEX");
            songTitle.setText(extras.getString("SONG_NAME"));
            songPathList = extras.getStringArrayList("SONG_PATH_LIST");
            songList = extras.getStringArrayList("SONG_LIST");
            albums = extras.getLongArray("ALBUM_ID");
            player.resetPlayer(currentSongPosition);
            currentSongState = 0;
            //Con este intent auxiliar lo que hacemos es que si bloqueamos el móvil y desbloqueamos
            //no comience a reproducirse automáticamente ya que leía el último intent recibido
            //que tendría extras
            Intent aux = new Intent();
            setIntent(aux);
            play();
        }

        if(!pause) {
            play();
        }
    }


    //Hace como intent actual el último que haya activado la actividad
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }


    //Método que busca la portada (art) de un album a partir del id de este último
    //Código cogido desde stackoverflow
    private static Uri getCoverArtPath(Context context, long androidAlbumId) {
        String path = null;
        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                MediaStore.Audio.Albums._ID + "=?",
                new String[]{Long.toString(androidAlbumId)},
                null);
        if (c != null) {
            if (c.moveToFirst()) {
                path = c.getString(0);
            }
            c.close();
        }
        return Uri.parse(path);
    }

}
