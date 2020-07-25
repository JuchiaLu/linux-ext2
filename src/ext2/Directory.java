package ext2;

import java.util.ArrayList;

//目录类 directory = new list<DirectoryBlock>(),
//一个节点可以对应多个目录项（如硬链接），但一个目录项只能对应一个节点
//一个目录可以有多个目录块，一个目录块里可写多个目录项
public class Directory extends ArrayList<DirectoryBlock> {

    public Directory() {
        super();
    }

    //找目录下是否有包含 name 的目录项, 新建目录时用来判断是否重名
    public DirectoryEntry findEntry(String name) {
        for (DirectoryBlock block : this) {//遍历所有目录块
            for (DirectoryEntry dirEntry : block) {//遍历每个块中的目录项
                if (dirEntry.getFilename().equals(name)) {
                    return dirEntry;
                }
            }
        }
        return null;
    }

    //找目录下是否有包含 name 的目录块
    public DirectoryBlock getBlockContaining(String name) {
        for (DirectoryBlock block : this) {//遍历目录下的每个块
            for (DirectoryEntry entry : block) {//遍历块中的每个目录项
                if (entry.getFilename().equals(name)) {
                    return block;
                }
            }
        }
        return null;
    }

    //返回本目录的节点号
    public int getInode() {
        DirectoryBlock firstBlock = this.get(0);//目录的第一个块
        DirectoryEntry self = firstBlock.get(0);//第一个块中的第一个目录项，存的就是自己
        return self.getInode();//返回目录项的节点号, 也就是这个目录自己的节点号
    }

    //返回此目录的父目录节点号
    public int getParentInode() {
        DirectoryBlock firstBlock = this.get(0);//目录的第一个块
        DirectoryEntry parent = firstBlock.get(1);//第一个块中的第二个目录项，存的就是父目录的目录项
        return parent.getInode();
    }

    //获取最后一个目录块，用于新填目录项时，写入最后一个目录块
    public DirectoryBlock getLastBlock() {
        return get(size() - 1);
    }
}
