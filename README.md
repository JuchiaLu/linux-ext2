### 简介

一个简单的 Linux EXT2 文件系统的实现, 最大支持256MB

数据块位示图设计为占用两个块, 每个块设定大小为4KB: 2 * 4KB = 2 * 4 * 1024 * 8 bit = 65536 bit,

即位示图一共可以表示65536个块, 每个块大小4KB, 总共可以表示: 4KB * 65536 = 262144 KB = 256MB

fork from [wcmolina/EXT2](https://github.com/wcmolina/EXT2)

### 使用
```
ls       ------     显示命令, 支持使用 -l 参数显示长信息
cd       ------     切换目录命令, 支持使用 . 和 .. 代表 当前目录 和 上级目录
cat      ------     文件查看命令, 支持使用 > 和 >> 重定向符写文件, 结束写入以 ！EOF 结尾行
mkdir    ------     新建目录命令
rmdir    ------     删除目录命令
rm       ------     删除文件命令
show     ------     显示位示图和索引表
help     ------     显示帮助信息
```
### 原理

  ![](https://raw.githubusercontent.com/JuchiaLu/linux-ext2/master/pictures/EXT2_1.png)

  ![](https://raw.githubusercontent.com/JuchiaLu/linux-ext2/master/pictures/EXT2_2.png)

  ![](https://raw.githubusercontent.com/JuchiaLu/linux-ext2/master/pictures/EXT2_3.png)
