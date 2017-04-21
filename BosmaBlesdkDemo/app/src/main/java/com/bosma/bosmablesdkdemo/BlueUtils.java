/*  
 * 文件名：     Utils.java 
 * 版权 ：        Bosma Technologies Co., Ltd. Copyright 1999-2050, All rights reserved 
 * 文件描述： (用一句话描述该文件做什么)
 * 修改人：     [moyc][莫运川]  
 * 生成时间：  2014年7月14日 下午1:56:49 
 * 修改记录：
 */
package com.bosma.bosmablesdkdemo;

/**   
 * 蓝牙字符转换辅助类
 * @author moyc
 * @version： [版本号， 2014年7月14日] 
 * 
 */
public class BlueUtils {
	
	/**
	 * Convert byte[] to hex string.这里我们可以将byte转换成int，然后利用Integer.toHexString(int)来转换成16进制字符串。  
	 * @param src byte[] data  
	 * @return hex string  
	 */     
	public static String bytesToHexString(byte[] src){
	    StringBuilder stringBuilder = new StringBuilder("");
	    if (src == null || src.length <= 0) {  
	        return null;  
	    }  
	    for (int i = 0; i < src.length; i++) {  
	        int v = src[i] & 0xFF;  
	        String hv = Integer.toHexString(v);
	        if (hv.length() < 2) {  
	            stringBuilder.append(0);  
	        }  
	        stringBuilder.append(hv);  
	    }  
	    return stringBuilder.toString();  
	}  
	/** 
	 * Convert hex string to byte[] 
	 * @param hexString the hex string 
	 * @return byte[] 
	 */  
	public static byte[] hexStringToBytes(String hexString) {
	    if (hexString == null || hexString.equals("")) {  
	        return null;  
	    }  
	    hexString = hexString.toUpperCase();  
	    int length = hexString.length() / 2;  
	    char[] hexChars = hexString.toCharArray();  
	    byte[] d = new byte[length];  
	    for (int i = 0; i < length; i++) {  
	        int pos = i * 2;  
	        d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));  
	    }  
	    return d;  
	}  
	/** 
	 * Convert char to byte 
	 * @param c char 
	 * @return byte 
	 */  
	 private static byte charToByte(char c) {  
	    return (byte) "0123456789ABCDEF".indexOf(c);  
	}

	/**
	 *
	 * @param mac
	 * @return
	 */
	public static String getMacAdress(String mac) {
		if(mac == null || "".equals(mac)) {
			return null;
		}

		StringBuffer macBuffer = new StringBuffer();
		for(int i = 0; i < mac.length(); i = i+2) {

			if(i == mac.length() - 2) {
				macBuffer.append(mac.substring(i,mac.length()));
			} else {
				macBuffer.append(mac.substring(i,i+2)).append(":");
			}

		}
		return macBuffer.toString();
	}

}
