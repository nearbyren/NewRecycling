package com.serial.port.utils;

public class CmdData {

    //开仓回复：7F 03 00 02 12 01 08 09
    public static final byte[] cmd_open_1 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x01 ,0x01};
    public static final byte[] cmd_open_2 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x02 ,0x01};
    public static final byte[] cmd_open_3 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x03 ,0x01};
    public static final byte[] cmd_open_4 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x04 ,0x01};
    public static final byte[] cmd_open_5 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x05 ,0x01};
    public static final byte[] cmd_open_6 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x06 ,0x01};
    public static final byte[] cmd_open_7 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x07 ,0x01};
    public static final byte[] cmd_open_8 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x08 ,0x01};
    public static final byte[] cmd_open_9 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x09 ,0x01};
    public static final byte[] cmd_open_10 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x0A ,0x01};
    public static final byte[] cmd_open_11 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x0B ,0x01};
    public static final byte[] cmd_open_12 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x0C ,0x01};

    public static final byte[] cmd_close_1 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x01 ,0x01};
    public static final byte[] cmd_close_2 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x02 ,0x01};
    public static final byte[] cmd_close_3 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x03 ,0x01};
    public static final byte[] cmd_close_4 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x04 ,0x01};
    public static final byte[] cmd_close_5 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x05 ,0x01};
    public static final byte[] cmd_close_6 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x06 ,0x01};
    public static final byte[] cmd_close_7 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x07 ,0x01};
    public static final byte[] cmd_close_8 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x08 ,0x01};
    public static final byte[] cmd_close_9 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x09 ,0x01};
    public static final byte[] cmd_close_10 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x0A ,0x01};
    public static final byte[] cmd_close_11 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x0B ,0x01};
    public static final byte[] cmd_close_12 = new byte[]{0x7F ,0x09 ,0x00 ,0x02 ,0x0C ,0x01};
    public static final byte[] cmd_one_power = new byte[]{0x7F ,0x0A ,0x00 ,0x02 ,0x01 ,0x01};


    public static final byte[] cmd_open_13 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x0D ,0x01};
    public static final byte[] cmd_open_14 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x0E ,0x01};
    public static final byte[] cmd_open_15 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x0F ,0x01};
    public static final byte[] cmd_open_16 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x10 ,0x01};
    public static final byte[] cmd_open_17 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x11 ,0x01};
    public static final byte[] cmd_open_18 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x12 ,0x01};
    public static final byte[] cmd_open_19 = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x13 ,0x01};
//    public static final byte[] cmd_open_all = new byte[]{0x7F ,0x03 ,0x00 ,0x02 ,0x12 ,0x01};
    //仓状态回复-------------------------------------------------------------DATA18 01：消防状态
    //查询回复：7F 06 00 27 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 18 19 11 12
    public static final byte[] cmd_status_arr = new byte[]{0x7F ,0x06 ,0x00 ,0x02 ,0x00 ,0x00};

    /**
     *   充电状态                门/电机状态
     * 0x00 空闲（门锁正常）      门异常 0x08
     * 0x01等待关门充电          门异常  0x09
     * 0x02正在充电             门异常  0xA
     * 0x03已充满               门异常  0xB
     * 0x04 充电故障            门异常  0xC
     * 0x05等待关门             门异常  0xD
     */

    public static int getStatusByCode(int code){
        if(code<=5){
            return code;
        }else {
            switch (code){
                case 8: return  0;
                case 9: return  1;
                case 10: return  2;
                case 11: return  3;
                case 12: return  4;
                case 13: return  5;
                default: return  0;
            }
        }
    }

    //开指定舱回复：7F 03 00 04 01 01 09 01 7A B3 ：开1号和9号
    public static final byte[] cmd_status_some = new byte[]{0x7F ,0x03 ,0x00 ,0x04 ,0x01 ,0x01 ,0x09 ,0x01};

    //存储仓状态指令，回复：7F 05 00 02 00 00 11 12
    public static final byte[] cmd_save_locker_status = new byte[]{0x7F ,0x05 ,0x00 ,0x02 ,0x00 ,0x00};

    //设置温湿度模块地址,设置为1  预计回复：01 06 00 0A 00 01 00 00
    public static final byte[] cmd_set_module_addr = new byte[]{0x01 ,0x06 ,0x00 ,0x0A ,0x00 ,0x01};

    //设置温湿度模块波特率,设置为9600  预计回复：01 06 00 0B 00 01 00 00
    public static final byte[] cmd_set_module_serial = new byte[]{0x01 ,0x06 ,0x00 ,0x0B ,0x00 ,0x01};

    //读取温湿度,01 04 00 00 00 02  预计回复：01 04 04 00 0B 00 01 00 00 //1.1℃，湿度0.1
    public static final byte[] cmd_get_temp_humid = new byte[]{0x01 ,0x04 ,0x00 ,0x00 ,0x00 ,0x02};

    //MCU复位
    public static final byte[] cmd_reset_mcu = new byte[]{0x7F ,0x0F ,0x00 ,0x02 ,0x00 ,0x00};

    //开灯
    public static final byte[] cmd_turn_on_light = new byte[]{0x7F ,0x10 ,0x00 ,0x02 ,0x00 ,0x01};

    //关灯
    public static final byte[] cmd_turn_off_light = new byte[]{0x7F ,0x10 ,0x00 ,0x02 ,0x00 ,0x00};

    //切换USB通道
    public static final byte[] cmd_switch_usb_channel = new byte[]{(byte) 0xEE, (byte) 0x99,0x00 ,0x02 ,0x01 ,0x01};

}
