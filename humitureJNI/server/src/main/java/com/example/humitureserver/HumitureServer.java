import java.io.*;
import java.net.*;

public class HumitureServer {
    private static final int PORT = 8080; // 服务端监听的端口号
package com.example.humiture;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

    public class MainActivity extends AppCompatActivity {

        private Button btnHeat;
        private Button btnHumidify;
        private Button btnConnect;
        private EditText etIp;
        private EditText etPort;
        private boolean isHeating = false;
        private boolean isHumidifying = false;
        private Socket socket;
        private OutputStream outputStream;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_main);
            setupViews();
            setupClickListeners();
            setupWindowInsets();
        }

        private void setupViews() {
            btnHeat = findViewById(R.id.btnHeat);
            btnHumidify = findViewById(R.id.btnHumidify);
            btnConnect = findViewById(R.id.btnConnect);
            etIp = findViewById(R.id.etIp);
            etPort = findViewById(R.id.etPort);
        }

        private void setupClickListeners() {
            btnConnect.setOnClickListener(v -> handleConnectClick());
            btnHeat.setOnClickListener(v -> handleHeatClick());
            btnHumidify.setOnClickListener(v -> handleHumidifyClick());
        }

        private void setupWindowInsets() {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        /**
         * 处理连接点击事件
         * 此方法验证用户输入的IP地址和端口号，并尝试建立Socket连接
         */
        private void handleConnectClick() {
            // 获取并修剪IP地址输入
            String ip = etIp.getText().toString().trim();
            // 获取并修剪端口号输入
            String portStr = etPort.getText().toString().trim();
            // 初始化端口号变量
            int port = 0;

            // 验证IP地址格式
            if (!isValidIp(ip)) {
                // 如果IP地址无效，显示提示并记录日志
                Toast.makeText(MainActivity.this, "无效的IP地址", Toast.LENGTH_SHORT).show();
                Log.e("MainActivity", "无效的IP地址: " + ip);
                return;
            }

            // 尝试将端口号字符串转换为整数并验证范围
            try {
                port = Integer.parseInt(portStr);
                if (port < 0 || port > 65535) {
                    // 如果端口号超出范围，显示提示并记录日志
                    Toast.makeText(MainActivity.this, "无效的端口号", Toast.LENGTH_SHORT).show();
                    Log.e("MainActivity", "无效的端口号: " + portStr);
                    return;
                }
            } catch (NumberFormatException e) {
                // 如果端口号格式错误，显示提示并记录日志
                Toast.makeText(MainActivity.this, "无效的端口号", Toast.LENGTH_SHORT).show();
                Log.e("MainActivity", "端口号格式错误: " + portStr);
                return;
            }

            // 初始化Socket连接
            int finalPort = port;
            // 在新线程中执行连接操作，避免阻塞主线程
            new Thread(() -> {
                try {
                    // 创建Socket连接
                    socket = new Socket(ip, finalPort);
                    // 获取Socket的输出流
                    outputStream = socket.getOutputStream();
                    // 连接成功，更新UI并记录日志
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show());
                    Log.d("MainActivity", "连接成功: " + ip + ":" + finalPort);
                } catch (IOException e) {
                    // 连接失败，更新UI并记录错误日志
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接失败", Toast.LENGTH_SHORT).show());
                    Log.e("MainActivity", "连接失败: " + ip + ":" + finalPort, e);
                }
            }).start();
        }

        /**
         * 处理加热按钮点击事件
         *
         * 此方法用于响应加热按钮的点击事件根据当前是否正在加热的状态，
         * 动态改变按钮的文本和文本颜色，并发送相应的指令控制加热状态
         */
        private void handleHeatClick() {
            if (isHeating) {
                // 当前正在加热，点击后停止加热
                btnHeat.setText("加热");
                btnHeat.setTextColor(getResources().getColor(android.R.color.black)); // 恢复默认颜色
                isHeating = false;
                sendData("0");
            } else {
                // 当前未加热，点击后开始加热
                btnHeat.setText("加热中...");
                btnHeat.setTextColor(Color.RED);
                isHeating = true;
                sendData("1");
            }
        }

        /**
         * 处理加湿按钮点击事件
         * 根据当前是否正在加湿（isHumidifying标志），来决定按钮的显示文本和颜色，并通过sendData方法发送指令控制加湿设备
         */
        private void handleHumidifyClick() {
            if (isHumidifying) {
                // 当前正在加湿，设置按钮为"加湿"状态，文本颜色恢复默认
                btnHumidify.setText("加湿");
                btnHumidify.setTextColor(getResources().getColor(android.R.color.black)); // 恢复默认颜色
                isHumidifying = false;
                sendData("0");
            } else {
                // 当前未在加湿，设置按钮为"加湿中..."状态，文本颜色变为红色
                btnHumidify.setText("加湿中...");
                btnHumidify.setTextColor(Color.RED);
                isHumidifying = true;
                sendData("1");
            }
        }

        /**
         * 发送数据到输出流
         * 此方法在一个新的线程中执行，以避免阻塞主线程
         * 主要用于与外部设备、网络等进行异步通信
         *
         * @param data 要发送的字符串数据
         */
        private void sendData(String data) {
            // 创建并启动一个新的线程来处理数据发送
            new Thread(() -> {
                try {
                    // 检查输出流是否已初始化
                    if (outputStream != null) {
                        // 将字符串数据转换为字节数组并写入输出流
                        outputStream.write(data.getBytes());
                        // 刷新输出流以确保数据被立即发送
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    // 打印异常信息以进行调试
                    e.printStackTrace();
                    // 在主线程中显示发送失败的Toast消息
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "发送失败", Toast.LENGTH_SHORT).show());
                    // 在日志中记录发送失败的信息和异常堆栈
                    Log.e("MainActivity", "发送失败", e);
                }
            }).start();
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            closeConnection();
        }

        private void closeConnection() {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("MainActivity", "关闭连接失败", e);
            }
        }

        private boolean isValidIp(String ip) {
            try {
                InetAddress inetAddress = InetAddress.getByName(ip);
                return inetAddress.getHostAddress().equals(ip);
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            // 创建一个服务器套接字，监听指定端口
            serverSocket = new ServerSocket(PORT);
            System.out.println("服务端已启动，等待客户端连接...");

            // 等待客户端连接
            clientSocket = serverSocket.accept();
            System.out.println("客户端已连接: " + clientSocket.getInetAddress());

            // 获取输入流和输出流
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String inputLine;
            // 读取客户端发送的数据
            while ((inputLine = in.readLine()) != null) {
                System.out.println("收到客户端数据: " + inputLine);

                // 根据接收到的数据执行相应的操作
                if ("1".equals(inputLine)) {
                    System.out.println("开始加热/加湿");
                    // 在这里添加实际的加热/加湿逻辑
                } else if ("0".equals(inputLine)) {
                    System.out.println("停止加热/加湿");
                    // 在这里添加实际的停止加热/加湿逻辑
                } else {
                    System.out.println("未知命令");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭资源
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (clientSocket != null) {
                    clientSocket.close();
                }
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}