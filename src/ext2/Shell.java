package ext2;

import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Shell {

    private FileSystem fileSystem;
    private String currentPath = "/";//默认当前路径为根目录
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_RESET = "\u001B[0m";

    public Shell(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in);//获取用户输入
        String input, command;
        help();
        mainloop:
        for (; ; ) {
            System.out.printf("%n%s$ ", getCurrentPath());//输出当前路径
            input = scanner.nextLine();//获取一行输入
            command = input.split(" ")[0];//空格分割命令和参数，只保留命令
            switch (command) {
                case "ls": {
                    String opts[] = input.split(" ");//空格分割命令和参数
                    if (opts.length == 2) {
                        // ls -l
                        if (opts[1].equals("-l"))
                            lsExtended(fileSystem.getCurrentDirectory());
                        else
                            System.out.printf("不支持的命令参数 '%s'%n", opts[1]);
                    } else if (opts.length == 1) {
                        // ls
                        ls(fileSystem.getCurrentDirectory());
                    } else {
                        System.out.println("ls命令使用错误. 只支持 'ls' 或 'ls -l'");
                    }
                    break;
                }
                case "cd": {
                    String opts[] = input.split(" ", 2);//空格分割命令和参数，只保留前 命令 和 一个参数
                    String path = (opts.length == 2) ? opts[1] : ".";//有参数则cd到目标目录，没有参数则cd到当前目录
                    try {
                        cd(path);
                    } catch (IOException ioe) {
                        System.out.println("未知IO异常");
                    }
                    break;
                }
                case "cat": {
                    if (input.contains(" > ")) {//判断是否有覆盖写入符号
                        String opts[] = input.split(" > ");//分割命令和参数
                        String fileName = opts[1].trim();//清除首尾空格，得文件名
                        if (Utils.containsIllegals(fileName)) {//检测文件名合法性
                            System.out.println("文件名包含非法字符");
                            break;
                        }
                        if (fileName.length() > 255) {
                            System.out.println("文件名过长 (最大255个字符)");
                            break;
                        }
                        String content = "";
                        String line;
                        while (!(line = scanner.nextLine()).equalsIgnoreCase("!eof")) {//按行读取屏幕输入，直到 ！EOF结束
                            content += line + "\n";
                        }
                        try {
                            fileSystem.writeFile(fileName, content);//写入到文件
                        } catch (IllegalArgumentException iae) {
                            System.out.println(iae.getMessage());
                        }
                    } else if (input.contains(" >> ")) {//判断是否有追加写入符号
                        String opts[] = input.split(">>");//分割命令和参数
                        String fileName = opts[1].trim();//清除首尾空格，得文件名
                        String content = "";
                        String line;
                        while (!(line = scanner.nextLine()).equalsIgnoreCase("!eof")) {//按行读取屏幕输入，直到 ！EOF结束
                            content += line + "\n";
                        }
                        if (!fileSystem.append(fileName, content)) {
                            System.out.println("文件不存在");
                            break;
                        }
                    } else {//否则就是读取文件
                        String opts[] = input.split(" ", 2);//分割命令和参数
                        if (opts.length == 2) {
                            // cat file.txt
                            String fileName = opts[1];//清除首尾空格，得文件名
                            cat(fileName);
                        } else {
                            System.out.println("cat命令参数错误. 请使用 'cat <文件名>' 或 'cat > <文件名>' 过 'cat >> <文件名>'");
                        }
                    }
                    break;
                }
                case "mkdir": {
                    String opts[] = input.split(" ", 2);//空格分割命令和参数

                    String dirs[] = opts[1].split("/");//多级目录分割

                    for(String dir : dirs){
                        String dirName = dir;//得到目录名称
                        if (Utils.containsIllegals(dirName)) {//目录合法性检测
                            System.out.println("目录名包含非法字符");
                            break;
                        }
                        try {
                            fileSystem.writeDirectory(dirName);
                        } catch (IllegalArgumentException iae) {
                            System.out.println(iae.getMessage());
                        }
                        cd(dirName);

                    }

                    for(String dir : dirs){//回到原来位置
                        cd("..");
                    }
                    break;

                }

                case "rmdir": {
                    // . and .. 不能删除
                    String opts[] = input.split(" ", 2);//空格分割命令和参数，只保留命令和第一个参数
                    if (opts.length == 2) {//一次只能删除一个目录
                        String name = opts[1];//得到目录名称
                        if (name.equals(".") || name.equals("..")) {
                            System.out.println("无法删除这个目录");
                        } else {
                            try {
                                if (!fileSystem.removeEntry(name)) {
                                    System.out.printf("没有这个目录 '%s'%n", name);
                                }
                            } catch (IllegalArgumentException iae) {
                                System.out.println(iae.getMessage());
                            }
                        }
                    } else {
                        System.out.println("rmdir命令使用错误. 请使用 'rmdir <目录名>'");
                    }
                    break;
                }

                case "rm": {
                    String opts[] = input.split(" ", 2);//空格分割命令和参数，只保留命令和第一个参数
                    if (opts.length == 2) {
                        String name = opts[1];//得到文件名称，目录也能删，
                        if (!fileSystem.removeEntry(name)) {
                            System.out.printf("没有这个文件 '%s'%n", name);
                        }
                    } else {
                        System.out.println("'rm'命令使用错误. 请使用 'rm <文件名>'");
                    }
                    break;
                }

                case "!ln": {
                    String params[] = input.split(" ", 3);//空格分割命令和参数，只保留命令和两个参数
                    if (params[1].equals("-s")) {//软链接
                        // ln -s (软链接)
                        String paths[] = input.split(" -s ")[1].split(" ", 2);//分割得到源地址 和 目的地址
                        if (paths.length == 2) {
                            String source = paths[0];
                            String dest = paths[1];
                            fileSystem.writeLink(source, dest, DirectoryEntry.SYM_LINK);
                        } else {
                            System.out.println("'ln'命令使用错误. 请使用 'ln [-s] <源> <目的>' 或 'ln <源> <目的>'");
                        }
                    } else {
                        // ln (硬链接)
                        String paths[] = input.split(" ", 2)[1].split(" ", 2);//分割得到源地址 和 目的地址
                        String source = paths[0];
                        String des = paths[1];
                        fileSystem.writeLink(source, des, DirectoryEntry.HARD_LINK);
                    }
                    break;
                }
                case "exit":
                    break mainloop;
                case "show": {
                    fileSystem.show();
                    break;
                }
                case "help": {
                    help();
                    break;
                }
                default:
                    System.out.printf("未知命令 '%s'%n", input.trim());
                    break;
            }
        }
    }

    public void ls(Directory directory) {//需要一个目录类型的参数
        for (int block = 0; block < directory.size(); block++) {//遍历目录中的各个块
            DirectoryBlock dirBlock = directory.get(block);
            for (int entry = 0; entry < dirBlock.size(); entry++) {//遍历块中的各个目录项
                DirectoryEntry dirEntry = dirBlock.get(entry);
                if (dirEntry.getFilename().equals(".") || dirEntry.getFilename().equals("..")) continue;//不显示 . 和 .. 目录

                System.out.printf((block == directory.size() - 1) && (entry == dirBlock.size() - 1)//是否是最后一个目录项, 非最后一个后跟空格
                        ? (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? ANSI_BLUE + "%s%n" + ANSI_RESET : "%s%n" //是否是目录， 目录颜色输出蓝色
                        : (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? ANSI_BLUE + "%s  " + ANSI_RESET : "%s  ", dirEntry.getFilename());
            }
        }
    }

    public void lsExtended(Directory directory) {
        InodeTable inodeTable = fileSystem.getInodeTable();
        Inode inode;
        String creationDate, accessDate, modifiedDate, fileName, type, size;
        if (directory.get(0).hasEntries())// 如果目录中有目录项（除了 . 和 ..），输出表头
            System.out.format("%-10s%-25s%-25s%-25s%-25s%-25s%-25s%n", "Inode","Created", "Last access", "Modified", "Type", "Size(bytes)", "Name");

        for (DirectoryBlock block : directory) {//遍历目录块
            for (DirectoryEntry dirEntry : block) {// 变量块中的目录项
                if (dirEntry.getFilename().equals(".") || dirEntry.getFilename().equals("..")) continue;//不显示 . 和 .. 目录

                inode = inodeTable.get(dirEntry.getInode());//根据节点号码从节点表中取出节点实体， 不用再取读磁盘
                creationDate = Utils.epochTimeToDate(inode.getCreationTime());//创建时间
                accessDate = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Utils.epochTimeToDate(inode.getLastAccessTime());//为文件类型时显示访问时间
                modifiedDate = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Utils.epochTimeToDate(inode.getModifiedTime());//为文件类型时显示修改时间
                size = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Integer.toString(inode.getSize());//为文件类型时显示五文件大小
                type = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "<DIR>" : "";// 为目录类型时，输出标识
                fileName = dirEntry.getFilename();

                System.out.format(
                        (dirEntry.getType() == DirectoryEntry.DIRECTORY)// 是否为目录类型，目录显示蓝色
                                ? "%-10d%-25s%-25s%-25s%-25s%-25s" + ANSI_BLUE + "%-25s" + ANSI_RESET + "%n"
                                : "%-10d%-25s%-25s%-25s%-25s%-25s%-25s%n",
                        inode.getInode(),creationDate, accessDate, modifiedDate, type, size, fileName);
            }
        }
    }

    public void cat(String fileName) {
        try {
            byte contentBytes[] = fileSystem.readFile(fileName);
            if (contentBytes == null) {
                System.out.println("没有这个文件");
                return;
            }
            String content = new String(contentBytes);
            System.out.println(content);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void cd(String path) throws IOException {

        String rollbackPath = getCurrentPath(); // 用于恢复路径，以防此方法在构建路径时引发异常
        Directory initialDir = (path.startsWith("/")) ? fileSystem.getRoot() : fileSystem.getCurrentDirectory();//获取初始目录， / 开头表示从根开始， 否则表示从当前路径下开始
        currentPath = (path.startsWith("/")) ? "/" : currentPath;//获取当前路径， / 开头表示从根开始， 否则表示从当前路径下开始

        ArrayList<String> directories = Utils.splitPath(path);// 根据 / 分割路径存到数组
        for (String name : directories) {//遍历所有路径， 每遍历一次，进入一层路径
            DirectoryEntry entry = initialDir.findEntry(name);// 从初始路径下找目录项
            if (entry != null) {// 能找到
                if (entry.getType() == DirectoryEntry.DIRECTORY) {// 且目录项类型是目录
                    Directory directory = new Directory();
                    ArrayList<Integer> dirBlocks;
                    int inodeNumber = entry.getInode();// 获取目录的节点号
                    Inode inode = fileSystem.getInodeTable().get(inodeNumber);// 根据节点号从节点表拿出节点实体
                    dirBlocks = inode.getDirectBlocks();// 获取这个节点的所有块

                    // 遍历每个块并把他们添加到目录
                    for (int block : dirBlocks)
                        directory.add(fileSystem.readDirectoryBlock(block));

                    initialDir = directory;//初始目录等于进来的这个目录
                    currentPath = FilenameUtils.concat(getCurrentPath(), name.concat("/"));// 当前路径 = 当前路径/name/
                } else {
                    // 它是一个文件，所以没有目录条目
                    currentPath = rollbackPath;
                    System.out.println("没有这个目录");
                    return;
                }
            } else {
                currentPath = rollbackPath;
                System.out.println("没有这个目录");
                return;
            }
        }
        fileSystem.setCurrentDirectory(initialDir);//遍历完所有路径后，初始目录就是当前进来的目录了
    }

    public String getCurrentPath() {
        return currentPath == null ? "/" : FilenameUtils.separatorsToUnix(currentPath);
    }

    public void help(){
        System.out.println( "\u001B[45;30m" +
                            "ls     ------      显示命令, 支持使用 -l 参数显示长信息\n" +
                            "cd     ------      切换目录命令, 支持使用 . 和 .. 代表 当前目录 和 上级目录\n" +
                            "cat    ------      文件查看命令, 支持使用 > 和 >> 重定向符写文件, 结束写入以 ！EOF 结尾行\n" +
                            "mkdir  ------      新建目录命令\n" +
                            "rmdir  ------      删除目录命令, 暂时不能递归删除\n" +
                            "rm     ------      删除文件命令\n" +
                            "show   ------      显示位示图和索引表\n" +
                            "help   ------      显示帮助信息\n" +
                "\u001B[0m"
        );
    }
}

//"ln     ------      创建链接命令, 支持使用 -s 参数代表创建软链接\n" +
// 只实现了在当前目录下创建链接 且 只能链接到当前目录
// 硬链接实现了创建,读取和写入
// 软链接实现了只创建和读取, 没有实现写入
