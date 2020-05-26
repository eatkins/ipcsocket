package org.scalasbt.ipcsocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.Random;

public class UnixDomainSocketTest {
  boolean useJNI() {
    return false;
  }

  @Test
  public void testAssertEquals() throws IOException, InterruptedException {
    if (System.getProperty("os.name", "").toLowerCase().startsWith("win")) return;
    Random rand = new Random();
    Path tempDir = Files.createTempDirectory("ipcsocket");
    Path sock = tempDir.resolve("foo" + rand.nextInt() + ".sock");
    ServerSocket serverSocket = new UnixDomainServerSocket(sock.toString(), useJNI());

    CompletableFuture<Boolean> server =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                EchoServer echo = new EchoServer(serverSocket);
                echo.run();
              } catch (IOException e) {
              }
              return true;
            });
    Thread.sleep(100);

    Socket client = new UnixDomainSocket(sock.toString(), useJNI());
    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    out.println("hello");
    String line = in.readLine();
    client.close();
    server.cancel(true);
    serverSocket.close();
    assertEquals("echo did not return the content", line, "hello");
  }

  @Test
  public void shortReadWrite() throws IOException, InterruptedException {
    if (System.getProperty("os.name", "").toLowerCase().startsWith("win")) return;
    Random rand = new Random();
    Path tempDir = Files.createTempDirectory("ipcsocket");
    Path sock = tempDir.resolve("foo" + rand.nextInt() + ".sock");
    ServerSocket serverSocket = new UnixDomainServerSocket(sock.toString(), useJNI());

    CompletableFuture<Boolean> server =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                EchoServer echo = new EchoServer(serverSocket);
                echo.run();
              } catch (IOException e) {
              }
              return true;
            });
    Thread.sleep(100);

    Socket client = new UnixDomainSocket(sock.toString(), useJNI());
    OutputStream out = client.getOutputStream();
    InputStream in = client.getInputStream();
    String printed = "hellofoo\n";
    byte[] printedBytes = printed.getBytes();
    out.write(printedBytes, 0, 4);
    out.write(printedBytes, 4, 5);
    byte[] buf = new byte[16];
    assertEquals("Did not read 4 bytes", in.read(buf, 0, 4), 4);
    assertEquals("Did not read 5 bytes", in.read(buf, 4, 6), 5);
    String line = new String(buf, 0, printed.length());
    client.close();
    server.cancel(true);
    serverSocket.close();
    assertEquals("echo did not return the content", line, printed);
  }
}
