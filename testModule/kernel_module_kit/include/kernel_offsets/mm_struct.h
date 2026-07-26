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
* 获取 mm_struct 结构体中 mm_mt 字段的偏移量 （限 Linux >= 6.1.0）
* 参数: offset      输出参数，返回 mm_mt 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_mm_mt_offset(uint32_t & offset);

/***************************************************************************
* 获取 mm_struct 结构体中 mmap 字段的偏移量 （限 Linux < 6.1.0）
* 参数: offset      输出参数，返回 mmap 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_mmap_offset(uint32_t & offset);

/***************************************************************************
* 获取 mm_struct 结构体中 pgd 字段的偏移量
* 参数: offset      输出参数，返回 pgd 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_pgd_offset(uint32_t & offset);

/***************************************************************************
* 获取 mm_struct 结构体中 map_count 字段的偏移量
* 参数: offset      输出参数，返回 map_count 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_map_count_offset(uint32_t & offset);

/***************************************************************************
* 获取 mm_struct 结构体中 total_vm 字段的偏移量
* 参数: offset      输出参数，返回 map_count 字段相对于 total_vm 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_total_vm_offset(uint32_t & offset);

/***************************************************************************
* 获取 mm_struct 结构体中 arg_start\arg_end 字段的偏移量
* 参数: arg_start_offset        输出参数，返回 arg_start 字段相对于 mm_struct 起始的偏移量（字节）
*       arg_end_offset      输出参数，返回 arg_end 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_arg_offset(uint32_t & arg_start_offset, uint32_t & arg_end_offset);

/***************************************************************************
* 获取 mm_struct 结构体中 env_start\env_end 字段的偏移量
* 参数: env_start_offset        输出参数，返回 env_start 字段相对于 mm_struct 起始的偏移量（字节）
*       env_end_offset      输出参数，返回 env_end 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_env_offset(uint32_t & env_start_offset, uint32_t & env_end_offset);

/***************************************************************************
* 获取 mm_struct 结构体中 rss_stat 字段的偏移量
* 参数: offset      输出参数，返回 rss_stat 字段相对于 mm_struct 起始的偏移量（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_mm_struct_rss_stat_offset(uint32_t & offset);

}
