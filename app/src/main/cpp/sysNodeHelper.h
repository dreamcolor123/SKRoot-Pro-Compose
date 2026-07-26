#pragma once
#include <fcntl.h>
#include <unistd.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sys/system_properties.h>

static int get_selinux_enforce_status() {
    int fd = open("/sys/fs/selinux/enforce", O_RDONLY);
    if (fd < 0) return -1;
    char ch = 0;
    ssize_t n = read(fd, &ch, 1);
    close(fd);
    if (n != 1) return -1;
    if (ch == '1') return 1;
    if (ch == '0') return 0;
    return -1;
}

static int get_seccomp_status() {
    static const char kField[] = "Seccomp:";
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd < 0) return -1;
    char buf[8192];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';
    const char* p = strstr(buf, kField);
    if (!p) return -1;
    p += strlen(kField);
    while (*p == ' ' || *p == '\t') ++p;
    if (*p < '0' || *p > '9') return -1;
    return atoi(p);
}

static std::string get_system_property(const char* key) {
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, value);
    if (len <= 0) return {};
    return std::string(value, static_cast<size_t>(len));
}

static bool is_enable_adb() {
	if(get_system_property("service.adb.root") == "1") return true;
	std::string adb_status = get_system_property("init.svc.adbd");
    if(adb_status != "stopped" && !adb_status.empty()) return true;
	return false;
}
