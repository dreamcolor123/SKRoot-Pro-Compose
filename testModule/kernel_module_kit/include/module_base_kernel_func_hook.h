#pragma once
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <vector>
#include <errno.h>
#include "third_party/____DO_NOT_EDIT____/private_mod_api_runtime_helper.h"

namespace kernel_module {
typedef struct HookContext* HookHandle;

/***************************************************************************
 * Hook函数开始
 * 参数： a 		ASMJIT 汇编器上下文
 ***************************************************************************/
void arm64_before_hook_start(asmjit::a64::Assembler* a);

/***************************************************************************
 * Hook函数结束：可选择是否跳回原函数
 * 参数： a 					ASMJIT 汇编器上下文
 *  - continue_original = true  ：恢复现场并跳回原函数（从被覆盖指令后继续执行）
 *  - continue_original = false ：直接生成 RET，不再执行原函数，返回到调用者
 ***************************************************************************/
void arm64_before_hook_end(asmjit::a64::Assembler* a, bool continue_original);

/***************************************************************************
 * 安装内核Hook（可在任意点位安装，执行前触发）
 * 参数:
 *   kaddr				 目标内核地址（指令地址/Hook 点位）
 *   handler_shellcode	 Hook 处理函数的机器码（shellcode）。
 *                       （注意：Hook处理函数开头必须调用 arm64_before_hook_start，结尾必须调用 arm64_before_hook_end，否则不合法。）
 *   out_hook_handle     输出参数；成功时返回 Hook 句柄，用于后续卸载。如果传 nullptr 表示调用者不在乎卸载。
 * 返回: OK 表示成功，其他值为错误码。
 * 工作原理：仅将 kaddr 位置的 1 条指令（4 字节）替换为 1 条 B 跳转指令，0性能损耗。
 * 
 * Hook处理函数示例：
 *   arm64_before_hook_start(a);
 *   // TODO: 在此开始编写代码
 *   arm64_before_hook_end(a, true);
 ***************************************************************************/
KModErr install_kernel_function_before_hook(uint64_t kaddr, const std::vector<uint8_t>& handler_shellcode, HookHandle* out_hook_handle = nullptr);


/***************************************************************************
 * Hook函数开始
 * 参数： a 		ASMJIT 汇编器上下文
 ***************************************************************************/
void arm64_after_hook_start(asmjit::a64::Assembler* a);

/***************************************************************************
 * Hook函数结束
 * 参数： a			ASMJIT 汇编器上下文
 ***************************************************************************/
void arm64_after_hook_end(asmjit::a64::Assembler* a);

/***************************************************************************
 * 安装内核Hook（在内核函数执行后触发）
 * 参数:
 *   target_func_kaddr   目标内核函数的起始地址（函数入口）
 *   handler_shellcode   Hook 处理函数的机器码（shellcode）。
 *                       （注意：Hook处理函数开头必须调用 arm64_after_hook_start，结尾必须调用 arm64_after_hook_end，否则不合法。）
 *   out_hook_handle     输出参数；成功时返回 Hook 句柄，用于后续卸载。如果传 nullptr 表示调用者不在乎卸载。
 * 返回: OK 表示成功，其他值为错误码
 * 工作原理：仅将 kaddr 位置的 1 条指令（4 字节）替换为 1 条 B 跳转指令，0性能损耗。
 *  
 * Hook处理函数示例：
 *   arm64_after_hook_start(a);
 *   // TODO: 在此开始编写代码
 *   arm64_after_hook_end(a);
 ***************************************************************************/
KModErr install_kernel_function_after_hook(uint64_t target_func_kaddr, const std::vector<uint8_t>& handler_shellcode, HookHandle* out_hook_handle = nullptr);

/***************************************************************************
 * 卸载内核 Hook
 * 参数:
 *   handle     安装Hook时的句柄
 * 返回: OK 表示成功，其他值为错误码
 ***************************************************************************/
KModErr uninstall_kernel_hook(HookHandle handle);

/***************************************************************************
 * （可选）手动执行原始函数
 * 作用：可在 Hook 处理函数内部，手动执行原始函数（不会再次命中本 Hook 导致死循环）
 *
 * 使用条件（重要）：
 *   - 仅适用于 Hook 点位为“目标函数入口地址”时使用。
 *   - 必须在 Hook 处理函数内部调用。
 *
 * 注意事项：
 *   - 本函数不会替你保存任何寄存器；需要保留的寄存器请在调用前自行保存。
 *   - 若你已手动执行过原始函数，则 arm64_before_hook_end 的 continue_original 参数需传 false，否则原始函数会再多执行一次，导致意外发生。
 *
 * 用法示例1：
 *   arm64_before_hook_start(a);
 *   arm64_emit_call_original(a);
 *   arm64_before_hook_end(a, false);
 * 
 * 用法示例2：
 *   arm64_after_hook_start(a);
 *   arm64_emit_call_original(a);
 *   arm64_after_hook_end(a);
 * 
 * 用法示例3：
 *   arm64_before_hook_start(a);
 *   {
 *     RegProtectGuard g1(a, x0, x1, x2); // 提前保存自己需要的参数，因为调用原始函数后会破坏寄存器值。
 *     arm64_emit_call_original(a); //执行后，x0是函数返回值
 *   }
 *   arm64_before_hook_end(a, false);
 *
 ***************************************************************************/
void arm64_emit_call_original(asmjit::a64::Assembler* a);

}
