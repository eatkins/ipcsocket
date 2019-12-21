/*

Copyright 2004-2015, Martian Software, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/
package org.scalasbt.ipcsocket;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;
import com.sun.jna.Union;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.scalasbt.ipcsocket.UnixDomainSocketLibrary.SockaddrUn;

/** Utility class to bridge native Unix domain socket calls to Java using JNA. */
public class UnixDomainSocketLibrary {
  public static final int PF_LOCAL = 1;
  public static final int AF_LOCAL = 1;
  public static final int SOCK_STREAM = 1;

  public static final int SHUT_RD = 0;
  public static final int SHUT_WR = 1;

  // Utility class, do not instantiate.
  private UnixDomainSocketLibrary() {}

  // BSD platforms write a length byte at the start of struct sockaddr_un.
  private static final boolean HAS_SUN_LEN =
      Platform.isMac()
          || Platform.isFreeBSD()
          || Platform.isNetBSD()
          || Platform.isOpenBSD()
          || Platform.iskFreeBSD();

  /** Bridges {@code struct sockaddr_un} to and from native code. */
  public static class SockaddrUn extends Structure implements Structure.ByReference {
    /**
     * On BSD platforms, the {@code sun_len} and {@code sun_family} values in {@code struct
     * sockaddr_un}.
     */
    public static class SunLenAndFamily extends Structure {
      public byte sunLen;
      public byte sunFamily;

      protected List getFieldOrder() {
        return Arrays.asList(new String[] {"sunLen", "sunFamily"});
      }
    }

    /**
     * On BSD platforms, {@code sunLenAndFamily} will be present. On other platforms, only {@code
     * sunFamily} will be present.
     */
    public static class SunFamily extends Union {
      public SunLenAndFamily sunLenAndFamily;
      public short sunFamily;
    }

    public SunFamily sunFamily = new SunFamily();
    public byte[] sunPath = new byte[104];

    /** Constructs an empty {@code struct sockaddr_un}. */
    public SockaddrUn() {
      if (HAS_SUN_LEN) {
        sunFamily.sunLenAndFamily = new SunLenAndFamily();
        sunFamily.setType(SunLenAndFamily.class);
      } else {
        sunFamily.setType(Short.TYPE);
      }
      allocateMemory();
    }

    /**
     * Constructs a {@code struct sockaddr_un} with a path whose bytes are encoded using the default
     * encoding of the platform.
     */
    public SockaddrUn(String path) throws IOException {
      byte[] pathBytes = path.getBytes();
      if (pathBytes.length > sunPath.length - 1) {
        throw new IOException(
            "Cannot fit name [" + path + "] in maximum unix domain socket length");
      }
      System.arraycopy(pathBytes, 0, sunPath, 0, pathBytes.length);
      sunPath[pathBytes.length] = (byte) 0;
      if (HAS_SUN_LEN) {
        int len = fieldOffset("sunPath") + pathBytes.length;
        sunFamily.sunLenAndFamily = new SunLenAndFamily();
        sunFamily.sunLenAndFamily.sunLen = (byte) len;
        sunFamily.sunLenAndFamily.sunFamily = AF_LOCAL;
        sunFamily.setType(SunLenAndFamily.class);
      } else {
        sunFamily.sunFamily = AF_LOCAL;
        sunFamily.setType(Short.TYPE);
      }
      allocateMemory();
    }

    protected List getFieldOrder() {
      return Arrays.asList(new String[] {"sunFamily", "sunPath"});
    }
  }

  private static final AtomicReference<UnixDomainSocketLibraryProvider> provider =
      new AtomicReference<>();

  private static UnixDomainSocketLibraryProvider provider() {
    final UnixDomainSocketLibraryProvider p = provider.get();
    if (p != null) return p;
    synchronized (provider) {
      if (provider.get() == null) provider.set(JNIUnixDomainSocketLibrary.provider);
    }
    return provider.get();
  }

  public static int socket(int domain, int type, int protocol) throws IOException {
    return provider().socket(domain, type, protocol);
  }

  public static int bind(int fd, String path) throws IOException {
    return provider().bind(fd, path);
  }

  public static int listen(int fd, int backlog) throws IOException {
    return provider().listen(fd, backlog);
  }

  public static int accept(int fd, String path) throws IOException {
    return provider().accept(fd, path);
  }

  public static int connect(int fd, String path) throws IOException {
    return provider().connect(fd, path);
  }

  public static int read(int fd, byte[] buffer, int count) throws IOException {
    return provider().read(fd, buffer, count);
  }

  public static int write(int fd, byte[] buffer, int count) throws IOException {
    return provider().write(fd, buffer, count);
  }

  public static int close(int fd) throws IOException {
    return provider().close(fd);
  }

  public static int shutdown(int fd, int how) throws IOException {
    return provider().close(fd);
  }
}

class JNAUnixDomainSocketLibrary {
  private static final AtomicBoolean isRegistered = new AtomicBoolean(false);
  private static final AtomicReference<JNAUnixDomainSocketLibrary> delegate =
      new AtomicReference<>();

  private static void register() {
    if (isRegistered.compareAndSet(false, true)) {
      Native.register(Platform.C_LIBRARY_NAME);
    }
  }

  private JNAUnixDomainSocketLibrary() {
    register();
  }

  private static JNAUnixDomainSocketLibrary library() {
    JNAUnixDomainSocketLibrary l = delegate.get();
    if (l != null) return l;
    synchronized (delegate) {
      if (delegate.get() == null) {
        delegate.set(new JNAUnixDomainSocketLibrary());
      }
    }
    return delegate.get();
  }

  static UnixDomainSocketLibraryProvider provider =
      new UnixDomainSocketLibraryProvider() {
        @Override
        public int socket(int domain, int type, int protocol) throws IOException {
          try {
            return library().socket(domain, type, protocol);
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int bind(int fd, String path) throws IOException {
          try {
            final SockaddrUn un = new SockaddrUn(path);
            return library().bind(fd, un, un.size());
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int listen(int fd, int backlog) throws IOException {
          try {
            return library().listen(fd, backlog);
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int accept(int fd, String path) throws IOException {
          try {
            SockaddrUn un = new SockaddrUn(path);
            IntByReference len = new IntByReference();
            final int result = library().accept(fd, un, len);
            return result;
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int connect(int fd, String path) throws IOException {
          try {
            final SockaddrUn un = new SockaddrUn(path);
            return library().connect(fd, un, un.size());
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int read(int fd, byte[] buffer, int len) throws IOException {
          try {
            return library().read(fd, buffer, len);
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int write(int fd, byte[] buffer, int len) throws IOException {
          try {
            return library().write(fd, buffer, len);
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int close(int fd) throws IOException {
          try {
            return library().close(fd);
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }

        @Override
        public int shutdown(int fd, int how) throws IOException {
          try {
            return library().shutdown(fd, how);
          } catch (final LastErrorException e) {
            throw new NativeErrorException(e.getErrorCode());
          }
        }
      };

  public native int socket(int domain, int type, int protocol) throws LastErrorException;

  public native int bind(int fd, SockaddrUn address, int addressLen) throws LastErrorException;

  public native int listen(int fd, int backlog) throws LastErrorException;

  public native int accept(int fd, SockaddrUn address, IntByReference addressLen)
      throws LastErrorException;

  public native int connect(int fd, SockaddrUn address, int addressLen) throws LastErrorException;

  public native int read(int fd, byte[] buffer, int count) throws LastErrorException;

  public native int write(int fd, byte[] buffer, int count) throws LastErrorException;

  public native int close(int fd) throws LastErrorException;

  public native int shutdown(int fd, int how) throws LastErrorException;
}

class JNIUnixDomainSocketLibrary {
  static {
    try {
      final ClassLoader loader = UnixDomainSocketLibrary.class.getClassLoader();
      final URL url = loader.getResource("darwin_x86_64/libsbtipcsocket.dylib");
      if (url == null) throw new NullPointerException();
      final Path output = Files.createTempFile("libsbtipcsocket", ".dylib");
      try (final InputStream in = url.openStream();
          final FileChannel channel = FileChannel.open(output, StandardOpenOption.WRITE)) {
        final byte[] buffer = new byte[4096];
        int read = 0;
        do {
          read = in.read(buffer);
          channel.write(ByteBuffer.wrap(buffer));
        } while (read > 0);
      }
      System.load(output.toString());
      output.toFile().deleteOnExit();
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    }
  }

  static final UnixDomainSocketLibraryProvider provider =
      new UnixDomainSocketLibraryProvider() {
        @Override
        public int socket(int domain, int type, int protocol) throws NativeErrorException {
          return JNIUnixDomainSocketLibrary.socket(domain, type, protocol);
        }

        @Override
        public int bind(int fd, String path) throws NativeErrorException {
          final byte[] bytes = path.getBytes();
          return JNIUnixDomainSocketLibrary.bind(fd, bytes, bytes.length);
        }

        @Override
        public int listen(int fd, int backlog) throws NativeErrorException {
          return JNIUnixDomainSocketLibrary.listen(fd, backlog);
        }

        @Override
        public int accept(int fd, String path) throws NativeErrorException {
          final byte[] bytes = path.getBytes();
          return JNIUnixDomainSocketLibrary.accept(fd, bytes, bytes.length);
        }

        @Override
        public int connect(int fd, String path) throws NativeErrorException {
          final byte[] bytes = path.getBytes();
          return JNIUnixDomainSocketLibrary.connect(fd, bytes, bytes.length);
        }

        @Override
        public int read(int fd, byte[] buffer, int len) throws NativeErrorException {
          return JNIUnixDomainSocketLibrary.read(fd, buffer, len);
        }

        @Override
        public int write(int fd, byte[] buffer, int len) throws NativeErrorException {
          return JNIUnixDomainSocketLibrary.write(fd, buffer, len);
        }

        @Override
        public int close(int fd) throws NativeErrorException {
          return JNIUnixDomainSocketLibrary.close(fd);
        }

        @Override
        public int shutdown(int fd, int how) throws NativeErrorException {
          return JNIUnixDomainSocketLibrary.shutdown(fd, how);
        }
      };

  public static native int socket(int domain, int type, int protocol) throws NativeErrorException;

  public static native int bind(int fd, byte[] path, int len) throws NativeErrorException;

  public static native int listen(int fd, int backlog) throws NativeErrorException;

  public static native int accept(int fd, byte[] path, int len) throws NativeErrorException;

  public static native int connect(int fd, byte[] path, int len) throws NativeErrorException;

  public static native int read(int fd, byte[] buffer, int len) throws NativeErrorException;

  public static native int write(int fd, byte[] buffer, int len) throws NativeErrorException;

  public static native int close(int fd) throws NativeErrorException;

  public static native int shutdown(int fd, int how) throws NativeErrorException;
}

class NativeErrorException extends IOException {
  final int errno;

  NativeErrorException(int errno) {
    super("NativeErrorException(" + errno + ")");
    this.errno = errno;
  }

  public int getErrorCode() {
    return errno;
  }
}

