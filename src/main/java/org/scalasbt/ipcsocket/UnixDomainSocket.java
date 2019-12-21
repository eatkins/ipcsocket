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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements a {@link Socket} backed by a native Unix domain socket.
 *
 * <p>Instances of this class always return {@code null} for {@link Socket#getInetAddress()}, {@link
 * Socket#getLocalAddress()}, {@link Socket#getLocalSocketAddress()}, {@link
 * Socket#getRemoteSocketAddress()}.
 */
public class UnixDomainSocket extends Socket {
  private final ReferenceCountedFileDescriptor fd;
  private final InputStream is;
  private final OutputStream os;

  /** Creates a Unix domain socket backed by a file path. */
  public UnixDomainSocket(String path) throws IOException {
    try {
      AtomicInteger fd =
          new AtomicInteger(
              UnixDomainSocketLibrary.socket(
                  UnixDomainSocketLibrary.PF_LOCAL, UnixDomainSocketLibrary.SOCK_STREAM, 0));
      int socketFd = fd.get();
      UnixDomainSocketLibrary.connect(socketFd, path);
      this.fd = new ReferenceCountedFileDescriptor(socketFd);
      this.is = new UnixDomainSocketInputStream();
      this.os = new UnixDomainSocketOutputStream();
    } catch (LastErrorException e) {
      throw new IOException(e);
    }
  }

  /** Creates a Unix domain socket backed by a native file descriptor. */
  public UnixDomainSocket(int fd) {
    this.fd = new ReferenceCountedFileDescriptor(fd);
    this.is = new UnixDomainSocketInputStream();
    this.os = new UnixDomainSocketOutputStream();
  }

  public InputStream getInputStream() {
    return is;
  }

  public OutputStream getOutputStream() {
    return os;
  }

  public void shutdownInput() throws IOException {
    doShutdown(UnixDomainSocketLibrary.SHUT_RD);
  }

  public void shutdownOutput() throws IOException {
    doShutdown(UnixDomainSocketLibrary.SHUT_WR);
  }

  private void doShutdown(int how) throws IOException {
    try {
      int socketFd = fd.acquire();
      if (socketFd != -1) {
        UnixDomainSocketLibrary.shutdown(socketFd, how);
      }
    } catch (NativeErrorException e) {
      throw new IOException(e);
    } finally {
      fd.release();
    }
  }

  public void close() throws IOException {
    super.close();
    fd.close();
  }

  private class UnixDomainSocketInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      byte[] res = new byte[1];
      int read = read(res, 0, 1);
      return read == 0 ? -1 : res[0] & 0xFF;
    }

    @Override
    public int read(byte[] b) throws IOException {
      return read(b, 0, b.length);
      }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      byte[] buf = off == 0 ? b : new byte[len];
      int result = doRead(buf);
      if (result == 0) {
        result = -1;
      }
      if (off > 0) {
        for (int i = 0; i < result; ++i) {
          b[off + i] = buf[i];
        }
      }
      return result;
    }

    private int doRead(byte[] buf) throws IOException {
      try {
        int fdToRead = fd.acquire();
        if (fdToRead == -1) {
          return -1;
        }
        return UnixDomainSocketLibrary.read(fdToRead, buf, buf.length);
      } catch (LastErrorException e) {
        throw new IOException(e);
      } finally {
        fd.release();
      }
    }
  }

  private class UnixDomainSocketOutputStream extends OutputStream {

    @Override
    public void write(int b) throws IOException {
      byte[] buf = new byte[] {(byte) b};
      doWrite(buf);
    }

    @Override
    public void write(byte[] b) throws IOException {
      write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return;
      }
      byte[] buf = off == 0 ? b : new byte[len];
      if (off > 0) {
        for (int i = 0; i < len; ++i) {
          buf[i] = b[i + off];
        }
      }
      doWrite(buf);
    }

    private void doWrite(byte[] buf) throws IOException {
      try {
        int fdToWrite = fd.acquire();
        if (fdToWrite == -1) {
          return;
        }
        int ret = UnixDomainSocketLibrary.write(fdToWrite, buf, buf.length);
        if (ret != buf.length) {
          // This shouldn't happen with standard blocking Unix domain sockets.
          throw new IOException(
              "Could not write "
                  + buf.length
                  + " bytes as requested "
                  + "(wrote "
                  + ret
                  + " bytes instead)");
        }
      } catch (NativeErrorException e) {
        throw new IOException(e);
      } finally {
        fd.release();
      }
    }
  }
}
