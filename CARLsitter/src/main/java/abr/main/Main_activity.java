/**
 * Rescue Robotics 2016 App
 * Developed by Cognitive Anteater Robotics Laboratory at University of California, Irvine
 * Controls wheeled robot through IOIO
 * Parts of code adapted from OpenCV blob follow
 * Before running, connect phone to IOIO with a bluetooth connection
 * If you would like to uncomment sections for message passing, first connect peer phones using wifi direct
 */
package abr.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import java.util.List;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;


import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.PrintWriter;

public class Main_activity extends Activity implements IOIOLooperProvider, CvCameraViewListener2 // implements IOIOLooperProvider: from IOIOActivity
{
	private final IOIOAndroidApplicationHelper helper_ = new IOIOAndroidApplicationHelper(this, this); // from IOIOActivity
	
	// ioio variables
	IOIO_thread_rover m_ioio_thread;
	
	//blob detection variables
	private CameraBridgeViewBase mOpenCvCameraView;
	private Mat mRgba;
	private Scalar mBlobColorRgba;
	private ColorBlobDetector mDetector;
	private Mat mSpectrum;
	private Scalar CONTOUR_COLOR;
	
	//app state variables
	private boolean autoMode=false;
	private boolean recording=false;
	private boolean streaming=false;

	//ui variables
	TextView sonar1Text;
	TextView sonar2Text;
	TextView sonar3Text;
	TextView distanceText;
	TextView bearingText;
	TextView headingText;
	
	//sockets for message passing
	ServerSocket serverSocket;
	Socket socket;
	Socket clientSocket;

	private MediaRecorder myRecorder;
	private String outputFile = null;

	long timer = 0;
	
	// called to use OpenCV libraries contained within the app as opposed to a separate download
	static {
		if (!OpenCVLoader.initDebug()) {
			// Handle initialization error
		}
	}
	
	// called whenever the activity is created
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.main);
		
		helper_.create(); // from IOIOActivity
		
		//set up opencv camera
		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
		mOpenCvCameraView.enableView();

		//initialize textviews
		sonar1Text = (TextView) findViewById(R.id.sonar1);
		sonar2Text = (TextView) findViewById(R.id.sonar2);
		sonar3Text = (TextView) findViewById(R.id.sonar3);
		distanceText = (TextView) findViewById(R.id.distanceText);
		bearingText = (TextView) findViewById(R.id.bearingText);
		headingText = (TextView) findViewById(R.id.headingText);

		// store it to sd card

		outputFile = Environment.getExternalStorageDirectory().
				getAbsolutePath() + "/test.wav"; //this is the folder in which your Audio file willl save

		myRecorder = new MediaRecorder();
		myRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		myRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		myRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
		myRecorder.setOutputFile(outputFile);

		//add functionality to autoMode button
		Button buttonAuto = (Button) findViewById(R.id.btnAuto);
		buttonAuto.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (!autoMode) {
					v.setBackgroundResource(R.drawable.button_auto_on);
					autoMode = true;
				} else {
					v.setBackgroundResource(R.drawable.button_auto_off);
					autoMode = false;
				}
			}
		});

		//add functionality to autoMode button
		Button buttonRecord = (Button) findViewById(R.id.record);
		buttonRecord.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(!recording) {
					start(v);
					recording = true;
				}
				else{
					stop(v);
					recording = false;
				}
			}
		});
		
		//set starting autoMode button color
		if (autoMode) {
			buttonAuto.setBackgroundResource(R.drawable.button_auto_on);
		} else {
			buttonAuto.setBackgroundResource(R.drawable.button_auto_off);
		}



	}

	//Called whenever activity resumes from pause
	@Override
	public void onResume() {
		super.onResume();
	    if (mOpenCvCameraView != null)
			mOpenCvCameraView.enableView();
	}
	
	//Called when activity pauses
	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}
	
	//Called when activity restarts. onCreate() will then be called
	@Override
	public void onRestart() {
		super.onRestart();
		Log.i("activity cycle","main activity restarting");
	}

	//Called when camera view starts. change bucket color here
	public void onCameraViewStarted(int width, int height) {
		mRgba = new Mat(height, width, CvType.CV_8UC4);
		mDetector = new ColorBlobDetector();
		mSpectrum = new Mat();
		mBlobColorRgba = new Scalar(255);
		CONTOUR_COLOR = new Scalar(255, 0, 0, 255);

		//To set color, find HSV values of desired color and convert each value to 1-255 scale
		//mDetector.setHsvColor(new Scalar(7, 196, 144)); // red
		mDetector.setHsvColor(new Scalar(46, 211, 214)); // neon yellow
		//mDetector.setHsvColor(new Scalar(253.796875,222.6875,195.21875));
	}
	//Called when camera view stops
	public void onCameraViewStopped() {
		mRgba.release();
	}
	//Called at every camera frame. Main controls of the robot movements are in this function
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

		if(m_ioio_thread != null){
			setText("sonar1: "+m_ioio_thread.get_ir1_reading(), sonar1Text);
			setText("sonar2: "+m_ioio_thread.get_ir2_reading(), sonar2Text);
			setText("sonar3: "+m_ioio_thread.get_ir3_reading(), sonar3Text);
		}

		mRgba = inputFrame.rgba();
		mDetector.process(mRgba);
		
		List<MatOfPoint> contours = mDetector.getContours();
		// Log.e("rescue robotics", "Contours count: " + contours.size());
		Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);

		Mat colorLabel = mRgba.submat(4, 68, 4, 68);
		colorLabel.setTo(mBlobColorRgba);

		Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70,
				70 + mSpectrum.cols());
		mSpectrum.copyTo(spectrumLabel);

		if (autoMode) { // only move if autoMode is on
			if(m_ioio_thread.get_ir2_reading() < .4)
				m_ioio_thread.move(1500);
			else {
				double momentX = mDetector.getMomentX();
				double centerX = mDetector.getCenterX();
				int centerThreshold = (int) (.333 * centerX);
				Log.i("hahaha","area:"+(mDetector.getMaxArea()/(centerX*centerX*4)));
				if((mDetector.getMaxArea()/(centerX*centerX*4)) > .01) {
					if (momentX > centerThreshold) {
						m_ioio_thread.turn(1600);
					} else if (momentX < -centerThreshold) {
						m_ioio_thread.turn(1400);
					} else {
						m_ioio_thread.move(1600);
					}
				} else {
					m_ioio_thread.move(1500);
				}
			}
		} else {
			m_ioio_thread.move(1500);
		}

		return mRgba;
	}
	
	//set the text of any text view in this application
	public void setText(final String str, final TextView tv) 
	{
		  runOnUiThread(new Runnable() {
			  @Override
			  public void run() {
				  tv.setText(str);
			  }
		  });
	}

	/****************************************************** functions from IOIOActivity *********************************************************************************/

	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		if (m_ioio_thread == null
				&& connectionType
						.matches("ioio.lib.android.bluetooth.BluetoothIOIOConnection")) {
			m_ioio_thread = new IOIO_thread_rover();
			return m_ioio_thread;
		} else
			return null;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i("activity cycle","main activity being destroyed");
		helper_.destroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.i("activity cycle","main activity starting");
		helper_.start();
	}

	@Override
	protected void onStop() {
		Log.i("activity cycle","main activity stopping");
		super.onStop();
		helper_.stop();
		try {
			if(socket != null)
				socket.close();
			if(serverSocket != null)
				serverSocket.close();
			if(clientSocket != null)
				clientSocket.close();
		} catch (IOException e) {
			Log.e("rescue robotics", e.getMessage());
		}
		
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
			if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
			helper_.restart();
		}
	}

	protected void start(View view){
		Log.i("hahaha","start");
		try {
			myRecorder.prepare();
			myRecorder.start();
		} catch (IllegalStateException e) {
			// start:it is called before prepare()
			// prepare: it is called after start() or before setOutputFormat()
			e.printStackTrace();
		} catch (IOException e) {
			// prepare() fails
			e.printStackTrace();
		}

		Toast.makeText(getApplicationContext(), "Start recording...",
				Toast.LENGTH_SHORT).show();
	}
	public void stop(View view){
		Log.i("hahaha","stop");
		try {
			myRecorder.stop();
			myRecorder.release();
			myRecorder  = null;

			Toast.makeText(getApplicationContext(), "Stop recording...",
					Toast.LENGTH_SHORT).show();
		} catch (IllegalStateException e) {
			//  it is called before start()
			e.printStackTrace();
		} catch (RuntimeException e) {
			// no valid audio/video data has been received
			e.printStackTrace();
		}
		sendAudio();
	}
	public void sendAudio(){
		Log.i("hahaha","sendAudio");
		try{
			Socket soc=new Socket("0.tcp.ngrok.io",17209);
			DataOutputStream dout=new DataOutputStream(soc.getOutputStream());

			byte[] myBuffer = new byte[1024];
			int bytesRead = 0;
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(new File(outputFile)));
			while ((bytesRead = in.read(myBuffer,0,1024)) != -1)
			{
				dout.write(myBuffer);
				dout.flush();
				Log.i("hahaha","writing");
			}

			dout.flush();
			dout.close();
			soc.shutdownOutput();
			BufferedReader br = new BufferedReader(new InputStreamReader(soc.getInputStream()));
			PrintWriter pw = new PrintWriter(soc.getOutputStream(), true);
			String str = br.readLine();
			Log.i("hahaha","Class:"+str);
			//soc.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
