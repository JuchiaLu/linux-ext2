package ext2;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            Disk disk; //磁盘
            FileSystem fileSystem;//文件系统
            File binaryFile = new File("disk.bin");//打开disk.bin,下面当作虚拟磁盘
            if (binaryFile.exists() && !binaryFile.isDirectory()) {
                disk = new Disk(binaryFile);//新建一个虚拟磁盘
                fileSystem = new FileSystem(disk);//new一个文件系统对象
                fileSystem.load();//载入文件系统
            } else {
                binaryFile.createNewFile();//disk.bin不存在, 新建
                disk = new Disk(binaryFile);
                fileSystem = new FileSystem(disk);
                System.out.println("格式化磁盘中...");
                fileSystem.format();
                System.out.println("格式化完成");
            }
            Shell shell = new Shell(fileSystem);//建立一个shell界面
            shell.start();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
