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
* 获取 pte_fn_t 函数类型的 KCFI 哈希值。
* 动态生成 pte_fn_t 回调时，需要把这个 32-bit的 KCFI 哈希值放在函数代码前面：
*     [ KCFI 哈希值 ][ function body ... ]
* 如果没有这个哈希值，内核调用时可能会死机。
*
* 参数: out_hash    输出参数，返回 pte_fn_t 函数原型对应的 KCFI 哈希值。
* 返回: OK 表示成功；其它值为错误码
***************************************************************************/
KModErr get_pte_fn_t_kcfi_hash(uint32_t& out_hash);

}
