package com.je.dejpeg;

import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.compose.material3.Text;
import androidx.compose.ui.platform.ComposeView;
// Import the BeforeAfterImage Composable if you know the exact package.
// For example: import com.smarttoolfactory.beforeafter.BeforeAfterImage;
// Or, if it's in a sub-package: import com.smarttoolfactory.compose.beforeafter.BeforeAfterImage;

// For now, we will use a placeholder, assuming image loading is complex.
// If using Coil or another image loading library for Compose:
// import coil.compose.rememberAsyncImagePainter;
// import androidx.compose.foundation.Image;
// import androidx.compose.ui.Modifier;
// import androidx.compose.foundation.layout.fillMaxSize;

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

        ComposeView composeView = findViewById(R.id.compose_view_comparison);
        String originalImageUriString = getIntent().getStringExtra(EXTRA_ORIGINAL_URI);
        String processedImageUriString = getIntent().getStringExtra(EXTRA_PROCESSED_URI);

        if (originalImageUriString != null && processedImageUriString != null) {
            // For now, we use a placeholder Text composable.
            // Loading images from URIs in Compose can be complex and might require
            // libraries like Coil, Glide, or custom painter logic.
            // This will be addressed in a future step if necessary.

            // Placeholder using simple Text
            composeView.setContent(context -> {
                // This lambda is of type (@Composable () -> Unit)
                // Ensure you have the correct imports for Text if not already present.
                // For Material 3: import androidx.compose.material3.Text;
                // For Material 2: import androidx.compose.material.Text;
                TextKt.Text("Original URI: " + originalImageUriString + "\nProcessed URI: " + processedImageUriString, null, 0, 0, false, 0, 0, null, null, 0, 0, false, 0, 0, null, null, null, 0b111111111111111111111111111111, 0);
                return kotlin.Unit.INSTANCE;
            });

            /*
            // Example of how it might look with an image loading library like Coil:
            // This requires adding Coil dependency and proper setup.
            composeView.setContent {
                // Assuming SmartToolFactory.Compose.BeforeAfter is the Composable
                // This is a conceptual example. The actual API may differ.
                // You would need to convert URIs to Painters.
                // Painter originalPainter = rememberAsyncImagePainter(model = Uri.parse(originalImageUriString));
                // Painter processedPainter = rememberAsyncImagePainter(model = Uri.parse(processedImageUriString));

                // Hypothetical usage of the BeforeAfterImage composable:
                // com.smarttoolfactory.beforeafter.BeforeAfterImage(
                // modifier = Modifier.fillMaxSize(),
                // beforeImage = originalPainter, // This would be Painter, not ImageBitmap directly with URI
                // afterImage = processedPainter,  // This would be Painter
                // contentScale = androidx.compose.ui.layout.ContentScale.Fit
                // );

                // Fallback to Text if the above is too complex for now
                // Text(text = "Original URI: " + originalImageUriString + "\nProcessed URI: " + processedImageUriString);
            }
            */

        } else {
            composeView.setContent(context -> {
                TextKt.Text("Error: Image URIs not provided.", null, 0, 0, false, 0, 0, null, null, 0, 0, false, 0, 0, null, null, null, 0b111111111111111111111111111111, 0);
                return kotlin.Unit.INSTANCE;
            });
            Toast.makeText(this, "Error: Image URIs not provided.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Or NavUtils.navigateUpFromSameTask(this); if you have specific up navigation
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
