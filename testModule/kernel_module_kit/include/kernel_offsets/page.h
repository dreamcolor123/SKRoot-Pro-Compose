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
* 获取 struct page 结构的大小
* 参数: size      输出参数，返回 struct page 结构体的大小（字节）
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_struct_page_size(uint32_t & size);
}
