package com.horses.duofire;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.hdodenhof.circleimageview.CircleImageView;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    @BindView(R.id.surface)
    protected SurfaceView surface;

    @BindView(R.id.receiver)
    protected ImageView receiver;

    @BindView(R.id.circle)
    protected CircleImageView circle;

    private Camera camera;
    private SurfaceHolder holder;

    private boolean running = false;

    private DatabaseReference root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        //region init holder
        holder = surface.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //endregion

        root = FirebaseDatabase.getInstance().getReference("camera").child("1");

        FirebaseDatabase.getInstance().getReference("camera").child("2").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                try {

                    String base = dataSnapshot.getValue(String.class);

                    receiver.setImageBitmap(stringToBitmap(base));

                } catch (Exception e) {
                    Log.e("FIRE", e.toString());
                    FirebaseCrash.report(e);
                    FirebaseCrash.log("Not jet load object with image");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        camera = Camera.open(1);
        camera.setPreviewCallback((bytes, camera) -> {

            //region convert to bitmap and send to firebase
            Camera.Size previewSize = camera.getParameters().getPreviewSize();

            YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, previewSize.width, previewSize.height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 60, baos);
            byte[] jdata = baos.toByteArray();

            Bitmap original = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);

            Matrix matrix = new Matrix();
            matrix.postRotate(270);
            matrix.postScale(-1, 1, 0, 0);
            Bitmap rotated = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);

            Bitmap scale = Bitmap.createScaledBitmap(rotated, 80, 80, true);

            root.setValue(bitmapToString(scale));
            circle.setImageBitmap(scale);
            //endregion
        });
    }

    @SuppressWarnings("Convert2streamapi")
    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int w, int h) {

        if (running) {
            camera.stopPreview();
        }

        camera.setDisplayOrientation(90);

        //region get all square sizes
        Camera.Parameters parameters = camera.getParameters();

        for (Camera.Size preview : parameters.getSupportedPreviewSizes()) {

            if (preview.height == preview.width) {
                parameters.setPreviewSize(preview.width, preview.height);
            }
        }
        //endregion

        camera.setParameters(parameters);

        try {

            camera.setPreviewDisplay(holder);
        } catch (Exception e) {

            FirebaseCrash.report(e);
            Log.e("setPreviewDisplay", e.toString());
        }

        camera.startPreview();

        running = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        camera.stopPreview();
        running = false;
    }

    public Bitmap stringToBitmap(String encodedString) {
        try {
            byte[] encodeByte = Base64.decode(encodedString, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
        } catch (Exception e) {
            e.getMessage();
            FirebaseCrash.report(e);
            return null;
        }
    }

    public String bitmapToString(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }
}
