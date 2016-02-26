package material.sahitya.traveljarassigment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.text.DecimalFormat;

import material.sahitya.traveljarassigment.travelLibrary.Camera;

public class MainActivity extends AppCompatActivity {

    private final static int CAMERA_RQ = 6969;
    private final static int PERMISSION_RQ = 84;

    private Button button_satart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        button_satart = (Button) findViewById(R.id.start_camera);
        button_satart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File saveDir = null;

                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    saveDir = new File(Environment.getExternalStorageDirectory(), "Camera");
                    saveDir.mkdirs();
                }

                new Camera(MainActivity.this)
                        .saveDir(saveDir)
                        .showPortraitWarning(false)
                        .allowRetry(true)
                        .countdownSeconds(15f)
                        .defaultToFrontFacing(true)
                        .start(CAMERA_RQ);


            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_RQ);
        }


    }

    private String readableFileSize(long size) {
        if (size <= 0) return size + " B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private String fileSize(File file) {
        return readableFileSize(file.length());
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_RQ) {
            if (resultCode == RESULT_OK) {
                final File file = new File(data.getData().getPath());
                Toast.makeText(this, String.format("Saved to: %s, size: %s",
                        file.getAbsolutePath(), fileSize(file)), Toast.LENGTH_LONG).show();
            } else if (data != null) {
                Exception e = (Exception) data.getSerializableExtra(Camera.ERROR_EXTRA);
                if (e != null) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Videos will be saved in a cache directory instead of an external storage directory since permission was denied.", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
