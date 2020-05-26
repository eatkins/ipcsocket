package org.scalasbt.ipcsocket;

class JNIUnixDomainSocketLibraryProvider implements UnixDomainSocketLibraryProvider {
  private static final JNIUnixDomainSocketLibraryProvider instance =
      new JNIUnixDomainSocketLibraryProvider();

  static final JNIUnixDomainSocketLibraryProvider instance() {
    return instance;
  }

  public native int socket(int domain, int type, int protocol) throws NativeErrorException;

  public native int bind(int fd, byte[] address, int addressLen) throws NativeErrorException;

  public native int listen(int fd, int backlog) throws NativeErrorException;

  public native int accept(int fd) throws NativeErrorException;

  public native int connect(int fd, byte[] address, int len) throws NativeErrorException;

  public native int read(int fd, byte[] buffer, int count) throws NativeErrorException;

  public native int write(int fd, byte[] buffer, int count) throws NativeErrorException;

  public native int close(int fd) throws NativeErrorException;

  public native int shutdown(int fd, int how) throws NativeErrorException;

  static {
    NativeLoader.load();
  }
}
