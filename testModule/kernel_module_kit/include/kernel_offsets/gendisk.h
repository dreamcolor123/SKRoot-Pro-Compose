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
* 获取 gendisk 结构体中 struct block_device *part0; 字段的偏移量（限 Linux >= 5.11.0）
* 参数: offset 输出参数，返回 part0 字段相对于 gendisk 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_gendisk_part0_ptr_offset(uint32_t & offset);

/***************************************************************************
* 获取 gendisk 结构体中 struct hd_struct part0; 字段的偏移量（限 Linux < 5.11.0）
* 参数: offset 输出参数，返回 part0 字段相对于 gendisk 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_gendisk_part0_offset(uint32_t & offset);
}
