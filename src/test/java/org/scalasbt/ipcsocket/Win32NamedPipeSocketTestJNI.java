package org.scalasbt.ipcsocket;

public class Win32NamedPipeSocketTestJNI extends Win32NamedPipeSocketTest {
  @Override
  public boolean useJNI() {
    return true;
  }
}
