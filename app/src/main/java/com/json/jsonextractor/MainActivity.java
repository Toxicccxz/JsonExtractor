package com.json.jsonextractor;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MainActivity extends AppCompatActivity {

    private static final int READ_REQUEST_CODE = 42;
    private String extractedTexts; // 类成员变量，用于临时存储提取的文本

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button buttonSelectFile = findViewById(R.id.button_select_file);
        buttonSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkManageExternalStoragePermission()) {
                    selectJsonFile();
                } else {
                    requestManageExternalStoragePermission();
                }
            }
        });
    }

    private boolean checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        } else {
            // 在Android 11以下版本，仍然使用READ_EXTERNAL_STORAGE权限
            Toast.makeText(this, "请在设置中授予访问文件的权限", Toast.LENGTH_LONG).show();
        }
    }

    private void selectJsonFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "选择一个JSON文件"), READ_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装一个文件管理器.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                if (uri != null) {
                    // 获取文件的 MIME 类型
                    String mimeType = getContentResolver().getType(uri);

                    // 检查 MIME 类型是否为 JSON
                    if ("application/json".equals(mimeType)) {
                        // MIME 类型匹配，处理文件
                        processJsonFile(uri);
                    } else {
                        // MIME 类型不匹配，提示用户
                        Toast.makeText(this, "请选择JSON格式的文件", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } else if (requestCode == CREATE_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    writer.write(this.extractedTexts);
                    writer.close();
                    outputStream.close();
                    Toast.makeText(this, "文件保存成功", Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Toast.makeText(this, "文件保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // 示例方法：处理JSON文件
    private void processJsonFile(Uri fileUri) {
        try {
            // 读取原始 JSON 文件
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            reader.close();
            inputStream.close();

            // 解析 JSON 并提取 "Text" 字段
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());
            JSONArray translationLabels = jsonObject.getJSONArray("TranslationLabels");
            StringBuilder extractedTextsBuilder  = new StringBuilder();

            for (int i = 0; i < translationLabels.length(); i++) {
                JSONObject label = translationLabels.getJSONObject(i);
                String text = label.getString("Text");
                // 将文本中的换行转义符 `\n` 替换为字面字符串 "\\n"
                text = text.replace("\n", "\\n");
                extractedTextsBuilder .append(text).append("\n"); // 添加每个文本到 StringBuilder，并在每个文本之后添加换行符
                Log.e("xavier", "Lines = " + (i + 1));
            }

            this.extractedTexts = extractedTextsBuilder.toString();
            createFile(null); // 调用方法以选择文件保存位置
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "处理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static final int CREATE_FILE = 1;

    private void createFile(Uri pickerInitialUri) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "extracted_texts.txt");

        startActivityForResult(intent, CREATE_FILE);
    }
}
