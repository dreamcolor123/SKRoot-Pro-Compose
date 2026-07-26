#pragma once
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>

namespace kernel_module {
/***************************************************************************
* 获取 renamedata 结构体中 inode* old_dir; 字段的偏移量（限 Linux >= 5.12.0）
* 参数: old_dir_offset        输出参数，返回 old_dir 字段相对于 renamedata 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_renamedata_old_dir_offset(uint32_t & old_dir_offset);

/***************************************************************************
* 获取 renamedata 结构体中 inode* new_dir; 字段的偏移量（限 Linux >= 5.12.0）
* 参数: new_dir_offset        输出参数，返回 new_dir 字段相对于 renamedata 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_renamedata_new_dir_offset(uint32_t & new_dir_offset);
}
