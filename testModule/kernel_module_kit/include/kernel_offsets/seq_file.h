#pragma once
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>

namespace kernel_module {
/***************************************************************************
* 获取 seq_file 结构体中 buf 字段的偏移量
* 参数: offset        输出参数，返回 buf 字段相对于 seq_file 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_seq_file_buf_offset(uint32_t & offset);

/***************************************************************************
* 获取 seq_file 结构体中 size 字段的偏移量
* 参数: offset        输出参数，返回 size 字段相对于 seq_file 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_seq_file_size_offset(uint32_t & offset);

/***************************************************************************
* 获取 seq_file 结构体中 count 字段的偏移量
* 参数: offset        输出参数，返回 host 字段相对于 seq_file 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_seq_file_count_offset(uint32_t & offset);
}
