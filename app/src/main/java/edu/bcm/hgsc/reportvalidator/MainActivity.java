package edu.bcm.hgsc.reportvalidator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;

import edu.bcm.hgsc.jsonobj.Ledger;

import static android.graphics.Color.GRAY;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.RED;

/**
 * Created by sl9 on 4/2/18.
 */

public class MainActivity extends AppCompatActivity {
    private static final String LEDGER_FILE_NAME = "eValidate_ledger.json";
    private static final String META_DATA_FILE_NAME = "metadata.json";
    private static final String TOKEN = "c2o6cXIJ3Bcnlz6ah7GPlBmIhoT0VWLB";
    private static final String URL_FIND_DATA_OBJECT = "https://api.dnanexus.com/system/findDataObjects";
    private static final String LEDGER_FILE_PROJECT_ID = "project-FBB2bF00JgvyKqZ84V2v5qQg";
    private static final String LEDGER_FILE_PATH = "/tools";
    private static final String TAG = MainActivity.class.getName();
    private static final String REPORT_NOT_EXIST = "ReportNotExist";
    private static final String REPORT_NOT_LASTEST = "ReportNotLastest";
    private static final String REPORT_VALID = "ReportValid";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RETURN_RESULT_KEY_NAME = "ScanData";
    private static final int STATUS_NOT_CONNECTED_LEDGER_NOT_EXISTS = 0;
    private static final int STATUS_CONNECTED_LEDGER_NOT_EXISTS = 1;
    private static final int STATUS_CONNECTED_LEDGER_EXISTS = 2;
    private static final int STATUS_NOT_CONNECTED_LEDGER_EXISTS = 3;
    public static final int REQUEST_CODE = 100;
    public static final int PERMISSION_REQUEST = 200;



    String scannedData;
    int appStatus;
    Button scanBtn;
    Button refreshBtn;
    ProgressBar progressBar;
    TextView titleTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.activity_barcode_scanner);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.my_custom_title);
        super.onCreate(savedInstanceState);
        final Activity activity =this;

        setContentView(R.layout.activity_barcode_scanner);
        progressBar = (ProgressBar) findViewById(R.id.progressbar);
        titleTextView = (TextView) findViewById(R.id.scan_txtview_title);

        // Scan Button to scan QR code
        scanBtn = (Button)findViewById(R.id.scan_btn);
        scanBtn.setVisibility(View.VISIBLE);
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                startActivityForResult(intent, REQUEST_CODE);
            }
        });

        // Refresh Button to get current Internet connectivity and Leger file status
        refreshBtn = (Button) findViewById(R.id.refresh_btn);
        refreshBtn.setVisibility(View.VISIBLE);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                titleTextView.setText("DOWNLOADING LATEST DATA FILE...");
                initialApps();
            }
        });

        initialApps();
        titleTextView.setText("INITIALIZING...");
    }


    /**
     * Initial apps
     * Check Internect connection, Ledger file existance,
     * Download ledger file if needed
     */
    private void initialApps() {
        Log.d(TAG, "initialApps - initialApps started");
        progressBar.setVisibility(View.VISIBLE);
        boolean isConnected = isConnected();
        boolean isLegerExist = isLegerExist();
        String outputMsg;

        int currentApiVersion = Build.VERSION.SDK_INT;
        if(currentApiVersion <= 22 ) {
            outputMsg = "Android version " + currentApiVersion + " is not supported! \n";
            setResultMessage(outputMsg, true);
            refreshBtn.setVisibility(View.INVISIBLE);
            scanBtn.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.INVISIBLE);
            return;
        }

        if(!isConnected && !isLegerExist) {
            appStatus = STATUS_NOT_CONNECTED_LEDGER_NOT_EXISTS;
        }
        else if (!isConnected && isLegerExist){
            appStatus = STATUS_NOT_CONNECTED_LEDGER_EXISTS;
        }
        else if(isConnected && !isLegerExist) {
            appStatus = STATUS_CONNECTED_LEDGER_NOT_EXISTS;
        }
        else if(isConnected && isLegerExist) {
            appStatus = STATUS_CONNECTED_LEDGER_EXISTS;
        }

        // No Internect connection and No previouse Leger data exists
        // User has to enable internect connection to download initial Leger file
        if(appStatus == STATUS_NOT_CONNECTED_LEDGER_NOT_EXISTS) {
            outputMsg = "No Internet Connection! \n" +
                    "Please enable internet connection and click refresh again!";
            setResultMessage(outputMsg, true);
            refreshBtn.setVisibility(View.VISIBLE);
            scanBtn.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.INVISIBLE);
        }
        // No Internect connection and Old Leger data exists
        // User can either refresh and try to enable internect
        // Or use old Leger data to proceed
        else if (appStatus == STATUS_NOT_CONNECTED_LEDGER_EXISTS){
            outputMsg = "No Internect Connection. \n" +
                    "Please enable internect connection and click Refresh again. \n" +
                    "Or click Scan to proceed with Old data.";
            setResultMessage(outputMsg, true);
            refreshBtn.setVisibility(View.VISIBLE);
            scanBtn.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.INVISIBLE);
        }
        // Internect connection, always download latest Leger.
        else if (appStatus == STATUS_CONNECTED_LEDGER_NOT_EXISTS ||
                appStatus == STATUS_CONNECTED_LEDGER_EXISTS) {
            Log.d(TAG, "initialApps - RetriveLedgerTask called");
            new RetriveLedgerTask().execute();
        }
    }

    /**
     * Task which retrives ledger from DNANexus
     */
    public class RetriveLedgerTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... strings) {

            // Get Ledger file ID through DNANexus API
            String fileID = getLedgerFileID(LEDGER_FILE_NAME);
            if(fileID.indexOf("Error:") > 0) {
                return fileID;
            }

            // Check Ledger file version
            Boolean isLatestLedger = isLatestLedger(fileID);
            if(isLatestLedger)
                return RESULT_SUCCESS;

            // Get Ledger file download URL through DNANexus API
            String fileDownloadURL = getFileDownloadURL(fileID);
            if(fileDownloadURL.indexOf("Error:") > 0) {
                return fileDownloadURL;
            }
            try {
                URL url = new URL(fileDownloadURL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("X-Authorization", TOKEN);
                conn.setReadTimeout(15000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.connect();
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + conn.getResponseCode()
                            + " " + conn.getResponseMessage();
                }
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "utf-8"));

                String line = null;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }
                br.close();
                String strJsonResult = sb.toString();
                // Write to Ledger file
                OutputStream output = openFileOutput(LEDGER_FILE_NAME, Context.MODE_PRIVATE);
                output.write(strJsonResult.getBytes());
                // Write Ledger file ID to Meta data file
                output = openFileOutput(META_DATA_FILE_NAME, Context.MODE_PRIVATE);
                output.write(fileID.getBytes());
                File datafile = new File(getApplicationContext().getFilesDir(), META_DATA_FILE_NAME);
                File ledgerfile = new File(getApplicationContext().getFilesDir(), LEDGER_FILE_NAME);
                boolean DatafileExist = datafile.exists();
                boolean LedgerfileExist = ledgerfile.exists();

            } catch (Exception ex) {
                Log.e(TAG, "RetriveLedgerTask - Error Retrive Ledger: " + ex.getMessage());
                return new String("Exception: " + ex.getMessage());
            }
            return RESULT_SUCCESS;
        }


        @Override
        protected void onPostExecute(String s) {
            progressBar.setVisibility(View.INVISIBLE);
            if(s.equalsIgnoreCase(RESULT_SUCCESS)) {
                // Set refresh button invisible
                refreshBtn.setVisibility(View.VISIBLE);
                // Enable scan button
                scanBtn.setVisibility(View.VISIBLE);
                // Clean up Error messages
                setResultMessage("Initialization successful", false);
                // Set title back to QR Scan
                titleTextView.setText("CLICK SCAN TO VALIDATE");
            } else {
                Log.e(TAG, "RetriveLedgerTask - Error retrive Ledger: " + s);
                if(appStatus == STATUS_CONNECTED_LEDGER_NOT_EXISTS) {
                    setResultMessage("Failed to initialize Ledger file, Please Contact  ",true);
                    refreshBtn.setVisibility(View.VISIBLE);
                    scanBtn.setVisibility(View.VISIBLE);
                }
                else if(appStatus == STATUS_CONNECTED_LEDGER_EXISTS) {
                    setResultMessage("Failed to download the latest Ledger file.\n" +
                            "Please Refresh Again\n" +
                            "Or click Scan to proceed with Old Ledger.",true);
                    refreshBtn.setVisibility(View.VISIBLE);
                    scanBtn.setVisibility(View.VISIBLE);
                }
            }
        }
    }


    // Scanner Activity Result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK) {
            scannedData = data.getStringExtra(RETURN_RESULT_KEY_NAME);
            // Start validate scan result
            new ValidateRequest().execute();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    /**
     * Load Ledger file from local storage and validate scan barcode against ledger
     */
    public class ValidateRequest extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            Context context = getApplicationContext();
            String returnResult = "";
            String reportID = scannedData;
            try {
                // Read ledger file
                FileInputStream fis = context.openFileInput(LEDGER_FILE_NAME);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                String result = sb.toString();

                Gson gson = new Gson();
                Type collectionType = new TypeToken<Collection<Ledger>>() {}.getType();
                // Convert string to JSON objects
                Collection<Ledger> enums = gson.fromJson(result, collectionType);
                Ledger[] ledgers = enums.toArray(new Ledger[enums.size()]);
                String localID = null;
                String lastestDate = null;
                String lastestReportID = null;
                boolean reportExist = false;
                // Loop each ledger and find the one matching scan barcode
                for(Ledger eachLeger : ledgers) {
                    if(eachLeger.getRptid() != null && eachLeger.getRptid().equalsIgnoreCase(reportID)) {
                        localID = eachLeger.getLocalid();
                        reportExist = true;
                        break;
                    }
                }

                // Check if it is the latest report
                if (reportExist) {
                    lastestReportID = getLatestReportByLocalID(localID, ledgers);
                    if(lastestReportID.indexOf("Error:") >= 0) {
                        return lastestReportID;
                    }
                }

                // Generate result
                if (reportID != null && reportID.equalsIgnoreCase(lastestReportID)) {
                    returnResult = REPORT_VALID;
                } else if(reportExist) {
                    returnResult = REPORT_NOT_LASTEST;
                } else if(!reportExist){
                    returnResult = REPORT_NOT_EXIST;
                }
            } catch (Exception ex) {
                Log.e(TAG, "ValidateRequest - Error Validate Barcode: " + ex.getMessage());
                return new String("Exception: " + ex.getMessage());
            }
            return returnResult;
        }


        // Generate user-friendly messages
        @Override
        protected void onPostExecute(String result) {
            String outputMsg;
            TextView result_textView = (TextView) findViewById(R.id.result_textView);
            if(result.equalsIgnoreCase(REPORT_VALID)) {
                outputMsg = "This is a valid HGSC Clinical Laboratory report and is the latest for this patient.";
                result_textView.setTextColor(GREEN);
            }
            else if(result.equalsIgnoreCase(REPORT_NOT_EXIST)){
                outputMsg = "This is not a valid HGSC Clinical Laboratory report.";
                result_textView.setTextColor(RED);
            }
            else if(result.equalsIgnoreCase(REPORT_NOT_LASTEST)) {
                outputMsg = "This is a valid HGSC Clinical Laboratory report but is outdated, an updated version of this report is now available.";
                result_textView.setTextColor(RED);
            }
            else {
                outputMsg = "The system is experiencing an internal server error and is unable to scan this barcode. Please email eMERGE@hgsc.bcm.edu for assistance.";
                result_textView.setTextColor(RED);
            }
            result_textView.setVisibility(View.VISIBLE);
            result_textView.setText(outputMsg);
        }
    }

    /**
     * Set Output message
     * @param result
     */
    private void setResultMessage(String result, Boolean isWarning) {
        TextView result_textView = (TextView) findViewById(R.id.result_textView);
        result_textView.setText(result);
        if(isWarning) {
            result_textView.setTextColor(RED);
        } else {
            result_textView.setTextColor(GRAY);
        }
        result_textView.setVisibility(View.VISIBLE);
    }

    /**
     * Check Internect connectivity
     * @return
     */
    private boolean isConnected(){
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        return isConnected;
    }

    /**
     * Check Ledger file exist
     * @return
     */
    private boolean isLegerExist() {
        File file = getApplicationContext().getFileStreamPath(LEDGER_FILE_NAME);
        boolean isLegerExist = file.exists();
        return isLegerExist;
    }


    /**
     * Check Ledger file version
     * @param fileID
     * @return
     */
    private boolean isLatestLedger(String fileID) {
        Context context = getApplicationContext();
        try {
            File file = new File(context.getFilesDir(), META_DATA_FILE_NAME);
            if(!file.exists()) {
                return false;
            } else {
                FileInputStream fis = context.openFileInput(META_DATA_FILE_NAME);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                String result = sb.toString().trim();
                if (result.equalsIgnoreCase(fileID)) {
                    return true;
                } else {
                    return false;
                }
            }

        } catch (Exception ex) {
            Log.e(TAG, "isLatestLedger - Error Access Meta data file: " + ex.getMessage());
            return false;
        }
    }


    /**
     * Find Ledger file id based on file name from DNANexus
     * @param fileName
     * @return
     */
    private String getLedgerFileID(String fileName) {
        String fileID = null;
        JSONObject result = null;
        try {
            URL url = new URL(URL_FIND_DATA_OBJECT);
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            // Set Authorization header
            con.setRequestProperty("Authorization", "Bearer " + TOKEN);
            con.setRequestMethod("POST");

            // Input parameter
            JSONObject param = new JSONObject();
            JSONObject scope = new JSONObject();

//            scope.put("project", LEDGER_FILE_PROJECT_ID);
//            scope.put("folder", LEDGER_FILE_PATH);
//            param.put("scope", scope.toString());
            param.put("name", fileName);
            OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
            wr.write(param.toString());
            wr.flush();

            StringBuilder sb = new StringBuilder();

            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "getLedgerFileID - Connection Issue: " + con.getResponseCode()
                        + " " + con.getResponseMessage());
                return "Server returned HTTP " + con.getResponseCode()
                        + " " + con.getResponseMessage();
            }
            String strJsonResult = null;
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
            br.close();
            strJsonResult = sb.toString();
            // Convert response string to JSON
            result = new JSONObject(strJsonResult);
            JSONArray jsonresult = result.getJSONArray("results");
            if(jsonresult!=null) {
                fileID = (String)((JSONObject)jsonresult.get(0)).get("id");
            }
        } catch (Exception ex) {
            Log.e(TAG, "getLedgerFileID - Error get Ledger File ID: " + ex.getMessage());
            return new String("Error: " + ex.getMessage());
        }
        return fileID;

    }


    /**
     * Find Download URL from DNANexus based on fileID
     * @param fileID
     * @return
     */
    private String getFileDownloadURL(String fileID) {
        String strurl = "https://api.dnanexus.com/"+ fileID + "/download";
        String fileDownloadURL = null;
        try {
            URL url = new URL(strurl);
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            // Set Authroization header
            con.setRequestProperty("Authorization", "Bearer " + TOKEN);
            con.setRequestMethod("POST");

            // Have to be an empty JSON as input
            JSONObject param = new JSONObject();
            OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
            wr.write(param.toString());
            wr.flush();

            StringBuilder sb = new StringBuilder();
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "getFileDownloadURL - Connection Issue - getFileDownloadURL: " + con.getResponseCode()
                        + " " + con.getResponseMessage());
                return "Error: Server returned HTTP " + con.getResponseCode()
                        + " " + con.getResponseMessage();
            }
            String strJsonResult = null;
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
            br.close();
            strJsonResult = sb.toString();
            // Convert response string to JSON
            JSONObject result = new JSONObject(strJsonResult);
            fileDownloadURL = (String)result.get("url");

        } catch (Exception ex) {
            Log.e(TAG, "getFileDownloadURL - Error get file download URL: " + ex.getMessage());
            return new String("Error: " + ex.getMessage());
        }
        return fileDownloadURL;

    }


    /**
     * Find lastest Report based on the local ID
     * @param localID
     * @param ledgers
     * @return
     */
    private String getLatestReportByLocalID(String localID, Ledger[] ledgers) {
        long date = 0;
        String reportID = "";
        try {
            for (Ledger eachLeger : ledgers) {
                if (eachLeger.getLocalid().equalsIgnoreCase(localID) // local id
                        && eachLeger.getFilepath().indexOf(".pdf") > 0 // pdf file
                        && date <= Long.parseLong(eachLeger.getDate())
                        && eachLeger.getRptid() != null) {
                    date = Long.parseLong(eachLeger.getDate());
                    reportID = eachLeger.getRptid();
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "getLatestReportByLocalID - Error get latest report by local ID: " + ex.getMessage());
            return new String("Error: " + ex.getMessage());
        }
        return reportID;
    }
}
