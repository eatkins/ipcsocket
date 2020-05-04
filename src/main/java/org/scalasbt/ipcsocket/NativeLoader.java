package org.scalasbt.ipcsocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

class NativeLoader {
  private static final AtomicBoolean loaded = new AtomicBoolean(false);

  static void load() throws UnsatisfiedLinkError {
    if (!loaded.get()) {
      final String os = System.getProperty("os.name", "").toLowerCase();
      final boolean isMac = os.startsWith("mac");
      final boolean isLinux = os.startsWith("linux");
      final boolean isWindows = os.startsWith("windows");
      final boolean is64bit = System.getProperty("sun.arch.data.model", "64").equals("64");
      String tmpDir =
          System.getProperty("sbt.ipcsocket.tmpdir", System.getProperty("java.io.tmpdir"));
      if (is64bit && (isMac || isLinux || isWindows)) {
        final String extension = "." + (isMac ? "dylib" : isWindows ? "dll" : "so");
        final String libName = (isWindows ? "" : "lib") + "sbtipcsocket" + extension;
        final String prefix = isMac ? "darwin" : isLinux ? "linux" : "win32";

        final String resource = prefix + "/x86_64/" + libName;
        final URL url = NativeLoader.class.getClassLoader().getResource(resource);
        if (url == null) throw new UnsatisfiedLinkError(resource + " not found on classpath");
        try {
          final Path base =
              tmpDir == null ? Files.createTempDirectory("sbtipcsocket") : Paths.get(tmpDir);
          final Path output = Files.createTempFile(base, "libsbtipcsocket", extension);
          try (final InputStream in = url.openStream();
              final FileChannel channel = FileChannel.open(output, StandardOpenOption.WRITE)) {
            int total = 0;
            int read = 0;
            byte[] buffer = new byte[1024];
            do {
              read = in.read(buffer);
              if (read > 0) channel.write(ByteBuffer.wrap(buffer, 0, read));
            } while (read > 0);
            channel.close();
          } catch (final IOException ex) {
            throw new UnsatisfiedLinkError();
          }
          output.toFile().deleteOnExit();
          try {
            System.load(output.toString());
          } catch (final UnsatisfiedLinkError e) {
            Files.deleteIfExists(output);
            throw e;
          }
          loaded.set(true);
          return;
        } catch (final IOException e) {
          throw new UnsatisfiedLinkError(e.getMessage());
        }
      }
      throw new UnsatisfiedLinkError();
    }
  }
}
