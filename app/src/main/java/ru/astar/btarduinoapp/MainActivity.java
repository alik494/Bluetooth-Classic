package ru.astar.btarduinoapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        View.OnClickListener {
    public static final int REQUEST_CODE_LOC = 1;
    private static final long START_TIME_IN_MILLIS = 5000;
    private GraphView graph;
    int numberOfElements;
    private static final String TAG = MainActivity.class.getSimpleName();
    //перемикач для читання тестового лог файлу
    public static boolean isTest = false;
    //назва файлу який свториться після отримання даних по блютуз каналу
    private static final String FILE_NAME = "logFileFromBlueTooth";

    //початок інінціалізаціїї
    private static final int REQ_ENABLE_BT = 10;
    public static final int BT_BOUNDED = 21;
    public static final int BT_SEARCH = 22;
    private FrameLayout frameMessage;
    private LinearLayout frameControls;
    private RelativeLayout frameLedControls;
    private Button btnDisconnect;
    private Button btn_send_message;
    private Button btn_create_graph;
    private EditText et_input_comand;
    private Switch switchRedLed;
    private Switch switchGreenLed;
    private EditText etConsole;
    private StringBuffer sbgolbal = new StringBuffer();
    private int counter =0;
    private Switch switchEnableBt;
    private Button btnEnableSearch;
    private ProgressBar pbProgress;
    private ListView listBtDevices;
    private BluetoothAdapter bluetoothAdapter;
    private BtListAdapter listAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        frameMessage = findViewById(R.id.frame_message);
        frameControls = findViewById(R.id.frame_control);
        switchEnableBt = findViewById(R.id.switch_enable_bt);
        btnEnableSearch = findViewById(R.id.btn_enable_search);
        pbProgress = findViewById(R.id.pb_progress);
        listBtDevices = findViewById(R.id.lv_bt_device);
        frameLedControls = findViewById(R.id.frameLedControls);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        switchGreenLed = findViewById(R.id.switch_led_green);
        switchRedLed = findViewById(R.id.switch_led_red);
        etConsole = findViewById(R.id.et_console);
        btn_send_message = findViewById(R.id.btn_send_message);
        btn_create_graph = findViewById(R.id.btn_create_graph);
        et_input_comand = findViewById(R.id.et_input_comand);
        graph = (GraphView) findViewById(R.id.graphView);
        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);
        btnDisconnect.setOnClickListener(this);
        switchGreenLed.setOnCheckedChangeListener(this);
        switchRedLed.setOnCheckedChangeListener(this);
        bluetoothDevices = new ArrayList<>();
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getString(R.string.connecting));
        progressDialog.setMessage(getString(R.string.please_wait));
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "onCreate: " + getString(R.string.bluetooth_not_supported));
            finish();
        }
        if (bluetoothAdapter.isEnabled()) {
            showFrameControls();
            switchEnableBt.setChecked(true);
            setListAdapter(BT_BOUNDED);
        }
        //кінец ініціалізаціїї

        //слухач кнопки яка відправляє дані з текстового поля на ESP блютузом
        btn_send_message.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (et_input_comand.getText().toString().length() > 0) {
                    connectedThread.writeMesToBlu(et_input_comand.getText().toString());
//                    Toast.makeText(MainActivity.this, R.string.succ_send, Toast.LENGTH_SHORT).show();
                    et_input_comand.setText("");
                    etConsole.setText("");
                    sbgolbal = new StringBuffer();
                    if (isTest) {//під час тесту перезапише головну зміну тримання даних
                        sbgolbal = new StringBuffer(readStringFromTestLog());
                    }
                }
            }
        });
        //слухач кнопки draw graph
        btn_create_graph.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //читання зміної що містить дані з блютузу
                String dataFromBl = sbgolbal.toString();
                //зберігання цих даних на смартфоні для подальшого аналізу
                save(dataFromBl);
                //малювання графу
                graph.setVisibility(View.VISIBLE);
//                drawGraph();

            }
        });
    }

    //Зберігання даних на смартфон
    public void save(String text) {
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(FILE_NAME, MODE_PRIVATE);
            fos.write(text.getBytes());
            Toast.makeText(this, "Saved to " + getFilesDir() + "/" + FILE_NAME,
                    Toast.LENGTH_LONG).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //читання лог файлу з телефону
    int[][] readFile() {
        int[][] intsAll = null;
        int sizeOfList = 0;
        String[] dataAr = new String[2];
        FileInputStream fis = null;
        BufferedReader br = null;
        List<String> list = new ArrayList();
        InputStreamReader isr = null;
        String data = "";
        try {
            fis = openFileInput(FILE_NAME);
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);

            while ((data = br.readLine()) != null) {
                //запис кожної строки в колекцію листа
                if (data.length() < 20) {
                    list.add(data);
                }
            }
            //зміна яка зберігає кількість строк
            sizeOfList = list.size();
            intsAll = new int[sizeOfList + 1][2];
            int j = 0;
            while (j < sizeOfList - 1) {
                //Отримання кожної строки з колекції
                data = list.get(j);
                //розбиття строки на масив з двух значень які були розділені пробілом
                dataAr = data.split(" ");
                //парсинг кожного числа в твою комірку дво вимірного масиву
                intsAll[j][0] = Integer.parseInt(dataAr[0]);
                intsAll[j][1] = Integer.parseInt(dataAr[1]);
                j++;
            }
            Toast.makeText(this, "Readed lines " + j + getFilesDir() + "/" + FILE_NAME,
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "e" + e, Toast.LENGTH_LONG);
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        numberOfElements = sizeOfList;
        return intsAll;
    }

    //знаходження значення сатурації послідовним викликом всіх методів
    public double DiscoverSaO2(int[][] ints) {
        double[][] XiAndYiDoubles = XiAndYi(ints);
        double[][] XXandXYandYYDoubles = XXandXYandYY(XiAndYiDoubles);
        double sumX = sumX(XiAndYiDoubles);
        double sumY = sumY(XiAndYiDoubles);
        double sumXX = sumXX(XXandXYandYYDoubles);
        double sumXY = sumXY(XXandXYandYYDoubles);
        double sumYY = sumYY(XXandXYandYYDoubles);
        double r1 = rOne(sumX, sumY, sumXX, sumXY, sumYY) - 1;
        double a = aVidnosh(sumX, sumY, sumXX, sumXY);
        Log.i("rd1", r1 + "");
        if (r1 >= .99) {
            //перевірка на кореляцію
            return SaO2(a);
        } else
            return 0;
    }

    public void drawGraph() {
        int[][] intsAll = readFile();
        int i = numberOfElements;
        double[] doublesSo2 = new double[i / 200];
        int[][] ints = new int[200][2];
        List<Double> listSo2 = new ArrayList();
        for (int j = 0; j < i / 200; j++) {
            for (int k = 0; k < 200; k++) {
                //використання методу вікна в 200 знаачень
                ints[k][0] = intsAll[k + j * 200][0];
                ints[k][1] = intsAll[k + j * 200][1];
            }
            doublesSo2[j] = (DiscoverSaO2(ints));
            if (doublesSo2[j] != 0) {
                //якщо значення не дорівню нулю тобто коефіціент парнох кореляції нормальний то тільки тоді додається значеня
                listSo2.add(doublesSo2[j]);
            }
        }
        graph.setVisibility(View.VISIBLE);
        LineGraphSeries<DataPoint> series = new LineGraphSeries<>();
        int size = listSo2.size();
        for (int i2 = 0; i2 < size; i2++) {
            //Додавання всіх значень на графік
            DataPoint point = new DataPoint(i2, listSo2.get(i2) * 100);
            series.appendData(point, true, size);
        }
        //заданя межі графіку
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(size);
        graph.getViewport().setMinY(-10);
        graph.getViewport().setMaxY(110);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
        series.setColor(Color.RED);
        graph.addSeries(series);
        double sred = 0;
        //визначення середньго начення сатурації
        for (int l = 0; l < size; l++) {
            sred += listSo2.get(l);
        }
        sred = sred / size;
        StringBuffer stringBuffer = new StringBuffer();
        for (int i2 = 0; i2 < size; i2++) {
            //Запис всіх значень у верхне поле разом з результатом
            stringBuffer.append(listSo2.get(i2)).append("\n");
        }
        stringBuffer.append("So2 mid= " + sred);
        etConsole.setText(stringBuffer.toString());
        Toast.makeText(this, "среднее " + sred, Toast.LENGTH_LONG).show();
    }

    //читаня лог файлу аналогічна звичайному читаню
    public String readStringFromTestLog() {
        FileInputStream fis = null;
        BufferedReader br = null;
        String data = "";
        StringBuilder sb = new StringBuilder();
        try {
            //читаня  тестогового лог файлу з телефону
            InputStream isr = this.getResources().openRawResource(R.raw.log);
            br = new BufferedReader(new InputStreamReader(isr));
            while ((data = br.readLine()) != null) {
                //запис кожної строки в один текст
                sb.append(data).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    private class ConnectedThread extends Thread {
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private boolean isConnected = false;

        public ConnectedThread(BluetoothSocket bluetoothSocket) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            isConnected = true;
        }

        @Override
        public void run() {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            StringBuffer buffer = new StringBuffer();
            final StringBuffer sbConsole = new StringBuffer();
            String s1 = "";
            final ScrollingMovementMethod movementMethod = new ScrollingMovementMethod();
            while (isConnected) {
                try {
                    int bytes = bis.read();
                    buffer.append((char) bytes);
                    int eof = buffer.indexOf("\r\n");
                    if (eof > 0) {
                        final String finalS = buffer.toString();
                        buffer.delete(0, buffer.length());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //запис вхідних даних до глобальнох зміни що зберігає дані з блютузу
//                                sbgolbal = sbConsole;
                                sbgolbal.append(finalS);
                                counter++;
                                etConsole.setText(finalS);
//                                etConsole.setMovementMethod(movementMethod);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeMesToBlu(String command) {
            byte[] bytes = command.getBytes();
            if (outputStream != null) {
                try {
                    // передача даних в нижнього поля по блютузу ESP
                    outputStream.write(bytes);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel() {
            try {
                isConnected = false;
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public double SaO2(double a) {
        Log.i("saO2A", a + "");
        double rez = (1.13 - (a / 3));
        Log.i("saO2rez", rez + "");
        return rez;
    }

    private double SaCO(double a) {
        Log.i("saOA", a + "");
        double rez = ((0.86 * a) - 0.72) * 0.1;
        Log.i("saOrez", rez + "");
        return rez;
    }

    public double aVidnosh(double sumX, double sumY, double sumXX, double sumXY) {
        double rez = ((((sumX * sumY) - (200 * sumXY))) / ((sumX * sumX) - (200 * sumXX)));
        Log.i("aVidnosh", rez + "");
        return rez;
    }

    private double rOne(double sumX, double sumY, double sumXX, double sumXY, double sumYY) {
        double rez = ((sumXY - ((sumX * sumY) / 200))) / ((Math.sqrt(sumXX - ((sumX * sumX) / 200))) * (Math.sqrt(sumYY - ((sumY * sumY) / 200))));
        return rez + 1;
    }

    public int Xmax(int[][] ints) {
        int XmaxInt = 0;
        for (int i = 0; i < 200; i++) {
            if (ints[i][0] > XmaxInt) {
                XmaxInt = ints[i][0];
            }
        }
        return XmaxInt;
    }

    public int Ymax(int[][] ints) {
        int YmaxInt = 0;
        for (int i = 0; i < 200; i++) {
            if (ints[i][1] > YmaxInt) {
                YmaxInt = ints[i][1];
            }
        }
        return YmaxInt;
    }

    public double[][] XiAndYi(int[][] ints) {
        double[][] XiAndYiDoubles = new double[200][2];
        double Xmax = Xmax(ints);
        double Ymax = Ymax(ints);
        for (int i = 0; i < 200; i++) {
            XiAndYiDoubles[i][0] = (double) ints[i][0] / Xmax;
            XiAndYiDoubles[i][1] = (double) ints[i][1] / Ymax;
        }
        return XiAndYiDoubles;
    }

    public double[][] XXandXYandYY(double[][] doubles) {
        double[][] XXandXYandYYDoubles = new double[200][3];
        for (int i = 0; i < 200; i++) {
            XXandXYandYYDoubles[i][0] = doubles[i][0] * doubles[i][0];
            XXandXYandYYDoubles[i][1] = doubles[i][0] * doubles[i][1];
            XXandXYandYYDoubles[i][2] = doubles[i][1] * doubles[i][1];
        }
        return XXandXYandYYDoubles;
    }

    public double sumX(double[][] XiAndYiDoubles) {
        double rez = 0;
        for (int i = 0; i < 200; i++) {
            rez += XiAndYiDoubles[i][0];
        }
        return rez;
    }

    public double sumY(double[][] XiAndYiDoubles) {
        double rez = 0;
        for (int i = 0; i < 200; i++) {
            rez += XiAndYiDoubles[i][1];
        }
        return rez;
    }

    public double sumXX(double[][] XXandXYandYYDoubles) {
        double rez = 0;
        for (int i = 0; i < 200; i++) {
            rez += XXandXYandYYDoubles[i][0];
        }
        return rez;
    }

    public double sumXY(double[][] XXandXYandYYDoubles) {
        double rez = 0;
        for (int i = 0; i < 200; i++) {
            rez += XXandXYandYYDoubles[i][1];
        }
        return rez;
    }

    public double sumYY(double[][] XXandXYandYYDoubles) {
        double rez = 0;
        for (int i = 0; i < 200; i++) {
            rez += XXandXYandYYDoubles[i][2];
        }
        return rez;
    }

    public double DiscoverSaCo(int[][] ints) {
        double[][] XiAndYiDoubles = XiAndYi(ints);
        double[][] XXandXYandYYDoubles = XXandXYandYY(XiAndYiDoubles);
        double sumX = sumX(XiAndYiDoubles);
        double sumY = sumY(XiAndYiDoubles);
        double sumXX = sumXX(XXandXYandYYDoubles);
        double sumXY = sumXY(XXandXYandYYDoubles);
        double sumYY = sumYY(XXandXYandYYDoubles);
        double r1 = rOne(sumX, sumY, sumXX, sumXY, sumYY) - 1;
        double a = aVidnosh(sumX, sumY, sumXX, sumXY);
        Log.i("r1_Saco", r1 + "");
        Log.i("a1_Saco", a + "");
        Log.i("sumX1_Saco", sumX + "");
        if (r1 >= .98) {
            Log.i("saCo", SaCO(a) + "");
            return SaCO(a);
        } else return 0;
    }

    //Далі ідуть системні обовязкові для перевизначення методи
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        if (connectThread != null) {
            connectThread.cancel();
        }
        if (connectedThread != null) {
            connectedThread.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.equals(btnEnableSearch)) {
            enableSearch();
        } else if (v.equals(btnDisconnect)) {
            if (connectedThread != null) {
                connectedThread.cancel();
            }
            if (connectThread != null) {
                connectThread.cancel();
            }
            showFrameControls();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.equals(listBtDevices)) {
            BluetoothDevice device = bluetoothDevices.get(position);
            if (device != null) {
                connectThread = new ConnectThread(device);
                connectThread.start();
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(switchEnableBt)) {
            enableBt(isChecked);
            if (!isChecked) {
                showFrameMessage();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == RESULT_OK && bluetoothAdapter.isEnabled()) {
                showFrameControls();
                setListAdapter(BT_BOUNDED);
            } else if (resultCode == RESULT_CANCELED) {
                enableBt(true);
            }
        }
    }

    private void showFrameMessage() {
        frameMessage.setVisibility(View.VISIBLE);
        frameLedControls.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void showFrameControls() {
        frameMessage.setVisibility(View.GONE);
        frameLedControls.setVisibility(View.GONE);
        frameControls.setVisibility(View.VISIBLE);
    }

    private void showFrameLedControls() {
        frameLedControls.setVisibility(View.VISIBLE);
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void enableBt(boolean flag) {
        if (flag) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);
        } else {
            bluetoothAdapter.disable();
        }
    }

    private void setListAdapter(int type) {

        bluetoothDevices.clear();
        int iconType = R.drawable.ic_bluetooth_bounded_device;
        listAdapter = new BtListAdapter(this, bluetoothDevices, iconType);
        listBtDevices.setAdapter(listAdapter);
    }

    private ArrayList<BluetoothDevice> getBoundedBtDevices() {
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> tmpArrayList = new ArrayList<>();
        if (deviceSet.size() > 0) {
            for (BluetoothDevice device : deviceSet) {
                tmpArrayList.add(device);
            }
        }
        return tmpArrayList;
    }

    private void enableSearch() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            accessLocationPermission();
            bluetoothAdapter.startDiscovery();
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    btnEnableSearch.setText(R.string.stop_search);
                    pbProgress.setVisibility(View.VISIBLE);
                    setListAdapter(BT_SEARCH);
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    btnEnableSearch.setText(R.string.start_search);
                    pbProgress.setVisibility(View.GONE);
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        bluetoothDevices.add(device);
                        listAdapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    private void accessLocationPermission() {
        int accessCoarseLocation = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            accessCoarseLocation = this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        int accessFineLocation = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            accessFineLocation = this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        List<String> listRequestPermission = new ArrayList<String>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[listRequestPermission.size()]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:
                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        // Check if request is granted or not
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                }
                break;
            default:
                return;
        }
    }

    private class ConnectThread extends Thread {

        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;

        public ConnectThread(BluetoothDevice device) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);

                progressDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success = true;
                progressDialog.dismiss();
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Не могу соединиться!", Toast.LENGTH_SHORT).show();
                    }
                });
                cancel();
            }
            if (success) {
                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameLedControls();
                    }
                });
            }
        }

        public boolean isConnect() {
            return bluetoothSocket.isConnected();
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

