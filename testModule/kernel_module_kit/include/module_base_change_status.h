#pragma once

#include "skroot_env/skroot_module.h"

namespace kernel_module {
/***************************************************************************
 * 获取当前模块的运行状态。
 ***************************************************************************/
skroot_env::ModuleRunState get_current_module_run_state();

/***************************************************************************
 * 修改当前模块的运行状态。
 * 参数:
 *   new_state  新的运行状态。
 ***************************************************************************/
void set_current_module_run_state(skroot_env::ModuleRunState new_state);

/***************************************************************************
 * 修改当前模块的描述文本。
 * * 
 * 注意：如果传入空字符串 ("")，则恢复为模块的默认描述文本。
 *
 * 参数:
 *   new_description  新的模块描述文本。
 ***************************************************************************/
void set_current_module_description(const std::string& new_description);

} // namespace kernel_module