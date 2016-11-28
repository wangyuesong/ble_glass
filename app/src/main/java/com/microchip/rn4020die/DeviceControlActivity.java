/*
 * Copyright (C) 2014 Microchip Technology Inc. and its subsidiaries.  You may use this software and any derivatives
 * exclusively with Microchip products.
 *
 * THIS SOFTWARE IS SUPPLIED BY MICROCHIP "AS IS".  NO WARRANTIES, WHETHER EXPRESS, IMPLIED OR STATUTORY, APPLY TO THIS
 * SOFTWARE, INCLUDING ANY IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR
 * PURPOSE, OR ITS INTERACTION WITH MICROCHIP PRODUCTS, COMBINATION WITH ANY OTHER PRODUCTS, OR USE IN ANY APPLICATION. 
 *
 * IN NO EVENT WILL MICROCHIP BE LIABLE FOR ANY INDIRECT, SPECIAL, PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSS, DAMAGE,
 * COST OR EXPENSE OF ANY KIND WHATSOEVER RELATED TO THE SOFTWARE, HOWEVER CAUSED, EVEN IF MICROCHIP HAS BEEN ADVISED OF
 * THE POSSIBILITY OR THE DAMAGES ARE FORESEEABLE.  TO THE FULLEST EXTENT ALLOWED BY LAW, MICROCHIP'S TOTAL LIABILITY ON
 * ALL CLAIMS IN ANY WAY RELATED TO THIS SOFTWARE WILL NOT EXCEED THE AMOUNT OF FEES, IF ANY, THAT YOU HAVE PAID
 * DIRECTLY TO MICROCHIP FOR THIS SOFTWARE.
 *
 * This file includes code modified from "The Android Open Source Project" copyright (C) 2013.
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
 *
 * MICROCHIP PROVIDES THIS SOFTWARE CONDITIONALLY UPON YOUR ACCEPTANCE OF THESE TERMS. 
 */

package com.microchip.rn4020die;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;
import java.util.HashMap;

/**
 * This Activity receives a Bluetooth device address provides the user interface to connect, display data, and display GATT services
 * and characteristics supported by the device. The Activity communicates with {@code BluetoothLeService}, which in turn
 * interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();      //Get name of activity to tag debug and warning messages

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";                      //Name passed by intent that lanched this activity
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";                //MAC address passed by intent that lanched this activity
    //Should be this 4D696372-6F63-6869-702D-524E34383730
    private static final String MLDP_PRIVATE_SERVICE = "4d696372-6f63-6869-702d-524e34383730";
    private static final String MLDP_DATA_PRIVATE_CHAR = "bf3fbd80-063f-11e5-9e69-0002a5d5c503";
//    private static final String MLDP_PRIVATE_SERVICE = "00035b03-58e6-07dd-021a-08123a000300"; //Private service for Microchip MLDP
//    private static final String MLDP_DATA_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a000301"; //Characteristic for MLDP Data, properties - notify, write
    private static final String MLDP_CONTROL_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a0003ff"; //Characteristic for MLDP Control, properties - read, write
    //Get this by inspecting the characteric_value on phone
    private static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";	//Special UUID for descriptor needed to enable notifications

    private BluetoothAdapter mBluetoothAdapter;                                         //BluetoothAdapter controls the Bluetooth radio in the phone
    private BluetoothGatt mBluetoothGatt;                                               //BluetoothGatt controls the Bluetooth communication link
    private BluetoothGattCharacteristic mDataMDLP, mControlMLDP;                        //The BLE characteristic used for MLDP data transfers

    private Handler mHandler;                                                           //Handler used to send die roll after a time delay

    private TextView mConnectionState, redDieText;                                      //TextViews to show connection state and die roll number on the display
    private Button redDieButton;                                                        //Button to initiate a roll of the die

    private String mDeviceName, mDeviceAddress;                                         //Strings for the Bluetooth device name and MAC address
    private String incomingMessage;                                                     //String to hold the incoming message from the MLDP characteristic
    private boolean mConnected = false;                                                 //Indicator of an active Bluetooth connection
    private boolean writeComplete = false;                                              //Indicator that the characteristic write has completed (for reference - not used)

    private Die redDie;                                                                 //Die object for rolling a number from 1 to 6


    private Spinner directionSpinner;
    private HashMap<String, String> directionMap;

    private Spinner voltageSpinner;
    private HashMap<String, String> voltageMap;
    private Button sendButton;
    private Button testDarkButton;
    private Button testLightButton;

    private TextView lengthText;
    private SeekBar lengthPicker;


    private String direction;
    UIAdapter mUIUpdater;
    private String voltage;
    private int length = 1;
    boolean remoteReady = false;
    // -------------------------------
    // ---------------------------------------------------------------------------------
    // Activity launched
    // Invoked by Intent in onListItemClick method in DeviceScanActivity
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.die_screen);                                            //Show the screen with the die number and button

        final Intent intent = getIntent();                                              //Get the Intent that launched this activity 
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);                        //Get the BLE device name from the Intent
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);                  //Get the BLE device address from the Intent
        mHandler = new Handler();                                                       //Create Handler to delay sending first roll after new connection

        ((TextView) findViewById(R.id.deviceAddress)).setText(mDeviceAddress);          //Display device address on the screen
        mConnectionState = (TextView) findViewById(R.id.connectionState);               //TextView that will display the connection state

        redDie = new Die();                                                             //Create a new Die
//        redDieText = (TextView) findViewById(R.id.textRedDie);                          //TextView that will display the roll of the die
//        redDieText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);                        //Hardware acceleration does not have cache large enough for huge fonts
//        redDieText.setOnClickListener(redDieTextClickListener);                         //Set onClickListener for when text is pressed
//        redDieButton = (Button) findViewById(R.id.buttonRedDie);                        //Button that will roll the die when clicked
//        redDieButton.setOnClickListener(redDieButtonClickListener);                     //Set onClickListener for when button is pressed
        sendButton = (Button)findViewById(R.id.send_button);
        sendButton.setOnClickListener(sendButtonClickListener);
        testDarkButton = (Button)findViewById(R.id.test_dark);
        testDarkButton.setOnClickListener(testDarkButtonClickListener);
        testLightButton = (Button)findViewById(R.id.test_light);
        testLightButton.setOnClickListener(testLightButtonClickListener);

        directionSpinner = (Spinner) findViewById(R.id.direction_spinner);
        ArrayAdapter<CharSequence> directionSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.direction_array, android.R.layout.simple_spinner_item);
        directionSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        directionSpinner.setAdapter(directionSpinnerAdapter);
        directionSpinner.setOnItemSelectedListener(directionSpinnerOnClickListener);

        voltageSpinner = (Spinner) findViewById(R.id.voltage_spinner);
        ArrayAdapter<CharSequence> voltageSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.voltage_array, android.R.layout.simple_spinner_item);
        voltageSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voltageSpinner.setAdapter(voltageSpinnerAdapter);
        voltageSpinner.setOnItemSelectedListener(voltageSpinnerOnClickListener);

        lengthPicker = (SeekBar) findViewById(R.id.length_picker);
        lengthPicker.setOnSeekBarChangeListener(lengthPickerOnValueChangedListener);
        lengthPicker.setMax(15);
        lengthPicker.setProgress(8);
        lengthText = (TextView)findViewById(R.id.length_text);
        lengthText.setText("Length:" + length + " second");

        directionMap = new HashMap<String, String>();
        voltageMap = new HashMap<String, String>();
        directionMap.put("Light->Dark","C");
        directionMap.put("Dark->Light","F");

        voltageMap.put("2.5V","1");
        voltageMap.put("2V","2");
        voltageMap.put("1.5V","3");



        incomingMessage = new String();                                                 //Create new string to hold incoming message data
        this.getActionBar().setTitle(mDeviceName);                                      //Set the title of the ActionBar to the name of the BLE device 
        this.getActionBar().setDisplayHomeAsUpEnabled(true);                            //Make home icon clickable with < symbol on the left 

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE); //Get the BluetoothManager
        mBluetoothAdapter = bluetoothManager.getAdapter();                              //Get a reference to the BluetoothAdapter (radio)
        if (mBluetoothAdapter == null) {                                                //Check if we got the BluetoothAdapter
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show(); //Message that Bluetooth is not supported
            finish();                                                                   //End the activity
        }


        mUIUpdater  = new UIAdapter(new Runnable(){
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!remoteReady) {
                            disableAll();
                            if(mDataMDLP != null) {
                                mDataMDLP.setValue(new byte[]{(byte)66});                     //Set value of MLDP characteristic to send die roll information
                                writeCharacteristic(mDataMDLP);
                            }
                        }
                    }
                });
            }
        });

        mUIUpdater.startUpdates();

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    @Override
    protected void onResume() {
        super.onResume();

        if (mBluetoothAdapter == null || mDeviceAddress == null) {                      //Check that we still have a Bluetooth adappter and device address 
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");     //Warn that something went wrong
            finish();                                                                   //End the Activity
        }

/*      if (mBluetoothGatt != null) {                                                   //See if there is a previous connection
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {                                             //Try to reconnect to the previous device
                Log.w(TAG, "Existing Gatt unable to connect.");                         //Warn that something went wrong
                finish();                                                               //Attempt failed so end the Activity
            }
        }
*/
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress); //Get the Bluetooth device by referencing its address
        if (device == null) {                                                           //Check whether a device was returned
            Log.w(TAG, "Device not found.  Unable to connect.");                        //Warn that something went wrong
            finish();                                                                   //End the Activity
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);                //Directly connect to the device so autoConnect is false
        Log.d(TAG, "Trying to create a new connection.");
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    @Override
    protected void onPause() {
        super.onPause();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity is ending
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothGatt.disconnect();                                                    //Activity is ending so disconnect from Bluetooth device
        mBluetoothGatt.close();                                                         //Close the connection
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether connected or not
    // Show Connect option if not connected or show Disconnect option if we are connected
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);                          //Show the Options menu
        if (mConnected) {                                                               //See if connected
            menu.findItem(R.id.menu_connect).setVisible(false);                         // then dont show disconnect option
            menu.findItem(R.id.menu_disconnect).setVisible(true);                       // and do show connect option
        }
        else {                                                                          //If not connected
            menu.findItem(R.id.menu_connect).setVisible(true);                          // then show connect option
            menu.findItem(R.id.menu_disconnect).setVisible(false);                      // and don't show disconnect option
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Connect or disconnect to BLE device
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {                                                     //Get which menu item was selected
            case R.id.menu_connect:                                                     //Option to Connect chosen
                if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
                    mBluetoothGatt.connect();                                           // then connect
                }
                return true;
            case R.id.menu_disconnect:                                                  //Option to Disconnect chosen
                if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
                    mBluetoothGatt.disconnect();                                        // then disconnect
                }
                return true;
            case android.R.id.home:                                                     //Option to go back was chosen
                onBackPressed();                                                        //Execute functionality of back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Update text with connection state
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);                                   //Update text to say "Connected" or "Disconnected"
//                redDieText.setText(null);                                               //Reset die text to blank when connection changes
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Update text roll of die and send over Bluetooth
    private void updateDieState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDataMDLP.setValue("=>R" + redDie.Roll()+ "\r\n");                     //Set value of MLDP characteristic to send die roll information
                writeCharacteristic(mDataMDLP);                                         //Call method to write the characteristic
            }
        });
    }
    private void sendVoltageAndDirection(){
        runOnUiThread(new Runnable() {
//            @Override
            public void run() {

                mDataMDLP.setValue(getByteRepresentation(direction + voltage));                     //Set value of MLDP characteristic to send die roll information
                writeCharacteristic(mDataMDLP);                                         //Call method to write the characteristic
            }
        });
    };
    private void sendLength(){
        runOnUiThread(new Runnable() {
            //            @Override
            public void run() {
                String prefix = "A";
                if(direction.equals("F")){
                    prefix = "D";
                }
                mDataMDLP.setValue(getByteRepresentation(prefix + Integer.toHexString(length)));                     //Set value of MLDP characteristic to send die roll information
                writeCharacteristic(mDataMDLP);                                         //Call method to write the characteristic
            }
        });
    };
    // Input String version of two Hex num, e.g 11
    // One char is a byte. We only need each byte's first 4 bit(enough for 11 ~ ff), then combine them to one byte and send it out.
    private byte[] getByteRepresentation(String s){
        byte[] bytes = new byte[1];
        int firstByte = Character.digit(s.charAt(0), 16);
        int secondByte = Character.digit(s.charAt(1),16);
        int result = (firstByte << 4) | secondByte;
        bytes[0] = (byte)(result & 0xff);
        return bytes;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Look for message with switch pressed indicator "->S1\n\r"
    private void processIncomingPacket(String data) {
        char switchState;
        int indexStart, indexEnd;
        incomingMessage = incomingMessage.concat(data);                                 //Add the new data to what is left of previous data
        if (incomingMessage.length() >= 6 && incomingMessage.contains("=>S") && incomingMessage.contains("\r\n")) { //See if we have the right nessage
            indexStart = incomingMessage.indexOf("=>S");                                //Get the position of the matching characters
            indexEnd = incomingMessage.indexOf("\r\n");                                 //Get the position of the end of frame "\r\n"
            if (indexEnd - indexStart == 4) {                                           //Check that the packet does not have missing or extra characters 
                switchState = incomingMessage.charAt(indexStart + 3);                   //Get the character that represents the switch being pressed
                if (switchState == '1') {                                               //Is it a "1"
                    updateDieState();                                                   // if so then update the state of the die with a new roll and send over BLE
                }
            }
            incomingMessage = incomingMessage.substring(indexEnd + 2);                  //Thow away everything up to and including "\n\r" 
        }
        else if (incomingMessage.contains("\r\n")) {                                    //See if we have an end of frame "\r\n" without a valid message
            incomingMessage = incomingMessage.substring(incomingMessage.indexOf("\r\n") + 2); //Thow away everything up to and including "\n\r" 
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Listener for the roll red die button
    private final Button.OnClickListener redDieButtonClickListener = new Button.OnClickListener() {

        public void onClick(View view) {                                                //Button was clicked
            updateDieState();                                                           //Update the state of the die with a new roll and send over BLE
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Listener for the red die text
    private final TextView.OnClickListener redDieTextClickListener = new TextView.OnClickListener() {

        public void onClick(View view) {                                                //Die text was clicked
            updateDieState();                                                           //Update the state of the die with a new roll and send over BLE
        }
    };

    private final Button.OnClickListener sendButtonClickListener = new Button.OnClickListener() {

        public void onClick(View view) {                                                //Button was clicked
//            updateDieState();                                                           //Update the state of the die with a new roll and send over BLE
            sendLength();
        }
    };
    private final Button.OnClickListener testLightButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if(mDataMDLP != null) {
                        mDataMDLP.setValue(getByteRepresentation("B1"));                     //Set value of MLDP characteristic to send die roll information
                        writeCharacteristic(mDataMDLP);
                    }//Call method to write the characteristic
                }
            });
        }
    };

    private final Button.OnClickListener testDarkButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(mDataMDLP != null) {
                mDataMDLP.setValue(getByteRepresentation("B2"));                     //Set value of MLDP characteristic to send die roll information
                writeCharacteristic(mDataMDLP);
            }//Call method to write the characteristic
        }
    };

    private final SeekBar.OnSeekBarChangeListener lengthPickerOnValueChangedListener = new SeekBar.OnSeekBarChangeListener(){

        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
            length = progress;
        }
        public void onStartTrackingTouch(SeekBar seekBar) {
        }
        public void onStopTrackingTouch(SeekBar seekBar) {
            lengthText.setText("Length:" + length + " second");
            Toast.makeText(DeviceControlActivity.this,"Set length to : "+ length + " second",
                    Toast.LENGTH_SHORT).show();
        }

    };


    private final Spinner.OnItemSelectedListener directionSpinnerOnClickListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String selectedValue = parent.getItemAtPosition(position).toString();
            direction = directionMap.get(selectedValue);
            if(mDataMDLP != null)
                sendVoltageAndDirection();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };
    private final Spinner.OnItemSelectedListener voltageSpinnerOnClickListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String selectedValue = parent.getItemAtPosition(position).toString();
            voltage = voltageMap.get(selectedValue);
            if(mDataMDLP != null)
                sendVoltageAndDirection();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };


    // ----------------------------------------------------------------------------------------------------------------
    // Iterate through the supported GATT Services/Characteristics to see if the MLDP srevice is supported
    private void findMldpGattService(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {                                                     //Verify that list of GATT services is valid
            Log.d(TAG, "findMldpGattService found no Services");
            return;
        }
        String uuid;                                                                    //String to compare received UUID with desired known UUIDs
        mDataMDLP = null;                                                               //Searching for a characteristic, start with null value

        for (BluetoothGattService gattService : gattServices) {                         //Test each service in the list of services
            uuid = gattService.getUuid().toString();                                    //Get the string version of the service's UUID
            if (uuid.equals(MLDP_PRIVATE_SERVICE)) {                                    //See if it matches the UUID of the MLDP service 
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics(); //If so then get the service's list of characteristics
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Test each characteristic in the list of characteristics
                    uuid = gattCharacteristic.getUuid().toString();                     //Get the string version of the characteristic's UUID
                    if (uuid.equals(MLDP_DATA_PRIVATE_CHAR)) {                          //See if it matches the UUID of the MLDP data characteristic
                        mDataMDLP = gattCharacteristic;                                 //If so then save the reference to the characteristic 
                        Log.d(TAG, "Found MLDP data characteristics");
                    }
                    else if (uuid.equals(MLDP_CONTROL_PRIVATE_CHAR)) {                  //See if UUID matches the UUID of the MLDP control characteristic
                        mControlMLDP = gattCharacteristic;                              //If so then save the reference to the characteristic
                        Log.d(TAG, "Found MLDP control characteristics");
                    }
                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables notification on the server
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                        mBluetoothGatt.writeDescriptor(descriptor);                     //Write the descriptor
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) { //See if the characteristic has the Indicate property
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification (and indication) in the BluetoothGatt
                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables indication on the server
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); //Set the value of the descriptor to enable indication
                        mBluetoothGatt.writeDescriptor(descriptor);                     //Write the descriptor
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (acknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with acknowledge) in the BluetoothGatt
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                    }
                }
                break;                                                                  //Found the MLDP service and are not looking for any other services
            }
        }
        if (mDataMDLP == null) {                                                        //See if the MLDP data characteristic was not found
            Toast.makeText(this, R.string.mldp_not_supported, Toast.LENGTH_SHORT).show(); //If so then show an error message
            Log.d(TAG, "findMldpGattService found no MLDP service");
            finish();                                                                   //and end the activity
        }
        mHandler.postDelayed(new Runnable() {                                           //Create delayed runnable that will send a roll of the die after a delay
            @Override
            public void run() {
                updateDieState();                                                       //Update the state of the die with a new roll and send over BLE
            }
        }, 200);                                                                        //Do it after 200ms delay to give the RN4020 time to configure the characteristic

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Implements callback methods for GATT events that the app cares about.  For example: connection change and services discovered.
    // When onConnectionStateChange() is called with newState = STATE_CONNECTED then it calls mBluetoothGatt.discoverServices()
    // resulting in another callback to onServicesDiscovered()
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) { //Change in connection state
            if (newState == BluetoothProfile.STATE_CONNECTED) {                         //See if we are connected
                Log.i(TAG, "Connected to GATT server.");
                mConnected = true;                                                      //Record the new connection state
                updateConnectionState(R.string.connected);                              //Update the display to say "Connected"
                invalidateOptionsMenu();                                                //Force the Options menu to be regenerated to show the disconnect option
                mBluetoothGatt.discoverServices();                                      // Attempt to discover services after successful connection.
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                 //See if we are not connected
                Log.i(TAG, "Disconnected from GATT server.");
                mConnected = false;                                                     //Record the new connection state
                updateConnectionState(R.string.disconnected);                           //Update the display to say "Disconnected"
                invalidateOptionsMenu();                                                //Force the Options menu to be regenerated to show the connect option
                remoteReady = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {              //Service discovery complete
            if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGatt != null) {       //See if the service discovery was successful
                findMldpGattService(mBluetoothGatt.getServices());                      //Get the list of services and call method to look for MLDP service
            }
            else {                                                                      //Service discovery was not successful
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        //For information only. This application uses Indication to receive updated characteristic data, not Read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Read has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the read was successful
            String dataValue = characteristic.getStringValue(0);                        //Get the value of the characteristic
            processIncomingPacket(dataValue);                                           //Process the data that was received
            }
        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Write has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the write was successful
                writeComplete = true;                                                   //Record that the write has completed
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) { //Indication or notification was received
            byte[] bytesValue = characteristic.getValue();
//            String dataValue = characteristic.getStringValue(0);                        //Get the value of the characteristic
//            processIncomingPacket(dataValue);                                           //Process the data that was received
            int a = (int)bytesValue[0];
            if(a == ProtocolConstants.COMMAND_RECEIVED){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DeviceControlActivity.this, R.string.command_sent_to_glass, Toast.LENGTH_LONG).show();
                        disableAll();
                    }
                });
            }
            else if(a == ProtocolConstants.COMMAND_FINISHED){
                //Remote ready, enable everything
                remoteReady = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DeviceControlActivity.this, R.string.command_finished, Toast.LENGTH_SHORT).show();
                        enableAll();
                    }
                });
            }
        }
    };



    // ----------------------------------------------------------------------------------------------------------------
    // Request a read of a given BluetoothGattCharacteristic. The Read result is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicRead callback method.
    // For information only. This application uses Indication to receive updated characteristic data, not Read

    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);                              //Request the BluetoothGatt to Read the characteristic
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Write to a given characteristic. The completion of the write is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicWrire callback method.
    private void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        int test = characteristic.getProperties();                                      //Get the properties of the characteristic
        if ((test & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 && (test & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) { //Check that the property is writable 
            return;
        }

        if (mBluetoothGatt.writeCharacteristic(characteristic)) {                       //Request the BluetoothGatt to do the Write
            Log.d(TAG, "writeCharacteristic successful");                               //The request was accepted, this does not mean the write completed
        }
        else {
            Log.d(TAG, "writeCharacteristic failed");                                   //Write request was not accepted by the BluetoothGatt
        }
    }

    private void disableAll(){
        sendButton.setEnabled(false);
        testLightButton.setEnabled(false);
        testDarkButton.setEnabled(false);
        voltageSpinner.setEnabled(false);
        directionSpinner.setEnabled(false);
        lengthPicker.setEnabled(false);
    }

    private void enableAll(){
        sendButton.setEnabled(true);
        testLightButton.setEnabled(true);
        testDarkButton.setEnabled(true);
        voltageSpinner.setEnabled(true);
        directionSpinner.setEnabled(true);
        lengthPicker.setEnabled(true);
    }

}
