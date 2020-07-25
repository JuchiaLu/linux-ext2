package ext2;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;

//位工具类
public final class BitUtils {


    //返回下一个空闲位索引
    public static int nextClearBit(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) == 0) return index;
                index++;
            }
        }
        return 0;
    }


    //返回第一个不空闲位的索引
    public static int nextSetBit(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) != 0) return index;
                index++;
            }
        }
        return 0;
    }


    //返回第一个空闲位的索引,并将其置为不空闲
    public static int nextClearBitThenSet(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) == 0) {
                    b |= (1 << j); // set bit j to 1
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }


    //返回第一个不空闲位的索引,并将其置为空闲
    public static int nextSetBitThenClear(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) != 0) {
                    b &= ~(1 << j); // set bit j to 0
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }


    //置所给索引位，置为不空闲
    public static void setBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Set bit
                if (index == bitIndex) {
                    b |= (1 << j); // set bit j to 1
                    array[i] = b;
                    return;
                }
                index++;
            }
        }
    }

    //置所给索引位，置为空闲
    public static void clearBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Set bit
                if (index == bitIndex) {
                    b &= ~(1 << j); // set bit j to 0
                    array[i] = b;
                    return;
                }
                index++;
            }
        }
    }


    //取反所给索引位置
    public static void toggleBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (index == bitIndex) {
                    // Toggle bit
                    b ^= (1 << j);
                    array[i] = b;
                    return;
                }
                index++;
            }
        }
    }


    //返回所有不空闲的位置索引
    public static ArrayList<Integer> findAllSetBits(byte[] array) {
        ArrayList<Integer> list = new ArrayList<>();
        int index = 1;
        for (byte b : array) {
            for (int bit = 7; bit >= 0; bit--) {
                if (((b >>> bit) & 1) != 0) list.add(index);
                index++;
            }
        }
        return list;
    }


    //将位图转换成List
    public static ArrayList<Integer> bitMapToList(byte[] array) {
        ArrayList<Integer> list = new ArrayList<>();
        for (byte b : array) {
            for (int bit = 7; bit >= 0; bit--) {
                if (((b >>> bit) & 1) != 0) {
                    list.add(1);
                } else {
                    list.add(0);
                }
            }
        }
        return list;
    }


    //分割成固定chunkSize大小的组
    public static byte[][] splitBytes(byte[] data, int chunkSize) {
        final int length = data.length;
        int groundCount = (int)length/chunkSize;
        int more = length % chunkSize;
        if(more!=0){
            groundCount+=1;
        }
        final byte[][] result = new byte[groundCount][];
        if(groundCount==1){
            result[0] = data;
        } else {
            for (int i = 0; i < groundCount-1; i++) {
                result[i] = Arrays.copyOfRange(data, i * chunkSize, (i + 1) * chunkSize);
            }
            result[groundCount-1] = Arrays.copyOfRange(data, (groundCount-1) * chunkSize, length);
        }
        return result;
    }

    //将多个int转成byte[]
    public static byte[] toByteArray(int... array) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(array);
        return byteBuffer.array();
    }

    //将多个short转成byte[]
    public static byte[] toByteArray(short... array) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 2);
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
        shortBuffer.put(array);
        return byteBuffer.array();
    }

    //将多个byte转成byte[]
    public static byte[] toByteArray(byte... array) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length);
        byteBuffer.put(array);
        return byteBuffer.array();
    }


    //以二进制形式打印每个byte
    public static void printBytes(byte... array) {
        String bin = "";
        for (byte b : array) {
            bin += String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0') + " ";
        }
        System.out.println(bin);
    }
}
