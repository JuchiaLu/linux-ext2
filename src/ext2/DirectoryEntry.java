package ext2;

import com.google.common.primitives.Bytes;

public class DirectoryEntry {

    //文件类型常量
    public static final byte DIRECTORY = 1;//目录
    public static final byte FILE = 2;//文件
    public static final byte SYM_LINK = 3;//软链接
    public static final byte HARD_LINK = 4;//硬链接
    // //节点号码(4 bytes)
    private int inode;
    // 记录长度 (2 bytes)
    private short recLen;//记录长度（目录项真实长度(名字优化过后的)，但目录块中最后一个目录项的记录长度总是等于这个块上次剩余的长度）, 用于读取和删除时使用这个字段 这是在目录块的 addEntry方法中计算的
    // 名字长度 (1 byte)
    private byte nameLen;
    // 文件类型 (1 byte)
    private byte fileType;
    // 文件名 (0 - 255 bytes)
    private String filename;//这里会优化使其为4个字节的整数倍

    public DirectoryEntry(int inode, byte type, String name) {
        this.inode = inode;
        fileType = type;
        filename = name;
        nameLen = (byte) filename.length(); //只要文件名的长度不大于255字节, 就能强转成byte
        if (nameLen % 4 != 0) {
            //不是4的倍数，填充null
            int nullsToAdd = 4 - (nameLen % 4);//计算需要填充o的数量
            for (int i = 0; i < nullsToAdd; i++) {
                filename += '\0';
            }
        }
    }

    public DirectoryEntry(int inode, short recLen, byte type, String name) {
        this(inode, type, name);
        this.recLen = recLen;
    }

    // 一个目录项的字节数组表示，因此才可以将它写回磁盘
    public byte[] toByteArray() {
        final byte I_NODE[] = BitUtils.toByteArray(inode);
        final byte REC_LEN[] = BitUtils.toByteArray(recLen);
        final byte NAME_LEN[] = BitUtils.toByteArray(nameLen);
        final byte TYPE[] = BitUtils.toByteArray(fileType);
        final byte FILE_NAME[] = filename.getBytes();
        return Bytes.concat(I_NODE, REC_LEN, NAME_LEN, TYPE, FILE_NAME);
    }

    //理想长度:每个目录项的理想长度(4的倍数)取决于它的文件名有多少个字符
    // 如文件名长度1-4: 12        4-8: 16    8-12: 20  固定长度为8字节,加上文件名的优化过后的长度等于理想长度
    //返回整个目录项的理想长度, 而不是文件名的理想长度
    public short getIdealLen() {
        return (short) (4 * ((8 + nameLen + 3) / 4));
    }

    public int getInode() {
        return inode;
    }

    public void setInode(int inode) {
        this.inode = inode;
    }

    public short getRecLen() {
        return recLen;
    }

    public void setRecLen(short recLen) {
        this.recLen = recLen;
    }

    public int getType() {
        return fileType;
    }

    public String getFilename() {
        return filename.trim();
    }
}
