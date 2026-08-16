# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("permissionmanager")

# Magica
set(MAGICA_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/magica/jni")
add_library(lsplt STATIC
		"${MAGICA_JNI_DIR}/lsplt/elf_util.cc"
		"${MAGICA_JNI_DIR}/lsplt/lsplt.cc"
		)
target_include_directories(lsplt
		PUBLIC
		"${MAGICA_JNI_DIR}/lsplt/include"
		"${MAGICA_JNI_DIR}/lsplt"
		)
add_library(magica SHARED
		"${MAGICA_JNI_DIR}/magica.cpp"
		)
find_library(log-lib log)
target_include_directories(magica
		PRIVATE
		"${MAGICA_JNI_DIR}"
		"${MAGICA_JNI_DIR}/lsplt/include"
		)
target_link_libraries(magica
		lsplt
		${log-lib}
		)

# The upstream 4.5.6 source and matching kernel_module_kit archive are available
# for reference. The Compose build intentionally packages the four exact
# libraries from the official upstream APK via src/main/jniLibs, with pinned
# SHA-256 verification, so its Native behavior stays byte-for-byte identical.
