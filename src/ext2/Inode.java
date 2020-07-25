package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Math.toIntExact;

public class Inode {

    //节点类型常量，不等同于目录项类型常量
    public static final int DIRECTORY = 1;//目录
    public static final int FILE = 2;// 文件
    public static final int SYM_LINK = 3;//软链接
    // 4 bytes
    private int type;//节点类型
    // 4 bytes
    private int size;//文件大小（目录类型不填这个字段）
    // 4 bytes
    private int creationTime;//创建时间
    // 4 bytes
    private int modifiedTime;//修改时间
    // 4 bytes
    private int lastAccessTime;//最近访问时间
    // 4 bytes
    private int deletionTime;//删除时间, 硬盘软删除
    // 4 bytes
    private int linkCount;//链接数量
    // 48 bytes (12 x 4 bytes)
    private final int[] directPointers = new int[12];//12个直接指针，软链接时，这段空间用来存url，url长度不够就填充0
    // 4 bytes
    private int indirectPointer;//间接指针
    // Inode number
    private int inode;//节点号码字段,并不存到磁盘，只是方便程序使用，存到磁盘的只有八十个字节, 根据磁盘偏移量可以计算出节点号
    // Sym link url
    private String url = "";//软链接地址字段，,并不存到磁盘，只是方便程序使用，而是占用directPointers字段的空间，存到磁盘

    public Inode(int inode, int type) {//给节点类型是目录或软链接时使用
        this.inode = inode;
        this.type = type;
        creationTime = modifiedTime = lastAccessTime = toIntExact(System.currentTimeMillis() / 1000);
        linkCount = 1;
    }

    public Inode(int inode, int type, int size) {//给节点类型是文件时使用
        this(inode, type);
        this.size = size;
    }

    //添加块到直接指针位置中
    public void addBlocks(int... blocks) {//可以传多个块号进来, 一个节点可以占用多个块
        int pointersLeft = 12 - getDirectBlocks().size();//剩余装直接指针的位置数量
        if (blocks.length > pointersLeft) {//传进来的块数比剩余的直接指针位置数量还多
            throw new IllegalArgumentException(String.format("只剩 %d 个直接指针位置, 你要添加%d个块, 指针位置不够!",
                    pointersLeft,
                    blocks.length));
        }
        for (int block : blocks) {
            for (int i = 0; i < 12; i++) {
                if (directPointers[i] == 0) {//判断原指针位置是否为空
                    directPointers[i] = block;//为空,将块号写入
                    break;
                }
            }
        }
    }

    // 从byte数组[]（磁盘）中读取80个字节，并从中创建一个新的Inode实例, 80个字节是节点的数据结构总长度
    public static Inode fromByteArray(byte array[], int inodeNumber) {
        // 将80字节数组拆分为子数组
        final byte TYPE[] = Arrays.copyOfRange(array, 0, 4);

        // 在继续之前，检查类型是否为0(没有inode使用类型0。如果是0，则表示没有inode)
        int type = Ints.fromByteArray(TYPE);
        if (type == 0) return null;

        //下面类似, 将那80个字节切割成属性
        final byte SIZE[] = Arrays.copyOfRange(array, 4, 8);
        final byte CR_TIME[] = Arrays.copyOfRange(array, 8, 12);
        final byte M_TIME[] = Arrays.copyOfRange(array, 12, 16);
        final byte A_TIME[] = Arrays.copyOfRange(array, 16, 20);
        final byte DEL_TIME[] = Arrays.copyOfRange(array, 20, 24);
        final byte LINKS[] = Arrays.copyOfRange(array, 24, 28);
        final byte POINTERS[] = Arrays.copyOfRange(array, 28, 76);
        final byte IND_POINTER[] = Arrays.copyOfRange(array, 76, 80);

        // 将字节数组转换成相应的属性
        int size = Ints.fromByteArray(SIZE);
        int crTime = Ints.fromByteArray(CR_TIME);
        int modTime = Ints.fromByteArray(M_TIME);
        int accTime = Ints.fromByteArray(A_TIME);
        int delTime = Ints.fromByteArray(DEL_TIME);
        int links = Ints.fromByteArray(LINKS);
        int indPointer = Ints.fromByteArray(IND_POINTER);
        String url = "";
        int pointers[] = null;//指针未定，先判断节点类型后做决定

        if (type == SYM_LINK) {//节点类型是软链接
            url = new String(POINTERS);//十二个指针位置存的就是链接源地址
        } else {
            // 不是软链接，则指针位置中存的就是块号,将块号还原出来
            IntBuffer intBuffer = ByteBuffer.wrap(POINTERS).asIntBuffer();
            pointers = new int[intBuffer.remaining()];
            intBuffer.get(pointers);//将buffer内容保存到 pointers数组
        }

        // 创建实例并返回它
        Inode inode = new Inode(inodeNumber, type, size);
        inode.setCreationTime(crTime);
        inode.setModifiedTime(modTime);
        inode.setLastAccessTime(accTime);
        inode.setDeletionTime(delTime);
        inode.setLinkCount(links);
        if (type == SYM_LINK) {
            inode.setSymLinkUrl(url);
        } else {
            inode.addBlocks(pointers);//链接类型节点是没有块指针的
        }
        inode.setIndirectPointer(indPointer);
        return inode;
    }

    //将inode转换成byte后才能写入硬盘
    public byte[] toByteArray() {
        final byte TYPE[] = BitUtils.toByteArray(type);
        final byte SIZE[] = BitUtils.toByteArray(size);
        final byte CR_TIME[] = BitUtils.toByteArray(creationTime);
        final byte M_TIME[] = BitUtils.toByteArray(modifiedTime);
        final byte A_TIME[] = BitUtils.toByteArray(lastAccessTime);
        final byte DEL_TIME[] = BitUtils.toByteArray(deletionTime);
        final byte LINKS[] = BitUtils.toByteArray(linkCount);

        byte urlBytes[] = new byte[48];
        if (type == SYM_LINK) {//写入的时候，如果节点类型是软链接，就把那12个指针位置用来存url，不够48字节则填充0
            byte[] bytes = url.getBytes();
            for (int i = 0; i < urlBytes.length; i++) {
                if (i > bytes.length - 1) {
                    urlBytes[i] = '\0';
                } else {
                    urlBytes[i] = bytes[i];
                }
            }
        }

        final byte POINTERS[] = (type == SYM_LINK) ? urlBytes : BitUtils.toByteArray(directPointers);
        final byte IND_POINTERS[] = BitUtils.toByteArray(indirectPointer);
        return Bytes.concat(TYPE, SIZE, CR_TIME, M_TIME, A_TIME, DEL_TIME, LINKS, POINTERS, IND_POINTERS);
    }

    //获取节点指向的12直接块号
    public ArrayList<Integer> getDirectBlocks() {
        ArrayList<Integer> blocks = new ArrayList<>();
        for (int i : directPointers) {
            if (i == 0) continue;
            blocks.add(i);
        }
        return blocks;
    }

    public void setSymLinkUrl(String url) {
        this.url = url.trim();
    }

    public String getSymLinkUrl() {
        return url;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(int time) {
        creationTime = time;
    }

    public int getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(int modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public int getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(int lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public int getDeletionTime() {
        return deletionTime;
    }

    public void setDeletionTime(int time) {
        deletionTime = time;
    }

    public int getLinkCount() {
        return linkCount;
    }

    public void setLinkCount(int linkCount) {
        this.linkCount = linkCount;
    }

    public int getIndirectPointer() {
        return indirectPointer;
    }

    public void setIndirectPointer(int indirectPointer) {
        this.indirectPointer = indirectPointer;
    }

    public int getInode() {
        return inode;
    }

    public int getType() {
        return type;
    }
}
