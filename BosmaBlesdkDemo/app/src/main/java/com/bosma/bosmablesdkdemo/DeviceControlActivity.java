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
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bosma.blesdk.business.BleParseFactory;
import com.bosma.blesdk.business.bean.CommonSetBean;
import com.bosma.blesdk.business.bean.MeasureResp;
import com.bosma.blesdk.business.bean.RequestData;
import com.bosma.blesdk.business.bean.TempBean;
import com.bosma.blesdk.business.bean.TherConnectBean;
import com.bosma.blesdk.business.bean.TherHistoryBean;
import com.bosma.blesdk.business.bean.TherRealTimeBean;
import com.bosma.blesdk.business.interf.IParseBack;
import com.bosma.blesdk.common.ParseStateCode;

import java.lang.ref.WeakReference;
import java.util.List;

import static com.bosma.blesdk.business.BleParseFactory.getHandle;
import static com.bosma.blesdk.business.BleParseFactory.getTherHandle;
import static com.bosma.blesdk.common.ParseStateCode.BS_PARSE_MEASURE;
import static com.bosma.bosmablesdkdemo.DeviceScanActivity.DN_BOSMA;

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

    private CommHandler readBackHandler;

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
                if(DeviceScanActivity.DN_JUST_FIT.equals(mDeviceName)) {
                    //TODO 单包的情况直接 丢入sdk
                    getHandle().parseFromBle(data);
                } else {
                    //TODO 体温计存在接收多包数据的情况，需要将数据接收全后再丢入 SDK 处理
                    BtReadManager.getIntence(readBackHandler).add(data);
                }
            }
        }
    };



    /**
     * 蓝牙读取解析返回
     */
    public static class ReadBackHandler extends CommHandler {
        private WeakReference<DeviceControlActivity> mService;

        public ReadBackHandler(DeviceControlActivity service, Looper looper) {
            super(looper);
            this.mService = new WeakReference<DeviceControlActivity>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg.obj == null) {
                super.handleMessage(msg);
                return;
            }
            String origin = (String) msg.obj;
            getTherHandle().parseFromBle(origin);
            super.handleMessage(msg);
        }

        @Override
        public String getMessageName(Message message) {
            return super.getMessageName(message);
        }
    }


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
        Button btnFatUnit = (Button) findViewById(R.id.btn_fatunit);

        readBackHandler = new ReadBackHandler(this, getMainLooper());


        btnFatUnit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mDeviceName.startsWith(DN_BOSMA)) {
                    //体温计数据测试（app端主动发起）

                    //1、时间同步命令
//                    String syncTime = BleParseFactory.getTherHandle().parseTimeSync();
//                    Log.i(TAG,"时间同步命令：" + syncTime);
//                    write(syncTime);


//                    //2、防丢命令
//                    String alert = BleParseFactory.getTherHandle().parseAlert(ParseStateCode.ALERT_OFF);
//                    Log.i(TAG,"防丢命令：" + alert);
//                    write(alert);

//                    //3、设置历史温度保存频率命令
                    String hisRate = BleParseFactory.getTherHandle().parseHisRate(60);
                    Log.i(TAG,"历史数据保存频率命令：" + hisRate);
                    write(hisRate);


                    return;
                } else {

                    //体脂秤数据测试（app端主动发起）
                    String fatUnitString = BleParseFactory.getHandle().parseFatAndUnit(ParseStateCode.FAT_ON,ParseStateCode.UNIT_HALF_KG);
                    write(fatUnitString);
                }

            }
        });

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        //初始化测量数据
        RequestData data = new RequestData();
        data.setUserid("your app userid");//用户id，后台用来区分哪个用户测量
        data.setHeight("175");//身高cm
        data.setAge("27");//年龄
        data.setGender("4");//性别：0：男   1：女  4:儿童男  5：儿童女  6：孕妇
        data.setMac(mDeviceAddress);//设备mac地址


        displayData("");



        //************体脂秤
        getHandle()
                .init(ParseStateCode.FAT_ON, ParseStateCode.UNIT_KG)
                .initData(this,data);

        getHandle().initIParseBack(new IParseBack() {
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
                    case ParseStateCode.BS_PARSE_FATUNIT://测脂单位设置返回
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


        //***************体温计
        getTherHandle().initIParseBack(new IParseBack() {
            @Override
            public void parseResp(int code, Object data) {
                switch (code) {
                    case ParseStateCode.BS_ERROR_CHECK://数据错误
                        displayData("error:" + (String) data);
                        break;
                    case ParseStateCode.BS_PARSE_CONNECT://连接命令返回
                        TherConnectBean therConnectBean = (TherConnectBean) data;
                        displayData("to ble:" + therConnectBean.getParse());
                        Log.i(DeviceControlActivity.class.getSimpleName(), therConnectBean.toString());
                        write(therConnectBean.getParse());
                        break;

                    case ParseStateCode.BS_TH_PARSE_RTEMP: //实时温度
                        TherRealTimeBean therRealTimeBean = (TherRealTimeBean) data;
                        displayData("to ble:" + therRealTimeBean.getParse());
                        Log.i(DeviceControlActivity.class.getSimpleName(), therRealTimeBean.getTempObj().getTemp() +"  " + therRealTimeBean.getTempObj().getTime());
                        write(therRealTimeBean.getParse());
                        break;

                    case ParseStateCode.BS_TH_PARSE_HTEMP:  //历史温度
                        TherHistoryBean therHistoryBean = (TherHistoryBean) data;
                        displayData("to ble:" + therHistoryBean.getParse());

                        List<TempBean> tempBeanList = therHistoryBean.getTempList();
                        for(int i = 0; i < tempBeanList.size(); i++) {
                            Log.i(DeviceControlActivity.class.getSimpleName(), tempBeanList.get(i).getTemp() + "  " + tempBeanList.get(i).getTime());
                        }

                        write(therHistoryBean.getParse());

                        break;

                    case ParseStateCode.BS_TH_PARSE_COMMON_SET://时间同步命令、防丢命令、历史数据保存频率命令 解析返回都进入这里

                        CommonSetBean commonSetBean = (CommonSetBean) data;
                        if(commonSetBean.isOk()) {
                            Toast.makeText(DeviceControlActivity.this, "设置成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(DeviceControlActivity.this, "设置成功", Toast.LENGTH_SHORT).show();
                        }

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
