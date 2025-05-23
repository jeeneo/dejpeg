package com.je.dejpeg;

import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class ImageDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORIGINAL_URI = "com.je.dejpeg.EXTRA_ORIGINAL_URI";
    public static final String EXTRA_PROCESSED_URI = "com.je.dejpeg.EXTRA_PROCESSED_URI";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_detail);

        Toolbar toolbar = findViewById(R.id.toolbar_image_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Compare Images");
        }

        ImageView imageViewOriginal = findViewById(R.id.image_view_original);
        ImageView imageViewProcessed = findViewById(R.id.image_view_processed);

        String originalImageUriString = getIntent().getStringExtra(EXTRA_ORIGINAL_URI);
        String processedImageUriString = getIntent().getStringExtra(EXTRA_PROCESSED_URI);

        if (originalImageUriString != null && processedImageUriString != null) {
            imageViewOriginal.setImageURI(Uri.parse(originalImageUriString));
            imageViewProcessed.setImageURI(Uri.parse(processedImageUriString));
        } else {
            Toast.makeText(this, "Error: Image URIs not provided.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
