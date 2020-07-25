package ext2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

public class Disk extends RandomAccessFile {


    // 256 MB = 262,144 KB = 268,435,456 bytes
    private final int SIZE_MB = 256;//磁盘大小, 单位MB

    public Disk(File file) throws FileNotFoundException {
        super(file, "rw");//参数： 文件路径，操作模式
    }

    public int getSizeMB() {
        return SIZE_MB;
    }

    public int getSizeKB() {
        return SIZE_MB * 1024;
    }

    public int getSizeBytes() {
        return getSizeKB() * 1024;
    }
}
