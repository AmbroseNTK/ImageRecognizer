package ntk.ambrose.imagerecognizer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sdsmdg.tastytoast.TastyToast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import kotlin.jvm.Synchronized;

import static ntk.ambrose.imagerecognizer.ConstaintsKt.GRAPH_FILE_PATH;
import static ntk.ambrose.imagerecognizer.ConstaintsKt.GRAPH_INPUT_NAME;
import static ntk.ambrose.imagerecognizer.ConstaintsKt.GRAPH_OUTPUT_NAME;
import static ntk.ambrose.imagerecognizer.ConstaintsKt.IMAGE_SIZE;
import static ntk.ambrose.imagerecognizer.ConstaintsKt.LABELS_FILE_PATH;
import static ntk.ambrose.imagerecognizer.ImageUtilsKt.getCroppedBitmap;


public class MainActivity extends AppCompatActivity {


    Animation slideInAnim,slideOutAnim,hideImageAnim,showImageAnim;
    RelativeLayout slidePanel;
    Button btHide;
    TextView tvObjectName;
    TextView tvDescription;
    Button btRecognize;
    ImageView imgExample;

    TextureView textureView;
    SurfaceHolder surfaceHolder;

    MediaPlayer playSound;


    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private Handler handler;
    private Classifier foodClassifier;
    private Classifier eatableClassifier;

    private HashMap<String,Food> dictFood;

    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";


    private static final String MODEL_FILE = "file:///android_asset/graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/labels.txt";


    private static final boolean MAINTAIN_ASPECT = true;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private Integer sensorOrientation;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    TextureView.SurfaceTextureListener textureListener;

    private Classifier createClassifier(String graphFile, String labelsFile, int imageSize, String inputName, String outputName) {
        return ImageClassifierFactory.INSTANCE.create(
                getAssets(),
                graphFile,
                labelsFile,
                imageSize,
                inputName,
                outputName
        );
    }

    private void classifyPhoto(Bitmap photoBitmap) {
        Bitmap croppedBitmap = getCroppedBitmap(photoBitmap);
        Log.i("Classifier","Classifying");
        classifyAndShowResult(croppedBitmap);
    }

    private void classifyAndShowResult(Bitmap croppedBitmap) {
        Result eatableResult = eatableClassifier.recognizeImage(croppedBitmap);
        Log.i("Classifier", "Result = " + eatableResult.getResult() + " : " + String.valueOf(eatableResult.getConfidence()));
        if(eatableResult.getResult().equals("eatable")) {
            Result result = foodClassifier.recognizeImage(croppedBitmap);
            Log.i("Classifier", "Result = " + result.getResult() + " : " + String.valueOf(result.getConfidence()));
            if (result.getConfidence() > 0.7f) {
                playSound.start();
                TastyToast.makeText(this, "Done!", TastyToast.LENGTH_SHORT, TastyToast.SUCCESS);
                slidePanel.startAnimation(slideInAnim);
                if (dictFood.containsKey(result.getResult())) {
                    tvObjectName.setText(dictFood.get(result.getResult()).getName());
                    tvDescription.setText(dictFood.get(result.getResult()).getDescription());
                    try {
                        imgExample.setImageBitmap(new DownloadImage().execute(result).get());
                        imgExample.startAnimation(showImageAnim);
                    } catch (ExecutionException ex) {
                    } catch (InterruptedException ex) {
                    }
                } else {
                    TastyToast.makeText(this, "I have no idea", TastyToast.LENGTH_SHORT, TastyToast.CONFUSING);
                }
            } else {
                TastyToast.makeText(this, "Sorry! I don't know", TastyToast.LENGTH_SHORT, TastyToast.ERROR);
            }
        }
        else{
            TastyToast.makeText(this,"There are no food...",TastyToast.LENGTH_SHORT,TastyToast.ERROR);
        }
    }
    @Synchronized
    private void runInBackground(Runnable runnable) {
        handler.post(runnable);
    }

    public void createFoodDict(Context context){
        try {
            InputStream inputStream = context.getAssets().open("dictonary.xml");
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(inputStream);
            Element element =document.getDocumentElement();
            element.normalize();
            NodeList nodeList = document.getElementsByTagName("food");
            Node node;
            dictFood=new HashMap<>();
            for(int i=0;i<nodeList.getLength();i++){
                node = nodeList.item(i);
                if(node.getNodeType()==Node.ELEMENT_NODE){
                    Element subElement = (Element)node;
                    Food country = new Food(getNodeValue("pname",subElement),getNodeValue("name",subElement),getNodeValue("link",subElement),getNodeValue("description",subElement));
                    dictFood.put(country.getPredictName(),country);

                }
            }
            inputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getNodeValue(String tag, Element element) {
        try {
            NodeList nodeList = element.getElementsByTagName(tag).item(0).getChildNodes();
            Node node = nodeList.item(0);
            return node.getNodeValue();
        }catch(Exception e){
            Log.i("APP",e.getMessage());
            return "";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        dictFood = new HashMap<>();
        createFoodDict(this);
        foodClassifier = createClassifier(GRAPH_FILE_PATH,LABELS_FILE_PATH,IMAGE_SIZE,GRAPH_INPUT_NAME,GRAPH_OUTPUT_NAME);
        eatableClassifier = createClassifier("file:///android_asset/eatable_graph.pb","file:///android_asset/eatable_labels.txt",224,"Placeholder","final_result");

        playSound = MediaPlayer.create(this,R.raw.correct);
        imgExample = findViewById(R.id.imgExample);
        tvObjectName = findViewById(R.id.tvObjectName);
        tvDescription=findViewById(R.id.tvDescription);
        btRecognize = findViewById(R.id.btRecognize);
        slideInAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in);
        slideOutAnim = AnimationUtils.loadAnimation(this, R.anim.slide_out);
        showImageAnim=AnimationUtils.loadAnimation(this,R.anim.show_image);
        hideImageAnim = AnimationUtils.loadAnimation(this,R.anim.hide_image);

        slideOutAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                slidePanel.setVisibility(RelativeLayout.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        slideInAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                slidePanel.setVisibility(RelativeLayout.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        showImageAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                imgExample.setVisibility(LinearLayout.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        hideImageAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                imgExample.setVisibility(LinearLayout.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        imgExample.startAnimation(hideImageAnim);
        slidePanel = findViewById(R.id.slidePanel);
        btHide = findViewById(R.id.btHide);
        slidePanel.startAnimation(slideOutAnim);
        btHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                slidePanel.startAnimation(slideOutAnim);
                imgExample.startAnimation(hideImageAnim);
            }
        });

        textureView = findViewById(R.id.cameraPreview);
        textureView.setSurfaceTextureListener(textureListener);

        textureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Open camera khi ready
                openCamera();
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // Transform you image captured size according to the surface width and height, và thay đổi kích thước ảnh
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        };

        final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleWithFixedDelay(new Runnable()
        {
            @Override public void run()
            {

            }
        }, 0, 3, TimeUnit.SECONDS);
        btRecognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Classifier","Starting");
                if(textureView.getBitmap()!=null) {
                    Log.i("Classifier","Accepted");
                    classifyPhoto(textureView.getBitmap());
                }
            }
        });
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            // Camera opened

            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    // Thực hiển việc capture ảnh thông qua CAMERACAPTURESESSION
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            createCameraPreview();
        }
    };
    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    protected void takePicture() {
        if(null == cameraDevice) {

            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }

            // CAPTURE IMAGE với tuỳ chỉnh kích thước
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // kiểm tra orientation tuỳ thuộc vào mỗi device khác nhau như có nói bên trên
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;

                }

            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Khởi tạo camera để preview trong textureview
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            // Kiểm tra permission với android sdk >= 23
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    protected void updatePreview() {
        if(null == cameraDevice) {

        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {

        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private class DownloadImage extends AsyncTask<Result,Void,Bitmap>{

        URL url;
        Bitmap bmp;
        public DownloadImage() {
            super();
        }

        @Override
        protected Bitmap doInBackground(Result... results) {
            try {
                url = new URL(dictFood.get(results[0].getResult()).getLink());
                bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                return bmp;
            }
            catch(IOException ex){

            }
            return bmp;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onCancelled(Bitmap bitmap) {
            super.onCancelled(bitmap);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }
    }

}
