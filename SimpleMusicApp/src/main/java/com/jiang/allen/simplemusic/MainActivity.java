package com.jiang.allen.simplemusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity {

    final String MUSIC_PATH = "/storage/emulated/0/Music/";
    final int LOCATION_REQUEST_CODE = 2;

    final int NUM_RESULTS = 10;

    ProgressBar progressBar;
    EditText queryField;
    EditText albumField;
    Switch apiSwitch;
    TextView statusLabel;
    TextView titleLabel;
    Button searchBtn;
    Button goBtn;
    Button previousBtn;
    Button nextBtn;
    WebView webView;

    ArrayList<String[]> list = new ArrayList<>();
    String query = "";
    int currentIndex = -1;
    boolean useConverter = false;
    String[] vidIDs;

    String firstVideoName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old);

        handleSSLHandshake();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        goBtn = (Button) findViewById(R.id.goBtn);
        searchBtn = (Button) findViewById(R.id.searchBtn);
        previousBtn = (Button) findViewById(R.id.previousBtn);
        nextBtn = (Button) findViewById(R.id.nextBtn);
        apiSwitch = (Switch) findViewById(R.id.apiSwitch);
        queryField = (EditText) findViewById(R.id.queryField);
        albumField = (EditText) findViewById(R.id.albumField);
        statusLabel = (TextView) findViewById(R.id.statusLabel);
        titleLabel = (TextView) findViewById(R.id.titleLabel);
        webView = (WebView) findViewById(R.id.webView);

        apiSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    useConverter = true;
                    apiSwitch.setText("Using Converter API (More Videos but Slower)");
                    previousBtn.setEnabled(false);
                    nextBtn.setEnabled(false);
                    previousBtn.setVisibility(View.GONE);
                    nextBtn.setVisibility(View.GONE);
                    titleLabel.setVisibility(View.GONE);
                    searchBtn.setVisibility(View.GONE);
                    searchBtn.setEnabled(false);
                    webView.setVisibility(View.VISIBLE);
                    currentIndex = 0;
                }
                else {
                    useConverter = false;
                    apiSwitch.setText("Using fast API");
                    previousBtn.setEnabled(true);
                    nextBtn.setEnabled(true);
                    previousBtn.setVisibility(View.VISIBLE);
                    nextBtn.setVisibility(View.VISIBLE);
                    titleLabel.setVisibility(View.VISIBLE);
                    searchBtn.setVisibility(View.VISIBLE);
                    searchBtn.setEnabled(true);
                    webView.setVisibility(View.INVISIBLE);
                    currentIndex = -1;
                }
            }
        });
    }

    public void searchSong(android.view.View view) {
        searchSong();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void getMusic(android.view.View view) {
        askPermission(Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS, LOCATION_REQUEST_CODE);
        if(currentIndex == -1) return;
        Thread t = new Thread(new Runnable() {
            @TargetApi(Build.VERSION_CODES.O)
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void run() {
                try {
                    if(!useConverter) {
                        Log.i("Main Thread", "USING STANDARD");
                        String downloadLink = list.get(currentIndex)[0];
                        String fileName = list.get(currentIndex)[1];
                        Log.d("getMusic", "Downloading \"" + fileName + "\"...");
                        for(int i = 0; i < 1; i++) {
                            if (download(downloadLink, MUSIC_PATH + fileName)) {
                                setStatus("Done! Downloaded to: " + (MUSIC_PATH + fileName));
                                progressBar.setProgress(0);
                            }
                            else
                                i--;
                        }
                    } else {
                        Log.i("Main Thread", "USING CONVERTER");
                        searchSong();
                        setStatus("Getting download link...");
                        webView.post(new Runnable() {
                            @Override
                            public void run() {
                                String downloadWebsite = "https://youtube2mp3api.com/@api/button/mp3/" + vidIDs[0];
                                webView.setDownloadListener(new DownloadListener()
                                {
                                    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength)
                                    {
                                        //for downloading directly through download manager
                                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                                        request.allowScanningByMediaScanner();
                                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        request.setDestinationInExternalPublicDir( MUSIC_PATH, firstVideoName + ".mp3");
                                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                                        dm.enqueue(request);
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
                                setStatus("Downloaded: " + firstVideoName + ".mp3");

                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    public void searchSong() {
        Log.d("getMusic", "BUTTON PRESSED");
        currentIndex = -1;
        try {
            String query = queryField.getText().toString();
            if (query.length() == 0)
                return;

            setStatus("Searching music");
            Log.d("getMusic", "SEARCHING MUSIC");
            vidIDs = search(query + " topic");
            Log.d("getMusic", "GOT VIDIDs: " + Arrays.toString(vidIDs));
            if(!useConverter) {
                setStatus("Getting download links");
                list.clear();
                Log.i("Main thread", "USING STANDARD");
                for (int i = 0; i < NUM_RESULTS; i++) {
                    Log.d("getMusic", "GETTING DOWNLOAD LINK");
                    String[] converted = null;
                    try {
                        String YTURL = "https://www.youtube.com/watch?v=" + vidIDs[i];
                        Log.i("searchSong()", "Fetching download for: " + YTURL);
                        converted = getDownloadLink(YTURL);
                    } catch (Exception e) {
                        Log.e("searchSong()", "FAILED DOWNLOAD " + i);
                        continue;
                    }
                    if (converted.length == 0) {
                        setStatus("Error, Use converter instead");
                        return;
                    }
                    String downloadLink = converted[0];
                    String fileName = converted[1].replaceAll("[ #%&<>{}'\"\\\\*?/$!:@]+", "\\ ");
                    Log.d("getMusic", "GOT LINK: " + downloadLink + "   Title: " + fileName);
                    list.add(new String[]{downloadLink, fileName});
                }
                setStatus("Ready!");
                currentIndex = 0;
                if(list.size() == 0) {
                    setStatus("Error, Use converter instead");
                    return;
                }
                setTitle(list.get(currentIndex)[1]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean download(String URL, String fileName) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException, CannotWriteException {
        URL url = new URL(URL);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 Safari/537.36");
        double fileSize = 1;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            fileSize = http.getContentLengthLong();
        }
        if(fileSize == -1) {
            return false;
        }
        BufferedInputStream in = new BufferedInputStream(http.getInputStream());
        File out = new File(fileName+".mp3");
        FileOutputStream fos = new FileOutputStream(out);
        BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
        byte[] buffer = new byte[1024];
        double downloaded = 0;
        int read = 0;
        double percentDownloaded = 0;

        while((read = in.read(buffer, 0, 1024)) >= 0) {
            bout.write(buffer, 0, read);
            downloaded += read;
            percentDownloaded = (downloaded*100/fileSize);
            progressBar.setProgress((int) percentDownloaded);
        }
        AudioFile f = AudioFileIO.read(out);
        Tag t = f.getTag();
        String album = albumField.getText().toString();
        if(!album.isEmpty()) t.setField(FieldKey.ALBUM, album.substring(0, 1).toUpperCase() + album.substring(1).toLowerCase());
        try {
            f.commit();
            bout.close();
            in.close();
            fos.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.O)
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void downloadConverter(String URL) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException, CannotWriteException {
        URL url = new URL(URL);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 Safari/537.36");
        String fileName = (http.getHeaderField("Content-Disposition"));
        if(fileName == null) {
            setStatus("Song is converting, please try again in a few minutes");
        }
        fileName = fileName.trim()
                .split("=")[1]
                .replaceAll("\"","")
                .replaceAll("[ #%&<>{}'\"\\\\*?/$!:@]+", "\\ ");
        double fileSize = http.getContentLengthLong();
        BufferedInputStream in = new BufferedInputStream(http.getInputStream());
        File out = new File(MUSIC_PATH+fileName);
        FileOutputStream fos = new FileOutputStream(out);
        BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
        byte[] buffer = new byte[1024];
        double downloaded = 0;
        int read = 0;
        double percentDownloaded = 0;
        while((read = in.read(buffer, 0, 1024)) >= 0) {
            bout.write(buffer, 0, read);
            downloaded += read;
            percentDownloaded = (downloaded*100/fileSize);
            progressBar.setProgress((int) percentDownloaded);
        }
        AudioFile f = AudioFileIO.read(out);
        Tag t = f.getTag();
        String album = albumField.getText().toString();
        t.setField(FieldKey.ALBUM, album.substring(0, 1).toUpperCase() + album.substring(1).toLowerCase());
        f.commit();
        bout.close();
        in.close();
        System.out.println("Done!");
    }

    public String[] getDownloadLink(String YTURL) throws IOException, JSONException
    {
        URL url = new URL("https://www.convertmp3.io/fetch/?format=JSON&video=" + YTURL);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 Safari/537.36");
        BufferedReader in = new BufferedReader(
                new InputStreamReader(http.getInputStream())
        );
        String inputLine;
        String inputAll = "";
        while((inputLine = in.readLine()) != null) {
            inputAll += inputLine;
        }
        in.close();

        String downloadLink = "";
        try
        {
            downloadLink = new JSONObject(inputAll).getString("link");
        } catch (JSONException e) {
            System.out.println("Sorry, but this title is not supported. Please try another.");
        }
        String fileName = new JSONObject(inputAll).getString("title");
        return new String[]{downloadLink, fileName};
    }

    public static String getDownloadLinkConverter(String YTURL) throws IOException
    {
        URL url = new URL("https://www.convertmp3.io/widget/button/?video=https://www.youtube.com/watch?v=" +
                YTURL + "&format=mp3&text=ffffff&color=3880f3");
        System.out.println(url);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setReadTimeout(3000);
        http.setInstanceFollowRedirects(true);
        http.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 Safari/537.36");
        BufferedReader in = new BufferedReader(
                new InputStreamReader(http.getInputStream())
        );
        String inputLine;
        String inputAll = "";
        while((inputLine = in.readLine()) != null) {
            inputAll += inputLine;
        }
        in.close();

        Document doc = Jsoup.parse(inputAll);
        String downloadLink = "https://www.convertmp3.io" + doc.body().getElementById("downloadButton").attr("href");
        System.out.println(downloadLink);
        return downloadLink;
    }

    public String[] search(String query) throws IOException, JSONException
    {
        query = query.replace(" ", "%20");
        String URL = "https://content.googleapis.com/youtube/v3/search?type=video&q="+query+"&maxResults=" + NUM_RESULTS +
                "&part=snippet&key=AIzaSyA8TkKNVRQeiGBqJQK-zh-kRAGkdFKHTNQ";

        URL url = new URL(URL);
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

        String[] results = new String[NUM_RESULTS];
        try {
            for (int i = 0; i < NUM_RESULTS; i++) {
                JSONObject JSONObj2 = JSONObj.getJSONArray("items").getJSONObject(i);
                String videoID = JSONObj2.getJSONObject("id").getString("videoId");
                if(i == 0) firstVideoName = JSONObj2.getJSONObject("snippet").getString("title");
                results[i] = videoID;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            setStatus("No results found!");
        }
        return results;
    }

    private void setStatus(final String value){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(value);
            }
        });
    }

    private void setTitle(final String value){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                titleLabel.setText(value);
            }
        });
    }

    public void nextButtonAction(android.view.View view) {
        if(list.size() == 0) return;
        currentIndex = Math.min(currentIndex + 1, list.size()-1);
        setTitle(list.get(currentIndex)[1]);
    }
    public void previousButtonAction(android.view.View view) {
        if(list.size() == 0) return;
        currentIndex = Math.max(currentIndex - 1, 0);
        setTitle(list.get(currentIndex)[1]);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void askPermission(String permission, int requestCode) {
        requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 200);
    }

    @SuppressLint("TrulyRandom")
    public static void handleSSLHandshake() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String arg0, SSLSession arg1) {
                    return arg1.isValid();
                }
            });
        } catch (Exception ignored) {
        }
    }

}