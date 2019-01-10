package com.jiang.allen.simplemusic;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.*;

import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.StandardArtwork;
import org.json.*;

import java.io.*;
import java.net.*;
import java.util.*;


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
    public void downloadSequence() {
        //  ask for file access permissions
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},0);


        //  prevent crash if download without searching
        if(resultsList.isEmpty()) return;

        //  get index of spinner and the information
        final int spinnerIndex = songSpinner.getSelectedItemPosition();
        final String vidID = resultsList.get(spinnerIndex).vidID;
        final String title = resultsList.get(spinnerIndex).title;

        //  delete the file if it already exists
        File f1 = new File("//sdcard//Music//" + title + ".mp3");
        File f2 = new File("//sdcard//Downloads//" + title + ".mp3");
        if(f1.exists()) {
            f1.delete();
        }
        if(f2.exists()) {
            f2.delete();
        }

        try {
            //  update search results list
            setStatus("Getting download link...");
            //  add webview downloader
            webView.post(new Runnable() {
                @Override
                public void run() {
                    String downloadWebsite = "https://youtube2mp3api.com/@api/button/mp3/" + vidID;
                    webView.setDownloadListener(new DownloadListener()
                    {
                        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength)
                        {
                            //  for downloading directly through download manager
                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                            request.allowScanningByMediaScanner();
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC,
                                    title + ".mp3");
                            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                            try {
                                dm.enqueue(request);
                            }
                            catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    webView.getSettings().setJavaScriptEnabled(true);
                    webView.setWebChromeClient(new WebChromeClient());
                    webView.setWebViewClient(new WebViewClient() {
                        public boolean shouldOverrideUrlLoading (WebView view, String url){
                            //True if the host application wants to leave the current WebView and handle the url itself, otherwise return false.
                            return true;
                        }
                    });
                    webView.loadUrl(downloadWebsite);
                    setStatus("Got Download: " + title + ".mp3");

                    //  add a background process to configure metadata whenever clicked
                    webView.setOnTouchListener(new View.OnTouchListener()
                    {
                        public boolean onTouch(View v, MotionEvent event) {
                            //  if touched
                            if(event.getAction() == MotionEvent.ACTION_UP) {
                                //  create background process:
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            //  wait 20 seconds for download finish
                                            Thread.sleep(20000);

                                            //  constantly check to see if file is finished
                                            // downloading every 2 seconds
                                            File mp3 = new File
                                                    ("//sdcard//Music//" + title + ".mp3");
//                                            while(!isCompletelyWritten(mp3)) {
//                                                Thread.sleep(200);
//                                            }

                                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(mp3)));

                                            File f2 = new File("//sdcard//Downloads//" + title + ".mp3");
                                            if (f2.exists()) f2.delete();

                                            print("Starting metadata write");

                                            //  write metadata to file
//                                            writeMetadata("//sdcard//Music//" + title + ".mp3");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();

                            }
                            return false;
                        }
                    });
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeMetadata(String path) throws IOException, InvalidAudioFrameException, TagException, ReadOnlyFileException {
        //  get vidID and url for thumbnail
        int spinnerIndex = songSpinner.getSelectedItemPosition();
        String vidTitle = resultsList.get(spinnerIndex).title;
//        String vidID = resultsList.get(spinnerIndex).vidID;
//        String thumbnailURL = "https://img.youtube.com/vi/"+ vidID +"/hqdefault.jpg";
//
//        InputStream is = null;
//        try {
//            is = (InputStream) new URL(thumbnailURL).getContent();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
//        int nRead;
//        byte[] data = new byte[20000000];
//        while ((nRead = is.read(data, 0, data.length)) != -1) {
//            buffer.write(data, 0, nRead);
//        }
//
//        Artwork artwork = new StandardArtwork();
//        artwork.setBinaryData(buffer.toByteArray());
//        artwork.setImageFromData();
//        print(artwork.getImageUrl());

        MP3File mp3 = new MP3File(path);

        //  create tag
        Tag tag = mp3.getTag();
        mp3.setTag(tag);

        //  edit metadata
        tag.setField(FieldKey.TITLE, vidTitle);
        tag.setField(FieldKey.ALBUM, "");
//        tag.addField(artwork);

        mp3.save();

        print("MP3: Done writing metadata!");
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void getDownloadBtn_onClick(View v) {
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