package com.example.samue.login;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;


public class MainActivity extends AppCompatActivity {

    private String usuario;
    public static final String downloadsFolder = Environment.getExternalStorageDirectory().getPath() + "/P2PArchiveSharing/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createDownloadsFolder();
        setContentView(R.layout.activity_main);
        String iid = InstanceID.getInstance(this).getId();

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        File af = new File("/data/data/com.example.samue.login/files/nombre.txt");
        try {
            if (af.isFile()) {
                BufferedReader aNombre = new BufferedReader(new InputStreamReader(openFileInput("nombre.txt")));
                usuario = aNombre.readLine();
                aNombre.close();

                final ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setIndeterminate(true);
                progressDialog.setMessage("Bienvenido/a de nuevo, " + usuario);
                progressDialog.show();


                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                // On complete call either onLoginSuccess or onLoginFailed

                                Intent intent = new Intent(MainActivity.this, Profile.class);
                                intent.putExtra("user", usuario);
                                startActivity(intent);
                                finish();
                                progressDialog.dismiss();
                            }
                        }, 2000);
            } else {
                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                // On complete call either onLoginSuccess or onLoginFailed
                                Intent intent = new Intent(MainActivity.this, CreateName.class);
                                startActivity(intent);
                                finish();

                            }
                        }, 2000);

            }

        } catch (Exception e) {
            Log.e("Error Nombre", e.getMessage());
        }

    }


    /**
     * Creación del directorio en el que irán las descargas.
     */
    private void createDownloadsFolder(){
        File file = new File(downloadsFolder);
        if(!file.isDirectory())
            file.mkdirs();
    }

}
