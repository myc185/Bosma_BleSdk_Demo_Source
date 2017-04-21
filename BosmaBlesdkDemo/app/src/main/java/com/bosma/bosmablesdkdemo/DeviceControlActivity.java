/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bosma.bosmablesdkdemo;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.bosma.blesdk.business.BleParseFactory;
import com.bosma.blesdk.business.bean.MeasureResp;
import com.bosma.blesdk.business.bean.RequestData;
import com.bosma.blesdk.business.interf.IParseBack;
import com.bosma.blesdk.common.ParseStateCode;

import java.util.List;

import static com.bosma.blesdk.common.ParseStateCode.BS_PARSE_MEASURE;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                displayData(data);
                BleParseFactory.getHandle().parseFromBle(data);
            }
        }
    };


    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        //初始化测量数据
        RequestData data = new RequestData();
        data.setUserid("your app userid");
        data.setHeight("175");
        data.setAge("27");
        data.setGender("0");
        data.setMac(mDeviceAddress);


        displayData("");
        BleParseFactory.getHandle()
                .init(ParseStateCode.FAT_ON, ParseStateCode.UNIT_KG)
                .initData(this,data);

        BleParseFactory.getHandle().initIParseBack(new IParseBack() {
            @Override
            public void parseResp(int code, Object data) {
                switch (code) {
                    case ParseStateCode.BS_ERROR_CHECK://数据错误
                        displayData("error:" + (String) data);
                        break;
                    case ParseStateCode.BS_ERROR_HTTP://网络请求失败
                        Toast.makeText(DeviceControlActivity.this,(String)data,Toast.LENGTH_SHORT).show();
                        break;
                    case ParseStateCode.BS_PARSE_CONNECT://连接命令返回
                        //需要往蓝牙写入
                        displayData("to ble:" + (String) data);
                        write((String) data);
                        break;
                    case BS_PARSE_MEASURE://测量数据解析返回
                        //需要往蓝牙写入
                        displayData("to ble:"+ (String) data);
                        write((String) data);
                        break;
                    case ParseStateCode.BS_PARSE_FATUNIT://测脂单位设置
                        String result = (String)data;
                        if("00".equals(result)) {
                            Toast.makeText(DeviceControlActivity.this,"测脂或单位设置成功",Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(DeviceControlActivity.this,"测脂或单位设置失败",Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case ParseStateCode.BS_DATA_WEIGH://蓝牙测量数据返回后，不经过网络请求，直接返回体重数据
                        Toast.makeText(DeviceControlActivity.this,"测量体重是：" + (float)data,Toast.LENGTH_SHORT).show();
                        break;

                    case ParseStateCode.BS_DATA_MEASURE://测量数据（包含八大测量数据）
                        MeasureResp measureResp = (MeasureResp) data;
                        Toast.makeText(DeviceControlActivity.this,"测量体脂是：" + measureResp.getBodyFat(),Toast.LENGTH_SHORT).show();
                        break;


                    default:
                        break;
                }
            }
        });
    }

    private void write(String data) {
        byte[] send = BlueUtils.hexStringToBytes(data);
        try {
            if (mNotifyCharacteristic == null) {
                displayData("no characteristic found, operation stop");
                return;
            }
            mNotifyCharacteristic.setValue(send);
            mBluetoothLeService.writeCharacteristic(mNotifyCharacteristic);
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            Log.i(TAG,"data:"+data);
            mDataField.append(data+"\n");
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            String serviceUid = gattService.getUuid().toString();
            Log.w(TAG,"=======" + serviceUid +"=======");
            if (serviceUid.equalsIgnoreCase(SampleGattAttributes.UUID.UUID_SERVICE.toString())) {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    if (gattCharacteristic.getUuid().toString().equalsIgnoreCase(SampleGattAttributes.UUID.UUID_TX_CHAR_NOTIFY.toString())) {
                        mBluetoothLeService.setCharacteristicNotification(
                                gattCharacteristic, true);
                    } else if (gattCharacteristic.getUuid().toString().equalsIgnoreCase(SampleGattAttributes.UUID.UUID_RX_CHAR.toString())) {
                        mNotifyCharacteristic = gattCharacteristic;
                    }
                }
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
