package com.example.betty.bletest;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.location.LocationManager;
import android.media.audiofx.BassBoost;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import static com.example.betty.bletest.R.id.progressbar;
import static com.example.betty.bletest.R.id.scan_device_switch;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener{
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_COARSE_GPS = 111;
    private Switch mSwitch;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandle = new Handler();
    private boolean mscanning;
    private ProgressBar mProgressBar;
    private ProgressBar mConnectionProgressBar;
    private MyAdapter myAdapter;
    private List<BluetoothDevice> mAvaliableDevice = new ArrayList<>();
    private ListViewForScrollView listView;
    private String mdeviceName;
    private String mdeviceAddress;
    private BluetoothService myBluetoothDevice;
    private boolean mServiceConnected = false;
    private boolean mGattConnected = false;
    public static final String CONNECTED_DEVICE = "CONNECTED_DEVICE";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar = (ProgressBar) findViewById(R.id.progressbar);
        mConnectionProgressBar = (ProgressBar) findViewById(R.id.connectProgressbar);
        mSwitch = (Switch) findViewById(scan_device_switch);
        listView = (ListViewForScrollView) findViewById(R.id.lv);
        mSwitch.setOnCheckedChangeListener(this);

        //检查是否支持ble
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this, "不支持蓝牙设备", Toast.LENGTH_SHORT).show();
            finish();
        }
        //获取蓝适配器
        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICE_DISCOVERED);
        intentFilter.addAction(BluetoothService.ACTION_DATA_AVALIABLE);
        registerReceiver(mGattReceive, intentFilter);
        //打开蓝牙使能
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            Intent enableBlueIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBlueIntent,REQUEST_ENABLE_BT);
        }
        myAdapter = new MyAdapter(MainActivity.this,mAvaliableDevice);
        listView.setAdapter(myAdapter);
        listView.setOnItemClickListener(new DeviceListItemClick());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            //开启蓝牙
            finish();
            return;
        }
    }
    /**
     * Adapter适配器中item被点击时候回调
     */
    private BluetoothDevice device;
    private class DeviceListItemClick implements AdapterView.OnItemClickListener {
        /**
         * @param parent 发生点击动作的AdapterView
         * @param view  在AdapterView中被点击的视图(它是由adapter提供的一个视图)。
         * @param position 视图在adapter中的位置。
         * @param id 被点击元素的行id。
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            scanbleDevice(false);
            device = mAvaliableDevice.get(position);
            mdeviceName = device.getName();
            mdeviceAddress = device.getAddress();
            Intent getServiceIntent = new Intent(MainActivity.this,BluetoothService.class);
            boolean bindservice = bindService(getServiceIntent, mServiceConnection,BIND_AUTO_CREATE);
            if (bindservice){
                Log.e(TAG,"---------bindService success");
            }else
                Log.e(TAG,"---------bindService fail");
        }
    }
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        //当与service建立连接时调用
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myBluetoothDevice = ((BluetoothService.LocalBilder) service).getService();
            if (! myBluetoothDevice.initialize()){
                Log.i(TAG, "-----unable to initialize Bluetooth");
                finish();
            }
            mServiceConnected = true;
            myBluetoothDevice.connectBle(mdeviceAddress);
            mConnectionProgressBar.setVisibility(View.VISIBLE);
        }
        //当与service的连接意外断开时调用
        @Override
        public void onServiceDisconnected(ComponentName name) {
            myBluetoothDevice = null;
        }
    };

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()){
            case R.id.scan_device_switch:
                if (isChecked){
                    mAvaliableDevice.clear();
                    myAdapter.notifyDataSetChanged();
                    //如果 API level 是大于等于 23(Android 6.0) 时
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                        //判断是否具有权限
                        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                            //请求开启ACCESS_COARSE_LOCATION位置权限
                            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_ENABLE_COARSE_GPS);
                            return;
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                        if (isGpsEnable(MainActivity.this)){
                            scanbleDevice(true);
                        }else{
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(intent,REQUEST_ENABLE_COARSE_GPS);
                            scanbleDevice(false);

                        }
                    }
                    scanbleDevice(true);
                }else
                    scanbleDevice(false);
                break;
                default:
                    break;
        }
    }

    /**
     * ActivityCompat.requestPermissions()对应与onRequestPermissionsResult（）
     * 执行完上面的请求权限后，系统会弹出提示框让用户选择是否允许改权限。选择的结果可以在回到接口中得知：
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_ENABLE_COARSE_GPS:
                if (permissions[0].equals(Manifest.permission.ACCESS_COARSE_LOCATION) && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mSwitch.setChecked(true);
                    scanbleDevice(true);
                }else {
                    //为了帮助查找用户可能需要解释的情形
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_COARSE_LOCATION)){
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("功能限制").setMessage("该APP需要访问GPS权限，不开启将无法正常工作").setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                      builder.show();
                        return;
                    }
                }
                break;
        }
    }

    //gps
    public static final boolean isGpsEnable(final Context context){
        LocationManager mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps || network)
            return true;
        else
            return false;
    }
    /**
     * 搜索蓝牙设备
     */
    private void scanbleDevice(boolean enable){
        if (enable){
            mscanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            Log.d(TAG,"开始搜索");
            Toast.makeText(this, "开始扫描", Toast.LENGTH_SHORT).show();
            mHandle.postDelayed(runnable,20000);
        }else {
            mscanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mHandle.removeCallbacks(runnable);
        }
        switchButton();
    }

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            mscanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            switchButton();
        }
    };

    private void switchButton(){
        if (!mscanning){
            mSwitch.setChecked(false);
            mProgressBar.setVisibility(View.GONE);
        }else {
            mSwitch.setChecked(true);
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!mAvaliableDevice.contains(device)){
                        mAvaliableDevice.add(device);
                    }
                    Log.d(TAG,"DEVICE" + device.getName());
                    myAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    public class MyAdapter extends BaseAdapter{
        private List<BluetoothDevice> devices;
        private Context context;
        public MyAdapter(Context context,List<BluetoothDevice> devices){
            this.context = context;
            this.devices = devices;
        }
        @Override
        public int getCount() {
            return mAvaliableDevice.size();
        }

        @Override
        public Object getItem(int position) {
            return mAvaliableDevice.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null){
                viewHolder = new ViewHolder();
                convertView = LayoutInflater.from(context).inflate(R.layout.list_item,null);
                viewHolder.deviceName = (TextView) convertView.findViewById(R.id.deviceName);
                convertView.setTag(viewHolder);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }
            BluetoothDevice device = devices.get(position);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0){
                viewHolder.deviceName.setText(device.getName());
            }else {
                viewHolder.deviceName.setText("未搜索到设备");
            }
            return convertView;
        }

    }
    class ViewHolder{
        TextView deviceName;
    }
    private Intent intentBroadcast;
    private BroadcastReceiver mGattReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothService.ACTION_GATT_CONNECTED.equals(action)){

            }else if (BluetoothService.ACTION_DATA_AVALIABLE.equals(action)){

            }else if (BluetoothService.ACTION_GATT_DISCONNECTED.equals(action)){
                Toast.makeText(context, "与BLE终端连接失败", Toast.LENGTH_SHORT).show();
            }else if (BluetoothService.ACTION_GATT_SERVICE_DISCOVERED.equals(action)){
                mGattConnected = true;
                mAvaliableDevice.clear();
                myAdapter.notifyDataSetChanged();
                mConnectionProgressBar.setVisibility(View.GONE);
                intentBroadcast = new Intent(MainActivity.this,BluetoothControlActivity.class);
                Bundle bundle = new Bundle();
                bundle.putParcelable(CONNECTED_DEVICE,device);
                intentBroadcast.putExtras(bundle);
                startActivity(intentBroadcast);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mGattReceive);
    }
}
