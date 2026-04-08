package com.serial.port.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ByteUtils {

    public static int isOdd(int num) {
        return num & 0x1;
    }

    public static int HexToInt(String inHex) {
        return Integer.parseInt(inHex, 16);
    }

    public static byte HexToByte(String inHex) {
        return (byte) Integer.parseInt(inHex, 16);
    }

    public static byte[] hexToByteArr(String hex) {
        int hexLen = hex.length();
        byte[] result;
        if (isOdd(hexLen) == 1) {
            hexLen++;
            result = new byte[hexLen / 2];
            hex = "0" + hex;
        } else {
            result = new byte[hexLen / 2];
        }
        int j = 0;
        for (int i = 0; i < hexLen; i += 2) {
            result[j] = HexToByte(hex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    //将字节数组转换为short类型，即统计字符串长度
    public static short bytes2Short2(byte[] b) {

        short i = (short) (((b[1] & 0xff) << 8) | b[0] & 0xff);
        return i;

    }

    //将字节数组转换为16进制字符串
    public static String BinaryToHexString(byte[] bytes) {

        String hexStr = "0123456789ABCDEF";
        String result = "";
        String hex = "";
        for (byte b : bytes) {
            hex = String.valueOf(hexStr.charAt((b & 0xF0) >> 4));
            hex += String.valueOf(hexStr.charAt(b & 0x0F));
            result += hex + " ";
        }
        return result;
    }

    //3.short转换为byte数组
    public static byte[] short2Bytes(short value) {
        byte[] data = new byte[2];
        data[0] = (byte) (value >> 8 & 0xff);
        data[1] = (byte) (value & 0xFF);
        return data;
    }

    /**
     * 将int转化成byte[]
     *
     * @param res 要转化的整数
     * @return 对应的byte[]
     */
    public static byte[] int2byte(int res) {
        byte[] targets = new byte[4];
        targets[0] = (byte) (res & 0xff);// 最低位
        targets[1] = (byte) ((res >> 8) & 0xff);// 次低位
        targets[2] = (byte) ((res >> 16) & 0xff);// 次高位
        targets[3] = (byte) (res >>> 24);// 最高位,无符号右移。
        return targets;
    }

    //  int zhuanhuawei byte[4]

    /**
     * 将byte[]转化成int  // byte[] int  mingitan byte[] int
     * @param res 要转化的byte[]
     * @return 对应的整数
     */
    public static int byte2int(byte[] res) {
        int targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00) | ((res[2] << 24) >>> 8) | (res[3] << 24);
        return targets;
    }

    /**
     * 将两个byte（数据的高低字节）转化成一个int
     * @param dataH 数据高字节
     * @param dataL 数据低字节
     * @return
     */
    public static int twobyte2int(byte dataH,byte dataL) {
        int targets = ((dataH & 0xff) << 8) + (dataL & 0xff);
       // int i = (short) ((dataH << 8) | dataL);
        return targets;
    }

    public static String byteArrayToHexStr(byte[] byteArray) {
        if (byteArray == null){
            return null;
        }
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[byteArray.length * 2];
        for (int j = 0; j < byteArray.length; j++) {
            int v = byteArray[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStrToByteArray(String str)
    {
        if (str == null) {
            return null;
        }
        if (str.length() == 0) {
            return new byte[0];
        }
        byte[] byteArray = new byte[str.length() / 2];
        for (int i = 0; i < byteArray.length; i++){
            String subStr = str.substring(2 * i, 2 * i + 2);
            byteArray[i] = ((byte) Integer.parseInt(subStr, 16));
        }
        return byteArray;
    }

    //inputstream转byte[]
    public static byte[] toByteArray(InputStream input) {

        // ByteARRAYOUTPUTSTREAM  output = new ByteArrayOutputStream();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        try {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output.toByteArray();
    }


    public static String toHexString(byte[] input, String separator) {
        if (input == null) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length; i++) {
            if (separator != null && sb.length() > 0) {
                sb.append(separator);
            }
            String str = Integer.toHexString(input[i] & 0xff);
            if (str.length() == 1) str = "0" + str;
            sb.append(str);
        }
        return sb.toString().toUpperCase();
    }

    public static String toHexString(byte[] input) {
        return toHexString(input, " ");
    }

    public static byte[] fromInt16(int input) {
        byte[] result = new byte[2];
        result[0] = (byte) (input >> 8 & 0xFF);
        result[1] = (byte) (input & 0xFF);
        return result;
    }

    public static byte[] fromInt16Reversal(int input) {
        byte[] result = new byte[2];
        result[1] = (byte) (input >> 8 & 0xFF);
        result[0] = (byte) (input & 0xFF);
        return result;
    }



    /**
     * 把单个字节转换成二进制字符串
     */
    public static String byteToBin(byte b) {
        String zero = "00000000";
        String binStr = Integer.toBinaryString(b & 0xFF);
        if(binStr.length() < 8) {
            binStr = zero.substring(0, 8 -binStr.length()) + binStr;
        }
        System.out.println(binStr);
        return binStr;
    }

    /**
     * 获取字节在内存中某一位的值,采用字符取值方式
     */
    public static Integer getBitByByte(byte b, int index) {
        if(index >= 8) { return null; }
        Integer val = null;
        String binStr = byteToBin(b);
        val = Integer.parseInt(String.valueOf(binStr.charAt(index)));
        return val;
    }

    /**
     * 获取字节在内存中多位的值,采用字符取值方式(包含endIndex位)
     */
    public static Integer getBitByByte(byte b, int begIndex, int endIndex) {
        if(begIndex >= 8 || endIndex >= 8 || begIndex >= endIndex) { return null; }
        Integer val = null;
        String binStr = byteToBin(b);
        val = Integer.parseInt(binStr.substring(begIndex, endIndex +1), 2);
        return val;
    }
}