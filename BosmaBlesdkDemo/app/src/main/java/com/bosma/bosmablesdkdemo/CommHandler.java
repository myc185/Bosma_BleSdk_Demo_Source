package com.bosma.bosmablesdkdemo;

import android.os.Handler;
import android.os.Looper;

/**
 * 项目名称：BluetoothKey
 * 类描述：
 * 创建人：moyc
 * 创建时间：2016/1/12 18:12
 * 修改人：moyc
 * 修改时间：2016/1/12 18:12
 * 修改备注：
 */
public abstract class CommHandler extends Handler {
    public CommHandler(Looper looper) {
        super(looper);
    }

}
