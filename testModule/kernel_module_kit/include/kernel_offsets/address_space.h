#pragma once
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>

namespace kernel_module {
/***************************************************************************
* 获取 address_space 结构体中 inode* host; 字段的偏移量
* 参数: offset        输出参数，返回 host 字段相对于 address_space 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_address_space_host_offset(uint32_t & offset);

/***************************************************************************
* 获取 address_space 结构体中 i_mmap 字段的偏移量
* 参数: offset        输出参数，返回 i_mmap 字段相对于 address_space 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_address_space_i_mmap_offset(uint32_t & offset);
}
