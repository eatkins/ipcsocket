package org.scalasbt.ipcsocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.Random;

public class Win32NamedPipeSocketTest {
  public boolean useJNI() {
    return false;
  }

  boolean skip() {
    String os = System.getProperty("os.name", "").toLowerCase();
    return os.startsWith("mac") || os.startsWith("linux");
  }

  @Test
  public void testAssertEquals() throws IOException, InterruptedException {
    if (skip()) return;
    Random rand = new Random();
    String pipeName = "\\\\.\\pipe\\ipcsockettest" + rand.nextInt();
    ServerSocket serverSocket = new Win32NamedPipeServerSocket(useJNI(), pipeName);
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

    Socket client = new Win32NamedPipeSocket(pipeName, useJNI());
    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    out.println("hello");
    String line = in.readLine();
    System.out.println("windows client: " + line);
    client.close();
    server.cancel(true);
    serverSocket.close();
    assertEquals("echo did not return the content", line, "hello");
  }

  @Test
  public void throwIOExceptionOnMissingFile() {
    if (skip()) return;
    boolean caughtIOException = false;
    try {
      Random rand = new Random();
      String pipeName = "\\\\.\\pipe\\nopipetest" + rand.nextInt();
      Socket client = new Win32NamedPipeSocket(pipeName, useJNI());
      client.getInputStream().read();
    } catch (final IOException e) {
      System.out.println(e.getMessage());
      caughtIOException = true;
    }
    assertTrue("No io exception was caught", caughtIOException);
  }
  @Test
  public void shortReadWrite() throws IOException, InterruptedException {
    if (skip()) return;
    Random rand = new Random();
    String pipeName = "\\\\.\\pipe\\ipcsockettest" + rand.nextInt();
    ServerSocket serverSocket = new Win32NamedPipeServerSocket(useJNI(), pipeName);
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

    Socket client = new Win32NamedPipeSocket(pipeName, useJNI());
    OutputStream out = client.getOutputStream();
    InputStream in = client.getInputStream();
    String printed = "hellofoo\n";
    byte[] printedBytes = printed.getBytes("UTF-8");
    out.write(printedBytes, 0, 4);
    out.write(printedBytes, 4, 5);
    byte[] buf = new byte[16];
    assertEquals("Did not read 4 bytes", in.read(buf, 0, 4), 4);
    assertEquals("Did not read 5 bytes", in.read(buf, 4, 5), 5);
    String line = new String(buf, 0, printed.length(), "UTF-8");
    client.close();
    server.cancel(true);
    serverSocket.close();
    assertEquals("echo did not return the content", line, printed);
  }
}
