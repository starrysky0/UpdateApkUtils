package com.update.starrysky.updateapkutils;


import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.update.starrysky.updateapkutils.manager.UpdateManager;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.lang.System.in;
import static java.lang.System.out;

/**
 * Created by Administrator on 2017/9/26.
 */

public class DownLoadRunnable implements Runnable {
    private String url;
    private Handler handler;
    private Context mContext;
    private boolean breakDownload = false;
    private DownloadManager downloadManager;
    private long requestId;

    public DownLoadRunnable(Context context, String url, Handler handler) {
        this.mContext = context;
        this.url = url;
        this.handler = handler;

    }

    @Override
    public void run() {
        //设置线程优先级为后台，这样当多个线程并发后很多无关紧要的线程分配的CPU时间将会减少，有利于主线程的处理
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        //具体下载方法
        if (UpdateManager.getInstance().isOkHttp()) {
            okhttpStartdownLoad();
        } else {
            downloadMagerStartDownload();
        }
    }

    private void downloadMagerStartDownload() {
        //获得DownloadManager对象
        downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        //获得下载id，这是下载任务生成时的唯一id，可通过此id获得下载信息
        requestId = downloadManager.enqueue(CreateRequest(url));
        //查询下载信息方法
        queryDownloadProgress(requestId, downloadManager);
    }

    private void queryDownloadProgress(long requestId, DownloadManager downloadManager) {


        DownloadManager.Query query = new DownloadManager.Query();
        //根据任务编号id查询下载任务信息
        query.setFilterById(requestId);
        try {
            boolean isGoging = true;
            long currentTime = 0;
            while (isGoging) {
                Cursor cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {

                    //获得下载状态
                    int state = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    switch (state) {
                        case DownloadManager.STATUS_SUCCESSFUL://下载成功
                            isGoging = false;
                            handler.obtainMessage(DownloadManager.STATUS_SUCCESSFUL).sendToTarget();//发送到主线程，更新ui
                            break;
                        case DownloadManager.STATUS_FAILED://下载失败
                            isGoging = false;
                            handler.obtainMessage(DownloadManager.STATUS_FAILED).sendToTarget();//发送到主线程，更新ui
                            break;

                        case DownloadManager.STATUS_RUNNING://下载中
                            if (System.currentTimeMillis() - 500 > currentTime) {
                                currentTime = System.currentTimeMillis();
                                /**
                                 * 计算下载下载率；
                                 */
                                int totalSize = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                int currentSize = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                int progress = (int) (((float) currentSize) / ((float) totalSize) * 100);
                                handler.obtainMessage(DownloadManager.STATUS_RUNNING, progress).sendToTarget();//发送到主线程，更新ui
                            }
                            break;

                        case DownloadManager.STATUS_PAUSED://下载停止
                            handler.obtainMessage(DownloadManager.STATUS_PAUSED).sendToTarget();
                            break;

                        case DownloadManager.STATUS_PENDING://准备下载
                            handler.obtainMessage(DownloadManager.STATUS_PENDING).sendToTarget();
                            break;
                    }
                }
                if (cursor != null) {
                    cursor.close();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private DownloadManager.Request CreateRequest(String url) {
        File file = new File(UpdateManager.getInstance().getDownLoadPath(), UpdateManager.getInstance().getFileName());
        if (file.isFile() && file.exists()) {
            file.delete();
        }
        Log.i("===f====", "===b=====" + url);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);// 隐藏notification
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);//设置下载网络环境为wifi
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
        request.setAllowedOverRoaming(true);
        Log.e("log", "DownloadManager: " + file.getAbsolutePath());
        if (UpdateManager.getInstance().isUseExternalFilesDir()) {
            request.setDestinationInExternalFilesDir(mContext, "updata", UpdateManager.getInstance().getFileName());//指定apk缓存路径，默认是在SD卡中的Download文件夹
        } else {
            request.setDestinationInExternalPublicDir("updata", UpdateManager.getInstance().getFileName());//指定apk缓存路径，默认是在SD卡中的Download文件夹
        }
        return request;
    }

    public void okhttpStartdownLoad() {
        // File updata = mContext.getExternalFilesDir("updata");
        File saveFile = new File(UpdateManager.getInstance().getDownLoadPath(), UpdateManager.getInstance().getFileName());
        File saveDir = new File(UpdateManager.getInstance().getDownLoadPath());
        if (!saveDir.exists()) {
            saveDir.mkdir();
            Log.e("saveDir", "okhttpStartdownLoad: " + saveDir.getAbsolutePath() + saveDir.exists());
        }
        deleteFile(saveFile);
        try {
            InputStream in = null;
            FileOutputStream out = null;
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder()
                    //确定下载的范围,添加此头,则服务器就可以跳过已经下载好的部分
                    .url(url)
                    .build();
            Response response = null;

            response = okHttpClient.newCall(request).execute();


            if (response.isSuccessful()) {
                in = response.body().byteStream();
                out = new FileOutputStream(saveFile, true);//②,写文件的时候,以追加的方式去写
                Long contentLength = Long.valueOf(response.header("Content-Length"));
                Log.e("tag", "okhttpStartdownLoad: " + contentLength);
                int len = 0;
                long progress = 0;
                long currentTime = 0;
                byte[] buffer = new byte[1024];
                while ((len = in.read(buffer)) != -1) {
                    if (breakDownload) {
                        deleteFile(saveFile);
                        break;
                    }
                    out.write(buffer, 0, len);
                    progress += len;
                    if (System.currentTimeMillis() - 500 > currentTime) {
                        currentTime = System.currentTimeMillis();
                        int curentProgress = 0;
                        if (contentLength != 0) {
                            curentProgress = (int) (((float) progress) / ((float) contentLength) * 100);
                        }
                        handler.obtainMessage(DownloadManager.STATUS_RUNNING, curentProgress).sendToTarget();//发送到主线程，更新ui
                    }
                         /*#######################################*/
                    //下载完成,按照while循环的语法,就会再一次来到((len = in.read(buffer)) != -1),
                    //但是在okHttp里面,看源码发现,如果这个时候再去读,读出来的结果是-1的时候会抛出异常
//                        Log.e(TAG, "run: saveFile.length()" + saveFile.length()+mDownLoadInfo.max);
                    if (contentLength != 0) {
                        if (saveFile.length() == contentLength) {//下载完成,提前跳出while循环
                            break;
                        }
                    }

                }
                //下载完成
                if (!breakDownload) {
                    handler.obtainMessage(DownloadManager.STATUS_SUCCESSFUL).sendToTarget();//发送到主线程，更新ui
                }


            } else {
                    /*############### 当前状态:下载失败 ###############*/
                handler.obtainMessage(DownloadManager.STATUS_FAILED).sendToTarget();//发送到主线程，更新ui
                    /*#######################################*/
            }
        } catch (IOException e) {
            e.printStackTrace();
            deleteFile(saveFile);
                 /*############### 当前状态:下载失败 ###############*/
            handler.obtainMessage(DownloadManager.STATUS_FAILED).sendToTarget();//发送到主线程，更新ui
                /*#######################################*/
        } finally {
            close(out);
            close(in);
        }
    }

    private void deleteFile(File file) {
        if (file != null && file.isFile() && file.exists()) {
            file.delete();
        }

    }

    public void close(Closeable x) {
        if (x != null) {
            try {
                x.close();
            } catch (Exception e) {
                // skip
            }
        }
    }


    public void breakDownload() {
        breakDownload = true;
        if (downloadManager != null) {
            downloadManager.remove(requestId);
        }
    }
}
