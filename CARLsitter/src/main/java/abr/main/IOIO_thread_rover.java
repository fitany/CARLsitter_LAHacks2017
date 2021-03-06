package abr.main;

import android.util.Log;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

public class IOIO_thread_rover extends IOIO_thread
{
	private PwmOutput pwm_left1, pwm_left2, pwm_right1,pwm_right2,pwm_pan,pwm_tilt;
	private DigitalOutput dir_left1, dir_left2, dir_right1, dir_right2;
	private AnalogInput ir1, ir2, ir3;
	float speed_left, speed_right;
	float ir1_reading, ir2_reading, ir3_reading;
	int pan_val,tilt_val;
	boolean direction_left, direction_right;

	@Override
	public void setup() throws ConnectionLostException
	{
		try
		{
			pwm_left1 = ioio_.openPwmOutput(3, 490); //motor channel 1: front left
			pwm_left2 = ioio_.openPwmOutput(5, 490); //motor channel 2: back left
			pwm_right1 = ioio_.openPwmOutput(7, 490); //motor channel 3: front right
			pwm_right2 = ioio_.openPwmOutput(10, 490); //motor channel 4: back right

			//pwm_pan = ioio_.openPwmOutput(12,100);
			//pwm_tilt = ioio_.openPwmOutput(11,100);

			ir1 = ioio_.openAnalogInput(42);
			ir2 = ioio_.openAnalogInput(43);
			ir3 = ioio_.openAnalogInput(44);

			dir_left1 = ioio_.openDigitalOutput(2, true);	//motor channel 1: front left
			dir_left2 = ioio_.openDigitalOutput(4, true);	//motor channel 2: back left
			dir_right1 = ioio_.openDigitalOutput(6, true); //motor channel 3: front right
			dir_right2 = ioio_.openDigitalOutput(8, true); //motor channel 4: back right

			ir1_reading = 0.0f;
			ir2_reading = 0.0f;
			ir3_reading = 0.0f;

			direction_left = false;
			direction_right = false;
			speed_left = 0;
			speed_right = 0;
			pan_val = 1500;
			tilt_val = 1500;
		}
		catch (ConnectionLostException e){throw e;}
	}

	@Override
	public void loop() throws ConnectionLostException
	{
		ioio_.beginBatch();

		try
		{
			pwm_left1.setDutyCycle(speed_left);
			pwm_left2.setDutyCycle(speed_left);
			pwm_right1.setDutyCycle(speed_right);
			pwm_right2.setDutyCycle(speed_right);

			//pwm_pan.setPulseWidth(pan_val);
			//pwm_tilt.setPulseWidth(tilt_val);

			dir_left1.write(direction_left);
			dir_left2.write(direction_left);
			dir_right1.write(!direction_right);
			dir_right2.write(direction_right);

			ir1_reading = ir1.getVoltage();
			ir2_reading = ir2.getVoltage();
			ir3_reading = ir3.getVoltage();

			//Log.i("hahaha","ir1:"+get_ir1_reading());
			//Log.i("hahaha","ir2:"+get_ir2_reading());
			//Log.i("hahaha","ir3:"+get_ir3_reading());

			Thread.sleep(10);
		}
		catch (InterruptedException e){ ioio_.disconnect();}
		finally{ ioio_.endBatch();}
	}

	public synchronized void move(int value)
	{
		if (value > 1500) {
			speed_left = (float)0.30;
			speed_right = (float)0.30;
			direction_left = true;
			direction_right = true;
		} else if (value < 1500) {
			speed_left = (float)0.30;
			speed_right = (float)0.30;
			direction_left = false;
			direction_right = false;
		} else {
			speed_left = 0;
			speed_right = 0;
		}
	}

	public synchronized void turn(int value)
	{
		if (value > 1500) {
			speed_left = (float)0.4;
			speed_right = (float)0.4;
			direction_left = false;
			direction_right = true;
		} else if (value < 1500) {
			speed_left = (float)0.4;
			speed_right = (float)0.4;
			direction_left = true;
			direction_right = false;
		} else {
			speed_left = 0;
			speed_right = 0;
		}
	}

	public synchronized void pan(int value)
	{
		pan_val = value;
	}

	public synchronized void tilt(int value)
	{
		tilt_val = value;
	}

	public float get_ir1_reading() {
		return 100*((1f/15.7f*(-ir1_reading))+0.22f);
	}
	public float get_ir2_reading() {
		return 100*((1f/15.7f*(-ir2_reading))+0.22f);
	}
	public float get_ir3_reading() { return 100*((1f/15.7f*(-ir3_reading))+0.22f); }
}