package projekt.iot.voicehome;

/**
 * Application for controlling an IoT SmartHome
 *
 * @author Tove de Verdier
 * @author Ninni Hörnaeus
 * @author Marcus Warglo
 *
 */

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.StrictMode;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;


public class MainActivity extends Activity implements OnClickListener {

    private TextView mText;
    private SpeechRecognizer sr;
    private static final String TAG = "VoiceHome"; //only for log
    private Button speakButton;
    private String temp = null;
    private boolean light1 = false;
    private boolean light2 = true;
    private CountDownTimer newtimer = null;
    private TextToSpeech hal;
    private ProgressDialog progress;

    //----------------------------------------------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speakButton = (Button) findViewById(R.id.btn_speak);
        speakButton.setOnClickListener(this);

        mText = (TextView) findViewById(R.id.textView1);
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new Lis());
        //Code to test available internet status before attempting to connect
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {

                    String ret = run("tdtool -l"); //sends tdtool -l to the run() method, which returns a string
                    useStrings(ret);                //uses return statement from run() method

            Log.d(TAG, networkInfo.toString());
        } else { //if internetconnection is unavailable -> toast meddelande with fail
            Context context = getApplicationContext();
            CharSequence text = "You don't have an internet connection";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
        hal = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    hal.setLanguage(Locale.UK);
                    hal.setSpeechRate(0.825f);
                    hal.setPitch(0.725f);
                    greeting();
                }
            }
        });

    }

    //----------------------------------------------------------------------------------------------

    public void greeting() {
        String greeting = "Welcome! \nPress the button to give voice command or ask for help on what to do.";
        hal.speak(greeting, TextToSpeech.QUEUE_FLUSH, null);
        mText.setText(greeting);

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
        } //if sound-level changed

        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "onEndofSpeech");
        }

        public void onError(int error) {
            Log.d(TAG, "error " + error);
//            mText.setText("error " + error);
            setButton();
            useVoiceInput("xx");
        }

        public void onResults(Bundle results) { //when result from voice input is found...
            String str = "";
            Log.d(TAG, "onResults " + results);

            voiceData = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); //creates arraylist of all possible results from voiceinput
            Log.d(TAG, voiceData.toString());


            for (int i = 0; i < voiceData.size(); i++) {    // builds possible words to string
                Log.d(TAG, "result " + voiceData.get(i));
                str += voiceData.get(i);
                Log.d(TAG, "result " + str);
            }
            useVoiceInput(str);
//            mText.setText("results: " + String.valueOf(voiceData.size()));
            setButton();
        }

        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults");
            setButton();
        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
        }

        /* changes so that speakbutton no longer in in active status
                and by that returns to inactive status -> color, state and so on.. */

        public void setButton() {
            speakButton.setSelected(false);
        }
    }

    //----------------------------------------------------------------------------------------------

    //lyssnaren som händer när man klickar på knappen speak.
    public void onClick(View v) {
        speakButton.setSelected(true);
        mText.setText("Listening...");
        /*
        With this we change so the button speakButton goes into selected state, and holds this state
        for as long as the process is executed by the buttonlistener. we then manually return the button to
        inactive state at the end of the method useVoiceInput(). Selected state changes the buttons
        color and text, whilst listening to voice input.
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
        String hostname = "1";
        String username = "username";
        String password = "password";         
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
            mText.setText("Connected");
        } catch (IOException e) {
//            e.printStackTrace(System.err);
//            System.exit(2);
            mText.setText("Couldn´t connect");
        }
        return total.toString();
    }

    //----------------------------------------------------------------------------------------------

    public void useStrings(String str) {  //takes the string from onCreate() fetched from run() methods SSH
        String[] lines = str.split("\\n");                      //splits string at every newline
        for (String s : lines) {
            if (s.contains("1") && s.contains("Lighting1")) {   //id given to actuator's
                light1 = !s.contains("OFF");
            }
            if (s.contains("2") && s.contains("Lighting2")) {   //id given to actuator's
                light2 = !s.contains("OFF");
            }
            if (s.contains("temperature") && s.contains("135")) {   //135==id on sensor used
                String[] strA = s.split("\\t");                     //split strings into tabs
                temp = strA[3];
            }
        }
    }

    //----------------------------------------------------------------------------------------------

    public void useVoiceInput(String str) {
        mText.setText("");
        String sayThis = "nothing";
        if (newtimer != null) //if we've checked time before and its running, cancel it
            newtimer.cancel();
        /* takes in string to compare to available commands in the system  */
        if (str.contains("light") || str.contains("lamp")) {
            if (str.contains("is")) {   //if command includes is -> check lamp status
                checkLamp(str);
            } else {                    //otherwise command set -> change light status
                setLamp(str);
            }
        } else if (str.contains("temperature") && !str.contains("outside")) {
            mText.setText(temp);
        }else if (str.contains("temperature") && str.contains("outside")) {
            new PostClass(this).execute();

        }else if ((str.contains("show") || str.contains("Tell") || str.contains("What")) && str.contains("time")) {//show time
            showTime();
        } else if ((str.contains("show") || str.contains("Tell") || str.contains("What")) && str.contains("date")) {//show date
            String currentDateTimeString = DateFormat.getDateInstance().format(new Date());
            mText.setText(currentDateTimeString);
        } else if (str.contains("help")) {//get help message
            sayThis = showHelp().toString();
        } else if(str.contains("xx")) {
            mText.setText("An error occured, try again!");
        } else {            //incase no available command
                mText.setText("I'm sorry. I'm afraid I can't doo that.");

        }
        if(sayThis.equals("nothing")){
             sayThis = mText.getText().toString();

        }
        hal.speak(sayThis, TextToSpeech.QUEUE_FLUSH, null);
    }

    //----------------------------------------------------------------------------------------------

    public String showHelp() {
        String help = "You can turn lights on or off. Show temperature, or show today's date or time.";
        mText.setText("Light 1/2 on/off\n" +
                "IS light 1/2 on/off\n" +
                "Temperature inside/outside\n" +
                "Show/tell/what time/date\n");
        return help;
    }

    //----------------------------------------------------------------------------------------------


    public void showTime() {
        String timeNow = DateFormat.getTimeInstance().format(new Date());
        mText.setText(timeNow);
        String sayThis = mText.getText().toString();
        hal.speak(sayThis, TextToSpeech.QUEUE_FLUSH, null);
        newtimer = new CountDownTimer(1000000000, 1000) {
            public void onTick(long millisUntilFinished) {
                Calendar c = Calendar.getInstance();
                mText.setText(c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND));
            }

            public void onFinish() {
            }
        };
        newtimer.start();
    }

    //----------------------------------------------------------------------------------------------

    public void checkLamp(String str) {
        /*
        takes voice input to check lamp status
        checks boolean light# if  true=on / false=off
         */
        if (str.contains("one")) {
            if (light1) {
                if (str.contains("on") || str.contains("own")) {
                    mText.setText("Yes");
                } else if (str.contains("off")) {
                    mText.setText("No");
                }
            } else {
                if (str.contains("off") || str.contains("of")) {
                    mText.setText("Yes");
                } else if (str.contains("on")) {
                    mText.setText("No");
                }
            }

        } else if (str.contains("two") || str.contains("2") || str.contains("to")) {
            if (light2) {
                if (str.contains("on") || str.contains("own")) {
                    mText.setText("Yes");
                } else if (str.contains("off")) {
                    mText.setText("No");
                }
            } else {
                if (str.contains("off") || str.contains("of")) {
                    mText.setText("Yes");
                } else if (str.contains("on")) {
                    mText.setText("No");
                }
            }
        }
    }

    //----------------------------------------------------------------------------------------------

    public void setLamp(String str) {
        /*
        takes voice input to change lamp status
         */
        if (str.contains("one") || str.contains("1")) {
            if (str.contains("off") || str.contains("of")) {
                light1 = false;
                run("tdtool --off 1");
                mText.setText("Light 1 set to off");
            } else if (str.contains("on") || str.contains("own")) {
                light1 = true;
                run("tdtool --on 1");
                mText.setText("Light 1 set to on");
            }
        } else if (str.contains("two") || str.contains("2") || str.contains("to")) {
            if (str.contains("off") || str.contains("of")) {
                light2 = false;
                run("tdtool --off 2");
                mText.setText("Light 2 set to off");
            } else if (str.contains("on") || str.contains("own")) {
                light2 = true;
                run("tdtool --on 2");
                mText.setText("Light 2 set to on");
            }
        } else if (str.contains("all") || str.contains("al") || str.contains("all")) {
            if (str.contains("off") || str.contains("of")) {
                light2 = false;
                run("tdtool --off 2");
                light1 = false;
                run("tdtool --off 1");
                mText.setText("All lights set to off");
            } else if (str.contains("on") || str.contains("own")) {
                light1 = true;
                run("tdtool --on 1");
                light2 = true;
                run("tdtool --on 2");
                mText.setText("All lights set to on");
            }
        }
    }


    //----------------------------------------------------------------------------------------------

    private class PostClass extends AsyncTask<String, Void, String> {

        /*
        this class fetches weather station information from trafikverket API. for fetching information
        a request is sent with a http-POST request in form of the XML information we want to retrieve.
        The information we send in xml form is the api-key we need to acces the API, name of the weather station
        we want to get information from and the filters we want to add -> measured air temperature.
        The XML is converted to a byte stream and send as a POST command to the URL address, and the response is read
        back as a input stream, converted and parsed to retrieve only the information of interest, Not the tags.
         */

        private String url1 = "http://api.trafikinfo.trafikverket.se/v1.1/data.xml";
        private final String KEY_TEMP = "TEMP";
        private final Context context;
        private String temperature = null;
        private String output2;

        public PostClass(Context c) {
            this.context = c;
        }


        protected void onPreExecute() {
            progress = new ProgressDialog(this.context);
            progress.setMessage("Loading");
            progress.show();
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                URL url = new URL(url1);    //sends the url to trafikverketAPI

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                //the parameters, xml, of information we want to retrieve
                String urlParameters = "<REQUEST>" +
                        "<LOGIN authenticationkey=\"658210b53e284deca40d0e21af4921bc\" />" +
                        "<QUERY objecttype=\"WeatherStation\">" +
                        "<FILTER>" +
                        "<EQ name=\"Name\" value=\"Solna\" />" +
                        "</FILTER>" +
                        "<INCLUDE>Measurement.Air.Temp</INCLUDE>" +
                        "</QUERY>" +
                        "</REQUEST>";

                connection.setRequestMethod("POST");
                connection.setRequestProperty("USER-AGENT", "Mozilla/5.0");
                connection.setRequestProperty("ACCEPT-LANGUAGE", "en-US,en;0.5");
                connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                connection.setDoOutput(true);

                DataOutputStream dStream = new DataOutputStream(connection.getOutputStream());
                dStream.writeBytes(urlParameters); //writes parameter as bytes
                dStream.flush();
                dStream.close();

                final StringBuilder output = new StringBuilder("");

                //reads response from POST request
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                String line = "";
                StringBuilder responseOutput = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    responseOutput.append(line);
                }
                br.close();

                output.append(System.getProperty("line.separator") + System.getProperty("line.separator") + System.getProperty("line.separator") + responseOutput.toString());

                output2 = parseResponse(output);

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return output2;
        }

        protected void onPostExecute(String result) {
            result = result+"\u00b0 outside";
            mText.setText(result);
            hal.speak(result, TextToSpeech.QUEUE_FLUSH, null);

            progress.dismiss();
        }

//        public static String getTemp(){
//            return output2;
//        }

        /*
        this method uses PullParser to create a parser method who takes in the StringBuilder we got from
        appending all the information retrieved from POST-request and going over all the tags in the string,
        when tag of interest,measured temp, fetches information and saves it as variable temperature
         */

        public String parseResponse(StringBuilder xml) {
            try {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser myParser = factory.newPullParser();
                String text = "";
                int eventType = myParser.getEventType();
                myParser.setInput(new StringReader(xml.toString()));

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tagName = myParser.getName();
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            break;
                        case XmlPullParser.TEXT:
                            text = myParser.getText();
                            break;
                        case XmlPullParser.END_TAG:
                            if (tagName.equalsIgnoreCase(KEY_TEMP)) {
                                temperature = text;
                                System.out.println(temperature);
                            } else {
                            }
                            break;
                    }
                    eventType = myParser.next();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return temperature;
        }

    } //end PostClass


}//end MainActivity