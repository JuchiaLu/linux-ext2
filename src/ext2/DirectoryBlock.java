package ext2;

import java.util.ArrayList;

// 这是一个块的类, 代表磁盘的一个块, 一个块可以存多个目录项
public class DirectoryBlock extends ArrayList<DirectoryEntry> {

    private final int BLOCK;//块号

    public DirectoryBlock(int block) {
        this.BLOCK = block;
    }

    public void addEntry(DirectoryEntry dirEntry) {
        if (this.isEmpty()) {//判断块中有没有目录项
            dirEntry.setRecLen((short) (FileSystem.BLOCK_SIZE));//让第一个目录项的记录长度设为块大小，读取时才知道里面只有一个目录项
            this.add(dirEntry);
        } else {
            int remaining = getRemainingLength();//获取块剩余的长度

            //将前面的dir_entry的rec_len更改为它的ideal_len
            DirectoryEntry lastDirEntry = this.getLastEntry();//获取这个块中存的最后一个目录项
            lastDirEntry.setRecLen(lastDirEntry.getIdealLen());//还原非最后一个目录项的记录长度为真实长度

            dirEntry.setRecLen((short) remaining);//把剩余的长度“全部记给”这个目录项，这样读取目录项时时才知道读取完了
            this.add(dirEntry);
        }
    }


    //返回从0到所给位置的所有记录长度的和
    public int getOffset(int index) {//这个索引代表list中目录项的索引
        if (index == 0 || index >= this.size()) {
            return 0;
        }
        int recLen = 0;
        for (int i = 0; i < index; i++) {
            recLen += this.get(i).getRecLen();
        }
        return recLen;
    }

    //返回剩余的长度(达到4KB, 即还剩多少字节占满这个块)
    public int getRemainingLength() {
        DirectoryEntry lastEntry = this.getLastEntry();//获取块中的最后一个目录项
        int lastRecLen = lastEntry.getRecLen();//最后一个目录项的记录长度
        int lastIdealLen = lastEntry.getIdealLen();//最后一个目录项的理想长度
        return lastRecLen - lastIdealLen;//记录长度-理想长度（就是目录项的真实长度，因为目录项的名字要优化成四个字节）
        //最后一个目录项的长度总是等于这个块上次剩余的长度，上次剩余长度-真实长度 得到本次剩余长度
    }

    //获取块已用的长度: 块长度-剩余的
    public int getLength() {
        return (FileSystem.BLOCK_SIZE) - getRemainingLength();
    }

    //获取最后一个目录项
    public DirectoryEntry getLastEntry() {
        return get(size() - 1);
    }

    //获取块号
    public int getBlock() {
        return BLOCK;
    }

    // 是否还有其他目录, 如果这个块除了有本目录和父目录外的其他目录项返回ture
    public boolean hasEntries() {
        for (DirectoryEntry entry : this) {
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) continue;
            return true;
        }
        return false;
    }
}
