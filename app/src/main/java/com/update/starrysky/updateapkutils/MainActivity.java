package com.update.starrysky.updateapkutils;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.update.starrysky.updateapkutils.manager.UpdateManager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    String url = "https://raw.githubusercontent.com/starrysky0/BoxingDemo0/master/app-debug.apk";
//    String url = "https://raw.githubusercontent.com/starrysky0/BoxingDemo0/master/app-debug.apk";
    String mssage = "1.更新功能\n 2.更新功能\n 3.更新功能\n 4.更新功能";
    final private int RC_PERMISSION_RECORD_AUDIO = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button).setOnClickListener(this);
        findViewById(R.id.button2).setOnClickListener(this);
        findViewById(R.id.button3).setOnClickListener(this);
        findViewById(R.id.button4).setOnClickListener(this);
        findViewById(R.id.button5).setOnClickListener(this);
        findViewById(R.id.button6).setOnClickListener(this);
        findViewById(R.id.button7).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button:
                UpdateManager.getInstance().checkUpdate(this, 2, url, mssage, UpdateManager.NO_PROGRESS_MODE);
                UpdateManager.getInstance().start();
                break;
            case R.id.button2:
                UpdateManager.getInstance().checkUpdate(this, 2, url, mssage, UpdateManager.GUIDE_MODE);
                UpdateManager.getInstance().start();
                break;
            case R.id.button3:
                UpdateManager.getInstance().checkUpdate(this, 2, url, mssage, UpdateManager.FORCED_UPDATES_MODE);
                UpdateManager.getInstance().start();
                break;
            case R.id.button4:
                UpdateManager.getInstance().checkUpdate(this, 2, url, mssage, UpdateManager.SHOW_NOTIFICATION_MODE);
                UpdateManager.getInstance().start();
                break;
            case R.id.button5:
                UpdateManager.getInstance().setUseExternalFilesDir(true);
                Toast.makeText(this, "设置包名目录成功", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button6:
                UpdateManager.getInstance().useOkHttpDownload(true);
                Toast.makeText(this, "设置OKhttp下载方式成功", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button7:
                boolean b = UpdateManager.getInstance().deleteFile(this);
                if (b) {
                    Toast.makeText(this, "删除apk文件成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "删除apk文件失败", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


}
