package com.jiang.allen.simplemusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainPage extends AppCompatActivity {

    final int NUM_RESULTS = 10;
    final String MUSIC_PATH = "/storage/emulated/0/Music/";
    final int LOCATION_REQUEST_CODE = 2;

    ArrayList<SearchResult> resultsList = new ArrayList<>();

    EditText queryField;
    Button searchBtn;
    Button getDownloadBtn;
    WebView webView;
    TextView statusLabel;
    Spinner songSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);

//        create GUI objects
        queryField = (EditText) findViewById(R.id.queryField);
        searchBtn = (Button) findViewById(R.id.searchBtn);
        getDownloadBtn = (Button) findViewById(R.id.getDownloadBtn);
        webView = (WebView) findViewById(R.id.webView);
        statusLabel = (TextView) findViewById(R.id.statusLabel);
        songSpinner = (Spinner) findViewById(R.id.songSpinner);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void getMusic(android.view.View view) {
//        ask for file access permissions
        askPermission(Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS, LOCATION_REQUEST_CODE);

        int currentIndex = songSpinner.

        Thread t = new Thread(new Runnable() {
            @TargetApi(Build.VERSION_CODES.O)
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void run() {
                try {
                    search();
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

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    public void search() throws IOException, JSONException
    {
        String query = queryField.getText().toString();

        query = query.replace(" ", "%20");
        String URL = "https://content.googleapis.com/youtube/v3/search?type=video&q="+query+"&maxResults=" + NUM_RESULTS +
                "&part=snippet&key=AIzaSyA8TkKNVRQeiGBqJQK-zh-kRAGkdFKHTNQ";

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

        try {
            for (int i = 0; i < NUM_RESULTS; i++) {
                JSONObject JSONObj2 = JSONObj.getJSONArray("items").getJSONObject(i);
                String videoID = JSONObj2.getJSONObject("id").getString("videoId");
                String title = JSONObj2.getJSONObject("snippet").getString("title");
                SearchResult result = new SearchResult(videoID, title);
                resultsList.add(result);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            setStatus("No results found!");
        }
    }

    public void searchBtn_onClick(View v) {

    }

    public void getDownloadBtn_onClick(View v) {

    }

    public void setStatus(String s) {

    }

    public void print(String s) {
        Log.i("Printout", s);
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

class SearchResult {

    static String vidID;
    static String title;

    public SearchResult(String vidID, String title) {
        this.vidID = vidID;
        this.title = title;
    }

}