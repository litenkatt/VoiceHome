package projekt.iot.voicehome;
/**
 * App for controlling an IoT SmartHome
 *
 * @author Tove de Verdier
 * @author Ninni Hörnaeus
 * @author Marcus Warglo
 */

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.StrictMode;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

public class MainActivity extends Activity implements OnClickListener {

    //om error7 går den inte tillbaka till defaultmode.

    private TextView mText;
    private TextView resText;
    private TextView answerText;
    private SpeechRecognizer sr;
    private static final String TAG = "SpeechStuff"; //bara för loggen
    private Button speakButton;
    private String temp = null;
    private boolean light1 = false;
    private boolean light2 = true;
   // private ArrayList<String> voiceData = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speakButton = (Button) findViewById(R.id.btn_speak);
        speakButton.setOnClickListener(this);

        mText = (TextView) findViewById(R.id.textView1);
        resText = (TextView) findViewById(R.id.textView2);
        answerText = (TextView) findViewById(R.id.textView3);
        mText.setText("Welcome");

        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new Lis());

        //try{
        //Kod som ska kolla om det finns internet innan den försöker ansluta
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            String ret = run("tdtool -l"); //sends tdtool -l to the run() method, which return a string
//        String ret = "hello";
            useStrings(ret);
            Log.d(TAG, networkInfo.toString());
        } else {
            resText.setText("lol internet fail");
        }
//}catch (ConnectException e) {
//
//}
    }

    //----------------------------------------------------------------------------------------------

    class Lis implements RecognitionListener {
        private ArrayList<String> voiceData = new ArrayList<>();
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
        }

        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
        }

        public void onRmsChanged(float rmsdB) {
            Log.d(TAG, "onRmsChanged");
        } //ljudnivån ändras

        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "onEndofSpeech");
        }

        public void onError(int error) {
            Log.d(TAG, "error " + error);
            mText.setText("error " + error);
            setButton();
        }

        public void onResults(Bundle results) {
            String str = "";
            Log.d(TAG, "onResults " + results);

            voiceData = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); //skapar en arraylist med möjliga resultat där det första är mest troligt
            Log.d(TAG, voiceData.toString());

            resText.setText(voiceData.toString());

            for (int i = 0; i < voiceData.size(); i++) {    // Bygger ihop alla möjliga ord till en string
                Log.d(TAG, "result " + voiceData.get(i));
                str += voiceData.get(i);
                Log.d(TAG, "result " + str);
            }
            useVoiceInput(str);
            mText.setText("results: " + String.valueOf(voiceData.size()));
            setButton();


        }

        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults");
            setButton();
        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
        }

        public void setButton() {
            /* ändrar till att speak-knappen inte längre är i valt läge
                och genom det ändrar tillbaka till inaktivt stadie, färg, osv */
            speakButton.setSelected(false);
            speakButton.setText("Speak");
        }
    }

    //----------------------------------------------------------------------------------------------

    //lyssnaren som händer när man klickar på knappen speak.
    public void onClick(View v) {
        mText.setText("Speak now..");
        speakButton.setSelected(true);
        speakButton.setText("Listening...");
        answerText.setText(" ");
        /*
        här över ändrar vi så knappen speakButton hamnar i läge(state) selected, och håller det
        läget under hela processen som utförs av knappen. knappen sätts manuellt tillbaka som
        inaktiv i slutet av metoden useVoiceInput(). Selected läge ändrar knappens färg och text
         */

        if (v.getId() == R.id.btn_speak) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");

            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            sr.startListening(intent);
        }
    }

    //----------------------------------------------------------------------------------------------

    public String run(String command) { //command = the SSH value we want to send
        String hostname = "213.89.203.106";                       //TODO hard-code our pi's ip here..
        String username = "pi";                     //TODO hard-code our pi's username here..
        String password = "raspberryiot11";           //TODO hard-code our pi's password, here..
        StringBuilder total = new StringBuilder();
        try {
            //code to avoid Network error massage
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
            Connection conn = new Connection(hostname); //init tcp/ip connection to SSH
            conn.connect(); //start connection to hostname
            Boolean isAuthenticated = conn.authenticateWithPassword(username, password);
            if (!isAuthenticated)
                throw new IOException("Authentication failed.");
            Session sess = conn.openSession();
            sess.execCommand(command);
            InputStream stdout = new StreamGobbler(sess.getStdout());
            BufferedReader br = new BufferedReader(new
                    InputStreamReader(stdout)); //reads text
            while (true) {
                String line = br.readLine(); //reads line
                if (line == null)
                    break;
                total.append(line).append('\n');
            }
            /* Shows exit status, if available (otherwise "null") */
            System.out.println("Exitcode: " + sess.getExitStatus());
            sess.close();
            conn.close();
            answerText.setText("Connected");
        } catch (IOException e) {
//            e.printStackTrace(System.err);
//            System.exit(2);
            answerText.setText("Couldn´t connect");
        }
        return total.toString();
    }

    //----------------------------------------------------------------------------------------------

    public void useStrings(String str) {  //takes the string from onCreate() fetched from run() methods SSH
        String[] lines = str.split("\\n");                      //splits string at every newline
        for (String s : lines) {
            if (s.contains("1") && s.contains("Lighting1")) {   //id som man gett actuator's
                if (s.contains("OFF")) {
                    light1 = false;
                } else {
                    light1 = true;
                }
            }
            if (s.contains("2") && s.contains("Lighting2")) {   //id som man gett actuator's
                if (s.contains("OFF")) {
                    light2 = false;
                } else {
                    light2 = true;
                }
            }
            if (s.contains("temperature") && s.contains("135")) {   //135==id på sensors
                String[] strA = s.split("\\t");                     //split strings into tabs
                temp = strA[3];
            }
        }
    }

    //----------------------------------------------------------------------------------------------

    public void useVoiceInput(String str) {
        /*
        tar string baserat på röstinläsning för att jämnföra mot tillgängliga kommandon..
         */

        if (str.contains("light") || str.contains("lamp")) {
            if (str.contains("is")) {   //om kommandot innehåller is -> fråga
                checkLamp(str);
            } else {                    //annars är kommandor utförande -> ändrar lampans läge
                setLamp(str);
            }

        } else if (str.contains("temperature")) {   //innehåller det temperatur så skrivut temperatur
            answerText.setText(temp);
            //eventuellt en till ifsats för att fråga om / sätta önskad temperatur


        } else {            //ifall inget passar skrivs felmeddelande ut
            answerText.setText("I couldn´t understand you");
        }
    }


    public void checkLamp(String str) {
        /*
        takes voice input to check lamp status
        kollar boolean light# om den är true=på / false=av
         */
        if (str.contains("one")) {
            if (light1) {
                if (str.contains("on") || str.contains("own")) {
                    answerText.setText("Yes");
                } else if (str.contains("off")) {
                    answerText.setText("No");
                }
            } else {
                if (str.contains("off") || str.contains("of")) {
                    answerText.setText("Yes");
                } else if (str.contains("on")) {
                    answerText.setText("No");
                }
            }

        } else if (str.contains("two") || str.contains("2") || str.contains("to")) {
            if (light2) {
                if (str.contains("on") || str.contains("own")) {
                    answerText.setText("Yes");
                } else if (str.contains("off")) {
                    answerText.setText("No");
                }
            } else {
                if (str.contains("off") || str.contains("of")) {
                    answerText.setText("Yes");
                } else if (str.contains("on")) {
                    answerText.setText("No");
                }
            }
        }
    }

    public void setLamp(String str) {
        /*
        takes voice input to change lamp status
         */
        if (str.contains("one") || str.contains("1")) {
            if (str.contains("off") || str.contains("of")) {
                light1 = false;
                run("tdtool --off 1");
                answerText.setText("Light 1 set to off");
            } else if (str.contains("on") || str.contains("own")) {
                light1 = true;
                run("tdtool --on 1");
                answerText.setText("Light 1 set to on");
            }
        } else if (str.contains("two") || str.contains("2") || str.contains("to")) {
            if (str.contains("off") || str.contains("of")) {
                light2 = false;
                run("tdtool --off 2");
                answerText.setText("Light 2 set to off");
            } else if (str.contains("on") || str.contains("own")) {
                light2 = true;
                run("tdtool --on 2");
                answerText.setText("Light 2 set to on");
            }
        }
    }


}