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
* 获取 block_device 结构体中 struct hd_struct* bd_part; 字段的偏移量（限 5.11.0 > Linux）
* 参数: offset 输出参数，返回 bd_part 字段相对于 block_device 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_block_device_bd_part_offset(uint32_t & offset);

/***************************************************************************
* 获取 block_device 结构体中 struct gendisk* bd_disk; 字段的偏移量
* 参数: offset 输出参数，返回 bd_disk 字段相对于 block_device 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_block_device_bd_disk_offset(uint32_t & offset);
}
