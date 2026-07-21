package top.kzre.krro.util.tile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RLE {
    /**
     * 将浮点数组进行游程编码并写入输出流。
     * 格式：总长度(int) → [计数(int) 值(float)]...
     * 计数 > 0 表示不重复的浮点数个数，紧跟相应数量的 float；
     * 计数 < 0 表示重复次数（绝对值），紧跟一个 float 作为重复值。
     */
    public static void writeRLE(float[] data, DataOutputStream out) throws IOException {
        out.writeInt(data.length);
        int i = 0;
        while (i < data.length) {
            // 寻找连续相等段
            int j = i + 1;
            while (j < data.length && data[j] == data[i]) {
                j++;
            }
            int repeatCount = j - i;
            if (repeatCount >= 2) {          // 至少重复2次才压缩，避免膨胀
                out.writeInt(-repeatCount);
                out.writeFloat(data[i]);
                i = j;
            } else {
                // 收集不重复段
                int start = i;
                while (i < data.length) {
                    int k = i + 1;
                    while (k < data.length && data[k] == data[i]) {
                        k++;
                    }
                    if (k - i >= 2) {        // 遇到下一个重复段，停止收集
                        break;
                    }
                    i++;
                }
                int len = i - start;
                out.writeInt(len);
                for (int t = start; t < i; t++) {
                    out.writeFloat(data[t]);
                }
            }
        }
    }

    /**
     * 从输入流读取游程编码数据并解码为浮点数组。
     * 输入流必须包含由 encodeRLE 写入的长度前缀。
     */
    public static float[] readRLE(DataInputStream in) throws IOException {
        int totalLength = in.readInt();
        float[] result = new float[totalLength];
        int index = 0;
        while (index < totalLength) {
            int count = in.readInt();
            if (count < 0) {
                int repeat = -count;
                float val = in.readFloat();
                for (int i = 0; i < repeat; i++) {
                    result[index++] = val;
                }
            } else if (count > 0) {
                for (int i = 0; i < count; i++) {
                    result[index++] = in.readFloat();
                }
            } else {
                throw new IOException("无效的游程编码数据：count为0");
            }
        }
        return result;
    }
}
