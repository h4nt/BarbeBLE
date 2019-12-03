package com.horanet.BarbeBLE;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;

import java.util.Arrays;
import java.util.HashSet;

import static com.horanet.BarbeBLE.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;
import static com.horanet.BarbeBLE.Constants.HEART_RATE_SERVICE_UUID;
import static com.horanet.BarbeBLE.Constants.SERVER_MSG_FIRST_STATE;
import static com.horanet.BarbeBLE.Constants.SERVER_MSG_SECOND_STATE;


public class PeripheralRoleActivity extends BluetoothActivity implements View.OnClickListener {

    private BluetoothGattService mSampleService;
    private BluetoothGattCharacteristic mSampleCharacteristic;

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private HashSet<BluetoothDevice> mBluetoothDevices;

    private Button mNotifyButton;
    private Switch mEnableAdvertisementSwitch;
    private RadioGroup mCharacteristicValueSwitch;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNotifyButton = (Button) findViewById(R.id.button_notify);
        mEnableAdvertisementSwitch = (Switch) findViewById(R.id.advertise_switch);
        mCharacteristicValueSwitch = (RadioGroup) findViewById(R.id.color_switch);


        mNotifyButton.setOnClickListener(this);
        mEnableAdvertisementSwitch.setOnClickListener(this);
        mCharacteristicValueSwitch.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                setCharacteristic(checkedId);
            }
        });

        setGattServer();
        setBluetoothService();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_peripheral_role;
    }


    @Override
    public void onClick(View view) {

        switch(view.getId()) {

            case R.id.advertise_switch:
                Switch switchToggle = (Switch) view;
                if (switchToggle.isChecked()) {
                    startAdvertising();
                } else {
                    stopAdvertising();
                }
                break;


            case R.id.button_notify:
                notifyCharacteristicChanged();
                break;

        }
    }


    @Override
    protected int getTitleString() {
        return R.string.peripheral_screen;
    }


    //Starts BLE Advertising by starting
    private void startAdvertising() {
        startService(getServiceIntent(this));
    }


    //Stops BLE Advertising by stopping
    private void stopAdvertising() {
        stopService(getServiceIntent(this));
        mEnableAdvertisementSwitch.setChecked(false);
    }

    private void setGattServer() {

        mBluetoothDevices = new HashSet<>();
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if (mBluetoothManager != null) {
            mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        } else {
            showMsgText(R.string.error_unknown);
        }
    }

    private void setBluetoothService() {

        // create the Service
        mSampleService = new BluetoothGattService(HEART_RATE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        /*
        create the Characteristic.
        we need to grant to the Client permission to read (for when the user clicks the "Request Characteristic" button).
        no need for notify permission as this is an action the Server initiate.
         */
        mSampleCharacteristic = new BluetoothGattCharacteristic(BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        setCharacteristic(); // set initial state

        // add the Characteristic to the Service
        mSampleService.addCharacteristic(mSampleCharacteristic);

        // add the Service to the Server/Peripheral
        if (mGattServer != null) {
            mGattServer.addService(mSampleService);
        }
    }


    private void setCharacteristic() {
        setCharacteristic(R.id.color_option_1);
    }

    private void setCharacteristic(int checkedId) {
        int value = checkedId == R.id.color_option_1 ? SERVER_MSG_FIRST_STATE : SERVER_MSG_SECOND_STATE;
        mSampleCharacteristic.setValue(getValue(value));
    }

    private byte[] getValue(int value) {
        return new byte[]{(byte) value};
    }

    /*
    send to the client the value of the Characteristic,
    as the user requested to notify.
     */
    private void notifyCharacteristicChanged() {
        /*
        done when the user clicks the notify button in the app.
        indicate - true for indication (acknowledge) and false for notification (un-acknowledge).
         */
        boolean indicate = (mSampleCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE;

        for (BluetoothDevice device : mBluetoothDevices) {
            if (mGattServer != null) {
                mGattServer.notifyCharacteristicChanged(device, mSampleCharacteristic, indicate);
            }
        }
    }


    private Intent getServiceIntent(Context context) {
        return new Intent(context, PeripheralAdvertiseService.class);
    }


    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {

            super.onConnectionStateChange(device, status, newState);

            String msg;

            if (status == BluetoothGatt.GATT_SUCCESS) {

                if (newState == BluetoothGatt.STATE_CONNECTED) {

                    mBluetoothDevices.add(device);

                    msg = "Connected to device: " + device.getAddress();
                    Log.v(MainActivity.TAG, msg);
                    showMsgText(msg);

                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {

                    mBluetoothDevices.remove(device);

                    msg = "Disconnected from device";
                    Log.v(MainActivity.TAG, msg);
                    showMsgText(msg);
                }

            } else {
                mBluetoothDevices.remove(device);

                msg = getString(R.string.status_error_when_connecting) + ": " + status;
                Log.e(MainActivity.TAG, msg);
                showMsgText(msg);

            }
        }


        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.v(MainActivity.TAG, "Notification sent. Status: " + status);
        }


        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {

            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            if (mGattServer == null) {
                return;
            }

            Log.d(MainActivity.TAG, "Device tried to read characteristic: " + characteristic.getUuid());
            Log.d(MainActivity.TAG, "Value: " + Arrays.toString(characteristic.getValue()));

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
        }


        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            Log.v(MainActivity.TAG, "Characteristic Write request: " + Arrays.toString(value));

            mSampleCharacteristic.setValue(value);

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
            }

        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {

            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            if (mGattServer == null) {
                return;
            }

            Log.d(MainActivity.TAG, "Device tried to read descriptor: " + descriptor.getUuid());
            Log.d(MainActivity.TAG, "Value: " + Arrays.toString(descriptor.getValue()));

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.getValue());
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                                             int offset,
                                             byte[] value) {

            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            Log.v(MainActivity.TAG, "Descriptor Write Request " + descriptor.getUuid() + " " + Arrays.toString(value));

        }
    };


}
