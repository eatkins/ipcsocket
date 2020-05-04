#!/bin/sh
echo "compiling..."
gcc -I/Library/Java/JavaVirtualMachines/graalvm-ce-19.2.1/Contents/Home/include/ -I/Library/Java/JavaVirtualMachines/graalvm-ce-19.2.1/Contents/Home/include/darwin org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary.c -shared -o ../src/main/resources/darwin_x86_64/libsbtipcsocket.dylib
echo $?
echo "success"
