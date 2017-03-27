package com.mao.mao_player.util;

/**
 * Created by 毛麒添 on 2016/10/13 0013.
 * 格式转换
 */

public class FormatUtil {
    /*
    字节数组转换成十六进制
     */
    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            //数据先转换成int,再由后利用Integer.toHexString(int)来转换成十六进制
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    /*
   十六进制转换成字节数组
    */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars=hexString.toCharArray();
        byte[] d=new byte[length];
        for (int i = 0; i < length; i++) {
            int pos=i*2;
            d[i]=(byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c){
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
}
