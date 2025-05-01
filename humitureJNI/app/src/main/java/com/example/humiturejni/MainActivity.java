package com.example.humiturejni;

import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.humiturejni.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;



public class MainActivity extends AppCompatActivity {

    private Handler handler = new Handler(Looper.getMainLooper());// 用于在主线程中更新UI
    private Socket client = null;

    // 定义日志标签
    private static final String TAG = "TempHumiditySender";

    // Used to load the 'humiturejni' library on application startup.
    static {
        System.loadLibrary("humiturejni");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        startServer();//启动服务器
        MyDeviceOpen();//打开设备
        updateTempHumidity();//更新显示
    }

    // 更新显示的Runnable任务
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                try {
                    int temperature = getTemperature();
                    int humidity = getHumidity();
                    int waterLevel = getWaterLevel();

                    handler.post(() -> {
                        binding.tvTemperature.setText(String.format(Locale.getDefault(), "%d°C", temperature));
                        binding.tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", humidity));
                        binding.tvWaterLevel.setText(String.format(Locale.getDefault(), "%d%%", waterLevel));
                    });

                    sendTempHumidityToClient();
                } catch (Exception e) {
                    Log.e(TAG, "Error updating data", e);
                }
            }).start();

            handler.postDelayed(this, 2000);
        }
    };


    // 更新温湿度显示
    private void updateTempHumidity() {
        handler.post(updateRunnable);
    }


    /*****************************************服务端*************************************************/
    /**
     * 启动服务器
     * 该方法在一个新的线程中启动一个服务器，监听指定端口，并接受客户端连接请求
     * 选择在新线程中运行是因为服务器通常需要长时间运行，且处理客户端请求时不应阻塞主线程
     * 使用ServerSocket来监听8888端口，这是服务器与客户端通信的入口
     * 该方法不接受参数，也无返回值
     */
    private void startServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(8888);
                while (!Thread.interrupted()) {
                    Socket newClient = serverSocket.accept();
                    // 关闭旧连接并保存新客户端
                    synchronized (this) {
                        if (client != null && !client.isClosed()) {
                            client.close();
                        }
                        client = newClient;
                    }
                    handleClient(newClient); // 处理新客户端
                }
            } catch (IOException e) {
                Log.e(TAG, "Server error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 处理客户端请求
     * 当客户端连接到服务器时，此方法将被调用以处理客户端的请求
     * 它读取客户端发送的每一行指令，并将其解析和执行
     *
     * @param client 代表客户端的Socket对象
     */
    private void handleClient(Socket client) {
        // 为每个客户端启动一个新的线程，以实现并发处理
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                String inputLine;
                // 持续监听客户端指令
                while ((inputLine = in.readLine()) != null) {
                    // 当收到指令时，记录日志
                    Log.d(TAG, "收到指令: " + inputLine);
                    // 解析并执行接收到的指令
                    parseAndExecuteCommand(inputLine.trim());
                }
            } catch (IOException e) {
                // 如果发生IO异常，记录错误日志
                Log.e(TAG, "客户端断开连接: " + e.getMessage());
            } finally {
                // 确保最终关闭客户端连接
                try {
                    client.close();
                } catch (IOException e) {
                    // 如果关闭连接时发生异常，记录错误日志
                    Log.e(TAG, "关闭连接失败: " + e.getMessage());
                }
            }
        }).start();
    }


    /**
     * 解析并执行客户端发送的指令
     * @param command 客户端发送的指令字符串
     */
    private void parseAndExecuteCommand(String command) {
        command = command.trim().toLowerCase(); // 转为小写并去除空格
        if ("tem_stop".equals(command)) {
            // 停止加热
            Log.d(TAG, "停止加热");
            CmdIoctl(0, 0);
        } else if ("tem_up".equals(command)) {
            // 开始加热
            Log.d(TAG, "开始加热");
            CmdIoctl(0, 1);
        } else if ("hum_stop".equals(command)) {
            // 停止加湿
            Log.d(TAG, "停止加湿");
            CmdIoctl(1, 0);
        } else if ("hum_up".equals(command)) {
            // 开始加湿
            Log.d(TAG, "开始加湿");
            CmdIoctl(1, 1);
        }else if ("fan_off".equals(command)){
            Log.d(TAG, "关闭风扇");
            CmdIoctl(2, 0);
        }else if ("fan_on".equals(command)) {
            //  开启风扇
            Log.d(TAG, "开启风扇");
            CmdIoctl(2, 1);
        }else if ("pump_off".equals(command)){
            Log.d(TAG, "关闭水泵");
            CmdIoctl(3, 0);
        }else if ("pump_on".equals(command)){
            Log.d(TAG, "开启水泵");
            CmdIoctl(3, 1);
        }
    }
/***************************************************************************************/

/***************************************客户端*************************************************/

    /**
     * 将当前的温度、湿度、水位发送给客户端
     * 此方法在一个新的线程中执行，以避免阻塞主线程
     * 需要同步以确保在同一时间只有一个线程可以发送数据
     */
    private void sendTempHumidityToClient() {
        // 创建并启动一个新的线程，以异步方式发送数据
        new Thread(() -> {
            // 同步代码块，确保线程安全
            synchronized (this) {
                try {
                    // 检查客户端连接是否处于活动状态
                    if (client == null || client.isClosed()) {
                        Log.e(TAG, "No active client connection");
                        return;
                    }
                    // 准备输出流以发送数据
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                    // 获取当前的温度和湿度
                    int temperature = getTemperature();
                    int humidity = getHumidity();
                    int waterLevel = getWaterLevel();
                    // 格式化信息
                    String message = String.format(Locale.getDefault(), "Temperature:%d,Humidity:%d,WaterLevel:%d", temperature, humidity,waterLevel);
                    // 发送格式化后的数据
                    out.println(message);
                    Log.d(TAG, "Data sent: " + message);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send data: " + e.getMessage());
                }
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        MyDeviceClose();
        super.onDestroy();
    }

    /**
     * A native method that is implemented by the 'humiturejni' native library,
     * which is packaged with this application.
     */
    public native String MyDeviceOpen();
    public native String MyDeviceClose();
    public native void CmdIoctl(int cmd, int arg);

    public native int getTemperature();
    public native int getHumidity();
    public native int getWaterLevel();
}
