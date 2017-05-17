/*
 * 文 件 名:  BtReadManager.java
 * 版    权:  Bosma Technologies Co., Ltd. YYYY-YYYY,  All rights reserved
 * 描    述:  <描述>
 * 修 改 人:  moyc
 * 修改时间:  2015年7月28日
 * 跟踪单号:  <跟踪单号>
 * 修改单号:  <修改单号>
 * 修改内容:  <修改内容>
 */
package com.bosma.bosmablesdkdemo;

import android.os.Message;
import android.util.Log;

import com.bosma.blesdk.business.BleParseFactory;
import com.vise.utils.assist.StringUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 蓝牙数据读取处理类
 *
 * @author moyc
 * @version [版本号, 2015年7月28日]
 * @see [相关类/方法]
 * @since [产品/模块版本]
 */
public class BtReadManager {

    private static final String TAG_LOG = BtReadManager.class.getSimpleName();

    private BlockingQueue<String> sharedQueue = new LinkedBlockingQueue<String>();

    private static BtReadManager readManager;
    private CommHandler mHandler;

    /**
     * 是否是多包接收
     */
    private boolean isRecivingMulti;

    /**
     * 是否还在读取数据
     */
    private boolean isTaking;

    private BtReadManager(CommHandler handler) {
        new Thread(new ReceiveRuner(sharedQueue)).start();
        isTaking = true;
        this.mHandler = handler;
    }

    public static BtReadManager getIntence(CommHandler handler) {
        if (readManager == null) {
            readManager = new BtReadManager(handler);
        } else {
        }
        return readManager;
    }

    /**
     * 添加数据
     */
    public synchronized void add(String message) {
        Log.w(TAG_LOG, "Value From BlueTooth: " + message);
        sharedQueue.add(message);
    }

    public synchronized void removeAll() {
        if (sharedQueue == null) {
            return;
        }
        sharedQueue.clear();
    }

    public synchronized void onDestroy() {
        isTaking = false;
        sharedQueue = null;
        mHandler = null;
        readManager = null;
        Log.i(BtReadManager.class.getSimpleName(), "onDestroy");
    }

    class ReceiveRuner implements Runnable {
        private final BlockingQueue<String> sharedQueue;

        public ReceiveRuner(BlockingQueue<String> sharedQueue) {
            this.sharedQueue = sharedQueue;
        }

        @Override
        public void run() {
            while (isTaking) {
                try {
                    String message = sharedQueue.take();
                    if (!StringUtil.isEmpty(message)) {
                        if (isRecivingMulti) {
                            parseMultiPackgeData(message);
                        } else {
                            parseSinglePackeData(message);
                        }
                    }
                } catch (InterruptedException ex) {

                }
            }
        }

        /**
         * 解析单个包
         */
        private void parseSinglePackeData(String message) {

            if(BleParseFactory.getTherHandle().isHistroyCmd(message)) {
                // 历史数据
                Log.e(TAG_LOG, "历史数据");
                initTimer();
                parseHistroyData(message);
            } else {
                sendMessageTotTarget(1, message);
            }

        }

        /**
         * 处理多包数据
         */
        private void parseMultiPackgeData(String message) {
            parseHistroyData(message);
        }

        private void parseHistroyData(String message) {
            // 如果传输超时
            if (getHistoryOverTimeTick() > TIME_RECIVEROVERTIME) {
                Log.i(TAG_LOG, "多包数据接收超时");
                stopTimer();
                return;
            }
            resettHistoryOverTimeTick();
            longDataBuff.append(message);
            packageCount++;
            Log.i(TAG_LOG, "正在接收第 ：" + packageCount + " 个包  " + longDataBuff.toString());
            // 是否上传完毕（一个时序传六个包）
            if (packageCount == 6) {
                Log.i(TAG_LOG, "数据包上传完： " + longDataBuff.toString());
                sendMessageTotTarget(1, longDataBuff.toString());
                isRecivingMulti = false;
                stopTimer();

            } else if (packageCount > 6) {
                stopTimer();
            }

        }

        private void sendMessageTotTarget(int what, Object device) {
            Message message = mHandler.obtainMessage();
            if (message == null) {
                message = new Message();
            }
            message.what = what;
            message.obj = device;
            mHandler.sendMessage(message);
        }
    }

    /*****
     * 多报数据处理
     ***/
    private StringBuffer longDataBuff = new StringBuffer();
    private int packageCount = 0;
    /**
     * 历史数据时间超时 5秒, 线程100毫秒累加一次
     */
    private static final int TIME_RECIVEROVERTIME = 50;
    /**
     * 历史数据上传超时
     */
    private int tickhistoryForOverTime = 0;
    private boolean isTimerRun = false;

    /**
     * 初始化超时计时
     */
    private void initTimer() {
        timerStart();
        isRecivingMulti = true;
        longDataBuff.setLength(0);
        packageCount = 0;
        resettHistoryOverTimeTick();
    }

    /**
     * 停止线程
     */
    private void stopTimer() {
        isRecivingMulti = false;
        longDataBuff.setLength(0);
        packageCount = 0;
        isTimerRun = false;
    }

    /*****
     * 历史数据
     ***/
    // 启动历史数据时间管理线程
    private void timerStart() {
        if (!isTimerRun) {
            isTimerRun = true;
            new Thread(new ThreadTimer()).start();
        }
    }

    // 复位超时计数器
    private void resettHistoryOverTimeTick() {
        tickhistoryForOverTime = 0x00;
    }

    // 获取超时计数器数值
    private int getHistoryOverTimeTick() {
        return tickhistoryForOverTime;
    }

    class ThreadTimer implements Runnable {
        @Override
        public void run() {
            while (isTimerRun) {
                try {
                    Thread.sleep(100);
                    tickhistoryForOverTime++;
                } catch (Exception e) {
                    Log.e(TAG_LOG, e.toString());
                }
            }
        }
    }


}
