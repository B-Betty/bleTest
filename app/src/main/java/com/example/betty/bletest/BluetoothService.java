package com.example.betty.bletest;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by betty on 2017/9/24.
 */

public class BluetoothService extends Service{
    private static final String TAG = BluetoothService.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private static BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private static BluetoothGatt mBluetoothGatt;
    private static BluetoothGattCharacteristic mBluetoothGattCharacteristic;//ble终端的特征值
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 1;

    private int mConnectionState = STATE_CONNECTED;
    private final IBinder mbinder = new LocalBilder();
    private List<BluetoothGattService> bluetoothGattServiceList = new ArrayList<>();

    public final static String ACTION_GATT_CONNECTED = "com.example.betty.bletest.BluetoothService.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.betty.bletest.BluetoothService.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICE_DISCOVERED = "com.example.betty.bletest.BluetoothService.ACTION_GATT_SERVICE_DISCOVERED";
    public final static String ACTION_DATA_AVALIABLE = "com.example.betty.bletest.BluetoothService.ACTION_DATA_AVALIABLE";
    public final static String EXTEA_DATA = "com.example.betty.bletest.BluetoothService.EXTEA_DATA";

    public class LocalBilder extends Binder{
        BluetoothService getService(){
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mbinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {

        return super.onUnbind(intent);
    }

    /**
     * 初始化BluetoothManager  BluetoothAdapter
     */
    public boolean initialize(){
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager == null){
            Log.i(TAG,"------unable initialize BluetoothManager------");
            return false;
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null){
           Log.i(TAG,"------unable obtain BluetoothAdapter");
            return false;
        }
        return true;
    }
    /**
     * 连接设备
     */
    public boolean connectBle(String mDeviceAddress){
        if (mBluetoothAdapter == null && mDeviceAddress ==null){
            Log.i(TAG, "-----BluetoothAdapter not initialize or unspecified mDeviceAddress");
            return false;
        }
        // Previously connected device. Try to reconnect. (先前连接的设备。 尝试重新连接)
        if (mBluetoothDeviceAddress != null &&mBluetoothGatt != null && !mDeviceAddress.equals(mBluetoothDeviceAddress)){
            Log.i(TAG,"Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()){
                mConnectionState = STATE_CONNECTED;
                return true;
            }else
                return false;
        }
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
        if (mDeviceAddress == null){
            Log.i(TAG,"---device not found, unable to connect");
            return false;
        }
        mBluetoothGatt = device.connectGatt(this,false,mBluetoothGattCallback); //这里才是真正连接
        Log.i(TAG,"Trying to create a new connection");
//
        mBluetoothDeviceAddress = mDeviceAddress;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void broadCastUpdate(String action){
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    public void broadCastUpdate(String action, BluetoothGattCharacteristic bluetoothGattCharacteristic){
        final Intent intent = new Intent(action);
        final byte[] data = bluetoothGattCharacteristic.getValue();
        if (data != null && data.length > 0){
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte bytechar : data){
                stringBuilder.append(String.format("%2x",bytechar));//字节类型转换为十六进制的字符
            }
            intent.putExtra(EXTEA_DATA, stringBuilder.toString());
        }
        sendBroadcast(intent);
    }

    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        //当连接上设备或者失去连接时会回调该函数
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED){//连接成功
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadCastUpdate(intentAction);
                Log.i(TAG,"connect to GATT Service");
                Log.i(TAG,"starting service diacovery" + mBluetoothGatt.discoverServices());//启动发现服务
            }else if (newState == BluetoothProfile.STATE_DISCONNECTING){
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                if (mBluetoothGatt == null){
                    mBluetoothGatt.close();
                }
                broadCastUpdate(intentAction);
                Log.i(TAG, "disconnected from GATT Service");
            }
        }
        //当设备是否找到服务时，会回调该函
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS){
                bluetoothGattServiceList = getSupportedGattServices();
                //打印所有服务和特征值
                List<BluetoothGattService> bluetoothGattServices = mBluetoothGatt.getServices();//获取所有的特征值
                for (int i = 0; i < bluetoothGattServices.size(); i++) {
                    Log.e("AAAAAAA","1 : BluetoothGattService UUID = : " + bluetoothGattServices.get(i).getUuid());
                    List<BluetoothGattCharacteristic> bluetoothGattCharacteristic = bluetoothGattServices.get(i).getCharacteristics();
                    for (int j = 0; j < bluetoothGattCharacteristic.size(); j++) {
                        int charaPro = bluetoothGattCharacteristic.get(j).getProperties();
                        Log.e("aaaaaaa", "2 : BluetoothGattCharacteristic UUID = : " + bluetoothGattCharacteristic.get(j).getUuid());
                    }
                }
                serviceToGatt();//发现服务读取特征值
                broadCastUpdate(ACTION_GATT_SERVICE_DISCOVERED);
            }else
                Log.i(TAG,"onServicesDiscovered received " + status);
        }

        //当读取设备时会回调该函数
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG, "----onCharacteristicRead-----");
            if (status == BluetoothGatt.GATT_SUCCESS){
                broadCastUpdate(ACTION_DATA_AVALIABLE, characteristic);
            }
        }
        //当向设备Descriptor中写数据时，会回调该函数
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS)
                Log.i(TAG,"---onCharacteristicWrite success--");
            else
                Log.i(TAG, "---onCharacteristicWrite fail");
        }
        //设备发出通知时会调用到该接口
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String uuid = characteristic.toString().toString();
            broadCastUpdate(ACTION_DATA_AVALIABLE, characteristic);
        }
    };

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> BluetoothGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();


    private List<BluetoothDevice>  BluetoothDevices = new ArrayList<>();
    private void serviceToGatt() {
        String unKnownName = getResources().getString(R.string.unkonwnDeviceName);
        String unKnownUuid = getResources().getString(R.string.unkonwnDeviceUUID);
        ArrayList<HashMap<String,String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String,String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String,String>>>();
        String uuid = null;
        for (BluetoothGattService gattService : bluetoothGattServiceList){
            List<BluetoothGattCharacteristic> BluetoothGattCharacteristicList = new ArrayList<>();
            BluetoothGattCharacteristicList = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : BluetoothGattCharacteristicList){
                uuid = gattCharacteristic.getUuid().toString();
                if (uuid.equals(GattAtributes.CHARCHTERISTIC_LIGHT)){
                    mBluetoothGattCharacteristic = gattCharacteristic;
//                    value = gattCharacteristic.getValue();
//                    Log.e("----特征值对应的值---- : ", value.toString());
                }
            }
        }
    }
    private byte[] value = new byte[4096];


    /**
     * 通过向通过向characteristic写入指令（发送指令），以达到控制BLE终端设备的目的
     * @param src
     */
    public static void write(byte[] src){
        if (mBluetoothGatt == null || mBluetoothAdapter == null){
            Log.i(TAG,"BluetoothAdapter is not initialize");
            return;
        }
        mBluetoothGattCharacteristic.setValue(src);
        mBluetoothGatt.writeCharacteristic(mBluetoothGattCharacteristic);
    }

    public List<BluetoothGattService> getSupportedGattServices(){
        if (mBluetoothGatt == null)
            return null;
        else
            return mBluetoothGatt.getServices();
    }

    /**
     * 断开连接
     */
    public void disconnect(){
        if (mBluetoothAdapter == null && mBluetoothGatt == null){
            Log.i(TAG, "BluetoothAdapter is not initialed");
            return ;
        }
        if (mConnectionState == STATE_CONNECTING | mConnectionState == STATE_CONNECTED){
            mBluetoothGatt.disconnect();
        }
    }
}
