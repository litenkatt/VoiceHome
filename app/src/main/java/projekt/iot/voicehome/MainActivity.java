package projekt.iot.voicehome;

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
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;



public class MainActivity extends Activity implements OnClickListener  {

    //testar att få igång github för mig /M

    private TextView mText;
    private TextView resText;
    private TextView answerText;
    private SpeechRecognizer sr;
    private static final String TAG = "SpeechStuff"; //bara för logen

    private Switch btnToggleLight1 = null;
    private Switch btnToggleLight2 = null;

    private String temp = null;

    private boolean light1 = false;
    private boolean light2 = true;
    private ArrayList <String> voiceData = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button speakButton = (Button) findViewById(R.id.btn_speak);
        speakButton.setOnClickListener(this);

        mText = (TextView) findViewById(R.id.textView1);
        resText = (TextView) findViewById(R.id.textView2);
        answerText = (TextView) findViewById(R.id.textView3);

        mText.setText("hejhej");



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
        Log.d(TAG,  networkInfo.toString());

    } else {
        resText.setText("lol internet failz");

    }
//}catch (ConnectException e) {
//
//}



    }

    //----------------------------------------------------------------------------------------------

    class Lis implements RecognitionListener
    {
        public void onReadyForSpeech(Bundle params)
        {
            Log.d(TAG, "onReadyForSpeech");
        }
        public void onBeginningOfSpeech()
        {
            Log.d(TAG, "onBeginningOfSpeech");
        }
        public void onRmsChanged(float rmsdB)
        {
            Log.d(TAG, "onRmsChanged");
        } //ljudnivån ändras
        public void onBufferReceived(byte[] buffer)
        {
            Log.d(TAG, "onBufferReceived");
        }
        public void onEndOfSpeech()
        {
            Log.d(TAG, "onEndofSpeech");
        }
        public void onError(int error)
        {
            Log.d(TAG,  "error " +  error);
            mText.setText("error " + error);
        }
        public void onResults(Bundle results)
        {
            String str = "";
            Log.d(TAG, "onResults " + results);

            voiceData = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); //skapar en arraylist med möjliga resultat där det första är mest troligt
            Log.d(TAG,  voiceData.toString());



            resText.setText(voiceData.toString());

//            Bygger ihop alla möjliga ord till en string
            for (int i = 0; i < voiceData.size(); i++)
            {
                Log.d(TAG, "result " + voiceData.get(i));

                str += voiceData.get(i);
                Log.d(TAG, "result " + str);


            }
            useVoiceInput(str);


            mText.setText("results: "+String.valueOf(voiceData.size()));

        }
        public void onPartialResults(Bundle partialResults)
        {
            Log.d(TAG, "onPartialResults");
        }
        public void onEvent(int eventType, Bundle params)
        {
            Log.d(TAG, "onEvent " + eventType);
        }
    }

    //----------------------------------------------------------------------------------------------

    public void onClick(View v) {
        mText.setText("Speak now!");

        if (v.getId() == R.id.btn_speak)
        {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"voice.recognition.test");

            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            sr.startListening(intent);
        }
    }

    //----------------------------------------------------------------------------------------------

    public String run(String command) { //command = the SSH value we want to send
        String hostname = "192.168.0.16";    //TODO hard-code our pi's ip here..
        String username = "pi";                 //TODO hard-code our pi's username here..
        String password = "raspberryiot11";           //TODO hard-code our pi's password, here..
        StringBuilder total = new StringBuilder();
        try {
            //code to avoid Network error massage
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
            //
            Connection conn = new Connection(hostname); //init tcp/ip connection to SSH
            conn.connect(); //start connection to hostname
            Boolean isAuthenticated = conn.authenticateWithPassword(username, password);
            if (isAuthenticated == false)
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


            if (str.contains("light")) {
                if (str.contains("is")) {
                    if (str.contains("one")) {
                        if (light1) {
                            if (str.contains("on")|| str.contains("own")) {
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
                            if (str.contains("on")|| str.contains("own")) {
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
                } else {
                    if (str.contains("one") || str.contains("1")) {
                        if (str.contains("off") || str.contains("of")){
                            light1 = false;
                            run("tdtool --off 1");
                            answerText.setText("Light 1 set to off");
                        } else if (str.contains("on" )|| str.contains("own")) {
                            light1 = true;
                            run("tdtool --on 1");
                            answerText.setText("Light 1 set to on");
                        }
                    } else if (str.contains("two")|| str.contains("2") || str.contains("to")) {
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

        } else if(str.contains("temperature")) {
                answerText.setText(temp);
            }else{
                answerText.setText("I couldn´t understand you");

            }

}}