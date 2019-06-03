package com.jiang.allen.simplemusic;

import android.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.*;
import android.widget.*;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.*;

import org.json.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;


public class MainPage extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    final int NUM_RESULTS = 5;
    final String MUSIC_PATH = "/storage/emulated/0/Music/";
    final int LOCATION_REQUEST_CODE = 2;

    ArrayList<SearchResult> resultsList = new ArrayList<>();  // SearchResult: (vidID, title)

    //  UI Objects
    EditText queryField;
    Button searchBtn;
    Button getDownloadBtn;
    WebView webView;
    TextView statusLabel;
    Spinner songSpinner;
    CheckBox useTopicCheckBox;
    ImageView previewImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //  create GUI objects
        queryField = (EditText) findViewById(R.id.queryField);
        searchBtn = (Button) findViewById(R.id.searchBtn);
        getDownloadBtn = (Button) findViewById(R.id.getDownloadBtn);
        webView = (WebView) findViewById(R.id.webView);
        statusLabel = (TextView) findViewById(R.id.statusLabel);
        songSpinner = (Spinner) findViewById(R.id.songSpinner);
        useTopicCheckBox = (CheckBox) findViewById(R.id.useTopicCheckBox);
        previewImage = (ImageView) findViewById(R.id.previewImage);

        // Spinner click listener
        songSpinner.setOnItemSelectedListener(this);
    }

    //  searches query field text, uses YT API to fill in search result list
    public void search() throws IOException, JSONException {
        String topic = "";
        if(useTopicCheckBox.isChecked()) topic = " topic";
        String query = queryField.getText().toString() + topic;
        if(query.length() == 0) return;

        query = query.replace(" ", "%20");  // replace spaces with %20
        //  YT API request URL
        String URL = "https://content.googleapis.com/youtube/v3/search?type=video&q="+query+"&maxResults=" + NUM_RESULTS +
                "&part=snippet&key=AIzaSyA8TkKNVRQeiGBqJQK-zh-kRAGkdFKHTNQ";

        //  read in JSON file
        java.net.URL url = new URL(URL);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(url.openStream())
        );

        String inputLine;
        String inputAll = "";
        while((inputLine = in.readLine()) != null) {
            inputAll += inputLine;
        }
        in.close();

        JSONObject JSONObj = new JSONObject(inputAll);

        resultsList.clear();

        int numResults = JSONObj.getJSONArray("items").length();
        if(numResults == 0) {
            setStatus("No results found!");
            return;
        }

        for (int i = 0; i < numResults; i++) {
            JSONObject JSONObj2 = JSONObj.getJSONArray("items").getJSONObject(i);
            String videoID = JSONObj2.getJSONObject("id").getString("videoId");
            String title = JSONObj2.getJSONObject("snippet").getString("title");
            SearchResult result = new SearchResult(videoID, title);
            resultsList.add(result);
        }
    }

    //  adds the titles to the spinner
    public void updateSpinner() {
        List<String> titles = new ArrayList<>();
        for(int i = 0; i < resultsList.size(); i++) {
            String title = resultsList.get(i).title;
            titles.add(title);
        }
        ArrayAdapter<String> aa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, titles);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        songSpinner.setAdapter(aa);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    //  puts the selected video download link in the webview
    public void downloadSequence() throws IOException, InterruptedException {
        //  ask for file access permissions

        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},0);


        //  prevent crash if download without searching
        if(resultsList.isEmpty()) return;

        //  get index of spinner and the information
        final int spinnerIndex = songSpinner.getSelectedItemPosition();
        final String vidID = resultsList.get(spinnerIndex).vidID;
        final String title = resultsList.get(spinnerIndex).title;

        setStatus("Starting Connection...");

        Thread t = new Thread() {
            public void run() {
                try {
                    try (final WebClient webClient = new WebClient(BrowserVersion.CHROME))
                    {
                        webClient.getOptions().setThrowExceptionOnScriptError(false);
                        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
                        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

                        // Get the first page
                        final HtmlPage page1 = webClient.getPage("https://www.bigconverter.com/");

                        final HtmlForm form = page1.getFormByName("");

                        final HtmlButton button = form.getButtonByName("submitForm");
                        final HtmlTextInput textField = form.getInputByName("videoURL");

                        textField.type("https://www.youtube.com/watch?v=" + vidID);

//            // Now submit the form by clicking the button and get back the second page.
                        HtmlPage page2 = button.click();

                        int fails = 0;
                        while(page2.getByXPath("//div[@id='conversionSuccess']").size() == 0) {
                            page2 = button.click();
                            fails++;
                            if(fails == 5) setStatus("Connection failed");
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setStatus("Converting ...");
                            }
                        });

                        while(page2.getByXPath("//a[@class='btn btn-success download-buttons']").size() == 0) {
                            Thread.sleep(100);
                        }

                        HtmlAnchor download = ((HtmlAnchor)page2.getByXPath("//a[@class='btn btn-success download-buttons']").get(0));
                        String originalURL = download.getHrefAttribute();

                        String urlChanged = originalURL.substring(0, 4) + "s" + originalURL.substring(4);

                        URL url = new URL(urlChanged);
                        URLConnection con = url.openConnection();
                        String fieldValue = con.getHeaderField("Content-Disposition");
                        String filename = fieldValue.substring(fieldValue.indexOf("filename=\"") + 10, fieldValue.length() - 1);
                        filename = filename.replaceAll("_", " ");

                        final String filenamefinal = filename;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setStatus("Downloading " + filenamefinal + "...");
                            }
                        });

                        saveUrl(url.toString(), "/sdcard/music/"+filename);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setStatus("Download Complete: " + filenamefinal);
                            }
                        });
                    }
                } catch(InterruptedException | IOException v) {
                    System.out.println(v);
                }
            }
        };

        t.start();
    }

    public void saveUrl(final String urlString, final String filename)
            throws MalformedURLException, IOException {
        BufferedInputStream in = null;
        FileOutputStream fout = null;
        try {
            in = new BufferedInputStream(new URL(urlString).openStream());
            fout = new FileOutputStream(filename);

            final byte data[] = new byte[102400];
            int count;
            while ((count = in.read(data, 0, 1024)) != -1) {
                fout.write(data, 0, count);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (fout != null) {
                fout.close();
            }
        }
    }

    public void previewThumbnail() {
        //  prevent crash from empty result list
        if(resultsList.isEmpty()) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                //  get vidID and url for thumbnail
                int spinnerIndex = songSpinner.getSelectedItemPosition();
                String vidID = resultsList.get(spinnerIndex).vidID;
                String url = "https://img.youtube.com/vi/"+ vidID +"/hqdefault.jpg";

                InputStream is = null;
                try {
                    is = (InputStream) new URL(url).getContent();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                final Drawable d = Drawable.createFromStream(is, "thumbnail");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        previewImage.setImageDrawable(d);
                    }
                });
            }
        }).start();
    }

    public void searchBtn_onClick(View v) {
        setStatus("Searching...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    search();
                    //  the spinner needs to run after search() but also on the main or UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateSpinner();
                            previewThumbnail();
                        }
                    });
                    setStatus("");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void getDownloadBtn_onClick(View v) throws IOException, InterruptedException {
        downloadSequence();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        previewThumbnail();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private boolean isCompletelyWritten(File file) {
        RandomAccessFile stream = null;
        try {
            stream = new RandomAccessFile(file, "rw");
            return true;
        } catch (Exception e) {
            print("Skipping file " + file.getName() + " for this iteration due it's not completely written");
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    print("Exception during closing file " + file.getName());
                }
            }
        }
        return false;
    }

    public void setStatus(String s) {
        statusLabel.setText(s);
    }

    public void print(String s) {
        Log.i("Printout", s);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void askPermission(String permission, int requestCode) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
    }

}

class SearchResult {

    String vidID;
    String title;

    public SearchResult(String vidID, String title) {
        this.vidID = vidID;
        this.title = title;
    }

}