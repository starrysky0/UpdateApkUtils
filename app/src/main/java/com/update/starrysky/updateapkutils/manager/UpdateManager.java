package com.update.starrysky.updateapkutils.manager;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.update.starrysky.updateapkutils.DownLoadRunnable;
import com.update.starrysky.updateapkutils.R;
import com.update.starrysky.updateapkutils.UpdataDialog;

import java.io.File;

import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * 版本更新
 */

public class UpdateManager {
    private final int UPDATA_APK_NOTIFICATION_ID = 10086;
    private static final String SAVE_APK_PATH = "SAVE_APK_PATH";
    private static final String VERSION_CODE = "VERSION_CODE";
    private static final String SAVE_APK = "SAVE_APK";

    //引导更新
    public static final int GUIDE_MODE = 0;
    //静默更新
    public static final int NO_PROGRESS_MODE = 1;
    //强制更新
    public static final int FORCED_UPDATES_MODE = 2;
    //显示Notification进度更新
    public static final int SHOW_NOTIFICATION_MODE = 3;
    private String downLoadPath;
    private String fileName;//文件名
    private int type = 0;//更新方式，0：引导更新，1：安装更新，2：强制更新,3,非强制更新
    private String url = "";//apk下载地址
    private String updateMessage = "";//更新内容
    private boolean isDownload = false;//是否下载
    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private UpdataDialog dialog;
    private ProgressDialog progressDialog;
    private Context context;
    private int newVersionCode;
    private static UpdateManager updateManager;
    private SharedPreferences sharedPreferences;
    private String saveUrl;
    private boolean isOkHttp;
    private DownLoadRunnable downLoadRunnable;
    private boolean haveNewVersion;

    public UpdateManager setUseExternalFilesDir(boolean useExternalFilesDir) {
        Log.e("UpdateManager", "setUseExternalFilesDir: " + useExternalFilesDir);
        this.useExternalFilesDir = useExternalFilesDir;
        return this;
    }

    public boolean isUseExternalFilesDir() {
        return useExternalFilesDir;
    }

    private boolean useExternalFilesDir = false;


    public String getDownLoadPath() {
        return downLoadPath;
    }

    public String getFileName() {
        return fileName;
    }

    private BackAppOnClickListener backAppOnClickListener;

    public static UpdateManager getInstance() {
        if (updateManager == null) {
            updateManager = new UpdateManager();
        }
        return updateManager;
    }

    private UpdateManager() {
    }

    public void useOkHttpDownload(boolean isOkhttp) {
        this.isOkHttp = isOkhttp;
    }

    public boolean isOkHttp() {
        return isOkHttp;
    }

    /**
     * 强制更新时自定义退出回调接口
     */
    public interface BackAppOnClickListener {
        void backAppClick();
    }

    public void setBackAppOnClickListener(BackAppOnClickListener backAppOnClickListener) {
        this.backAppOnClickListener = backAppOnClickListener;
    }


    /**
     * @param versionCode   最新的版本号
     * @param url           apk下载链接
     * @param updateMessage 更新内容
     * @param type          0：引导更新，1：静默更新，2：强制更新:3:显示Notification进度更新
     */
    public boolean checkUpdate(Context context, int versionCode, String url, String updateMessage, int type) {
        this.context = context;
        this.newVersionCode = versionCode;
        this.type = type;
        this.url = url;
        if (!TextUtils.isEmpty(updateMessage)) {
            this.updateMessage = updateMessage;
        }
        return haveNewVersion = versionCode > getVersionCode();
    }

    /**
     * 接着就是开始更新,回判断是否有现版本,检查是否下载,还有根据设置来确定接下来的操作.
     */
    public void start() {
        if (!haveNewVersion) {
            return;
        }
        init();
        //检测是否已下载
        if (checkDownload(newVersionCode)) {
            isDownload = true;
        } else {
            isDownload = false;
        }
        if (type == NO_PROGRESS_MODE && !isDownload) {
            downloadFile();
        } else {
            showDialog();
        }
    }


    private boolean checkDownload(int versionCode) {
        int saveVersionCode = sharedPreferences.getInt(VERSION_CODE, 0);
        if (saveVersionCode == versionCode) {
            saveUrl = sharedPreferences.getString(SAVE_APK_PATH, "");
            if (!TextUtils.isEmpty(saveUrl) && (new File(saveUrl)).exists()) {
                return true;
            }
        }
        return false;
    }

    private void init() {
        sharedPreferences = context.getSharedPreferences(SAVE_APK, Context.MODE_PRIVATE);
        if (TextUtils.isEmpty(downLoadPath)) {
            if (useExternalFilesDir) {
                downLoadPath = context.getExternalFilesDir("updata").getAbsolutePath();
            } else {
                downLoadPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/updata";
            }
            Log.w("downLoadPath", "init: " + downLoadPath);
        }
        if (TextUtils.isEmpty(fileName)) {
            fileName = context.getPackageName() + ".apk";
        }
    }

    public boolean deleteFile(Context context) {
        String apkUrl = context.getSharedPreferences(SAVE_APK, Context.MODE_PRIVATE).getString(SAVE_APK_PATH, "");
        if (TextUtils.isEmpty(apkUrl)) {
            return false;
        }
        File file = new File(apkUrl);
        if (file.isFile() && file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * 弹出版本更新提示框
     */
    public void showDialog() {
        String title = "";
        String left = "";
        String rigeht = "";
        boolean cancelable = true;
        // isDownload = false;
        if (isDownload) {
            title = "安装新版本";
            left = "立即安装";
            rigeht = type == FORCED_UPDATES_MODE ? "退出" : "取消";
        } else {
            title = "发现新版本";
            left = "立即更新";
            rigeht = type == FORCED_UPDATES_MODE ? "退出" : "取消";
        }
        dialog = new UpdataDialog.Builder(context).setTitle(title).setMessage(updateMessage).setCancelable(cancelable)
                .setLeftClick(left, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (isDownload) {
                            if (type == 3) {
                                if (mNotifyManager != null) {
                                    try {
                                        mNotifyManager.cancel(UPDATA_APK_NOTIFICATION_ID);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            installApk(context, new File(saveUrl));
                        } else {
                            dialog.dismiss();
                            if (url != null && !TextUtils.isEmpty(url)) {
                                if (type == FORCED_UPDATES_MODE || type == GUIDE_MODE) {
                                    createProgress();
                                } else if (type == SHOW_NOTIFICATION_MODE) {
                                    createNotification();
                                }
                                downloadFile();
                            } else {
                                Toast.makeText(context, "下载地址错误", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .setRightClick(rigeht, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (type == FORCED_UPDATES_MODE) {
                            if (backAppOnClickListener != null) {
                                backAppOnClickListener.backAppClick();
                            } else {
                                ((Activity) context).finish();
                                System.exit(0);
                            }
                        } else {
                            dialog.dismiss();
                        }
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.show();
    }


    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.e("msg", "handleMessage: " + msg.what);
            switch (msg.what) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    saveDownloadFilePath();
                    isDownload = true;
                    if (type == GUIDE_MODE) {
                        progressDialog.setProgress(100);
                        progressDialog.setMessage("下载完成");
                        progressDialog.dismiss();
                        showDialog();
                    } else if (type == FORCED_UPDATES_MODE) {
                        progressDialog.setProgress(100);
                        progressDialog.setMessage("下载完成");
                        progressDialog.dismiss();
                        installApk(context, new File(saveUrl));
                        showDialog();
                    } else if (type == NO_PROGRESS_MODE) {
                        showDialog();
                    } else if (type == SHOW_NOTIFICATION_MODE) {
                        mBuilder.setContentTitle("下载完成");
                        mBuilder.setContentText("点击安装");
                        mBuilder.setProgress(100, 100, false);
                        //点击安装PendingIntent
                        File file = new File(saveUrl);
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.setAction(Intent.ACTION_VIEW);
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                            intent.setDataAndType(FileProvider.getUriForFile(context, context.getPackageName() + ".file.provider", file), "application/vnd.android.package-archive");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } else {
                            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                        }
                        Log.e("log", "handleMessage: " + file.getAbsolutePath());
                        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                        mBuilder.setContentIntent(pendingIntent);
                        mBuilder.setAutoCancel(true);
                        mNotifyManager.notify(UPDATA_APK_NOTIFICATION_ID, mBuilder.build());
                        showDialog();
                    }

                    break;

                case DownloadManager.STATUS_RUNNING:
                    //int progress = (int) msg.obj;
                    //downloadDialog.setProgress((int) msg.obj);
                    //canceledDialog();
                    //实时更新通知栏进度条
                    if (type == 0) {
                        progressDialog.setProgress((int) msg.obj);
                    } else if (type == 2) {
                        progressDialog.setProgress((int) msg.obj);
                    } else if (type == 3) {
                        mBuilder.setProgress(100, (int) msg.obj, false);
                        mNotifyManager.notify(UPDATA_APK_NOTIFICATION_ID, mBuilder.build());
                    }
                    break;

                case DownloadManager.STATUS_FAILED:
                    if (type == 3) {
                        mBuilder.setContentTitle("下载失败");
                        mBuilder.setContentText("");
                        mBuilder.setAutoCancel(true);
                        mNotifyManager.notify(UPDATA_APK_NOTIFICATION_ID, mBuilder.build());
                    }
                    break;

                case DownloadManager.STATUS_PENDING:

                    break;
            }
        }
    };

    private void saveDownloadFilePath() {
        saveUrl = downLoadPath + "/" + fileName;
        Log.e("saveUrl", "saveDownloadFilePath: " + saveUrl);
        sharedPreferences.edit()
                .putInt(VERSION_CODE, newVersionCode)
                .commit();
        sharedPreferences.edit()
                .putString(SAVE_APK_PATH, saveUrl)
                .commit();
    }


    /**
     * 下载apk
     */
    public void downloadFile() {
        downLoadRunnable = new DownLoadRunnable(context, url, handler);
        new Thread(downLoadRunnable).start();
    }

    /**
     * 强制更新时显示在屏幕的进度条
     */
    private void createProgress() {
        Log.e("log", "createProgress: ");
        progressDialog = new ProgressDialog(context);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.setMessage("正在更新...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                breakDownload();
                if (type == FORCED_UPDATES_MODE) {
                    if (backAppOnClickListener != null) {
                        backAppOnClickListener.backAppClick();
                    } else {
                        ((Activity) context).finish();
                        System.exit(0);
                    }
                } else {
                    progressDialog.dismiss();
                }
                downLoadRunnable = null;
            }
        });
        progressDialog.show();
    }

    public void breakDownload() {
        downLoadRunnable.breakDownload();
    }

    /**
     * 创建通知栏进度条
     */
    private void createNotification() {
        Log.e("log", "createNotification: ");
        mNotifyManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setContentTitle("版本更新");
        mBuilder.setContentText("正在下载...");
        mBuilder.setProgress(100, 0, false);
        Intent intent = new Intent(context, context.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pendingIntent);
        Notification notification = mBuilder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotifyManager.notify(UPDATA_APK_NOTIFICATION_ID, notification);
        Log.e("log", "createNotification: 2");
    }


    /**
     * 安装apk
     *
     * @param context 上下文
     * @param file    APK文件
     */
    private void installApk(Context context, File file) {

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setAction(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            intent.setDataAndType(FileProvider.getUriForFile(context, context.getPackageName() + ".file.provider", file), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        }
        context.startActivity(intent);
    }

    /**
     * @return 当前应用的版本号
     */
    public int getVersionCode() {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            int version = info.versionCode;
            return version;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 判断当前网络是否wifi
     */
    public boolean isWifi(Context mContext) {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public UpdateManager setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

}
