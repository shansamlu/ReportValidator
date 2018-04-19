package edu.bcm.hgsc.reportvalidator;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


import static android.Manifest.permission.CAMERA;

public class ScanActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 1;
    private static final String RETURN_RESULT_KEY_NAME = "ScanData";
    public final int CUSTOMIZED_REQUEST_CODE = 0x0000ffff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setPrompt("Scan QR Barcode");
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();

        int currentApiVersion = Build.VERSION.SDK_INT;

        if(currentApiVersion >=  Build.VERSION_CODES.M)
        {
            if(!checkPermission())
            {
                requestPermission();
            }
        }else {
            if(ActivityCompat.shouldShowRequestPermissionRationale((Activity) getApplicationContext(), Manifest.permission.CAMERA)) {

            } else {
                ActivityCompat.requestPermissions((Activity) getApplicationContext(), new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            }
        }
    }

    private boolean checkPermission()
    {
        return (ContextCompat.checkSelfPermission(getApplicationContext(), CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestPermission()
    {
        ActivityCompat.requestPermissions(this, new String[]{CAMERA}, REQUEST_CAMERA);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result!=null) {
            Intent output = new Intent();
            output.putExtra(RETURN_RESULT_KEY_NAME, result.getContents());
            setResult(RESULT_OK, output);
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}