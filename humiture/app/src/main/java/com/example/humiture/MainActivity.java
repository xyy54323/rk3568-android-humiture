package com.example.humiture;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private Button btnHeat;
    private Button btnHumidify;
    private Button btnFan;
    private Button btnPump;
    private EditText ipEditText;
    private EditText portEditText;
    private Button connectButton;
    private TextView temperatureTextView;
    private TextView humidityTextView;
    private TextView waterLevelTextView;


    // 功能按钮状态
    private boolean isHeating = false;
    private boolean isHumidifying = false;
    private boolean isFaning = false;
    private boolean isPumping = false;

    // 网络参数
    private String serverIp;
    private int port;

    // 网络连接
    private Socket clientSocket;
    private PrintWriter writer;

    // 线程管理
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object connectionLock = new Object();

    // 状态标志（使用原子类型保证线程安全）
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private long lastSendTime = 0;
    private final AtomicBoolean isReceiving = new AtomicBoolean(false);

    // 读取服务器的响应
    private BufferedReader reader;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);// 加载布局文件
        setupViews();// 初始化控件
        setupWindowInsets();// 设置窗口 insets
        ClickListener();// 设置点击事件监听器
    }

    private void setupViews() {// 初始化控件
        btnHeat = findViewById(R.id.btnHeat);
        btnHumidify = findViewById(R.id.btnHumidify);
        btnFan = findViewById(R.id.btnFan);
        btnPump = findViewById(R.id.btnPump);
        connectButton = findViewById(R.id.btnConnect);
        ipEditText = findViewById(R.id.etIp);
        portEditText = findViewById(R.id.etPort);
        temperatureTextView = findViewById(R.id.tvTemperature);
        humidityTextView = findViewById(R.id.tvHumidity);
        waterLevelTextView = findViewById(R.id.tvWaterLevel);
    }

    private void setupWindowInsets() {// 设置窗口 insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // 设置点击事件监听器，处理点击事件
    private void ClickListener() {
        connectButton.setOnClickListener(v -> handleConnectClick());//处理连接的点击事件
        btnHeat.setOnClickListener(v -> handeClick(v)); // 处理功能点击事件
        btnHumidify.setOnClickListener(v -> handeClick(v));
        btnFan.setOnClickListener(v -> handeClick(v));
        btnPump.setOnClickListener(v -> handeClick(v));
    }

    private void handeClick(View v) {
    if (isConnected.get()){// 如果已经连接，才执行点击事件
        if (v.getId() == R.id.btnHeat) {
            if (isHeating) {
                isHeating = false;
                btnHeat.setText("加热");
                btnHeat.setBackgroundResource(R.drawable.bg);
                sendData("tem_stop"); // 发送停止加热指令
            } else {
                isHeating = true;
                btnHeat.setText("加热中...");
                btnHeat.setBackgroundResource(R.drawable.bg_disconnect);
                sendData("tem_up"); // 发送开始加热指令
            }
        } else if (v.getId() == R.id.btnHumidify) {
            if (isHumidifying) {
                isHumidifying = false;
                btnHumidify.setText("加湿");
                btnHumidify.setBackgroundResource(R.drawable.bg);
                sendData("hum_stop"); // 发送停止加湿指令
            } else {
                isHumidifying = true;
                btnHumidify.setText("加湿中...");
                btnHumidify.setBackgroundResource(R.drawable.bg_disconnect);
                sendData("hum_up"); // 发送开始加湿指令
            }
        }else if (v.getId() == R.id.btnFan) {
            if (isFaning){
                isFaning = false;
                btnFan.setText("风扇");
                btnFan.setBackgroundResource(R.drawable.bg);
                sendData("fan_off");// 发送关闭风扇指令
            }else{
                isFaning = true;
                btnFan.setText("散热中...");
                btnFan.setBackgroundResource(R.drawable.bg_disconnect);
                sendData("fan_on");// 发送开启风扇指令
            }
        }else if (v.getId() == R.id.btnPump) {
            if (isPumping){
                isPumping = false;
                btnPump.setText("水泵");
                btnPump.setBackgroundResource(R.drawable.bg);
                sendData("pump_off");// 发送关闭水泵指令
            }else{
                isPumping = true;
                btnPump.setText("抽水中...");
                btnPump.setBackgroundResource(R.drawable.bg_disconnect);
                sendData("pump_on");// 发送开启水泵指令
            }
        }
    }else{
        showToast("请先连接服务器");
    }
}

    /**
     * 处理连接或断开连接点击事件
     * 此方法旨在验证用户输入的IP和端口，并尝试连接到指定的服务器，或断开当前的连接
     */
    private void handleConnectClick() {
        if (isConnected.get()) {
            // 如果已经连接，则断开连接
            disconnectFromServer();
        } else {
            // 如果未连接，则尝试连接到服务器
            // 获取并修剪IP输入框中的内容
            String ip = ipEditText.getText().toString().trim();
            // 获取并修剪端口输入框中的内容
            String portStr = portEditText.getText().toString().trim();

            // 检查IP和端口是否都已输入，如果任一为空，则提示用户并返回
            if (ip.isEmpty() || portStr.isEmpty()) {
                showToast("请输入IP和端口");
                return;
            }
            // 验证IP地址格式，如果不正确，则提示用户并返回
            if (!isValidIp(ip)) {
                showToast("IP地址格式错误！");
                return;
            }
            try {
                // 将端口字符串转换为整数
                int port = Integer.parseInt(portStr);
                // 检查端口号是否在有效范围内（1到65535），如果不在，则提示用户并返回
                if (port < 1 || port > 65535) {
                    showToast("端口不存在！");
                    return;
                }
                // 如果IP和端口都有效，则设置服务器IP和端口为输入的值
                this.serverIp = ip;
                this.port = port;
                // 禁用连接按钮以防止重复点击
                setConnectButtonState(false);
                // 尝试连接到服务器
                connectToServer();
                // 显示连接中的提示信息
                showToast("连接中...");
            } catch (NumberFormatException e) {
                // 如果端口转换为整数时发生错误，则提示用户
                showToast("端口错误！");
            }
        }
    }


/***********************************客户端***********************************/
    // region 网络连接管理
    /**
     * 连接到服务器的方法
     * 该方法在一个单独的线程中执行，以避免网络操作阻塞主线程
     * 使用同步块来确保连接的互斥性，防止多个线程同时修改连接状态
     */
    private void connectToServer() {
        executor.execute(() -> {
            synchronized (connectionLock) {
                try {
                    // 在尝试新连接之前，关闭任何现有的连接
                    closeConnection();

                    // 创建到服务器的新Socket连接
                    clientSocket = new Socket(serverIp, port);
                    // 初始化用于向服务器发送数据的PrintWriter
                    writer = new PrintWriter(clientSocket.getOutputStream(), true);
                    // 初始化用于从服务器接收数据的BufferedReader
                    reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    // 更新应用内部的连接状态并显示连接成功的通知
                    updateConnectionStatus(true);
                    showToast("连接成功");
                    
                    // 设置接收标志为true
                    isReceiving.set(true);
                    // 启动数据接收线程
                    startReceivingData();

                } catch (IOException e) {
                    // 如果连接失败，更新连接状态并显示错误信息
                    updateConnectionStatus(false);
                    showToast("连接失败: " + e.getMessage());
                } finally {
                    // 重新启用连接按钮，无论连接成功还是失败
                    setConnectButtonState(true);
                    // 通知其他等待连接锁的线程
                    connectionLock.notifyAll();
                }
            }
        });
    }

    /**
     * 断开与服务器的连接
     * 该方法在一个单独的线程中执行，以避免网络操作阻塞主线程
     * 使用同步块来确保连接的互斥性，防止多个线程同时修改连接状态
     */
    private void disconnectFromServer() {
        executor.execute(() -> {
            synchronized (connectionLock) {
                try {
                    isReceiving.set(false); // 停止接收线程
                    // 关闭当前的连接
                    closeConnection();

                    // 更新应用内部的连接状态并显示断开连接的通知
                    updateConnectionStatus(false);
                    showToast("已断开连接");
                } catch (Exception e) {
                    // 如果断开连接时发生错误，更新连接状态并显示错误信息
                    updateConnectionStatus(false);
                    showToast("断开连接失败: " + e.getMessage());
                } finally {
                    // 重新启用连接按钮
                    setConnectButtonState(true);
                    // 通知其他等待连接锁的线程
                    connectionLock.notifyAll();
                }
            }
        });
    }

    /**
     * 发送数据到远程服务器
     * 该方法主要负责将信息打包成特定格式的字符串，并通过网络发送出去
     *
     * @param data 要发送的指令,"tem_up"、"tem_stop"、"hum_up"、"hum_stop"、"pump_on"、"pump_off"、"fan_on"、"fan_off"
     */
    private void sendData(String data) {
        // 发送频率限制（50ms）
        if (System.currentTimeMillis() - lastSendTime < 50) return;
        lastSendTime = System.currentTimeMillis();

        // 避免重复发送
        if (isSending.get()) return;
        isSending.set(true);

        // 使用线程池执行发送任务，避免阻塞当前线程
        executor.execute(() -> {
            synchronized (connectionLock) {
                try {
                    // 确保连接有效，否则尝试重新连接
                    if (!isConnectionValid()) {
                        connectToServer();
                        connectionLock.wait(1000);
                    }

                    // 发送数据
                    if (writer != null) {
                        String message = String.format(Locale.US,
                                "%s",data);
                        writer.println(message);
                        //打印log
                        Log.d("MainActivity", "Sent: " + message);
                    }
                } catch (Exception e) {
                    // 异常处理
                    handleSendError(e);
                } finally {
                    // 重置发送状态标志
                    isSending.set(false);
                }
            }
        });
    }
    // endregion
/******************************************************************************************/

/*******************************************服务端**********************************************/

    /**
     * 开始接收数据
     * 此方法启动一个新任务来接收客户端套接字的数据
     * 它读取数据直到线程被中断或套接字断开连接
     */
private void startReceivingData() {
    new Thread(() -> {
        while (isReceiving.get()) {
            Log.d("Network", "数据接收线程启动");
            try {
                // 检查连接是否有效
                if (clientSocket == null || clientSocket.isClosed()) {
                    Log.e("Network", "连接未建立或已关闭");
                    handleConnectionLost("连接已断开，请检查网络后重新连接");
                    break;  // 退出接收循环
                }

                String data;
                while (isReceiving.get() && (data = reader.readLine()) != null) {
                    Log.d("Network", "收到原始数据: " + data);
                    parseSensorData(data);
                }
            } catch (IOException e) {
                Log.e("Network", "接收数据失败: " + e.getMessage());
                handleConnectionLost("连接异常：" + e.getMessage());
                break;  // 退出接收循环
            }
        }
    }).start();
}

    /**
     * 解析传感器数据字符串
     * 此方法旨在处理格式化的传感器数据字符串，提取温度和湿度信息，并更新UI显示
     * 预期的数据格式为"Temperature:XX,Humidity:YY"，其中XX和YY是整数值
     *
     * @param data 格式化的传感器数据字符串
     */
    private void parseSensorData(String data) {
    try {
        // 格式示例: Temperature:25,Humidity:50,WaterLevel:75
        String[] parts = data.split(",");
        if (parts.length >= 3) { // 确保有三个数据段
            int temperature = Integer.parseInt(parts[0].split(":")[1].trim());
            int humidity = Integer.parseInt(parts[1].split(":")[1].trim());
            int waterLevel = Integer.parseInt(parts[2].split(":")[1].trim());

            Log.d("Parse", String.format("解析数据: %d°C, %d%%, 水位 %d%%",
                temperature, humidity, waterLevel));

            // 更新UI（需要先在布局中添加水位显示控件）
            runOnUiThread(() -> {
                temperatureTextView.setText(String.format("温度: %d°C", temperature));
                humidityTextView.setText(String.format("湿度: %d%%", humidity));
                waterLevelTextView.setText(String.format("水位: %d%%", waterLevel));
            });
        }
    } catch (Exception e) {
        Log.e("Parse", "数据解析失败: " + data, e);
        // 可选：显示错误状态的UI
        runOnUiThread(() -> {
            temperatureTextView.setText("温度: --");
            humidityTextView.setText("湿度: --");
            waterLevelTextView.setText("水位: --");
        });
    }
}

    /**
     * 处理连接丢失的情况
     * @param message 要显示给用户的消息
     */
    private void handleConnectionLost(String message) {
        // 确保停止接收
        isReceiving.set(false);
        
        // 关闭连接
        closeConnection();
        
        // 更新UI状态
        updateConnectionStatus(false);
        
        // 重置按钮状态
        setConnectButtonState(true);
        
        // 通知用户
        showToast(message);
        
        // 如果正在加热或加湿，重置这些状态
        runOnUiThread(() -> {
            if (isHeating) {
                isHeating = false;
                btnHeat.setText("加热");
                btnHeat.setBackgroundResource(R.drawable.bg);
            }
            if (isHumidifying) {
                isHumidifying = false;
                btnHumidify.setText("加湿");
                btnHumidify.setBackgroundResource(R.drawable.bg);
            }
            if (isFaning) {
                isFaning = false;
                btnFan.setText("风扇");
                btnFan.setBackgroundResource(R.drawable.bg);
            }
            if (isPumping) {
                isPumping = false;
                btnPump.setText("水泵");
                btnPump.setBackgroundResource(R.drawable.bg);
            }
        });
    }

// endregion

// 其他部分保持不变

// endregion


    // region 工具方法
    /**
     * 处理发送错误的方法
     * 当发送操作失败时调用此方法，它执行以下操作：
     * 1. 显示一个toast消息，提示发送失败的原因（异常消息）
     * 2. 关闭连接
     * 3. 更新连接状态为未连接
     *
     * @param e 异常对象，包含发送失败的原因
     */
    private void handleSendError(Exception e) {
        showToast("发送失败: " + e.getMessage());
        closeConnection();
        updateConnectionStatus(false);
    }

    /**
     * 检查当前客户端套接字的连接是否有效
     *
     * 此方法通过一系列条件判断来确定客户端与服务器之间的连接状态是否正常
     * 它首先确保clientSocket对象不为空，以防止空指针异常
     * 接着检查套接字是否已连接，且没有被关闭，以及输入输出流是否都处于正常工作状态
     * 只有当所有这些条件都满足时，才认为连接是有效的
     *
     * @return boolean 表示连接是否有效的布尔值如果连接有效则返回true，否则返回false
     */
    private boolean isConnectionValid() {
        return clientSocket != null
                && clientSocket.isConnected()// 确保套接字已连接
                && !clientSocket.isClosed()// 确保套接字没有被关闭
                && !clientSocket.isInputShutdown()// 确保输入流处于正常工作状态
                && !clientSocket.isOutputShutdown();// 确保输出流处于正常工作状态
    }

    /**
     * 关闭连接资源
     * 此方法旨在确保在不再需要时正确关闭连接和流，以防止资源泄露和潜在的连接问题
     */
    private void closeConnection() {
        try {
            isReceiving.set(false); // 确保接收线程停止
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
        } catch (IOException e) {
            Log.e("Network", "关闭连接时出错", e);
        }
    }

    /**
     * 更新应用界面的连接状态
     * 此方法根据连接状态更新应用界面，包括连接按钮的文本和背景颜色
     *
     * @param connected 一个布尔值，表示当前的连接状态 true表示已连接，false表示未连接
     */
    private void updateConnectionStatus(boolean connected) {
        // 更新内部状态变量以反映当前的连接状态
        isConnected.set(connected);

        // 在主线程中运行UI更新操作，以确保线程安全
        runOnUiThread(() -> {
            // 根据连接状态更新连接按钮的文本
            connectButton.setText(connected ? "断开连接" : "连接");

            // 根据连接状态更新连接按钮的背景颜色
            if (connected){//如果连接成功，设置背景为红色
                connectButton.setBackgroundResource(R.drawable.bg_disconnect);
            }else{//如果断开连接,恢复背景bg.xml
                connectButton.setBackgroundResource(R.drawable.bg);
                // 更新温度和湿度的文本
                temperatureTextView.setText("温度: --°C");
                humidityTextView.setText("湿度: --%");
                waterLevelTextView.setText("水位: --%");
            }
        });
    }

    /**
     * 设置连接按钮的状态
     * 此方法通过在主线程中运行来更新UI，确保按钮的状态更改在主线程中执行，
     * 以避免多线程环境下对UI组件进行操作时可能出现的问题
     *
     * @param enabled 指定按钮是否启用 true表示按钮将被启用，false表示按钮将被禁用
     */
    private void setConnectButtonState(boolean enabled) {
        runOnUiThread(() -> connectButton.setEnabled(enabled));
    }

    /**
     * 在主线程中显示短时间的Toast消息
     *
     * @param text 要显示的Toast消息文本
     */
    private void showToast(String text) {
        handler.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    /**
     * 验证输入的字符串是否为有效的IPv4地址
     * 该方法使用正则表达式来匹配IPv4地址的格式
     *
     * @param ip 要验证的IP地址字符串
     * @return 如果输入的字符串是有效的IPv4地址，则返回true；否则返回false
     */
    private boolean isValidIp(String ip) {
        // 编译一个正则表达式，用于匹配IPv4地址的格式
        Pattern pattern = Pattern.compile(
                "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
        // 使用编译的正则表达式创建一个匹配器，并验证输入的IP地址是否匹配IPv4地址的格式
        return pattern.matcher(ip).matches();
    }
    // endregion
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}