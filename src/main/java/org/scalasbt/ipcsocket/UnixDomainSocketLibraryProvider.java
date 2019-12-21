package org.scalasbt.ipcsocket;

import java.io.IOException;

public interface UnixDomainSocketLibraryProvider {
  int socket(int domain, int type, int protocol) throws IOException;

  int bind(int fd, String path) throws IOException;

  int listen(int fd, int backlog) throws IOException;

  int accept(int fd, String path) throws IOException;

  int connect(int fd, String path) throws IOException;

  int read(int fd, byte[] buffer, int len) throws IOException;

  int write(int fd, byte[] buffer, int len) throws IOException;

  int close(int fd) throws IOException;

  int shutdown(int fd, int how) throws IOException;

  static UnixDomainSocketLibraryProvider jni() {
    return JNIUnixDomainSocketLibrary.provider;
  }
  static UnixDomainSocketLibraryProvider jna() {
    return JNAUnixDomainSocketLibrary.provider;
  }
}
