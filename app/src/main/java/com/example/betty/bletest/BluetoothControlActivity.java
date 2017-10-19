package com.example.betty.bletest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ProviderInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import static com.example.betty.bletest.R.id.cancel_action;
import static com.example.betty.bletest.R.id.close;

/**
 * Created by betty on 2017/9/26.
 */

public class BluetoothControlActivity extends AppCompatActivity  implements View.OnClickListener{
    private Button btn_open;
    private Button btn_close;
    private static final Byte LIGHT_OPEN = 1;
    private static final Byte LIGHT_CLOSE = 0;
    private BluetoothService mBluetoothService;
    private boolean GATT_SERVICE = true;
    private BluetoothDevice device;
    private byte[] deviceByte = new byte[1];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ancivity_control);

        Bundle bundle =getIntent().getExtras();
        device = bundle.getParcelable(MainActivity.CONNECTED_DEVICE);

        btn_open = (Button) findViewById(R.id.open);
        btn_close = (Button) findViewById(R.id.close);

        btn_open.setOnClickListener(this);
        btn_close.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent serviceIntent = new Intent(BluetoothControlActivity.this, BluetoothService.class);
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBluetoothService = ((BluetoothService.LocalBilder)service).getService();
            GATT_SERVICE = true;
            Toast.makeText(mBluetoothService, "连接至"+device.getName(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothService = null;
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.open:
                deviceByte[0] = LIGHT_OPEN;
                BluetoothService.write(deviceByte);
                break;
            case R.id.close:
                deviceByte[0] = LIGHT_CLOSE;
                BluetoothService.write(deviceByte);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothService.disconnect();
        unbindService(mServiceConnection);
        unbindService(mServiceConnection);
    }
}
