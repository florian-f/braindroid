package org.florian.eeg.braindroid;

/**
 * Created by florian on 25.12.14.
 */
public class OpenBci {

    public static final int VENDOR_ID = 1027;
    public static final int PRODUCT_ID = 24597;
    public static final int BAUD_RATE = 115200;
    public static final byte PACKET_SIZE = (byte) 33;
    public static final byte START_BYTE = (byte) 0xa0;

    /*
     * see https://github.com/OpenBCI/OpenBCI_Processing/blob/master/OpenBCI_GUI/OpenBCI_ADS1299.pde
     */
    public static final float fs_Hz = 250.0f;  //sample rate used by OpenBCI board...set by its Arduino code
    public static final float ADS1299_Vref = 4.5f;  //reference voltage for ADC in ADS1299.  set by its hardware
    public static final float ADS1299_gain = 24;  //assumed gain setting for ADS1299.  set by its Arduino code
    public static final float scale_fac_uVolts_per_count = ADS1299_Vref / ((float)(Math.pow(2,23)-1)) / ADS1299_gain  * 1000000.f; //ADS1299 datasheet Table 7, confirmed through experiment
    public static final float scale_fac_accel_G_per_count = 0.002f / ((float)Math.pow(2,4));  //assume set to +/4G, so 2 mG per digit (datasheet). Account for 4 bits unused
    public static final float leadOffDrive_amps = 6.0e-9f;  //6 nA, set by its Arduino code

    public static float convertByteToMicroVolts(byte[] byteArray){
        return scale_fac_uVolts_per_count * interpret24bitAsInt32(byteArray);
    }

    /*
     * see http://docs.openbci.com/software/02-OpenBCI_Streaming_Data_Format
     *
     */
    public static int interpret24bitAsInt32(byte[] byteArray) {

            int newInt = (
                    ((0xFF & byteArray[0]) << 16) |
                            ((0xFF & byteArray[1]) << 8) |
                            (0xFF & byteArray[2])
            );
            if ((newInt & 0x00800000) > 0) {
                newInt |= 0xFF000000;
            } else {
                newInt &= 0x00FFFFFF;
            }
            return newInt;
    }

    public static int interpret16bitAsInt32(byte[] byteArray) {
        int newInt = (
                ((0xFF & byteArray[0]) << 8) |
                        (0xFF & byteArray[1])
        );
        if ((newInt & 0x00008000) > 0) {
            newInt |= 0xFFFF0000;
        } else {
            newInt &= 0x0000FFFF;
        }
        return newInt;
    }
}
