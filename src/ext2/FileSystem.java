package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.Math.toIntExact;

public class FileSystem {

    private final Disk DISK;

    //块大小
    public static final int BLOCK_SIZE = 4096;//4096字节

    //三个特殊用途的块组, 每个块组占用的块个数
    private final int DATA_BITMAP_BLOCKS = 2; //数据块位示图, 占用的块数
    private final int INODE_BITMAP_BLOCKS = 1;// 节点位示图, 占用的块数
    private final int INODE_TABLE_BLOCKS = 20; //节点表, 占用的块数

    //三个特殊用途的块组, 每个块组占用的字节数
    private final int DATA_BITMAP_SIZE = DATA_BITMAP_BLOCKS * BLOCK_SIZE; // 8192 bytes, 数据块位示图, 占用的字节数
    private final int INODE_BITMAP_SIZE = INODE_BITMAP_BLOCKS * BLOCK_SIZE; // 4096 bytes, 节点位示图, 占用的字节数
    private final int INODE_TABLE_SIZE = INODE_TABLE_BLOCKS * BLOCK_SIZE; // 81920 bytes, 节点表, 占用的字节数

    //三个特殊用途的块组, 每个块组的开始在磁盘中的字节偏移量
    private final int DATA_BITMAP_OFFSET = 0; //数据块位示图开始, 磁盘偏移量(单位字节)
    private final int INODE_BITMAP_OFFSET = DATA_BITMAP_SIZE; // byte 8192, 节点位示图开始, 磁盘偏移量(单位字节)
    private final int INODE_TABLE_OFFSET = INODE_BITMAP_OFFSET + INODE_BITMAP_SIZE; // byte 12288, 节点表开始, 磁盘偏移量(单位字节)
    private final int DATA_OFFSET = INODE_TABLE_OFFSET + INODE_TABLE_SIZE; // byte 94208, 数据块开始, 磁盘偏移量(单位字节)

    // 位示图
    private final byte DATA_BITMAP[] = new byte[DATA_BITMAP_SIZE]; //数据块位示图, 每一bit代表一个块, 为0代表空闲, 为1代表占用, 8192byte*8 = 65536个块
    private final byte INODE_BITMAP[] = new byte[INODE_BITMAP_SIZE]; //节点位示图, 每一bit代表一个节点, 为0代表空闲, 为1代表占用 4096byte*8 = 32768个节点

    private Directory currentDir; //当前目录, 用于表示当前操作目录
    private InodeTable inodeTable; //节点表

    public static final String ANSI_BLUE = "\u001B[44;30m";
    public static final String ANSI_RESET = "\u001B[0m";

    //打印 节点-块 索引表
    public void showInodeTable(InodeTable inodeTable) throws IOException {
        for (Map.Entry<Integer, Inode> entry : inodeTable.entrySet()) {//节点表中， 遍历所有节点
            if(entry.getValue().getDeletionTime()!=0){
                continue;//跳过已经删除了的节点, 因为这里直接从节点表中读取节点， 并不是先判断节点位视图中是否存在， 所以可能删除了的节点也会读取进来
            }
            System.out.printf("\u001B[44;30m"+"节点号: %d --> "+ANSI_RESET,entry.getKey().intValue());
            for (Integer dataBlockNumber : entry.getValue().getDirectBlocks()) {
                System.out.printf("\u001B[41;30m"+" 块号:%d,"+ANSI_RESET,dataBlockNumber.intValue());//  打印十二个直接指针
            }

            int indirectPointer = entry.getValue().getIndirectPointer();
            if(indirectPointer!=0) {//间接指针
                System.out.printf("\u001B[46;30m"+" 块号:%d,"+ANSI_RESET, indirectPointer);
                int referenceCount = (int) Math.ceil(entry.getValue().getSize() / (double) BLOCK_SIZE) - 12;//计算间接指针个数， 这样只能计算文件类型的， 目录类型没有size这个字段 ，但一般目录不会用到间接指针
                ArrayList<Integer> references = readIndirectPointer(indirectPointer, referenceCount);
                for (Integer dataBlockNumber : references) {
                    System.out.printf("\u001B[43;30m"+" 块号:%d,"+ANSI_RESET, dataBlockNumber.intValue());
                }
            }
//			System.out.printf(" ( 删除时间: %s ) ",Utils.epochTimeToDate(entry.getValue().getDeletionTime()));
            System.out.println();
        }
    }

    //打印位示图
    private void showBitMap(int oneLineSize, int totalShowSize, byte[] target){
        List<Integer> targetList = BitUtils.bitMapToList(target);
        int y=1;
        for(int x=0; x<totalShowSize; x++) {
//			打印表头
            if(0==x) {
                for(int t=0;t<=oneLineSize;t++) {
                    System.out.printf(ANSI_BLUE+"%3d"+ANSI_RESET,t);//打印列号
                }
                System.out.println();
            }
            if (x % oneLineSize == 0) {
                System.out.printf(ANSI_BLUE+"%3d"+ANSI_RESET, y); //打印行号
                y++;
            }
            int data = targetList.get(x);
            if(data==1){
                System.out.printf("\u001B[41;30m"+"%3d"+ANSI_RESET, data);//打印数据1
            }else{
                System.out.printf("\u001B[40;31m"+"%3d"+ANSI_RESET, data);//打印数据0
            }
            if(x!=0 && ((x+1)%oneLineSize)==0){
                System.out.println();
            }
        }
    }

    //显示位示图和索引表
    public void show() throws IOException {

        System.out.println();
        System.out.println("数据块位示图如下:");
        showBitMap(60,600,DATA_BITMAP);

        System.out.println();
        System.out.println("节点位示图如下:");
        showBitMap(60,600,INODE_BITMAP);

        System.out.println();
        System.out.println("索引表如下:");
        showInodeTable(inodeTable);

    }


    public FileSystem(Disk disk) {
        DISK = disk;
    }

    //载入, 从磁盘获取结构并将它们分配到内存中
    public void load() throws IOException {
        if (currentDir == null) {
            // 将 位示图 和 节点表 加载到内存中
            allocateBitmaps(); //加载数据块位示图和节点位示图
            allocateInodeTable();//加载节点表
            currentDir = getRoot(); //当前目录设为根目录
        }
    }

    //加载两个位示图
    private void allocateBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);//指针移动到数据块位示图开始
        DISK.read(DATA_BITMAP); // 读取数据块位示图
        DISK.seek(INODE_BITMAP_OFFSET);//指针移动到节点位示图开始
        DISK.read(INODE_BITMAP); //读取节点位示图
    }

    //加载节点表
    private void allocateInodeTable() throws IOException {
        byte inodeBytes[] = new byte[80]; //每个节点所以字段和为80字节
        inodeTable = new InodeTable();//节点表
        Inode inode; //节点

        ArrayList<Integer> usedInodes = BitUtils.findAllSetBits(INODE_BITMAP);//由节点位示图, 找出所有已分配的节点号
        for (Integer usedInode:usedInodes) {
            DISK.seek(getInodeOffset(usedInode.intValue()));//指针移动到该节点号
            DISK.read(inodeBytes);//读取该节点
            inode = Inode.fromByteArray(inodeBytes, usedInode.intValue());//将读取到的节点转换成对象
            if (inode != null)
                inodeTable.put(usedInode.intValue(), inode);//将(节点号,节点对象) 添加到节点表
        }
    }

    //格式化磁盘函数,用于第一次使用时
    public void format() throws IOException {
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];//磁盘大小的字节数组
        DISK.seek(0);//指针移动到磁盘开始
        DISK.write(ZEROS); //填充0

        //创建根目录
        int dirBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);//由数据块位示图获取一个空闲块号
        int dirInode = BitUtils.nextClearBitThenSet(INODE_BITMAP);//由节点位示图获取一个空闲节点号

        // 创建第一个目录(root)
        Inode inode = new Inode(dirInode, Inode.DIRECTORY);//新建一个节点对象
        inode.addBlocks(dirBlock); //将块号添加到那12个直接指针位置
        inodeTable = new InodeTable();//新建节点表对象
        inodeTable.put(dirInode, inode);//将(节点号,节点对象)添加到节点表

        // 创建 . and .. 目录项
        DirectoryBlock block = new DirectoryBlock(dirBlock);
        DirectoryEntry self, parent;//自身目录项, 父目录项
        self = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, ".");//new一个目录项(节点号, 节点类型, 目录名)
        parent = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, "..");//根目录的. 和 .. 都是自己
        block.addEntry(self);
        block.addEntry(parent);
        currentDir = new Directory();
        currentDir.add(block);//将根目录块添加到当前目录

        // 写目录节点 和 它的目录项到磁盘
        DISK.seek(getInodeOffset(dirInode));//指针移动到节点号
        DISK.write(inode.toByteArray());//将节点写入磁盘
        DISK.seek(getDataBlockOffset(dirBlock));//指针移动到该块号
        DISK.write(self.toByteArray());//将目录项写入到硬盘
        DISK.write(parent.toByteArray());

        writeBitmaps();//更新数据块位示图和节点位示图到硬盘
    }

    //写两个位示图到磁盘
    private void writeBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);//指针移动到数据块位示图开始
        DISK.write(DATA_BITMAP); // 写数据块位示图
        DISK.seek(INODE_BITMAP_OFFSET);//指针移动到节点位示图开始
        DISK.write(INODE_BITMAP); //写节点位示图
    }


    // 由块号读取目录块返回（只限目录类型）
    public DirectoryBlock readDirectoryBlock(int blockIndex) throws IOException {
        DirectoryBlock block = new DirectoryBlock(blockIndex);//新建目录块对象

        // 字节数组
        byte inodeBytes[] = new byte[4]; //目录项中节点号
        byte recLenBytes[] = new byte[2]; //目录项中记录长度
        byte filenameBytes[]; //目录项中 文件名


        int inode, idealLen; //节点号, 目录项理想(真实)长度
        short recLen;//记录长度
        byte nameLen, type; //文件名长度, 文件类型
        String name;//文件名

        // 这将决定何时停止读取一个块(当所有rec_len的和等于4096时)
        int recLenCount = 0;//已读取的长度

        DISK.seek(getDataBlockOffset(blockIndex));//指针移动到该块号
        while (recLenCount != BLOCK_SIZE) {//如果这块没读完

            DISK.read(inodeBytes);//节点号
            DISK.read(recLenBytes);//记录长度, 文件名已经优化过了
            nameLen = DISK.readByte();//文件名真实长度,即没有优化过的
            type = DISK.readByte();//文件类型

            inode = Ints.fromByteArray(inodeBytes);//节点号: 由读取的字节数组转 int
            recLen = Shorts.fromByteArray(recLenBytes);//记录长度（块中的最后一个目录项的记录长度并非目录项真实长度,其他都为名字优化过后的真实长度）

            // // 1-4: 12        4-8: 16    8-12: 20
            //文件名长度会填充成四个字节的整数倍
            idealLen = (4 * ((8 + nameLen + 3) / 4));
            filenameBytes = new byte[idealLen - 8];//4 8 12 16 ......
            DISK.read(filenameBytes);//读取文件名
            name = new String(filenameBytes);//文件名

            //检查条目是否已被删除(如果在其inode中设置了删除时间)
            Inode entryInode = inodeTable.get(inode);
            if (entryInode.getDeletionTime() == 0) {//等于0, 说明没删除, 删除时已将前一个目录项的记录长度覆盖掉被删除的， 但为了保险虽然磁盘读取得到, 但是已经删除了,不必返回去，TODO
                DirectoryEntry entry = new DirectoryEntry(inode, recLen, type, name);//节点号,文件长度,类型,文件名 重新构建一个目录项
                block.add(entry);
                recLenCount += recLen;//计算下一个目录项的指针
                DISK.seek(getDataBlockOffset(blockIndex) + recLenCount);//移动指针到下一个目录项
            }
        }
        return block;//返回的这个块并非原原本本磁盘读出来的， 而是把已经删除的目录项去掉后 和 文件名还原后的
    }

    //新建目录时，为新的目录申请节点，申请块，添加  .  和 .. 目录项
    public void writeDirectory(String name) throws IOException, IllegalArgumentException {
        if (currentDir.findEntry(name) != null) {
            throw new IllegalArgumentException("已存在相同的文件名!");
        }
        int dirInode = BitUtils.nextClearBitThenSet(INODE_BITMAP);//由节点位示图获取一个空闲节点号
        addDirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, name);//原来（当前）目录下也要添加一个目录项，这个目录项指向申请到的节点号的节点


        //  以下操作为新目录所需要的操作
        int dirBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);//由数据块位示图获取一个空闲块号
        Inode inode = new Inode(dirInode, Inode.DIRECTORY);//由节点号新建一个节点对象
        inode.addBlocks(dirBlock);//将块号添加到节点的直接指针位置
        inodeTable.put(dirInode, inode);//将(节点号,节点)添加到节点表


        int parentInode = currentDir.getInode();//父节点号等于当前目录节点号

        //为新建目录创建本目录和父目录别名
        DirectoryEntry self, parent;
        self = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, ".");
        parent = new DirectoryEntry(parentInode, DirectoryEntry.DIRECTORY, "..");

        DirectoryBlock block = new DirectoryBlock(dirBlock);//没用这行
        block.addEntry(self);//没用这行
        block.addEntry(parent);//没用这行

        //写节点, 和 它的目录项到磁盘
        DISK.seek(getInodeOffset(dirInode));//指针移动到该节点号
        DISK.write(inode.toByteArray());//写入节点数据
        DISK.seek(getDataBlockOffset(dirBlock));//指针移动到该块号
        DISK.write(self.toByteArray());//写入目录项
        DISK.write(parent.toByteArray());


        writeBitmaps();//更新位示图
    }


    //新添加目录后， 当前目录下也需要一个目录项
    private void addDirectoryEntry(int inodeNumber, byte type, String name) throws IOException {
        // 只有最后一个目录块是可写的，前面的块应该都是写满了目录项
        DirectoryBlock lastBlock = currentDir.getLastBlock();

        DirectoryEntry entry = new DirectoryEntry(inodeNumber, type, name);//新建一个目录项对象
        if (lastBlock.getRemainingLength() >= entry.getIdealLen()) {//块剩余的长度大于等于目录项总(理想)长度
            DirectoryEntry prevEntry = lastBlock.getLastEntry();//获取块中最后一个目录项
            int prevEntryOffset = lastBlock.getLength() - prevEntry.getIdealLen();//计算先前目录项的开始偏移量: 块已用长度 - 最后一个目录项真实长度
            lastBlock.addEntry(entry);//将目录项填加到块中, 当前目录要显示新添加的目录项

            //更新前一个目录项的记录长度是在addEntry方法中实现了,所以要更新到磁盘先前的目录项,再写新填的目录项
            DISK.seek(getDataBlockOffset(lastBlock.getBlock()) + prevEntryOffset);//计算先前一个目录块的磁盘偏移量
            DISK.write(prevEntry.toByteArray());
            DISK.write(entry.toByteArray());
        } else {
            // 新的目录项不能装到这个块中, 新申请一个块
            int newBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);//获取下一个空闲块号
            Inode inode = inodeTable.get(currentDir.getInode());//获取当前目录节点
            inode.addBlocks(newBlock);//将新块添加到节点

            DirectoryBlock block = new DirectoryBlock(newBlock);//new一个块
            block.addEntry(entry);//把目录项添加到块
            currentDir.add(block);//把块给到当前目录

            //更新当前目录节点到磁盘
            DISK.seek(getInodeOffset(inode.getInode()));//计算偏移量
            DISK.write(inode.toByteArray());//写节点进磁盘

            //将新目录项写入磁盘, 写到新申请到的块
            DISK.seek(getDataBlockOffset(newBlock));//计算块偏移量
            DISK.write(entry.toByteArray());//将目录项写进磁盘
        }
    }



    public void goToDirectory(String path) throws IOException {
        Directory initialDir = (path.startsWith("/")) ? getRoot() : currentDir;//获取初始目录, 目录是否时/开头,是就返回根目录, 否则当前目录
        DirectoryEntry entry = null;
        ArrayList<String> entries = Utils.splitPath(path);//分割路径
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i);
            entry = initialDir.findEntry(name);//在初始目录下查找目录项, 每循环一次初始目录进一层
            if (entry != null) {//找到了
                if (entry.getType() == DirectoryEntry.DIRECTORY) {//判断找到的类型是否是目录,而不是文件等其他的
                    Directory directory = new Directory();//新建一个目录对象
                    ArrayList<Integer> dirBlocks;//目录块列表
                    int inodeNumber = entry.getInode();//获取目录节点号
                    Inode inode = inodeTable.get(inodeNumber);//查找节点表, 得到节点
                    dirBlocks = inode.getDirectBlocks();//由节点获取全部的目录块号

                    ///遍历目录块号, 读取目录块
                    for (int block : dirBlocks) {
                        directory.add(readDirectoryBlock(block));
                    }

                    initialDir = directory;
                    currentDir = initialDir;
                }
            }
        }
    }

    //在给定路径下查找目录项
    public DirectoryEntry findEntry(String path) throws IOException {
        Directory initialDir = (path.startsWith("/")) ? getRoot() : currentDir;//获取初始目录
        DirectoryEntry entry = null;
        ArrayList<String> entries = Utils.splitPath(path);//分割路径
        for (int i = 0; i < entries.size(); i++) {//在初始目录下查找目录项, 每循环一次初始目录进一层
            String name = entries.get(i);
            entry = initialDir.findEntry(name);
            if (entry != null) {
                if (entry.getType() == DirectoryEntry.DIRECTORY) {
                    Directory directory = new Directory();
                    ArrayList<Integer> dirBlocks;
                    int inodeNumber = entry.getInode();
                    Inode inode = inodeTable.get(inodeNumber);
                    dirBlocks = inode.getDirectBlocks();

                    for (int block : dirBlocks) {
                        directory.add(readDirectoryBlock(block));
                    }

                    initialDir = directory;
                } else {
                    // 它是一个文件，所以没有目录条目。检查它是否是路径中的最后一个元素
                    return (i == entries.size() - 1) ? entry : null;
                }
            }
        }
        return entry;
    }


    //清除一个目录项从当前目录
    public boolean removeEntry(String name) throws IOException, IllegalArgumentException {
        DirectoryBlock block;//块
        DirectoryEntry entry;//目录项
        Inode inode;//节点
        if ((block = currentDir.getBlockContaining(name)) != null) {//找当前目录下存这个文件项的块
            for (int i = 0; i < block.size(); i++) {
                entry = block.get(i);//拿出这个文件项
                if (entry.getFilename().equals(name)) {

                    inode = inodeTable.get(entry.getInode());//这个项的节点

                    //如果这个目录项是一个目录,检测它是否是空目录
                    if (entry.getType() == DirectoryEntry.DIRECTORY) {
                        for (int index : inode.getDirectBlocks()) {
                            if (readDirectoryBlock(index).hasEntries()) {
                                throw new IllegalArgumentException("目录不为空不能删除!");
                            }
                        }
                    }

                    if (inode.getLinkCount() == 1) {
                        for (int index : inode.getDirectBlocks()) {//获取节点的直接指针块
                            BitUtils.clearBit(index, DATA_BITMAP);//清除直接指针块占用
                        }

                        // 检查是否由间接指针
                        int indirectPointer = inode.getIndirectPointer();//间接指针
                        ArrayList<Integer> references;
                        if (indirectPointer != 0) {
                            int referenceCount = (int) Math.ceil(inode.getSize() / (double) BLOCK_SIZE) - 12;//这种计算方式只针文件类型有效, 目录类型是没有size的
                            references = readIndirectPointer(indirectPointer, referenceCount);//读取间接指针中指向的块中存的间接指针
                            BitUtils.clearBit(indirectPointer, DATA_BITMAP);//清除间接指针
                            for (int index : references) {
                                BitUtils.clearBit(index, DATA_BITMAP);//清除间接指针指向的块
                            }
                        }

                        // 在inode位图中清除此inode的位，并设置其删除时间，然后将其写入磁盘
                        BitUtils.clearBit(inode.getInode(), INODE_BITMAP);//清除节点表占用
                        inode.setDeletionTime(toIntExact(System.currentTimeMillis() / 1000));//设置删除时间
                        inode.setLinkCount(0);//设置链接数量为0
                        DISK.seek(getInodeOffset(inode.getInode()));
                        DISK.write(inode.toByteArray());

                        writeBitmaps();
                    }
                    if (i == 0) {
                        DISK.seek(getDataBlockOffset(block.getBlock()));//如果这个目录项写在这个块的第一个, 将指针移动到该数据块开头
                    } else {
                        //更新前一个目录项的记录长度,使其覆盖掉删除的目录项, 达到假删除的目的
                        DirectoryEntry previous = block.get(i - 1);//获取前一个目录项
                        int recLen = previous.getRecLen() + entry.getRecLen();//前一个目录项的长度+被删除目录项的长度
                        int prevOffset = block.getOffset(i - 1);//从0到前一个目录项的偏移量
                        previous.setRecLen((short) recLen);//使前一个目录项长度变长
                        DISK.seek(getDataBlockOffset(block.getBlock()) + prevOffset);//移到前一个目录项指针开头
                        DISK.write(previous.toByteArray());//重新将前一个目录项写入
                    }
                    block.remove(i);
                    return true;
                }
            }
        }
        return false;
    }


    // 将文本保存到可用的数据块中，然后为文件创建dir_entry和inode
    public void writeFile(String fileName, String text) throws IOException, IllegalArgumentException {
        if (currentDir.findEntry(fileName) != null) {
            throw new IllegalArgumentException("该目录下已经存在该文件!");
        }

        //将文件字节分割为4KB的组，并将每个组写入磁盘(每个组一个块)
        byte content[][] = BitUtils.splitBytes(text.getBytes(), BLOCK_SIZE);//将内容分组, 每组大小等于1个块
        int blocksNeeded = content.length;// 一共需要多少个块

        byte direct[][] = (blocksNeeded > 12) ? Arrays.copyOfRange(content, 0, 12) : content;//存到直接指针指向的块的内容

        byte indirect[][] = (blocksNeeded > 12) ? Arrays.copyOfRange(content, 12, blocksNeeded) : null;//存到间接指针指向的块的内容

        // 首先添加直接指针
        int directBlocks[] = new int[direct.length];//用来存申请到的空闲块号
        for (int i = 0; i < direct.length; i++) {
            byte group[] = direct[i];//要写入的内容,大小为切割好的一个块大小
            int blockNumber = BitUtils.nextClearBitThenSet(DATA_BITMAP);//申请一个空闲块
            directBlocks[i] = blockNumber;
            DISK.seek(getDataBlockOffset(blockNumber));
            DISK.write(group);//将这组(块大小)文字写入磁盘
        }

        //写入间接指针指向的块, 如果有必要的话
        ArrayList<Integer> references;
        int indirectPointer = 0;
        if (indirect != null) {//判断是否有必要
            indirectPointer = BitUtils.nextClearBitThenSet(DATA_BITMAP);//申请一个空闲块,用来存间接指针
            references = new ArrayList<>();
            for (byte[] group : indirect) {//遍历要写入到间接指针指向的块中的每个分组
                int block = BitUtils.nextClearBitThenSet(DATA_BITMAP);//申请一个空闲块,用来存写入的文字
                references.add(block);
                DISK.seek(getDataBlockOffset(block));
                DISK.write(group);
            }

            // 将申请到的间接指针写入到磁盘
            DISK.seek(getDataBlockOffset(indirectPointer)); //一个块4k, 一个间接指针4字节, 最多写1024个间接指针, 多了会占用下一个块出bug
            for (int reference : references) {
                DISK.write(Ints.toByteArray(reference));
            }
        }

        //创建一个新节点给这个文件, 并写入磁盘
        int inodeNumber = BitUtils.nextClearBitThenSet(INODE_BITMAP);
        Inode inode = new Inode(inodeNumber, Inode.FILE, text.getBytes().length);
        inode.addBlocks(directBlocks);
        if (indirectPointer != 0) inode.setIndirectPointer(indirectPointer);
        inodeTable.put(inodeNumber, inode);
        DISK.seek(getInodeOffset(inodeNumber));
        DISK.write(inode.toByteArray());

        addDirectoryEntry(inodeNumber, DirectoryEntry.FILE, fileName);
        writeBitmaps();
    }

    // 给定一个文件名，搜索当前目录中的文件，并返回数据块中的数据
    public byte[] readFile(String fileName) throws IOException {
        int inode;
        try {
            inode = currentDir.findEntry(fileName).getInode();
        } catch (NullPointerException npe) {
            // 没有该文件
            return null;
        }

        Inode fileInode = inodeTable.get(inode);

        if (fileInode.getType() == Inode.SYM_LINK) {//节点类型是硬链接
            Directory rollback = currentDir;
            String path = FilenameUtils.getPath(fileInode.getSymLinkUrl());
            String name = FilenameUtils.getName(fileInode.getSymLinkUrl());
            goToDirectory(path);
            byte content[] = readFile(name);
            currentDir = rollback;
            return content;
        }

        fileInode.setLastAccessTime(toIntExact(System.currentTimeMillis() / 1000));//更新访问时间
        DISK.seek(getInodeOffset(fileInode.getInode()));
        DISK.write(fileInode.toByteArray());

        ArrayList<Integer> directBlocks = fileInode.getDirectBlocks();
        final int fileSize = fileInode.getSize();
        final int maxDirectBytes = BLOCK_SIZE * 12;
        byte directData[] = new byte[(fileSize > maxDirectBytes) ? maxDirectBytes : fileSize];

        int offset = 0;
        int len = (directData.length < BLOCK_SIZE) ? directData.length : BLOCK_SIZE;
        //读取直接指针指向块中的数据
        for (int block : directBlocks) {
            DISK.seek(getDataBlockOffset(block));
            DISK.read(directData, offset, len);

            // 下一个块的偏移量
            offset += BLOCK_SIZE;
            len = (directData.length - offset >= BLOCK_SIZE) ? BLOCK_SIZE : directData.length - offset;
        }

        // 读取间接指针指向块中的数据
        int indirectPointer;
        if ((indirectPointer = fileInode.getIndirectPointer()) != 0) {
            int remainingBytes = fileSize - maxDirectBytes;
            byte indirectData[] = new byte[remainingBytes];

            int referenceCount = (int) Math.ceil(fileSize / (double) BLOCK_SIZE) - 12;
            ArrayList<Integer> references = readIndirectPointer(indirectPointer, referenceCount);

            // 从数组的位置0开始写入，每个块最多读取4096个字节
            offset = 0;
            len = (remainingBytes > BLOCK_SIZE) ? BLOCK_SIZE : remainingBytes;
            for (int reference : references) {
                DISK.seek(getDataBlockOffset(reference));
                DISK.read(indirectData, offset, len);

                // 下一个块的偏移量
                offset += BLOCK_SIZE;
                len = (indirectData.length - offset >= BLOCK_SIZE) ? BLOCK_SIZE : indirectData.length - offset;
            }
            return Bytes.concat(directData, indirectData);
        }
        return directData;
    }

    public boolean append(String fileName, String text) throws IOException {
        byte content[] = text.getBytes();//字符转换成字节数组
        int appendLength = content.length;//数组长度
        int inodeNumber;//节点号码

        try {
            inodeNumber = currentDir.findEntry(fileName).getInode();//查找当前目录下该文件
        } catch (NullPointerException npe) {
            return false;
        }

        Inode inode = inodeTable.get(inodeNumber);//获取文件节点
        final ArrayList<Integer> directBlocks = inode.getDirectBlocks();//获取12个直接指针
        final int fileSize = inode.getSize();//获取文件字节数
        int freeBlocks = 12 - directBlocks.size();//获取12个直接指针中剩余的指针

        int remainder = fileSize % BLOCK_SIZE; // 模运算, 计算上次最后一块占用的字节数
        int lastBlockFreeBytes = (remainder == 0) ? 0 : BLOCK_SIZE - remainder;//上次最后一块剩余的字节数
        int freeDirectBytes = freeBlocks * BLOCK_SIZE + lastBlockFreeBytes;//计算12个直接指针总剩余字节数

        byte direct[];//存到直接指针的
        byte indirect[];//存到间接指针的
        if (appendLength < freeDirectBytes) {//要写入的小于剩余的直接指针字节数
            // 不需要间接指针
            direct = content;
            indirect = null;//不需要间接指针
        } else { //否则将写入的文章分组,一个组是写到直接指针的,另一个是写到间接指针的
            direct = (freeDirectBytes > 0) ? Arrays.copyOfRange(content, 0, freeDirectBytes) : null;
            indirect = (freeDirectBytes > 0) ? Arrays.copyOfRange(content, freeDirectBytes, appendLength) : content;
        }

        if (direct != null) {
            if (lastBlockFreeBytes > 0) {
                int lastBlock = directBlocks.get(directBlocks.size() - 1);//获取最后一个块
                if (direct.length < lastBlockFreeBytes) {//不需要间接指针且不需要新的直接指针
                    DISK.seek(getDataBlockOffset(lastBlock) + remainder);//移动指针到上次写的地址
                    DISK.write(direct);//写到上次写的最后一块
                    writeAppendModifiedDate(inode, appendLength);
                    return true;
                } else { //不需要间接指针但需要新的直接指针
                    byte blockFill[] = Arrays.copyOfRange(direct, 0, lastBlockFreeBytes);//最后一块还可以写的
                    direct = Arrays.copyOfRange(direct, lastBlockFreeBytes, direct.length);//要写到其它直接指针块
                    DISK.seek(getDataBlockOffset(lastBlock) + remainder);//写到最后一块
                    DISK.write(blockFill);//写还可以写入的
                    remainder = 0;
                }

            }

            if(freeBlocks!=0) {
                byte directBlockGroups[][] = BitUtils.splitBytes(direct, BLOCK_SIZE);//将剩下没写的分组
                int blocks[] = new int[directBlockGroups.length];//用来存新申请到块的块号
                for (int i = 0; i < directBlockGroups.length; i++) {
                    byte[] group = directBlockGroups[i];
                    int block = BitUtils.nextClearBitThenSet(DATA_BITMAP);//块号
                    blocks[i] = block;
                    DISK.seek(getDataBlockOffset(block));
                    DISK.write(group);
                }
                inode.addBlocks(blocks);
            }
        }

        if (indirect != null) {
            ArrayList<Integer> references = new ArrayList<>();//间接指针的值
            int indirectPointer = inode.getIndirectPointer(); //间接指针
            if (indirectPointer == 0) {
                // 如果它到达这里是因为直接块恰好有49152(12*4096)字节。余数应为0
                indirectPointer = BitUtils.nextClearBitThenSet(DATA_BITMAP);//原来没有间接指针,新申请一个
                inode.setIndirectPointer(indirectPointer);
            }

            int referenceCount = (int) Math.ceil(fileSize / (double) BLOCK_SIZE) - 12;//计算上次间接指针的块数
            references = readIndirectPointer(indirectPointer, referenceCount);//读取间接指针指向的所有块

            if (remainder > 0) {//上次最后一块占用的字节数

                int lastBlock = references.get(references.size() - 1);//最后一块
                if (indirect.length < lastBlockFreeBytes) {
                    DISK.seek(getDataBlockOffset(lastBlock) + remainder);
                    DISK.write(indirect);
                    writeAppendModifiedDate(inode, appendLength);
                    return true;
                } else {
                    byte blockFill[] = Arrays.copyOfRange(indirect, 0, lastBlockFreeBytes);
                    indirect = Arrays.copyOfRange(indirect, lastBlockFreeBytes, appendLength);
                    DISK.seek(getDataBlockOffset(lastBlock)+ remainder);
                    DISK.write(blockFill);
                }
            }

            byte indirectBlockGroups[][] = BitUtils.splitBytes(indirect, BLOCK_SIZE);
            for (byte[] group : indirectBlockGroups) {
                int block = BitUtils.nextClearBitThenSet(DATA_BITMAP);
                references.add(block);
                DISK.seek(getDataBlockOffset(block));
                DISK.write(group);
            }

            // 将(间接)块引用写入磁盘
            DISK.seek(getDataBlockOffset(indirectPointer));
            for (int reference : references) {
                DISK.write(Ints.toByteArray(reference));
            }
        }
        writeAppendModifiedDate(inode, appendLength);
        return true;
    }

    //追加文本后,更新节点信息
    private void writeAppendModifiedDate(Inode inode, int appendLength) throws IOException {
        inode.setSize(inode.getSize() + appendLength);
        inode.setModifiedTime(toIntExact(System.currentTimeMillis() / 1000));
        DISK.seek(getInodeOffset(inode.getInode()));
        DISK.write(inode.toByteArray());
        writeBitmaps();
    }

    //读取间接指针里存的块号,要给出里面存多少个块
    public ArrayList<Integer> readIndirectPointer(int pointer, int referenceCount) throws IOException {
        ArrayList<Integer> references = new ArrayList<>();
        byte blockBytes[] = new byte[4];
        DISK.seek(getDataBlockOffset(pointer));
        while (referenceCount > 0) {
            DISK.read(blockBytes);
            int block = Ints.fromByteArray(blockBytes);
            references.add(block);
            referenceCount--;
        }
        return references;
    }

    //链接:
    // 软链接和硬链接区别: 软链接新申请一个节点,节点中的SymLinkUrl指向源目录项, 没有直接指向数据块
    //                    硬链接申请一个目录项, 直接指向数据块, 将目录项给源节点, 即源节点有多个目录项, 目录项中的文件名不同, 但都指向同样的数据块, 会同步更新数据
    public void writeLink(String source, String dest, byte type) throws IOException, IllegalArgumentException {
        DirectoryEntry sourceEntry = findEntry(source);//找出原目录项
        Inode sourceInode = inodeTable.get(sourceEntry.getInode());//获取源目录节点

        if (type == DirectoryEntry.HARD_LINK) {//硬链接
            addDirectoryEntry(sourceInode.getInode(), DirectoryEntry.FILE, dest);//节点号,类型,文件名, 为源节点添加目录项
            sourceInode.setLinkCount(sourceInode.getLinkCount() + 1);//使源节点链接数加1
            DISK.seek(getInodeOffset(sourceInode.getInode()));//计算偏移量
            DISK.write(sourceInode.toByteArray());//更新源节点信息到硬盘
        } else if (type == DirectoryEntry.SYM_LINK) {//软链接
            int inodeNumber = BitUtils.nextClearBitThenSet(INODE_BITMAP);//申请一个节点号

            addDirectoryEntry(inodeNumber, DirectoryEntry.SYM_LINK, dest);//节点号,类型,文件名, 为新节点添加目录项

            Inode inode = new Inode(inodeNumber, Inode.SYM_LINK);
            inode.setSymLinkUrl(source);//
            inodeTable.put(inodeNumber, inode);//将节点写入节点表
            DISK.seek(getInodeOffset(inodeNumber));
            DISK.write(inode.toByteArray());
            writeBitmaps();
        }
    }



    public Directory getCurrentDirectory() {
        return currentDir;
    }

    public void setCurrentDirectory(Directory directory) {
        this.currentDir = directory;
    }

    public InodeTable getInodeTable() {
        return inodeTable;
    }

    public Directory getRoot() throws IOException {
        Directory root = new Directory();
        Inode rootInode = inodeTable.get(1); //1 是节点号, 不是序号
        for (int block : rootInode.getDirectBlocks()) {
            root.add(readDirectoryBlock(block));
        }
        return root;
    }



    // 计算给定数据块号的数据偏移量
    private int getDataBlockOffset(int blockNumber) {
        return DATA_OFFSET + (blockNumber - 1) * BLOCK_SIZE;
    }

    // 计算给定inode索引的inode偏移量
    private int getInodeOffset(int inode) {
        return INODE_TABLE_OFFSET + (inode - 1) * 80;
    }
}