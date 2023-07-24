package com.example.pedestrian;

import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor magnetometerSensor;
    private Sensor gyroscopeSensor;
    private Sensor proximitySensor;

    private float[] accelerometerValues = new float[3];
    private float[] magnetometerValues = new float[3];
    private float[] gyroscopeValues = new float[3];

    private final float ANGULAR_VELOCITY_THRESHOLD = 0.15f;
    private boolean isMovementDetected = false;
    private boolean isPhoneNear = false;

    private final String CHANNEL_ID = "pedestrian_channel";
    private final int NOTIFICATION_ID = 1;

    private Switch switch1;

    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switch1 = findViewById(R.id.switch1);
        switch1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startComputation();
            } else {
                stopComputation();
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SavePedestrian::WakeLock"
        );

        createNotificationChannel();
    }

    private void startComputation() {
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (magnetometerSensor != null) {
            sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        wakeLock.acquire();
    }

    private void stopComputation() {
        sensorManager.unregisterListener(this);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isPhoneUnlockedAndOpen()) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    accelerometerValues[0] = event.values[0];
                    accelerometerValues[1] = event.values[1];
                    accelerometerValues[2] = event.values[2];
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magnetometerValues[0] = event.values[0];
                    magnetometerValues[1] = event.values[1];
                    magnetometerValues[2] = event.values[2];
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    gyroscopeValues[0] = event.values[0];
                    gyroscopeValues[1] = event.values[1];
                    gyroscopeValues[2] = event.values[2];

                    float angularVelocityMagnitude = (float) Math.sqrt(
                            gyroscopeValues[0] * gyroscopeValues[0] +
                                    gyroscopeValues[1] * gyroscopeValues[1] +
                                    gyroscopeValues[2] * gyroscopeValues[2]
                    );

                    isMovementDetected = angularVelocityMagnitude > ANGULAR_VELOCITY_THRESHOLD;
                    break;
                case Sensor.TYPE_PROXIMITY:
                    isPhoneNear = event.values[0] < (proximitySensor != null ? proximitySensor.getMaximumRange() : 0f);
                    break;
            }

            float[] rotationMatrix = new float[9];
            boolean success = SensorManager.getRotationMatrix(
                    rotationMatrix,
                    null,
                    accelerometerValues,
                    magnetometerValues
            );

            if (success) {
                float[] orientationAngles = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientationAngles);

                float pitchAngle = (float) -Math.toDegrees(orientationAngles[1]);

                showNotification(isMovementDetected, isPhoneNear, pitchAngle);
            }
        }
    }

    private boolean isPhoneUnlockedAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            if (!powerManager.isInteractive()) {
                return false;
            }
        } else {
            if (!powerManager.isScreenOn()) {
                return false;
            }
        }

        return !keyguardManager.isKeyguardLocked();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }

    private void showNotification(boolean isMovementDetected, boolean isPhoneNear, float angle) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Pedestrian Status")
                .setContentText(getNotificationText(isMovementDetected, isPhoneNear, angle))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private String getNotificationText(boolean isMovementDetected, boolean isPhoneNear, float angle) {
        if (isPhoneNear) {
            return "Phone is in the Pocket or you are on Call.";
        } else {
            if (isMovementDetected) {
                if (angle >= 0.0 && angle <= 75.0) {
                    return "You are moving and using the phone, Heads Up !!!";
                } else {
                    return "You are moving.";
                }
            } else {
                if (angle >= 0.0 && angle <= 75.0) {
                    return "You are still and using the phone, Watch Out !!!";
                } else {
                    return "You are still.";
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pedestrian Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Channel for Pedestrian notifications");

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }
}