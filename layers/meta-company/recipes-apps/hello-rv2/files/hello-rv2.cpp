#include <iostream>
#include <unistd.h>

// App version, kept as a compile-time constant.
constexpr const char* kAppVersion = "0.1.0";

int main() {
    std::cout << "Hello from the Orange Pi RV2 -- RV2 Space Linux!\n";
    std::cout << "hello-rv2 version " << kAppVersion << '\n';

    char hostname[256];
    if (gethostname(hostname, sizeof(hostname)) == 0) {
        std::cout << "Running on host: " << hostname << '\n';
    } else {
        std::cout << "Running on host: <unknown>\n";
    }

    return 0;
}
